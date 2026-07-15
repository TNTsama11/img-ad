package com.imgad.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.imgad.data.local.entity.ModelProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelProfileDao {
    @Upsert
    suspend fun upsert(model: ModelProfileEntity)

    @Upsert
    suspend fun upsertAll(models: List<ModelProfileEntity>)

    @Query("SELECT * FROM model_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ModelProfileEntity?

    @Query("SELECT * FROM model_profiles WHERE providerId = :providerId ORDER BY displayName ASC, id ASC")
    fun observeByProvider(providerId: String): Flow<List<ModelProfileEntity>>

    @Query("SELECT * FROM model_profiles WHERE providerId = :providerId AND enabled = 1 ORDER BY displayName ASC, id ASC")
    fun observeEnabledByProvider(providerId: String): Flow<List<ModelProfileEntity>>

    @Query(
        """
        SELECT m.* FROM model_profiles AS m
        INNER JOIN providers AS p ON p.defaultModelId = m.id
        WHERE p.id = :providerId AND m.providerId = :providerId AND m.enabled = 1
        LIMIT 1
        """,
    )
    suspend fun getDefaultForProvider(providerId: String): ModelProfileEntity?

    @Query("DELETE FROM model_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
