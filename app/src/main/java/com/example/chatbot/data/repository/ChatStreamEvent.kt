package com.example.chatbot.data.repository

sealed interface ChatStreamEvent {
    data class Chunk(val accumulatedText: String) : ChatStreamEvent
    data class Done(val fullText: String) : ChatStreamEvent
    data class Failed(val error: Throwable) : ChatStreamEvent
}
