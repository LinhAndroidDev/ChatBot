package com.example.chatbot.data.repository

import com.example.chatbot.data.model.OllamaChatRequest
import com.example.chatbot.data.model.OllamaChatResponse
import com.example.chatbot.data.model.OllamaMessage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext
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

    override fun chatStream(messages: List<OllamaMessage>): Flow<ChatStreamEvent> = flow {
        val bodyJson = moshi.adapter(OllamaChatRequest::class.java)
            .toJson(OllamaChatRequest(model = model, messages = messages, stream = true))
        val request = Request.Builder()
            .url(chatUrl)
            .post(bodyJson.toRequestBody(JSON))
            .build()
        val call = client.newCall(request)
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    emit(
                        ChatStreamEvent.Failed(
                            IOException("HTTP ${response.code}: ${raw.ifBlank { response.message }}"),
                        ),
                    )
                    return@use
                }
                val source = response.body?.source()
                    ?: run {
                        emit(ChatStreamEvent.Failed(IOException("Không có nội dung phản hồi")))
                        return@use
                    }
                val adapter = moshi.adapter(OllamaChatResponse::class.java)
                var accumulated = ""
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val chunk = try {
                        adapter.fromJson(line)
                    } catch (_: Exception) {
                        continue
                    } ?: continue
                    val delta = chunk.message.content
                    if (delta.isNotEmpty()) {
                        accumulated += delta
                        emit(ChatStreamEvent.Chunk(accumulated))
                    }
                    if (chunk.done == true) {
                        emit(ChatStreamEvent.Done(accumulated))
                        return@use
                    }
                }
                emit(ChatStreamEvent.Done(accumulated))
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) {
                emit(ChatStreamEvent.Failed(e))
            }
        } catch (e: Exception) {
            emit(ChatStreamEvent.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
