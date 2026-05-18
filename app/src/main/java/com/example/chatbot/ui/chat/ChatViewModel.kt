package com.example.chatbot.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.local.ChatSessionStore
import com.example.chatbot.data.model.OllamaMessage
import com.example.chatbot.data.repository.ChatRepository
import com.example.chatbot.data.repository.ChatStreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val sessionStore: ChatSessionStore,
) : ViewModel() {

    private val systemMessage = OllamaMessage(
        role = "system",
        content = """
        Bạn là trợ lý AI chuyên trả lời bằng tiếng Việt.
        
        QUY TẮC BẮT BUỘC:
        - Chỉ được trả lời bằng tiếng Việt.
        - Không được dùng tiếng Anh.
        - Không được dùng tiếng Trung.
        - Không được trả lời đa ngôn ngữ.
        - Nếu có code thì chỉ code giữ nguyên ngôn ngữ lập trình.
        - Mọi giải thích phải bằng tiếng Việt.
        - Nếu lỡ sinh ra ngôn ngữ khác thì phải tự sửa lại sang tiếng Việt.
        """.trimIndent()
    )

    private var currentSessionId: String? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _recentSessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    val recentSessions: StateFlow<List<ChatSessionSummary>> = _recentSessions.asStateFlow()

    private val _ttsPrompts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ttsPrompts: SharedFlow<String> = _ttsPrompts.asSharedFlow()

    init {
        viewModelScope.launch {
            val id = ensureSessionId()
            val loaded = sessionStore.loadMessages(id)
            _uiState.update { it.copy(messages = loaded) }
            refreshRecentSessionsInternal()
        }
    }

    private suspend fun ensureSessionId(): String {
        currentSessionId?.let { return it }
        val id = sessionStore.getLatestSessionIdOrCreate()
        currentSessionId = id
        return id
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshRecentSessions() {
        viewModelScope.launch { refreshRecentSessionsInternal() }
    }

    private suspend fun refreshRecentSessionsInternal() {
        _recentSessions.value = sessionStore.recentSessions()
    }

    fun startNewChat() {
        viewModelScope.launch {
            val id = sessionStore.createSession("")
            currentSessionId = id
            _uiState.value = ChatUiState()
            refreshRecentSessionsInternal()
        }
    }

    fun openSession(sessionId: String) {
        viewModelScope.launch {
            currentSessionId = sessionId
            val loaded = sessionStore.loadMessages(sessionId)
            _uiState.update { ChatUiState(messages = loaded) }
            refreshRecentSessionsInternal()
        }
    }

    fun sendUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val sid = ensureSessionId()
            sessionStore.clearAllShowRetryInSession(sid)
            val now = System.currentTimeMillis()
            val userMessage = ChatListMessage(
                id = newMessageId(),
                speaker = ChatSpeaker.USER,
                content = trimmed,
                sentAtMillis = now,
            )
            val willBeFirst = _uiState.value.messages.isEmpty()
            _uiState.update {
                val cleared = it.messages.map { m ->
                    if (m.speaker == ChatSpeaker.USER && m.showRetry) m.copy(showRetry = false) else m
                }
                it.copy(
                    messages = cleared + userMessage,
                    isLoading = true,
                    error = null,
                )
            }
            sessionStore.appendMessage(sid, userMessage)
            if (willBeFirst) {
                sessionStore.touchSession(sid, trimmed.take(56))
            } else {
                sessionStore.touchSession(sid, null)
            }
            refreshRecentSessionsInternal()

            val assistantId = newMessageId()
            val replyAt = System.currentTimeMillis()
            val assistantPlaceholder = ChatListMessage(
                id = assistantId,
                speaker = ChatSpeaker.ASSISTANT,
                content = "",
                sentAtMillis = replyAt,
                isStreamingMarkdown = true,
            )
            _uiState.update {
                it.copy(messages = it.messages + assistantPlaceholder)
            }

            runAssistantStream(sid, assistantId, replyAt, userMessage.id)
        }
    }

    fun retrySend(userMessageId: String) {
        viewModelScope.launch {
            if (_uiState.value.isLoading) return@launch
            val sid = ensureSessionId()
            val msgs = _uiState.value.messages
            val idx = msgs.indexOfFirst { it.id == userMessageId && it.speaker == ChatSpeaker.USER }
            if (idx < 0) return@launch
            val user = msgs[idx]
            if (!user.showRetry) return@launch
            val head = msgs.take(idx + 1).map { m ->
                if (m.id == userMessageId) m.copy(showRetry = false) else m
            }
            _uiState.update {
                it.copy(messages = head, error = null, isLoading = true)
            }
            sessionStore.setMessageShowRetry(sid, userMessageId, false)

            val assistantId = newMessageId()
            val replyAt = System.currentTimeMillis()
            val assistantPlaceholder = ChatListMessage(
                id = assistantId,
                speaker = ChatSpeaker.ASSISTANT,
                content = "",
                sentAtMillis = replyAt,
                isStreamingMarkdown = true,
            )
            _uiState.update {
                it.copy(messages = it.messages + assistantPlaceholder)
            }

            runAssistantStream(sid, assistantId, replyAt, userMessageId)
        }
    }

    private suspend fun runAssistantStream(
        sid: String,
        assistantId: String,
        replyAt: Long,
        userMessageIdForRetryOnFail: String,
    ) {
        var receivedAnyChunk = false
        try {
            repository.chatStream(buildApiMessages()).collect { event ->
                when (event) {
                    is ChatStreamEvent.Chunk -> {
                        receivedAnyChunk = true
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == assistantId) {
                                        msg.copy(
                                            content = event.accumulatedText,
                                            isStreamingMarkdown = true,
                                        )
                                    } else {
                                        msg
                                    }
                                },
                            )
                        }
                    }
                    is ChatStreamEvent.Done -> {
                        val finalText = event.fullText
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    when {
                                        msg.id == assistantId -> msg.copy(
                                            content = finalText,
                                            isStreamingMarkdown = false,
                                        )
                                        msg.speaker == ChatSpeaker.USER -> msg.copy(showRetry = false)
                                        else -> msg
                                    }
                                },
                                isLoading = false,
                                error = null,
                            )
                        }
                        val assistantMessage = ChatListMessage(
                            id = assistantId,
                            speaker = ChatSpeaker.ASSISTANT,
                            content = finalText,
                            sentAtMillis = replyAt,
                            isStreamingMarkdown = false,
                        )
                        sessionStore.appendMessage(sid, assistantMessage)
                        sessionStore.clearAllShowRetryInSession(sid)
                        sessionStore.touchSession(sid, null)
                        refreshRecentSessionsInternal()
                        if (finalText.isNotBlank()) {
                            _ttsPrompts.tryEmit(finalText)
                        }
                    }
                    is ChatStreamEvent.Failed -> {
                        _uiState.update { state ->
                            val trimmed =
                                if (!receivedAnyChunk) {
                                    state.messages.filterNot { it.id == assistantId }
                                } else {
                                    state.messages.map { msg ->
                                        if (msg.id == assistantId && msg.speaker == ChatSpeaker.ASSISTANT) {
                                            msg.copy(isStreamingMarkdown = false)
                                        } else {
                                            msg
                                        }
                                    }
                                }
                            val withRetry = trimmed.map { msg ->
                                if (msg.id == userMessageIdForRetryOnFail && msg.speaker == ChatSpeaker.USER) {
                                    msg.copy(showRetry = true)
                                } else {
                                    msg
                                }
                            }
                            state.copy(
                                messages = withRetry,
                                isLoading = false,
                                error = event.error.message ?: event.error.toString(),
                            )
                        }
                        sessionStore.setMessageShowRetry(sid, userMessageIdForRetryOnFail, true)
                    }
                }
            }
        } finally {
            _uiState.update { state ->
                if (!state.isLoading) return@update state
                val cleaned = state.messages.filterNot {
                    it.id == assistantId &&
                        it.speaker == ChatSpeaker.ASSISTANT &&
                        it.content.isEmpty()
                }
                state.copy(messages = cleaned, isLoading = false)
            }
        }
    }

    private fun buildApiMessages(): List<OllamaMessage> {
        val tail = _uiState.value.messages.map { msg ->
            OllamaMessage(
                role = when (msg.speaker) {
                    ChatSpeaker.USER -> "user"
                    ChatSpeaker.ASSISTANT -> "assistant"
                },
                content = msg.content,
            )
        }
        return listOf(systemMessage) + tail
    }

    private fun newMessageId(): String = UUID.randomUUID().toString()
}
