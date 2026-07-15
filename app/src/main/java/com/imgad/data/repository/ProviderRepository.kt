package com.imgad.data.repository

import com.imgad.data.local.SecretStore
import com.imgad.data.local.dao.ModelProfileDao
import com.imgad.data.local.dao.ProviderDao
import com.imgad.data.local.entity.ModelProfileEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.port.ProviderSettingsStore
import com.imgad.domain.port.DefaultProviderStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProviderRepository(
    private val providerDao: ProviderDao,
    private val modelProfileDao: ModelProfileDao,
    private val secretStore: SecretStore,
    private val defaultProviderStore: DefaultProviderStore = object : DefaultProviderStore {
        private var value: String? = null
        override fun get(): String? = value
        override fun set(providerId: String?) { value = providerId }
    },
) : ProviderSettingsStore {
    override fun getDefaultProviderId(): String? = defaultProviderStore.get()

    override fun setDefaultProviderId(providerId: String?) = defaultProviderStore.set(providerId)
    override fun observeProviders(): Flow<List<Provider>> =
        providerDao.observeAll().map { providers -> providers.map(ProviderEntity::toDomain) }

    fun observeEnabledProviders(): Flow<List<Provider>> =
        providerDao.observeEnabled().map { providers -> providers.map(ProviderEntity::toDomain) }

    suspend fun getProvider(id: String): Provider? = providerDao.getById(id)?.toDomain()

    override suspend fun saveProvider(provider: Provider, apiKey: String?): Provider {
        val entity = provider.toEntity(apiKeyAlias(provider.id))
        if (apiKey == null) {
            providerDao.upsert(entity)
            return entity.toDomain()
        }

        val previousSecret = secretStore.get(entity.apiKeyAlias)
        secretStore.put(entity.apiKeyAlias, apiKey)
        try {
            providerDao.upsert(entity)
        } catch (writeFailure: Throwable) {
            try {
                if (previousSecret == null) {
                    secretStore.remove(entity.apiKeyAlias)
                } else {
                    secretStore.put(entity.apiKeyAlias, previousSecret)
                }
            } catch (rollbackFailure: Throwable) {
                writeFailure.addSuppressed(rollbackFailure)
            }
            throw writeFailure
        }
        return entity.toDomain()
    }

    suspend fun saveProvider(provider: Provider): Provider = saveProvider(provider, null)

    override suspend fun deleteProvider(providerId: String) {
        val alias = providerDao.getById(providerId)?.apiKeyAlias ?: apiKeyAlias(providerId)
        providerDao.deleteById(providerId)
        secretStore.remove(alias)
    }

    override fun observeModels(providerId: String): Flow<List<ModelProfile>> =
        modelProfileDao.observeByProvider(providerId).map { models -> models.map(ModelProfileEntity::toDomain) }

    fun observeEnabledModels(providerId: String): Flow<List<ModelProfile>> =
        modelProfileDao.observeEnabledByProvider(providerId)
            .map { models -> models.map(ModelProfileEntity::toDomain) }

    suspend fun getModel(id: String): ModelProfile? = modelProfileDao.getById(id)?.toDomain()

    override suspend fun saveModel(model: ModelProfile): ModelProfile {
        val entity = model.toEntity()
        modelProfileDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun deleteModel(id: String) {
        modelProfileDao.deleteById(id)
    }

    suspend fun getDefaultModel(providerId: String): ModelProfile? =
        modelProfileDao.getDefaultForProvider(providerId)?.toDomain()

    override suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long) {
        requireNotNull(providerDao.getById(providerId)) { "Provider does not exist" }
        if (modelId != null) {
            val model = requireNotNull(modelProfileDao.getById(modelId)) { "Model does not exist" }
            require(model.providerId == providerId) { "Model belongs to a different provider" }
        }
        providerDao.updateDefaultModel(providerId, modelId, updatedAt)
    }

    suspend fun setApiKey(providerId: String, value: String) {
        val provider = requireNotNull(providerDao.getById(providerId)) { "Provider does not exist" }
        secretStore.put(provider.apiKeyAlias, value)
    }

    suspend fun getApiKey(providerId: String): String? {
        val alias = providerDao.getById(providerId)?.apiKeyAlias ?: apiKeyAlias(providerId)
        return secretStore.get(alias)
    }

    suspend fun removeApiKey(providerId: String) {
        val alias = providerDao.getById(providerId)?.apiKeyAlias ?: apiKeyAlias(providerId)
        secretStore.remove(alias)
    }

    private fun apiKeyAlias(providerId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(providerId.toByteArray(StandardCharsets.UTF_8))
        val encodedId = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return "$API_KEY_ALIAS_PREFIX$encodedId"
    }

    private companion object {
        const val API_KEY_ALIAS_PREFIX = "imgad.provider-api-key.v1."
    }
}

private fun ProviderEntity.toDomain() = Provider(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKeyAlias = apiKeyAlias,
    enabled = enabled,
    defaultModelId = defaultModelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun String.normalizeBaseUrl(): String {
    val trimmed = trim()
    val normalized = if (trimmed.endsWith("/") && !trimmed.endsWith("://")) trimmed.trimEnd('/') else trimmed
    require(normalized.isNotBlank()) { "Base URL cannot be blank" }
    return normalized
}

private fun Provider.toEntity(internalApiKeyAlias: String) = ProviderEntity(
    id = id,
    name = name,
    baseUrl = baseUrl.normalizeBaseUrl(),
    apiKeyAlias = internalApiKeyAlias,
    enabled = enabled,
    defaultModelId = defaultModelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ModelProfileEntity.toDomain() = ModelProfile(
    id = id,
    providerId = providerId,
    modelName = modelName,
    displayName = displayName,
    supportsGeneration = supportsGeneration,
    supportsEdit = supportsEdit,
    supportsMask = supportsMask,
    supportsMultipleImages = supportsMultipleImages,
    supportedSizes = supportedSizes,
    supportedQualities = supportedQualities,
    enabled = enabled,
)

private fun ModelProfile.toEntity() = ModelProfileEntity(
    id = id,
    providerId = providerId,
    modelName = modelName,
    displayName = displayName,
    supportsGeneration = supportsGeneration,
    supportsEdit = supportsEdit,
    supportsMask = supportsMask,
    supportsMultipleImages = supportsMultipleImages,
    supportedSizes = supportedSizes,
    supportedQualities = supportedQualities,
    enabled = enabled,
)
