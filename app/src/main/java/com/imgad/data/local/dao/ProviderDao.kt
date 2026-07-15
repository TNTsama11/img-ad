package com.imgad.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.imgad.data.local.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Upsert
    suspend fun upsertAll(providers: List<ProviderEntity>)

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun observeEnabled(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE providers SET defaultModelId = :modelId, updatedAt = :updatedAt WHERE id = :providerId")
    suspend fun updateDefaultModel(providerId: String, modelId: String?, updatedAt: Long)
}
