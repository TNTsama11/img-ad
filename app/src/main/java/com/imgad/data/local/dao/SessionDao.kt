package com.imgad.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.imgad.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions " +
            "WHERE deletedAt IS NULL AND instr(lower(title), lower(:query)) > 0 " +
            "ORDER BY updatedAt DESC",
    )
    fun searchActiveByTitle(query: String): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
