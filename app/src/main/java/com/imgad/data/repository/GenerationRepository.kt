package com.imgad.data.repository

import androidx.room.withTransaction
import com.imgad.data.local.AppDatabase
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.MessageEntity
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.Asset
import com.imgad.domain.model.Message
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import com.imgad.domain.port.GenerationStore
import com.imgad.domain.port.GenerationTask
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.imgad.domain.port.SessionContent

class GenerationRepository(
    private val database: AppDatabase,
) : GenerationStore {
    override suspend fun beginTask(
        sessionId: String,
        title: String,
        prompt: String,
        requestSnapshotJson: String,
        inputAssets: List<Asset>,
        now: Long,
    ): GenerationTask {
        val messageId = UUID.randomUUID().toString()
        database.withTransaction {
            val existing = database.sessionDao().getById(sessionId)
            check(existing?.deletedAt == null) { "Deleted sessions cannot receive new tasks" }
            database.sessionDao().upsert(
                existing?.copy(title = title, updatedAt = now)
                    ?: SessionEntity(sessionId, title, createdAt = now, updatedAt = now),
            )
            database.messageDao().upsert(
                MessageEntity(
                    id = messageId,
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    text = prompt,
                    taskState = TaskState.RUNNING,
                    requestSnapshotJson = requestSnapshotJson,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.assetDao().upsertAll(inputAssets.map { it.toEntity(messageId, now) })
        }
        return GenerationTask(messageId, sessionId)
    }

    override suspend fun markSucceeded(messageId: String, outputAssets: List<Asset>, now: Long) {
        database.withTransaction {
            val current = requireNotNull(database.messageDao().getById(messageId))
            database.messageDao().upsert(current.copy(taskState = TaskState.SUCCEEDED, updatedAt = now))
            val assistantMessageId = UUID.randomUUID().toString()
            database.messageDao().upsert(
                MessageEntity(
                    id = assistantMessageId,
                    sessionId = current.sessionId,
                    role = MessageRole.ASSISTANT,
                    text = "",
                    taskState = TaskState.SUCCEEDED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.assetDao().upsertAll(outputAssets.map { it.toEntity(assistantMessageId, now) })
        }
    }

    override suspend fun markFailed(messageId: String, errorJson: String, now: Long) {
        database.withTransaction {
            val current = requireNotNull(database.messageDao().getById(messageId))
            database.messageDao().upsert(
                current.copy(taskState = TaskState.FAILED, errorJson = errorJson, updatedAt = now),
            )
        }
    }

    override suspend fun markCanceled(messageId: String, now: Long) {
        database.withTransaction {
            val current = requireNotNull(database.messageDao().getById(messageId))
            database.messageDao().upsert(current.copy(taskState = TaskState.CANCELED, updatedAt = now))
        }
    }

    override suspend fun getMessage(messageId: String): Message? = database.messageDao().getById(messageId)?.toDomain()

    override suspend fun messagesForSession(sessionId: String): List<Message> =
        database.messageDao().observeBySession(sessionId).first().map(MessageEntity::toDomain)

    override fun observeSessionContent(sessionId: String): Flow<SessionContent> = combine(
        database.sessionDao().observeById(sessionId),
        database.messageDao().observeBySession(sessionId),
        database.assetDao().observeBySession(sessionId),
    ) { session, messages, assets ->
        SessionContent(
            messages = messages.map(MessageEntity::toDomain),
            assetsByMessage = assets.map(AssetEntity::toDomain).groupBy { requireNotNull(it.messageId) },
            title = session?.title.orEmpty(),
        )
    }

    override suspend fun runningTasks(): List<Message> =
        database.messageDao().observeByTaskState(TaskState.RUNNING).first().map(MessageEntity::toDomain)
}

private fun Asset.toEntity(messageId: String, now: Long) = AssetEntity(
    id = UUID.randomUUID().toString(),
    messageId = messageId,
    localUri = localUri,
    thumbnailUri = thumbnailUri,
    mediaType = mediaType,
    width = width,
    height = height,
    byteSize = byteSize,
    source = source,
    createdAt = now,
    available = available,
)

private fun MessageEntity.toDomain() = Message(
    id = id,
    sessionId = sessionId,
    role = role,
    text = text,
    taskState = taskState,
    requestSnapshotJson = requestSnapshotJson,
    errorJson = errorJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AssetEntity.toDomain() = Asset(
    id = id,
    messageId = messageId,
    localUri = localUri,
    thumbnailUri = thumbnailUri,
    mediaType = mediaType,
    width = width,
    height = height,
    byteSize = byteSize,
    source = source,
    createdAt = createdAt,
    available = available,
)
