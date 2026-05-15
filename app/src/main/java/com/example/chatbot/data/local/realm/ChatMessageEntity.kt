package com.example.chatbot.data.local.realm

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class ChatMessageEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var sessionId: String = ""
    var speakerOrdinal: Int = 0
    var content: String = ""
    var sentAtMillis: Long = 0L
}
