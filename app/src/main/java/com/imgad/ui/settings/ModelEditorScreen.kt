package com.imgad.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.effectiveQualities
import com.imgad.domain.model.effectiveSizes
import java.util.UUID

@Composable
fun ModelEditorScreen(
    providerId: String,
    initial: ModelProfile?,
    onSave: (ModelProfile) -> Unit,
    onBack: () -> Unit,
) {
    var modelName by remember(initial?.id) { mutableStateOf(initial?.modelName.orEmpty()) }
    var displayName by remember(initial?.id) { mutableStateOf(initial?.displayName.orEmpty()) }
    var generation by remember(initial?.id) { mutableStateOf(initial?.supportsGeneration ?: true) }
    var edit by remember(initial?.id) { mutableStateOf(initial?.supportsEdit ?: false) }
    var mask by remember(initial?.id) { mutableStateOf(initial?.supportsMask ?: false) }
    var multiple by remember(initial?.id) { mutableStateOf(initial?.supportsMultipleImages ?: false) }
    var sizes by remember(initial?.id) {
        mutableStateOf(initial?.effectiveSizes()?.joinToString(", ") ?: "1024x1024")
    }
    var qualities by remember(initial?.id) {
        mutableStateOf(initial?.effectiveQualities()?.joinToString(", ") ?: "standard")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("模型编辑")
        OutlinedTextField(modelName, { modelName = it }, Modifier.fillMaxWidth(), label = { Text("模型标识") })
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") })
        OutlinedTextField(sizes, { sizes = it }, Modifier.fillMaxWidth(), label = { Text("支持尺寸（逗号分隔）") })
        OutlinedTextField(qualities, { qualities = it }, Modifier.fillMaxWidth(), label = { Text("支持质量（逗号分隔）") })
        Capability("文生图", generation) { generation = it }
        Capability("编辑", edit) { edit = it }
        Capability("蒙版", mask) { mask = it }
        Capability("多图", multiple) { multiple = it }
        Button(onClick = {
            onSave(
                initial?.snapshotCopy(
                    providerId = providerId,
                    modelName = modelName,
                    displayName = displayName,
                    supportsGeneration = generation,
                    supportsEdit = edit,
                    supportsMask = mask,
                    supportsMultipleImages = multiple,
                    supportedSizes = parseModelOptions(sizes),
                    supportedQualities = parseModelOptions(qualities),
                ) ?: ModelProfile(
                    id = UUID.randomUUID().toString(),
                    providerId = providerId,
                    modelName = modelName,
                    displayName = displayName,
                    supportsGeneration = generation,
                    supportsEdit = edit,
                    supportsMask = mask,
                    supportsMultipleImages = multiple,
                    supportedSizes = parseModelOptions(sizes),
                    supportedQualities = parseModelOptions(qualities),
                ),
            )
        }, enabled = modelName.isNotBlank() && displayName.isNotBlank()) { Text("保存") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

internal fun parseModelOptions(value: String): Set<String> = value
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toSet()

@Composable
private fun Capability(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row {
        Checkbox(checked, onCheckedChange)
        Text(label)
    }
}
