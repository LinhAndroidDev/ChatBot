package com.example.chatbot.data.repository

import com.example.chatbot.data.model.OllamaMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun chatStream(messages: List<OllamaMessage>): Flow<ChatStreamEvent>
}
