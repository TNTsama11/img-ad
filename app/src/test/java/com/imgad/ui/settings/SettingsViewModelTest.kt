package com.imgad.ui.settings

import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.port.ConnectionTestResult
import com.imgad.domain.port.FetchProviderModels
import com.imgad.domain.port.ProviderSettingsStore
import com.imgad.domain.port.TestProviderConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun savingProviderClearsApiKeyInputAndNeverStoresItInUiState() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeConnectionTester(), now = { 10 })
        val provider = provider("provider")

        viewModel.setApiKeyInput("secret")
        viewModel.saveProvider(provider)
        advanceUntilIdle()

        assertEquals("secret", store.apiKeys[provider.id])
        assertEquals("", viewModel.uiState.value.apiKeyInput)
    }

    @Test
    fun deletingCurrentDefaultRequiresReplacementProvider() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeConnectionTester(), now = { 10 })
        viewModel.setDefaultProvider("provider")

        viewModel.deleteProvider("provider", replacementProviderId = null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage!!.contains("默认"))
        assertTrue(store.deletedProviders.isEmpty())

        viewModel.deleteProvider("provider", replacementProviderId = "replacement")
        advanceUntilIdle()
        assertEquals(listOf("provider"), store.deletedProviders)
        assertEquals("replacement", store.getDefaultProviderId())
    }

    @Test
    fun connectionRunsOnlyWhenExplicitlyRequestedAndWarnsAboutBilling() = runTest(dispatcher) {
        val tester = FakeConnectionTester()
        val viewModel = SettingsViewModel(FakeSettingsStore(), tester, now = { 10 })

        assertEquals(0, tester.calls)
        assertTrue(viewModel.uiState.value.connectionWarning.contains("费用"))

        viewModel.testConnection(provider("provider"))
        advanceUntilIdle()

        assertEquals(1, tester.calls)
        assertEquals(ConnectionUiState.SUCCESS, viewModel.uiState.value.connectionState)
        assertFalse(viewModel.uiState.value.isTestingConnection)
    }

    @Test
    fun modelDiscoveryRunsOnlyWhenRequestedAndSelectsFetchedModels() = runTest(dispatcher) {
        val catalog = FakeModelCatalog(listOf("gpt-image-1", "flux-pro"))
        val viewModel = SettingsViewModel(FakeSettingsStore(), FakeConnectionTester(), catalog, now = { 10 })
        val provider = provider("provider")

        assertEquals(0, catalog.calls)
        assertTrue(viewModel.uiState.value.discoveredModelIds.isEmpty())

        viewModel.fetchModels(provider, "form-key")
        advanceUntilIdle()

        assertEquals(1, catalog.calls)
        assertEquals("form-key", catalog.lastApiKey)
        assertEquals(listOf("gpt-image-1", "flux-pro"), viewModel.uiState.value.discoveredModelIds)
        assertEquals(setOf("gpt-image-1", "flux-pro"), viewModel.uiState.value.selectedDiscoveredModelIds)
        assertFalse(viewModel.uiState.value.isFetchingModels)
    }

    @Test
    fun discoveredModelsCanBeSearchedAndSelected() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(
            FakeSettingsStore(),
            FakeConnectionTester(),
            FakeModelCatalog(listOf("gpt-image-1", "FLUX-pro")),
            now = { 10 },
        )
        viewModel.fetchModels(provider("provider"), "key")
        advanceUntilIdle()

        viewModel.updateModelSearchQuery("flux")
        assertEquals(listOf("FLUX-pro"), viewModel.uiState.value.visibleDiscoveredModelIds)

        viewModel.toggleDiscoveredModel("FLUX-pro")
        assertEquals(setOf("gpt-image-1"), viewModel.uiState.value.selectedDiscoveredModelIds)
        viewModel.toggleDiscoveredModel("FLUX-pro")
        assertEquals(setOf("gpt-image-1", "FLUX-pro"), viewModel.uiState.value.selectedDiscoveredModelIds)
    }

    @Test
    fun saveWithModelsPersistsProviderBeforeDeterministicProfiles() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeConnectionTester(), FakeModelCatalog(), now = { 10 })
        val provider = provider("new-provider")
        var saved = false

        viewModel.saveProviderWithModels(provider, "secret", setOf("gpt-image-1", "flux-pro")) { saved = true }
        advanceUntilIdle()

        assertEquals("provider:new-provider", store.operations.first())
        assertEquals(setOf("model:gpt-image-1", "model:flux-pro"), store.operations.drop(1).toSet())
        assertEquals("secret", store.apiKeys[provider.id])
        assertTrue(store.savedModels.all { it.providerId == provider.id && it.supportsGeneration })
        assertTrue(store.savedModels.all { !it.supportsEdit && !it.supportsMask && !it.supportsMultipleImages })
        assertTrue(store.savedModels.all { it.supportedSizes.isNotEmpty() })
        assertTrue(store.savedModels.all { it.supportedQualities.isNotEmpty() })
        val firstIds = store.savedModels.associate { it.modelName to it.id }

        viewModel.saveProviderWithModels(provider, "", setOf("gpt-image-1", "flux-pro"))
        advanceUntilIdle()

        assertEquals(firstIds, store.savedModels.takeLast(2).associate { it.modelName to it.id })
        assertTrue(saved)
    }

    @Test
    fun modelDiscoveryFailureClearsLoadingAndShowsMessage() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(
            FakeSettingsStore(),
            FakeConnectionTester(),
            FakeModelCatalog(failure = IllegalStateException("HTTP 401")),
            now = { 10 },
        )

        viewModel.fetchModels(provider("provider"), "bad-key")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFetchingModels)
        assertEquals("HTTP 401", viewModel.uiState.value.modelFetchError)
        assertTrue(viewModel.uiState.value.discoveredModelIds.isEmpty())
    }

    private fun provider(id: String) = Provider(id, "Provider", "https://example.com", "alias")

    private class FakeConnectionTester : TestProviderConnection {
        var calls = 0
        override suspend fun test(provider: Provider): ConnectionTestResult {
            calls++
            return ConnectionTestResult.Success
        }
    }

    private class FakeModelCatalog(
        private val models: List<String> = emptyList(),
        private val failure: Throwable? = null,
    ) : FetchProviderModels {
        var calls = 0
        var lastApiKey: String? = null

        override suspend fun fetch(provider: Provider, apiKeyOverride: String?): List<String> {
            calls++
            lastApiKey = apiKeyOverride
            failure?.let { throw it }
            return models
        }
    }

    private class FakeSettingsStore : ProviderSettingsStore {
        val apiKeys = mutableMapOf<String, String>()
        val deletedProviders = mutableListOf<String>()
        val savedModels = mutableListOf<ModelProfile>()
        val operations = mutableListOf<String>()
        private val providers = MutableStateFlow(
            listOf(
                Provider("provider", "Provider", "https://example.com", "alias"),
                Provider("replacement", "Provider", "https://example.com", "alias"),
            ),
        )

        private var defaultProviderId: String? = "provider"

        override fun getDefaultProviderId(): String? = defaultProviderId
        override fun setDefaultProviderId(providerId: String?) { defaultProviderId = providerId }

        override fun observeProviders(): Flow<List<Provider>> = providers
        override fun observeModels(providerId: String): Flow<List<ModelProfile>> = flowOf(emptyList())
        override suspend fun saveProvider(provider: Provider, apiKey: String?): Provider {
            operations += "provider:${provider.id}"
            apiKey?.let { apiKeys[provider.id] = it }
            providers.value = providers.value.filterNot { it.id == provider.id } + provider
            return provider
        }
        override suspend fun deleteProvider(providerId: String) {
            deletedProviders += providerId
            providers.value = providers.value.filterNot { it.id == providerId }
        }
        override suspend fun saveModel(model: ModelProfile): ModelProfile {
            operations += "model:${model.modelName}"
            savedModels += model
            return model
        }
        override suspend fun deleteModel(modelId: String) = Unit
        override suspend fun setDefaultModel(providerId: String, modelId: String?, now: Long) = Unit
    }
}
