package com.imgad.domain.model

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

enum class TaskState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val taskState: TaskState = TaskState.PENDING,
    val requestSnapshotJson: String? = null,
    val errorJson: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
