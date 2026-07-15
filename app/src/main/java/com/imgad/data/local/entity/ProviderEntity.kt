package com.imgad.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "providers",
    foreignKeys = [
        ForeignKey(
            entity = ModelProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultModelId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["defaultModelId"])],
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyAlias: String,
    val enabled: Boolean = true,
    val defaultModelId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
