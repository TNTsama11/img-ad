package com.imgad.domain.port

import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import kotlinx.coroutines.flow.Flow

interface ProviderSettingsStore {
    fun getDefaultProviderId(): String?

    fun setDefaultProviderId(providerId: String?)

    fun observeProviders(): Flow<List<Provider>>

    fun observeModels(providerId: String): Flow<List<ModelProfile>>

    suspend fun saveProvider(provider: Provider, apiKey: String? = null): Provider

    suspend fun deleteProvider(providerId: String)

    suspend fun saveModel(model: ModelProfile): ModelProfile

    suspend fun deleteModel(modelId: String)

    suspend fun setDefaultModel(providerId: String, modelId: String?, now: Long)
}
