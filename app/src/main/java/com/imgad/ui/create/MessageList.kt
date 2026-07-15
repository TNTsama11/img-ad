package com.imgad.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imgad.domain.model.Asset
import com.imgad.domain.model.Message
import com.imgad.domain.model.TaskState
import com.imgad.ui.component.AsyncImage

@Composable
fun MessageList(
    messages: List<Message>,
    assetsByMessage: Map<String, List<Asset>>,
    onRetry: (String) -> Unit,
    onPreview: (Asset) -> Unit,
    onCopyError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, assetsByMessage) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = Message::id) { message ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (message.text.isNotBlank()) Text(message.text)
                    message.requestSnapshotJson?.let { Text("参数：${it.take(120)}", style = MaterialTheme.typography.bodySmall) }
                    Text(statusLabel(message.taskState), style = MaterialTheme.typography.labelMedium)
                    assetsByMessage[message.id].orEmpty().forEach { asset ->
                        if (!asset.available) {
                            Text("资源不可用", color = MaterialTheme.colorScheme.error)
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(asset.thumbnailUri ?: asset.localUri, "生成图片", Modifier.width(88.dp).height(88.dp))
                            TextButton(onClick = { onPreview(asset) }) { Text("查看图片") }
                        }
                    }
                    if (message.taskState == TaskState.FAILED) {
                        val details = message.errorJson ?: "生成失败"
                        Text(details, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { onCopyError(details) }) { Text("复制详情") }
                            Button(onClick = { onRetry(message.id) }) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(state: TaskState): String = when (state) {
    TaskState.PENDING -> "等待中"
    TaskState.RUNNING -> "生成中"
    TaskState.SUCCEEDED -> "已完成"
    TaskState.FAILED -> "失败"
    TaskState.CANCELED -> "已取消"
}
