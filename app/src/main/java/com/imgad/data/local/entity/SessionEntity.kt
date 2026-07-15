package com.imgad.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deletedAt"])],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
