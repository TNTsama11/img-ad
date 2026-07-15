package com.imgad.ui.create

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.imgad.domain.model.Asset
import com.imgad.ui.component.AsyncImage

@Composable
fun PromptComposer(
    state: CreateUiState,
    supportsMask: Boolean,
    supportsMultipleImages: Boolean,
    onPromptChanged: (String) -> Unit,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    onRemoveAsset: (String) -> Unit,
    onPickMask: () -> Unit,
    onClearMask: () -> Unit,
    onOpenParameters: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.inputAssets.isNotEmpty() || state.maskAsset != null) {
                Text("参考图 / 编辑")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.inputAssets.forEachIndexed { index, asset ->
                        Attachment(asset, { onRemoveAsset(asset.id) }, label = "参考图 ${index + 1}")
                    }
                    state.maskAsset?.let { mask -> Attachment(mask, onClearMask, label = "蒙版") }
                }
            }
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("提示词") },
                minLines = 2,
                enabled = !state.isRunning,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onPickSingle, enabled = !state.isRunning) { Text("添加图片") }
                if (supportsMultipleImages) {
                    TextButton(onClick = onPickMultiple, enabled = !state.isRunning) { Text("添加多图") }
                }
                if (supportsMask) {
                    TextButton(onClick = onPickMask, enabled = !state.isRunning) { Text("选择蒙版") }
                }
                TextButton(onClick = onOpenParameters, enabled = !state.isRunning) { Text("参数") }
            }
            if (state.errorMessage != null) Text(state.errorMessage)
            if (state.isRunning) {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.inputAssets.isNotEmpty() || state.maskAsset != null) "编辑" else "生成")
                }
            }
        }
    }
}

@Composable
private fun Attachment(asset: Asset, onRemove: () -> Unit, label: String = "参考图") {
    val fileName = Uri.parse(asset.localUri).lastPathSegment?.substringAfterLast('/')
    val description = listOfNotNull(label, fileName?.takeIf(String::isNotBlank)).joinToString(" ")
    Column(modifier = Modifier.width(88.dp)) {
        AsyncImage(
            asset.thumbnailUri ?: asset.localUri,
            description,
            Modifier.width(88.dp).height(64.dp),
            maxDecodeSize = 256,
        )
        TextButton(
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = "删除 $description" },
        ) { Text("删除") }
    }
}
