package com.imgad.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParameterSheet(
    state: CreateUiState,
    model: ModelProfile?,
    onDismiss: () -> Unit,
    onApply: (String, String, String, Int, String?) -> Unit,
) {
    val sizeOptions = model?.effectiveSizes().orEmpty()
    val qualityOptions = model?.effectiveQualities().orEmpty()
    var size by remember(state.size, sizeOptions) {
        mutableStateOf(state.size.takeIf { it in sizeOptions } ?: sizeOptions.firstOrNull().orEmpty())
    }
    var quality by remember(state.quality, qualityOptions) {
        mutableStateOf(state.quality.takeIf { it in qualityOptions } ?: qualityOptions.firstOrNull().orEmpty())
    }
    var format by remember(state.outputFormat) { mutableStateOf(state.outputFormat) }
    var count by remember(state.count) { mutableStateOf(state.count.toString()) }
    var advanced by remember(state.advancedJson) { mutableStateOf(state.advancedJson.orEmpty()) }
    var showAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("生成参数")
            if (sizeOptions.isEmpty()) {
                OutlinedTextField(size, { size = it }, Modifier.fillMaxWidth(), label = { Text("尺寸") })
            } else {
                Text("尺寸")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeOptions.forEach { option ->
                        FilterChip(
                            selected = size == option,
                            onClick = { size = option },
                            label = { Text(option) },
                        )
                    }
                }
            }
            if (qualityOptions.isEmpty()) {
                OutlinedTextField(quality, { quality = it }, Modifier.fillMaxWidth(), label = { Text("质量") })
            } else {
                Text("质量")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualityOptions.forEach { option ->
                        FilterChip(
                            selected = quality == option,
                            onClick = { quality = option },
                            label = { Text(option) },
                        )
                    }
                }
            }
            OutlinedTextField(format, { format = it }, Modifier.fillMaxWidth(), label = { Text("输出格式") })
            OutlinedTextField(count, { count = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("数量") })
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "收起高级 JSON" else "高级 JSON")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = advanced,
                    onValueChange = { advanced = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("高级 JSON") },
                    minLines = 4,
                )
            }
            state.errorMessage?.let { Text(it) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        onApply(size, quality, format, count.toIntOrNull() ?: 1, advanced.ifBlank { null })
                        onDismiss()
                    },
                ) { Text("应用") }
            }
        }
    }
}
