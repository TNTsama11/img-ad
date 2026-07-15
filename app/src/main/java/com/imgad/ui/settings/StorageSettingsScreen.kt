package com.imgad.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.imgad.domain.port.ArchiveImportPreview

data class StorageUsage(val bytes: Long, val files: Int)

data class StorageSettingsActions(
    val onClear: () -> Unit = {},
    val onImport: (String?) -> Unit = {},
    val onExport: (Boolean, String?) -> Unit = { _, _ -> },
    val onConfirmImport: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
)

@Composable
fun StorageSettingsScreen(
    usage: StorageUsage,
    actions: StorageSettingsActions = StorageSettingsActions(),
    statusMessage: String? = null,
    importPreview: ArchiveImportPreview? = null,
) {
    var confirm by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var includeSecrets by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("私有资源：${usage.bytes} 字节，${usage.files} 个文件")
        statusMessage?.let { Text(it) }
        Button(onClick = { confirm = true }) { Text("清理私有缓存") }
        TextButton(onClick = {
            importPassword = ""
            showImport = true
        }) { Text("导入") }
        TextButton(onClick = {
            includeSecrets = false
            exportPassword = ""
            showExport = true
        }) { Text("导出") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认清理？") },
            text = { Text("将删除未引用的应用私有图片，不影响系统图库副本。") },
            confirmButton = {
                TextButton(onClick = { confirm = false; actions.onClear() }) { Text("清理") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = {
                showExport = false
                includeSecrets = false
                exportPassword = ""
            },
            title = { Text("导出本地数据") },
            text = {
                Column {
                    Row {
                        Checkbox(
                            modifier = Modifier.testTag("include-secrets"),
                            checked = includeSecrets,
                            onCheckedChange = {
                                includeSecrets = it
                                if (!it) exportPassword = ""
                            },
                        )
                        Text("包含 API Key")
                    }
                    if (includeSecrets) {
                        OutlinedTextField(
                            value = exportPassword,
                            onValueChange = { exportPassword = it },
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !includeSecrets || exportPassword.isNotBlank(),
                    onClick = {
                        actions.onExport(includeSecrets, exportPassword.takeIf(String::isNotBlank))
                        showExport = false
                        includeSecrets = false
                        exportPassword = ""
                    },
                ) { Text("选择导出位置") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExport = false
                        includeSecrets = false
                        exportPassword = ""
                    },
                ) { Text("取消") }
            },
        )
    }
    if (showImport) {
        AlertDialog(
            onDismissRequest = {
                showImport = false
                importPassword = ""
            },
            title = { Text("导入本地数据") },
            text = {
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text("密码（如需要）") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onImport(importPassword.takeIf(String::isNotBlank))
                        showImport = false
                        importPassword = ""
                    },
                ) { Text("选择归档") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImport = false
                        importPassword = ""
                    },
                ) { Text("取消") }
            },
        )
    }
    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = actions.onCancelImport,
            title = { Text("确认导入") },
            text = { Text("供应方 ${preview.providers}，会话 ${preview.sessions}，资源 ${preview.assets}") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm-import"),
                    onClick = actions.onConfirmImport,
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("cancel-import"),
                    onClick = actions.onCancelImport,
                ) { Text("取消") }
            },
        )
    }
}
