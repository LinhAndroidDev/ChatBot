package com.example.chatbot.data.model

data class OllamaMessage(
    val role: String,
    val content: String,
)

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean,
)

data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaMessage,
    val done: Boolean? = null,
)
