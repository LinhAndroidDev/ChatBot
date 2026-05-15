package com.example.chatbot.data.repository

import com.example.chatbot.data.model.OllamaMessage

interface ChatRepository {
    suspend fun chat(messages: List<OllamaMessage>): Result<String>
}
