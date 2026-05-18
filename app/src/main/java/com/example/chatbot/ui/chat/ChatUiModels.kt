package com.example.chatbot.ui.chat

enum class ChatSpeaker {
    USER,
    ASSISTANT,
}

data class ChatListMessage(
    val id: String,
    val speaker: ChatSpeaker,
    val content: String,
    val sentAtMillis: Long,
    /** Khi true: chỉ hiển thị text thường (stream); khi false: render Markdown đầy đủ. */
    val isStreamingMarkdown: Boolean = false,
    /** Tin user: hiện link Thử lại sau khi gửi bị lỗi. */
    val showRetry: Boolean = false,
)

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
)

data class ChatUiState(
    val messages: List<ChatListMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
