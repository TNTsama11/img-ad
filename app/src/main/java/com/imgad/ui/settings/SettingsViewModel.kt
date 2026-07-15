package com.imgad.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.model.defaultImageQualities
import com.imgad.domain.model.defaultImageSizes
import com.imgad.domain.port.FetchProviderModels
import com.imgad.domain.port.ProviderSettingsStore
import com.imgad.domain.port.TestProviderConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ConnectionUiState {
    const val IDLE = "idle"
    const val SUCCESS = "success"
    const val FAILURE = "failure"
}

data class SettingsUiState(
    val providers: List<Provider> = emptyList(),
    val selectedProviderId: String? = null,
    val models: List<ModelProfile> = emptyList(),
    val defaultProviderId: String? = null,
    val apiKeyInput: String = "",
    val apiKeyVisible: Boolean = false,
    val connectionWarning: String = "测试连接可能产生费用",
    val connectionState: String = ConnectionUiState.IDLE,
    val connectionError: String? = null,
    val isTestingConnection: Boolean = false,
    val discoveredModelIds: List<String> = emptyList(),
    val selectedDiscoveredModelIds: Set<String> = emptySet(),
    val modelSearchQuery: String = "",
    val isFetchingModels: Boolean = false,
    val modelFetchError: String? = null,
    val errorMessage: String? = null,
) {
    val visibleDiscoveredModelIds: List<String>
        get() = discoveredModelIds.filter { modelId ->
            modelSearchQuery.isBlank() || modelId.contains(modelSearchQuery.trim(), ignoreCase = true)
        }
}

class SettingsViewModel(
    private val store: ProviderSettingsStore,
    private val connectionTester: TestProviderConnection,
    private val modelCatalog: FetchProviderModels = FetchProviderModels { _, _ -> emptyList() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var modelsJob: Job? = null

    init {
        viewModelScope.launch {
            store.observeProviders().collect { providers ->
                _uiState.update { state ->
                    state.copy(
                        providers = providers,
                        selectedProviderId = state.selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
                            ?: providers.firstOrNull()?.id,
                        defaultProviderId = state.defaultProviderId?.takeIf { id -> providers.any { it.id == id } }
                            ?: store.getDefaultProviderId()?.takeIf { id -> providers.any { it.id == id } }
                            ?: providers.firstOrNull()?.id,
                    )
                }
                _uiState.value.selectedProviderId?.let(::observeModels)
            }
        }
    }

    fun selectProvider(providerId: String?) {
        _uiState.update { it.copy(selectedProviderId = providerId, errorMessage = null) }
        providerId?.let(::observeModels)
    }

    fun setDefaultProvider(providerId: String) {
        store.setDefaultProviderId(providerId)
        _uiState.update { it.copy(defaultProviderId = providerId, errorMessage = null) }
    }

    fun setApiKeyInput(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(apiKeyVisible = !it.apiKeyVisible) }
    }

    fun saveProvider(provider: Provider, onSaved: () -> Unit = {}) {
        val validationError = validateProvider(provider)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        val apiKey = _uiState.value.apiKeyInput.trim().takeIf(String::isNotEmpty)
        viewModelScope.launch {
            runCatching { store.saveProvider(provider, apiKey) }
                .onSuccess {
                    _uiState.update {
                        it.copy(apiKeyInput = "", apiKeyVisible = false, errorMessage = null)
                    }
                    onSaved()
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "保存供应方失败") } }
        }
    }

    fun deleteProvider(providerId: String, replacementProviderId: String?) {
        val deletingDefault = providerId == _uiState.value.defaultProviderId
        if (deletingDefault && replacementProviderId == null) {
            _uiState.update { it.copy(errorMessage = "删除当前默认供应方前，请先选择新的默认项") }
            return
        }
        if (replacementProviderId != null && _uiState.value.providers.none { it.id == replacementProviderId }) {
            _uiState.update { it.copy(errorMessage = "新的默认供应方不存在") }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (deletingDefault) store.setDefaultProviderId(replacementProviderId)
                try {
                    store.deleteProvider(providerId)
                } catch (error: Throwable) {
                    if (deletingDefault) store.setDefaultProviderId(providerId)
                    throw error
                }
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            defaultProviderId = if (deletingDefault) replacementProviderId else it.defaultProviderId,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "删除供应方失败") } }
        }
    }

    fun saveModel(model: ModelProfile, onSaved: () -> Unit = {}) {
        if (model.modelName.isBlank() || model.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "模型名称不能为空") }
            return
        }
        viewModelScope.launch {
            runCatching { store.saveModel(model) }
                .onSuccess { onSaved() }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "保存模型失败") } }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            runCatching { store.deleteModel(modelId) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "删除模型失败") } }
        }
    }

    fun setDefaultModel(providerId: String, modelId: String?) {
        viewModelScope.launch {
            runCatching { store.setDefaultModel(providerId, modelId, now()) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "设置默认模型失败") } }
        }
    }

    fun testConnection(provider: Provider) {
        _uiState.update {
            it.copy(
                isTestingConnection = true,
                connectionState = ConnectionUiState.IDLE,
                connectionError = null,
            )
        }
        viewModelScope.launch {
            runCatching { connectionTester.test(provider) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionState = if (result.success) ConnectionUiState.SUCCESS else ConnectionUiState.FAILURE,
                            connectionError = result.errorCode,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionState = ConnectionUiState.FAILURE,
                            connectionError = error.message ?: "连接失败",
                        )
                    }
                }
        }
    }

    fun fetchModels(provider: Provider, apiKey: String) {
        _uiState.update {
            it.copy(
                discoveredModelIds = emptyList(),
                selectedDiscoveredModelIds = emptySet(),
                modelSearchQuery = "",
                isFetchingModels = true,
                modelFetchError = null,
            )
        }
        viewModelScope.launch {
            runCatching { modelCatalog.fetch(provider, apiKey) }
                .onSuccess { modelIds ->
                    _uiState.update {
                        it.copy(
                            discoveredModelIds = modelIds,
                            selectedDiscoveredModelIds = modelIds.toSet(),
                            isFetchingModels = false,
                            modelFetchError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            discoveredModelIds = emptyList(),
                            selectedDiscoveredModelIds = emptySet(),
                            isFetchingModels = false,
                            modelFetchError = error.message ?: "获取模型失败",
                        )
                    }
                }
        }
    }

    fun updateModelSearchQuery(query: String) {
        _uiState.update { it.copy(modelSearchQuery = query) }
    }

    fun toggleDiscoveredModel(modelId: String) {
        _uiState.update { state ->
            if (modelId !in state.discoveredModelIds) return@update state
            val selected = state.selectedDiscoveredModelIds.toMutableSet()
            if (!selected.add(modelId)) selected.remove(modelId)
            state.copy(selectedDiscoveredModelIds = selected)
        }
    }

    fun clearDiscoveredModels() {
        _uiState.update {
            it.copy(
                discoveredModelIds = emptyList(),
                selectedDiscoveredModelIds = emptySet(),
                modelSearchQuery = "",
                modelFetchError = null,
            )
        }
    }

    fun saveProviderWithModels(
        provider: Provider,
        apiKey: String,
        modelIds: Set<String>,
        onSaved: () -> Unit = {},
    ) {
        val validationError = validateProvider(provider)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        viewModelScope.launch {
            runCatching {
                store.saveProvider(provider, apiKey.trim().takeIf(String::isNotEmpty))
                modelIds.forEach { modelId -> store.saveModel(importedModel(provider.id, modelId)) }
            }.onSuccess {
                clearDiscoveredModels()
                _uiState.update { it.copy(apiKeyInput = "", apiKeyVisible = false, errorMessage = null) }
                onSaved()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "保存供应方和模型失败") }
            }
        }
    }

    private fun importedModel(providerId: String, modelName: String): ModelProfile {
        val existing = _uiState.value.models.firstOrNull {
            it.providerId == providerId && it.modelName == modelName
        }
        if (existing != null) return existing
        val id = UUID.nameUUIDFromBytes("$providerId:$modelName".toByteArray(StandardCharsets.UTF_8)).toString()
        return ModelProfile(
            id = id,
            providerId = providerId,
            modelName = modelName,
            displayName = modelName,
            supportsGeneration = true,
            supportsEdit = false,
            supportsMask = false,
            supportsMultipleImages = false,
            supportedSizes = defaultImageSizes(modelName),
            supportedQualities = defaultImageQualities(modelName),
        )
    }

    private fun observeModels(providerId: String) {
        modelsJob?.cancel()
        modelsJob = viewModelScope.launch {
            store.observeModels(providerId).collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
    }

    private fun validateProvider(provider: Provider): String? = runCatching {
        require(provider.name.isNotBlank()) { "供应方名称不能为空" }
        val uri = URI(provider.baseUrl)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Base URL 必须是有效的 HTTP(S) 地址" }
    }.exceptionOrNull()?.message
}
