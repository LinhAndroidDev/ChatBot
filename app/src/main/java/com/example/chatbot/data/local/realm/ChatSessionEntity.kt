package com.example.chatbot.data.local.realm

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class ChatSessionEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var title: String = ""
    var createdAtMillis: Long = 0L
    var updatedAtMillis: Long = 0L
}
