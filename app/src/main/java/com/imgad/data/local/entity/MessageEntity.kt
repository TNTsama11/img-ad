package com.imgad.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["sessionId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val taskState: TaskState = TaskState.PENDING,
    val requestSnapshotJson: String? = null,
    val errorJson: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
