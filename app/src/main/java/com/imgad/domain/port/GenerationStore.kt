package com.imgad.domain.port

import com.imgad.domain.model.Asset
import com.imgad.domain.model.Message
import kotlinx.coroutines.flow.Flow

data class GenerationTask(
    val messageId: String,
    val sessionId: String,
)

interface GenerationStore {
    suspend fun beginTask(
        sessionId: String,
        title: String,
        prompt: String,
        requestSnapshotJson: String,
        inputAssets: List<Asset>,
        now: Long,
    ): GenerationTask

    suspend fun markSucceeded(messageId: String, outputAssets: List<Asset>, now: Long)

    suspend fun markFailed(messageId: String, errorJson: String, now: Long)

    suspend fun markCanceled(messageId: String, now: Long)

    suspend fun getMessage(messageId: String): Message?

    suspend fun messagesForSession(sessionId: String): List<Message>

    fun observeSessionContent(sessionId: String): Flow<SessionContent>

    suspend fun runningTasks(): List<Message>
}
