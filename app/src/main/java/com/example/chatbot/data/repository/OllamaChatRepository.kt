package com.example.chatbot.data.repository

import com.example.chatbot.data.model.OllamaChatRequest
import com.example.chatbot.data.model.OllamaChatResponse
import com.example.chatbot.data.model.OllamaMessage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OllamaChatRepository @Inject constructor(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    @param:Named("ollama_chat_url") private val chatUrl: String,
    @param:Named("ollama_model") private val model: String,
) : ChatRepository {

    override suspend fun chat(messages: List<OllamaMessage>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = moshi.adapter(OllamaChatRequest::class.java)
                .toJson(OllamaChatRequest(model = model, messages = messages, stream = false))
            val request = Request.Builder()
                .url(chatUrl)
                .post(bodyJson.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${raw.ifBlank { response.message }}"),
                    )
                }
                val parsed = moshi.adapter(OllamaChatResponse::class.java).fromJson(raw)
                    ?: return@withContext Result.failure(IOException("Không đọc được phản hồi JSON"))
                Result.success(parsed.message.content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
