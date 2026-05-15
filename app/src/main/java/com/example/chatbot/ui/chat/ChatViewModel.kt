package com.example.chatbot.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.local.ChatSessionStore
import com.example.chatbot.data.model.OllamaMessage
import com.example.chatbot.data.repository.ChatRepository
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
        content = "Bạn là trợ lý AI nói tiếng Việt.",
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
            val now = System.currentTimeMillis()
            val userMessage = ChatListMessage(
                id = newMessageId(),
                speaker = ChatSpeaker.USER,
                content = trimmed,
                sentAtMillis = now,
            )
            val willBeFirst = _uiState.value.messages.isEmpty()
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
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

            val apiMessages = buildApiMessages()
            val result = repository.chat(apiMessages)
            result.fold(
                onSuccess = { reply ->
                    val replyAt = System.currentTimeMillis()
                    val assistantMessage = ChatListMessage(
                        id = newMessageId(),
                        speaker = ChatSpeaker.ASSISTANT,
                        content = reply,
                        sentAtMillis = replyAt,
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false,
                        )
                    }
                    sessionStore.appendMessage(sid, assistantMessage)
                    sessionStore.touchSession(sid, null)
                    refreshRecentSessionsInternal()
                    _ttsPrompts.tryEmit(reply)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: e.toString(),
                        )
                    }
                },
            )
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
