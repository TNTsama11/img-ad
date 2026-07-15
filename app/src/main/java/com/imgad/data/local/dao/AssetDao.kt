package com.imgad.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.imgad.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Upsert
    suspend fun upsert(asset: AssetEntity)

    @Upsert
    suspend fun upsertAll(assets: List<AssetEntity>)

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE messageId = :messageId ORDER BY createdAt ASC, id ASC")
    fun observeByMessage(messageId: String): Flow<List<AssetEntity>>

    @Query(
        "SELECT assets.* FROM assets " +
            "INNER JOIN messages ON messages.id = assets.messageId " +
            "WHERE messages.sessionId = :sessionId ORDER BY assets.createdAt ASC, assets.id ASC",
    )
    fun observeBySession(sessionId: String): Flow<List<AssetEntity>>

    @Query("SELECT localUri, thumbnailUri FROM assets")
    fun getAllLocalUris(): List<AssetPathRow>

    @Query(
        "SELECT assets.localUri, assets.thumbnailUri FROM assets " +
            "INNER JOIN messages ON messages.id = assets.messageId " +
            "INNER JOIN sessions ON sessions.id = messages.sessionId " +
            "WHERE sessions.deletedAt IS NULL",
    )
    fun getActiveLocalUris(): List<AssetPathRow>

    @Query(
        "SELECT assets.localUri, assets.thumbnailUri FROM assets " +
            "INNER JOIN messages ON messages.id = assets.messageId " +
            "INNER JOIN sessions ON sessions.id = messages.sessionId " +
            "WHERE sessions.deletedAt IS NULL AND sessions.id != :sessionId",
    )
    fun getActiveLocalUrisExcludingSession(sessionId: String): List<AssetPathRow>

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: String)
}

data class AssetPathRow(
    val localUri: String,
    val thumbnailUri: String?,
)
