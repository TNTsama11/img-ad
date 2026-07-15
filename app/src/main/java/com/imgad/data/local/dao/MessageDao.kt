package com.imgad.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.imgad.data.local.entity.MessageEntity
import com.imgad.domain.model.TaskState
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE taskState = :taskState ORDER BY updatedAt ASC")
    fun observeByTaskState(taskState: TaskState): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)
}
