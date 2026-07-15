package com.imgad.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.imgad.domain.model.Provider
import java.util.UUID

data class ProviderModelDiscoveryUiState(
    val modelIds: List<String> = emptyList(),
    val selectedModelIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleModelIds: List<String>
        get() = modelIds.filter { modelId ->
            searchQuery.isBlank() || modelId.contains(searchQuery.trim(), ignoreCase = true)
        }
}

@Composable
fun ProviderEditorScreen(
    initial: Provider?,
    apiKeyVisible: Boolean,
    discovery: ProviderModelDiscoveryUiState,
    onSave: (Provider, String, Set<String>) -> Unit,
    onFetchModels: (Provider, String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleModel: (String) -> Unit,
    onClearDiscoveredModels: () -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onBack: () -> Unit,
) {
    val providerId = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember(initial?.id) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var apiKey by remember(initial?.id) { mutableStateOf("") }
    val currentProvider = {
        initial?.copy(
            name = name,
            baseUrl = baseUrl,
            updatedAt = System.currentTimeMillis(),
        ) ?: Provider(
            id = providerId,
            name = name,
            baseUrl = baseUrl,
            apiKeyAlias = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("title") {
            Text(
                if (initial == null) "新增供应方" else "编辑供应方",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item("name") {
            OutlinedTextField(
                name,
                { name = it },
                Modifier.fillMaxWidth().testTag("provider-name"),
                label = { Text("名称") },
                singleLine = true,
            )
        }
        item("base-url") {
            OutlinedTextField(
                baseUrl,
                { value ->
                    if (value != baseUrl) onClearDiscoveredModels()
                    baseUrl = value
                },
                Modifier.fillMaxWidth().testTag("provider-base-url"),
                label = { Text("Base URL") },
                singleLine = true,
            )
        }
        item("api-key") {
            OutlinedTextField(
                apiKey,
                { value ->
                    if (value != apiKey) onClearDiscoveredModels()
                    apiKey = value
                },
                Modifier.fillMaxWidth().testTag("provider-api-key"),
                label = { Text("API Key") },
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item("api-key-visibility") {
            TextButton(onClick = onToggleApiKeyVisibility) {
                Text(if (apiKeyVisible) "隐藏 API Key" else "显示 API Key")
            }
        }
        item("fetch-models") {
            Button(
                onClick = { onFetchModels(currentProvider(), apiKey) },
                enabled = baseUrl.isNotBlank() && (initial != null || apiKey.isNotBlank()) && !discovery.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("provider-fetch-models"),
            ) {
                if (discovery.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text("正在获取")
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("获取模型")
                }
            }
        }
        discovery.errorMessage?.let { message ->
            item("fetch-error") { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (discovery.modelIds.isNotEmpty()) {
            item("search") {
                OutlinedTextField(
                    discovery.searchQuery,
                    onSearchQueryChanged,
                    Modifier.fillMaxWidth().testTag("provider-model-search"),
                    label = { Text("搜索模型") },
                    singleLine = true,
                )
            }
            item("selection-summary") {
                Text(
                    "已选择 ${discovery.selectedModelIds.size} / ${discovery.modelIds.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(discovery.visibleModelIds, key = { "discovered-$it" }) { modelId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleModel(modelId) }
                        .testTag("provider-model-$modelId")
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = modelId in discovery.selectedModelIds,
                        onCheckedChange = null,
                    )
                    Text(modelId, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }
        }
        item("save") {
            val selectedCount = discovery.selectedModelIds.size
            Button(
                onClick = { onSave(currentProvider(), apiKey, discovery.selectedModelIds) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("provider-save"),
            ) {
                Text(if (selectedCount == 0) "保存供应方" else "保存并导入模型（$selectedCount）")
            }
        }
        item("back") {
            TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) { Text("返回") }
        }
    }
}
