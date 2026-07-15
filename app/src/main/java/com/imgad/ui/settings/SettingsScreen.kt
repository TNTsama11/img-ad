package com.imgad.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imgad.domain.model.Provider

data class SettingsScreenActions(
    val onEditProvider: (String?) -> Unit = {},
    val onEditModel: (String?) -> Unit = {},
    val onDeleteProvider: (String, String?) -> Unit = { _, _ -> },
    val onDeleteModel: (String) -> Unit = {},
    val onSetDefaultModel: (String, String) -> Unit = { _, _ -> },
    val onOpenStorage: () -> Unit = {},
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, actions: SettingsScreenActions = SettingsScreenActions()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state,
        actions.copy(
            onDeleteProvider = viewModel::deleteProvider,
            onSetDefaultModel = { providerId, modelId -> viewModel.setDefaultModel(providerId, modelId) },
        ),
        viewModel::selectProvider,
        viewModel::setDefaultProvider,
        viewModel::testConnection,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    onSelectProvider: (String) -> Unit = {},
    onSetDefaultProvider: (String) -> Unit = {},
    onTestConnection: (Provider) -> Unit = {},
) {
    var pendingDeleteProviderId by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.errorMessage?.let { message ->
                item("error") { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item("providers-header") { Text("供应方", style = MaterialTheme.typography.titleMedium) }
            item("add-provider") { Button(onClick = { actions.onEditProvider(null) }) { Text("新增供应方") } }
            items(state.providers, key = { "provider-${it.id}" }) { provider ->
                ProviderRow(provider, state, actions, onSelectProvider, onSetDefaultProvider, onTestConnection) {
                    pendingDeleteProviderId = it
                }
            }
            state.selectedProviderId?.let { providerId ->
                item("models-header-$providerId") { Text("模型", style = MaterialTheme.typography.titleMedium) }
                items(state.models, key = { "model-${it.id}" }) { model ->
                    val isDefault = state.providers.firstOrNull { it.id == providerId }?.defaultModelId == model.id
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(model.displayName)
                        Row {
                            TextButton(
                                onClick = { actions.onSetDefaultModel(providerId, model.id) },
                                enabled = !isDefault,
                                modifier = Modifier.testTag("model-${model.id}-default"),
                            ) { Text(if (isDefault) "默认模型" else "设为默认") }
                            TextButton(
                                onClick = { actions.onEditModel(model.id) },
                                modifier = Modifier.testTag("model-${model.id}-edit"),
                            ) { Text("编辑") }
                            TextButton(
                                onClick = { actions.onDeleteModel(model.id) },
                                modifier = Modifier.testTag("model-${model.id}-delete"),
                            ) { Text("删除") }
                        }
                    }
                }
                item("add-model-$providerId") {
                    TextButton(onClick = { actions.onEditModel(null) }) { Text("新增模型") }
                }
            }
            item("storage") { TextButton(onClick = actions.onOpenStorage) { Text("存储") } }
        }
    }
    pendingDeleteProviderId?.let { providerId ->
        val replacements = state.providers.filterNot { it.id == providerId }
        AlertDialog(
            onDismissRequest = { pendingDeleteProviderId = null },
            title = { Text("删除供应方") },
            text = {
                Column {
                    Text("如果它是默认供应方，请先选择新的默认项。")
                    replacements.forEach { replacement ->
                        TextButton(onClick = {
                            pendingDeleteProviderId = null
                            actions.onDeleteProvider(providerId, replacement.id)
                        }) { Text("使用 ${replacement.name} 并删除") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteProviderId = null
                    actions.onDeleteProvider(providerId, null)
                }) { Text("删除但不替换") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteProviderId = null }) { Text("取消") } },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProviderRow(
    provider: Provider,
    state: SettingsUiState,
    actions: SettingsScreenActions,
    onSelectProvider: (String) -> Unit,
    onSetDefaultProvider: (String) -> Unit,
    onTestConnection: (Provider) -> Unit,
    onDeleteRequested: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(provider.name)
            Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { onSelectProvider(provider.id) },
                    modifier = Modifier.testTag("provider-${provider.id}-select"),
                ) { Text("选择") }
                TextButton(
                    onClick = { onSetDefaultProvider(provider.id) },
                    modifier = Modifier.testTag("provider-${provider.id}-default"),
                ) { Text("设为默认") }
                TextButton(
                    onClick = { actions.onEditProvider(provider.id) },
                    modifier = Modifier.testTag("provider-${provider.id}-edit"),
                ) { Text("编辑") }
                TextButton(
                    onClick = { onDeleteRequested(provider.id) },
                    modifier = Modifier.testTag("provider-${provider.id}-delete"),
                ) { Text("删除") }
                TextButton(
                    onClick = { onTestConnection(provider) },
                    enabled = !state.isTestingConnection,
                    modifier = Modifier.testTag("provider-${provider.id}-test"),
                ) {
                    Text("测试连接")
                }
            }
            Text(state.connectionWarning, style = MaterialTheme.typography.labelSmall)
            when (state.connectionState) {
                ConnectionUiState.SUCCESS -> Text("连接成功")
                ConnectionUiState.FAILURE -> Text(state.connectionError ?: "连接失败")
                else -> Unit
            }
        }
    }
}
