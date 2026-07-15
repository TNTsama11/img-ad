package com.imgad.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imgad.domain.model.Asset
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.model.Message
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.model.ValidationError
import com.imgad.domain.model.effectiveQualities
import com.imgad.domain.model.effectiveSizes
import com.imgad.domain.port.SessionContent
import com.imgad.domain.usecase.ValidateGenerationRequest
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun interface GenerateAction {
    suspend fun execute(sessionId: String, request: GenerationRequest): GenerationResult
}

fun interface EditAction {
    suspend fun execute(sessionId: String, request: GenerationRequest): GenerationResult
}

fun interface RetryAction {
    suspend fun execute(messageId: String): GenerationResult
}

fun interface SessionMessages {
    fun observe(sessionId: String): Flow<SessionContent>
}

class CreateViewModel(
    providers: List<Provider>,
    models: List<ModelProfile>,
    private val generate: GenerateAction,
    private val edit: EditAction,
    private val retryAction: RetryAction,
    private val sessionMessages: SessionMessages,
    private val validator: ValidateGenerationRequest = ValidateGenerationRequest(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CreateUiState(providers = providers.toList(), models = models.toList()),
    )
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private var runningJob: Job? = null
    private var sessionJob: Job? = null
    private var observedSessionId: String? = null

    fun selectProvider(providerId: String?) {
        _uiState.update { state ->
            val selectedModel = state.selectedModelId?.let { id -> state.models.singleOrNull { it.id == id } }
            state.copy(
                selectedProviderId = providerId,
                selectedModelId = selectedModel?.takeIf { it.providerId == providerId }?.id,
                errorMessage = null,
            )
        }
    }

    fun selectModel(modelId: String?) {
        _uiState.update { state ->
            val model = modelId?.let { id -> state.models.singleOrNull { it.id == id } }
            val selectedModel = model?.takeIf { it.providerId == state.selectedProviderId }
            state.copy(
                selectedModelId = selectedModel?.id,
                size = selectParameter(state.size, selectedModel?.effectiveSizes().orEmpty()),
                quality = selectParameter(state.quality, selectedModel?.effectiveQualities().orEmpty()),
                errorMessage = if (modelId != null && model?.providerId != state.selectedProviderId) "模型不属于当前供应方" else null,
            )
        }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt, errorMessage = null) }
    }

    fun updateCatalog(
        providers: List<Provider>,
        models: List<ModelProfile>,
        defaultProviderId: String? = null,
    ) {
        _uiState.update { state ->
            val selectedProviderId = state.selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
                ?: defaultProviderId?.takeIf { id -> providers.any { it.id == id } }
            val providerDefaultModelId = providers.firstOrNull { it.id == selectedProviderId }?.defaultModelId
            val selectedModelId = state.selectedModelId?.takeIf { id -> models.any { it.id == id && it.providerId == selectedProviderId } }
                ?: providerDefaultModelId?.takeIf { id -> models.any { it.id == id && it.providerId == selectedProviderId } }
            val selectedModel = models.firstOrNull { it.id == selectedModelId }
            state.copy(
                providers = providers.toList(),
                models = models.toList(),
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                size = selectParameter(state.size, selectedModel?.effectiveSizes().orEmpty()),
                quality = selectParameter(state.quality, selectedModel?.effectiveQualities().orEmpty()),
            )
        }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun updateParameters(size: String, quality: String, outputFormat: String, count: Int, advancedJson: String?) {
        _uiState.update {
            it.copy(
                size = size,
                quality = quality,
                outputFormat = outputFormat,
                count = count,
                advancedJson = advancedJson,
                errorMessage = null,
            )
        }
    }

    fun addAsset(asset: Asset) {
        _uiState.update { it.copy(inputAssets = (it.inputAssets + asset).toList(), errorMessage = null) }
    }

    fun removeAsset(assetId: String) {
        _uiState.update { it.copy(inputAssets = it.inputAssets.filterNot { asset -> asset.id == assetId }) }
    }

    fun setMask(asset: Asset) {
        _uiState.update { it.copy(maskAsset = asset, errorMessage = null) }
    }

    fun clearMask() {
        _uiState.update { it.copy(maskAsset = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isRunning) return
        val provider = state.selectedProviderId?.let { id -> state.providers.singleOrNull { it.id == id } }
        val model = state.selectedModelId?.let { id -> state.models.singleOrNull { it.id == id } }
        val request = buildRequest(state, provider, model)
        if (request == null || provider == null || model == null) {
            _uiState.update { it.copy(errorMessage = "请选择供应方和模型") }
            return
        }
        validator(request, model)?.let { validation ->
            _uiState.update { it.copy(errorMessage = validationMessage(validation)) }
            return
        }

        val sessionId = state.currentSessionId ?: UUID.randomUUID().toString()
        val taskId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                title = it.title.ifBlank { request.prompt.take(80) },
                isRunning = true,
                currentTaskId = taskId,
                runningSessionId = sessionId,
                errorMessage = null,
            )
        }
        observeSession(sessionId)
        runningJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runTask(taskId, sessionId, clearDraftOnSuccess = true) {
                if (request.isEdit) edit.execute(sessionId, request) else generate.execute(sessionId, request)
            }
        }
    }

    fun cancel() {
        runningJob?.cancel()
    }

    fun retry(messageId: String) {
        if (_uiState.value.isRunning) return
        val taskId = UUID.randomUUID().toString()
        val sessionId = _uiState.value.currentSessionId
        _uiState.update { it.copy(isRunning = true, currentTaskId = taskId, runningSessionId = sessionId, errorMessage = null) }
        runningJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runTask(taskId, sessionId, clearDraftOnSuccess = false) { retryAction.execute(messageId) }
        }
    }

    fun loadSession(sessionId: String, title: String = "") {
        runningJob?.cancel()
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                title = title,
                messages = emptyList(),
                isRunning = false,
                currentTaskId = null,
                runningSessionId = null,
                errorMessage = null,
            )
        }
        observeSession(sessionId)
    }

    private fun observeSession(sessionId: String) {
        sessionJob?.cancel()
        observedSessionId = sessionId
        sessionJob = viewModelScope.launch {
            sessionMessages.observe(sessionId).collectLatest { content ->
                _uiState.update { state ->
                    if (state.currentSessionId == sessionId) {
                        state.copy(
                            messages = content.messages.toList(),
                            messageAssets = content.assetsByMessage.mapValues { it.value.toList() },
                            title = content.title.ifBlank { state.title },
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun openSession(sessionId: String?) {
        if (sessionId == null) {
            if (_uiState.value.currentSessionId != null) resetToNewSession()
        } else if (_uiState.value.currentSessionId != sessionId) {
            loadSession(sessionId)
        } else if (observedSessionId != sessionId) {
            observeSession(sessionId)
        }
    }

    fun resetToNewSession() {
        sessionJob?.cancel()
        observedSessionId = null
        runningJob?.cancel()
        _uiState.update {
            it.copy(
                currentSessionId = null,
                title = "",
                messages = emptyList(),
                messageAssets = emptyMap(),
                prompt = "",
                inputAssets = emptyList(),
                maskAsset = null,
                isRunning = false,
                currentTaskId = null,
                runningSessionId = null,
                errorMessage = null,
            )
        }
    }

    private suspend fun runTask(
        taskId: String,
        runningSessionId: String?,
        clearDraftOnSuccess: Boolean,
        action: suspend () -> GenerationResult,
    ) {
        try {
            action()
            if (clearDraftOnSuccess) _uiState.update { state ->
                if (!state.belongsTo(taskId, runningSessionId)) state else state.copy(
                    prompt = "",
                    inputAssets = emptyList(),
                    maskAsset = null,
                    errorMessage = null,
                )
            }
        } catch (_: CancellationException) {
            // Cancellation is an explicit UI action, not a displayable failure.
        } catch (error: Exception) {
            _uiState.update { state ->
                if (state.belongsTo(taskId, runningSessionId)) state.copy(errorMessage = error.message ?: "生成失败") else state
            }
        } finally {
            _uiState.update { state ->
                if (state.belongsTo(taskId, runningSessionId)) {
                    state.copy(isRunning = false, currentTaskId = null, runningSessionId = null)
                } else {
                    state
                }
            }
        }
    }

    private fun buildRequest(state: CreateUiState, provider: Provider?, model: ModelProfile?): GenerationRequest? {
        val selectedProvider = provider ?: return null
        val selectedModel = model ?: return null
        if (selectedModel.providerId != selectedProvider.id) return null
        return GenerationRequest(
            providerId = selectedProvider.id,
            model = selectedModel.modelName,
            prompt = state.prompt,
            size = state.size,
            quality = state.quality,
            outputFormat = state.outputFormat,
            count = state.count,
            advancedJson = state.advancedJson,
            inputAssets = state.inputAssets.toList(),
            maskAsset = state.maskAsset,
        )
    }

    private fun CreateUiState.belongsTo(taskId: String, sessionId: String?): Boolean =
        currentTaskId == taskId && currentSessionId == sessionId && runningSessionId == sessionId

    private fun validationMessage(error: ValidationError): String = when (error) {
        ValidationError.EMPTY_PROMPT -> "请输入提示词"
        ValidationError.MODEL_REQUIRED -> "请选择模型"
        ValidationError.GENERATION_NOT_SUPPORTED -> "当前模型不支持文生图"
        ValidationError.EDIT_NOT_SUPPORTED -> "当前模型不支持图片编辑"
        ValidationError.UNSUPPORTED_SIZE -> "当前模型不支持该尺寸"
        ValidationError.UNSUPPORTED_QUALITY -> "当前模型不支持该质量"
        ValidationError.INVALID_COUNT -> "生成数量无效"
        ValidationError.MULTIPLE_IMAGES_NOT_SUPPORTED -> "当前模型不支持多张参考图"
        ValidationError.EDIT_IMAGE_REQUIRED -> "蒙版需要输入图片"
        ValidationError.MASK_NOT_SUPPORTED -> "当前模型不支持蒙版"
        ValidationError.CORE_FIELD_OVERRIDE -> "高级参数不能覆盖核心字段"
        ValidationError.INVALID_ADVANCED_JSON -> "高级参数 JSON 无效"
    }

    private fun selectParameter(current: String, supported: Set<String>): String =
        current.takeIf { it.isNotBlank() && (supported.isEmpty() || it in supported) }
            ?: supported.firstOrNull().orEmpty()
}
