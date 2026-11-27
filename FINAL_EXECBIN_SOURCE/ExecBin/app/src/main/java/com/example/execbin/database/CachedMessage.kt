package com.example.execbin.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class CachedMessage(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val text: String,
    val senderId: String,
    val username: String,
    val nickname: String,
    val photoBase64: String,
    val replyToText: String,
    val replyToUsername: String,
    val timestamp: Long,
    val isEncrypted: Boolean = true,
    val read: Boolean = false,
    val edited: Boolean = false,
    val mediaUrl: String = "",
    val mediaType: String = "",
    val mediaDuration: Long = 0,
    val reactionsStr: String = ""  // Comma-separated "uid:emoji" entries
)