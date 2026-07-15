package com.imgad.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imgad.domain.model.Asset

const val CREATE_ROUTE = "create"

data class CreateScreenActions(
    val onSelectProvider: (String?) -> Unit = {},
    val onSelectModel: (String?) -> Unit = {},
    val onPromptChanged: (String) -> Unit = {},
    val onParametersChanged: (String, String, String, Int, String?) -> Unit = { _, _, _, _, _ -> },
    val onPickedUris: (List<String>) -> Unit = {},
    val onPickedMask: (String) -> Unit = {},
    val onRemoveAsset: (String) -> Unit = {},
    val onClearMask: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val onPreview: (Asset) -> Unit = {},
    val onCopyError: (String) -> Unit = {},
)

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onPickedUris: (List<String>) -> Unit,
    onPickedMask: (String) -> Unit,
    onPreview: (Asset) -> Unit,
    onCopyError: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CreateScreen(
        state = state,
        actions = CreateScreenActions(
            onSelectProvider = viewModel::selectProvider,
            onSelectModel = viewModel::selectModel,
            onPromptChanged = viewModel::updatePrompt,
            onParametersChanged = viewModel::updateParameters,
            onPickedUris = onPickedUris,
            onPickedMask = onPickedMask,
            onRemoveAsset = viewModel::removeAsset,
            onClearMask = viewModel::clearMask,
            onSubmit = viewModel::submit,
            onCancel = viewModel::cancel,
            onRetry = viewModel::retry,
            onPreview = onPreview,
            onCopyError = onCopyError,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    state: CreateUiState,
    actions: CreateScreenActions,
) {
    var showParameters by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    val selectedModel = state.selectedModelId?.let { id -> state.models.singleOrNull { it.id == id } }
    val selectedProvider = state.selectedProviderId?.let { id -> state.providers.singleOrNull { it.id == id } }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        dispatchPickedUri(uri, actions.onPickedUris)
    }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) actions.onPickedUris(uris.map { it.toString() })
    }
    val maskPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.onPickedMask(it.toString()) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (state.currentSessionId == null) "新建创作" else "创作详情",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                CurrentModelSelector(
                    providerName = selectedProvider?.name,
                    modelName = selectedModel?.displayName,
                    onClick = { showModelSelector = true },
                )
            }
        },
        bottomBar = {
            PromptComposer(
                state = state,
                supportsMask = selectedModel?.supportsMask == true,
                supportsMultipleImages = selectedModel?.supportsMultipleImages == true,
                onPromptChanged = actions.onPromptChanged,
                onPickSingle = {
                    singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickMultiple = {
                    multiplePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveAsset = actions.onRemoveAsset,
                onPickMask = {
                    maskPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClearMask = actions.onClearMask,
                onOpenParameters = { showParameters = true },
                onSubmit = actions.onSubmit,
                onCancel = actions.onCancel,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            MessageList(
                messages = state.messages,
                assetsByMessage = state.messageAssets,
                onRetry = actions.onRetry,
                onPreview = actions.onPreview,
                onCopyError = actions.onCopyError,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showParameters) {
        ParameterSheet(
            state = state,
            model = selectedModel,
            onDismiss = { showParameters = false },
            onApply = actions.onParametersChanged,
        )
    }

    if (showModelSelector) {
        ModelSelectorSheet(
            state = state,
            onDismiss = { showModelSelector = false },
            onSelected = { providerId, modelId ->
                actions.onSelectProvider(providerId)
                actions.onSelectModel(modelId)
                showModelSelector = false
            },
        )
    }
}

internal fun dispatchPickedUri(uri: Uri?, onPickedUris: (List<String>) -> Unit) {
    if (uri != null) onPickedUris(listOf(uri.toString()))
}

@Composable
private fun CurrentModelSelector(
    providerName: String?,
    modelName: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("model-selector"),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                Text("当前模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(modelName ?: "选择模型", style = MaterialTheme.typography.titleMedium)
                Text(
                    providerName ?: "尚未配置供应方",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "切换模型",
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorSheet(
    state: CreateUiState,
    onDismiss: () -> Unit,
    onSelected: (String, String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择模型",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        val providersWithModels = state.providers.mapNotNull { provider ->
            state.models.filter { it.providerId == provider.id && it.enabled }
                .takeIf { it.isNotEmpty() }
                ?.let { provider to it }
        }
        if (providersWithModels.isEmpty()) {
            Text(
                "暂无可用模型，请先在设置中添加或获取模型。",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                providersWithModels.forEach { (provider, models) ->
                    item("provider-${provider.id}") {
                        Text(
                            provider.name,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(models, key = { it.id }) { model ->
                        ListItem(
                            headlineContent = { Text(model.displayName) },
                            supportingContent = { Text(model.modelName) },
                            leadingContent = {
                                RadioButton(
                                    selected = model.id == state.selectedModelId,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(provider.id, model.id) }
                                .testTag("model-option-${model.id}"),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
