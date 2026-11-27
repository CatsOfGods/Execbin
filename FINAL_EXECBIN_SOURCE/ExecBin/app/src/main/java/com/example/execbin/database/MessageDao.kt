package com.example.execbin.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CachedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CachedMessage>)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<CachedMessage>>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteChatMessages(chatId: String)

    @Query("UPDATE messages SET read = 1 WHERE messageId IN (:messageIds)")
    suspend fun markAsRead(messageIds: List<String>)

    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<CachedMessage>

    @Query("SELECT messageId FROM messages WHERE chatId = :chatId AND read = 0 AND senderId != :myUid")
    suspend fun getUnreadMessageIds(chatId: String, myUid: String): List<String>
}