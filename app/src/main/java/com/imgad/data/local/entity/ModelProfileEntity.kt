package com.imgad.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "model_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["providerId"]), Index(value = ["providerId", "enabled"])],
)
data class ModelProfileEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelName: String,
    val displayName: String,
    val supportsGeneration: Boolean,
    val supportsEdit: Boolean,
    val supportsMask: Boolean,
    val supportsMultipleImages: Boolean,
    val supportedSizes: Set<String> = emptySet(),
    val supportedQualities: Set<String> = emptySet(),
    val enabled: Boolean = true,
)
