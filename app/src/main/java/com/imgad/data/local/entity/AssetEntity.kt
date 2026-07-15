package com.imgad.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.imgad.domain.model.AssetSource

@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"]), Index(value = ["messageId", "source"])],
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val messageId: String? = null,
    val localUri: String,
    val thumbnailUri: String? = null,
    val mediaType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long? = null,
    val source: AssetSource,
    val createdAt: Long = 0L,
    val available: Boolean = true,
)
