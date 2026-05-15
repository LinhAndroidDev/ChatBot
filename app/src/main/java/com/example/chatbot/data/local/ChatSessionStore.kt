package com.example.chatbot.data.local

import com.example.chatbot.data.local.realm.ChatMessageEntity
import com.example.chatbot.data.local.realm.ChatSessionEntity
import com.example.chatbot.ui.chat.ChatListMessage
import com.example.chatbot.ui.chat.ChatSessionSummary
import com.example.chatbot.ui.chat.ChatSpeaker
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSessionStore @Inject constructor(
    private val configuration: RealmConfiguration,
) {

    /**
     * Mở Realm sau khi graph Hilt đã xong — tránh [Realm.open] trong @Provides gây reentrancy DoubleCheck.
     */
    private val realm: Realm by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Realm.open(configuration) }

    suspend fun getLatestSessionIdOrCreate(): String = withContext(Dispatchers.IO) {
        val latest = realm.query<ChatSessionEntity>()
            .sort("updatedAtMillis", Sort.DESCENDING)
            .first()
            .find()
        if (latest != null) return@withContext latest.id
        createSessionInternal("")
    }

    suspend fun createSession(title: String): String = withContext(Dispatchers.IO) {
        createSessionInternal(title)
    }

    private suspend fun createSessionInternal(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        realm.write {
            copyToRealm(
                ChatSessionEntity().apply {
                    this.id = id
                    this.title = title.ifBlank { "Chat mới" }
                    this.createdAtMillis = now
                    this.updatedAtMillis = now
                },
            )
        }
        return id
    }

    suspend fun touchSession(sessionId: String, title: String?) = withContext(Dispatchers.IO) {
        realm.write {
            val session = query<ChatSessionEntity>("id == $0", sessionId).first().find() ?: return@write
            session.updatedAtMillis = System.currentTimeMillis()
            if (!title.isNullOrBlank()) {
                session.title = title.trim().take(TITLE_MAX)
            }
        }
    }

    suspend fun appendMessage(sessionId: String, message: ChatListMessage) = withContext(Dispatchers.IO) {
        realm.write {
            copyToRealm(
                ChatMessageEntity().apply {
                    id = message.id
                    this.sessionId = sessionId
                    speakerOrdinal = when (message.speaker) {
                        ChatSpeaker.USER -> 0
                        ChatSpeaker.ASSISTANT -> 1
                    }
                    content = message.content
                    sentAtMillis = message.sentAtMillis
                },
            )
            val session = query<ChatSessionEntity>("id == $0", sessionId).first().find()
            if (session != null) {
                session.updatedAtMillis = System.currentTimeMillis()
            }
        }
    }

    suspend fun loadMessages(sessionId: String): List<ChatListMessage> = withContext(Dispatchers.IO) {
        realm.query<ChatMessageEntity>("sessionId == $0", sessionId)
            .sort("sentAtMillis", Sort.ASCENDING)
            .find()
            .map { it.toUi() }
    }

    suspend fun recentSessions(limit: Int = 40): List<ChatSessionSummary> = withContext(Dispatchers.IO) {
        realm.query<ChatSessionEntity>()
            .sort("updatedAtMillis", Sort.DESCENDING)
            .limit(limit)
            .find()
            .map {
                ChatSessionSummary(
                    id = it.id,
                    title = it.title.ifBlank { "Chat mới" },
                    updatedAtMillis = it.updatedAtMillis,
                )
            }
    }

    private fun ChatMessageEntity.toUi(): ChatListMessage {
        val speaker = when (speakerOrdinal) {
            0 -> ChatSpeaker.USER
            else -> ChatSpeaker.ASSISTANT
        }
        return ChatListMessage(
            id = id,
            speaker = speaker,
            content = content,
            sentAtMillis = sentAtMillis,
        )
    }

    companion object {
        private const val TITLE_MAX = 56
    }
}
