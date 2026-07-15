package com.imgad.data.repository

import com.imgad.data.local.SecretStore
import com.imgad.data.local.dao.ModelProfileDao
import com.imgad.data.local.dao.ProviderDao
import com.imgad.data.local.entity.ModelProfileEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRepositoryTest {
    @Test
    fun saveProviderStoresApiKeyUnderStableInternalAlias() = runTest {
        val providerDao = FakeProviderDao()
        val secrets = FakeSecretStore()
        val repository = ProviderRepository(providerDao, FakeModelProfileDao(providerDao), secrets)

        val saved = repository.saveProvider(provider("provider/with spaces"), "plain-api-key")

        assertTrue(saved.apiKeyAlias.startsWith("imgad.provider-api-key.v1."))
        assertFalse(saved.apiKeyAlias.contains("provider/with spaces"))
        assertEquals("plain-api-key", repository.getApiKey(saved.id))
        assertEquals(saved.apiKeyAlias, providerDao.getById(saved.id)?.apiKeyAlias)
    }

    @Test
    fun saveProviderNormalizesTrailingBaseUrlSlashes() = runTest {
        val providerDao = FakeProviderDao()
        val repository = ProviderRepository(providerDao, FakeModelProfileDao(providerDao), FakeSecretStore())

        val saved = repository.saveProvider(provider("provider").copy(baseUrl = "https://example.com///"))

        assertEquals("https://example.com", saved.baseUrl)
        assertEquals("https://example.com", providerDao.getById("provider")?.baseUrl)
    }

    @Test
    fun saveProviderRestoresPreviousSecretWhenDatabaseWriteFails() = runTest {
        val providerDao = FakeProviderDao()
        val secrets = FakeSecretStore()
        val repository = ProviderRepository(providerDao, FakeModelProfileDao(providerDao), secrets)
        repository.saveProvider(provider("provider"), "old-key")
        providerDao.failWrites = true

        var failure: Throwable? = null
        try {
            repository.saveProvider(provider("provider"), "new-key")
        } catch (error: Throwable) {
            failure = error
        }

        assertNotNull(failure)
        assertEquals("database write failed", failure?.message)
        assertEquals("old-key", repository.getApiKey("provider"))
    }

    @Test
    fun deleteProviderRemovesEntityAndSecret() = runTest {
        val providerDao = FakeProviderDao()
        val secrets = FakeSecretStore()
        val repository = ProviderRepository(providerDao, FakeModelProfileDao(providerDao), secrets)
        repository.saveProvider(provider("provider"), "api-key")

        repository.deleteProvider("provider")

        assertNull(providerDao.getById("provider"))
        assertNull(repository.getApiKey("provider"))
    }

    @Test
    fun deleteProviderPropagatesSecretCleanupFailureAfterDatabaseDelete() = runTest {
        val providerDao = FakeProviderDao()
        val secrets = FakeSecretStore()
        val repository = ProviderRepository(providerDao, FakeModelProfileDao(providerDao), secrets)
        repository.saveProvider(provider("provider"), "api-key")
        secrets.failRemovals = true

        var failure: Throwable? = null
        try {
            repository.deleteProvider("provider")
        } catch (error: Throwable) {
            failure = error
        }

        assertNotNull(failure)
        assertEquals("secret removal failed", failure?.message)
        assertNull(providerDao.getById("provider"))
    }

    @Test
    fun modelRoundTripAndDefaultModelSwitchUseDomainTypes() = runTest {
        val providerDao = FakeProviderDao()
        val modelDao = FakeModelProfileDao(providerDao)
        val repository = ProviderRepository(providerDao, modelDao, FakeSecretStore())
        repository.saveProvider(provider("provider"))
        repository.saveModel(model("first"))
        repository.saveModel(model("second"))

        repository.setDefaultModel("provider", "second", updatedAt = 50L)

        assertEquals("second", repository.getDefaultModel("provider")?.id)
        assertEquals(setOf("1024x1024"), repository.getDefaultModel("provider")?.supportedSizes)
    }

    private fun provider(id: String) = Provider(
        id = id,
        name = "Provider",
        baseUrl = "https://example.com",
        apiKeyAlias = "caller-controlled-alias",
    )

    private fun model(id: String) = ModelProfile(
        id = id,
        providerId = "provider",
        modelName = id,
        displayName = id,
        supportsGeneration = true,
        supportsEdit = true,
        supportsMask = false,
        supportsMultipleImages = false,
        supportedSizes = setOf("1024x1024"),
        supportedQualities = setOf("standard"),
    )
}

private class FakeProviderDao : ProviderDao {
    private val state = MutableStateFlow<List<ProviderEntity>>(emptyList())
    var failWrites = false

    override fun observeAll(): Flow<List<ProviderEntity>> = state

    override fun observeEnabled(): Flow<List<ProviderEntity>> =
        MutableStateFlow(state.value.filter(ProviderEntity::enabled))

    override suspend fun getById(id: String): ProviderEntity? = state.value.singleOrNull { it.id == id }

    override suspend fun upsert(provider: ProviderEntity) {
        check(!failWrites) { "database write failed" }
        state.value = state.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun upsertAll(providers: List<ProviderEntity>) {
        providers.forEach { upsert(it) }
    }

    override suspend fun deleteById(id: String) {
        check(!failWrites) { "database write failed" }
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun updateDefaultModel(providerId: String, modelId: String?, updatedAt: Long) {
        val provider = requireNotNull(getById(providerId))
        upsert(provider.copy(defaultModelId = modelId, updatedAt = updatedAt))
    }
}

private class FakeModelProfileDao(
    private val providerDao: FakeProviderDao,
) : ModelProfileDao {
    private val state = MutableStateFlow<List<ModelProfileEntity>>(emptyList())

    override fun observeByProvider(providerId: String): Flow<List<ModelProfileEntity>> =
        MutableStateFlow(state.value.filter { it.providerId == providerId })

    override fun observeEnabledByProvider(providerId: String): Flow<List<ModelProfileEntity>> =
        MutableStateFlow(state.value.filter { it.providerId == providerId && it.enabled })

    override suspend fun getById(id: String): ModelProfileEntity? = state.value.singleOrNull { it.id == id }

    override suspend fun getDefaultForProvider(providerId: String): ModelProfileEntity? {
        val defaultId = providerDao.getById(providerId)?.defaultModelId
        return state.value.singleOrNull { it.id == defaultId && it.providerId == providerId }
    }

    override suspend fun upsert(model: ModelProfileEntity) {
        state.value = state.value.filterNot { it.id == model.id } + model
    }

    override suspend fun upsertAll(models: List<ModelProfileEntity>) {
        models.forEach { upsert(it) }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class FakeSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    var failRemovals = false

    override fun put(alias: String, value: String) {
        values[alias] = value
    }

    override fun get(alias: String): String? = values[alias]

    override fun remove(alias: String) {
        check(!failRemovals) { "secret removal failed" }
        values.remove(alias)
    }
}
