package com.imgad.ui.create

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.imgad.domain.model.Asset
import com.imgad.ui.component.AsyncImage
import kotlin.math.roundToInt

@Composable
fun ImagePreviewScreen(
    asset: Asset,
    requestSnapshotJson: String?,
    onClose: () -> Unit,
    onSave: (Asset) -> Unit,
    onShare: (Asset) -> Unit,
) {
    var scale by remember(asset.id) { mutableFloatStateOf(1f) }
    var offset by remember(asset.id) { mutableStateOf(Offset.Zero) }
    var showParameters by remember(asset.id) { mutableStateOf(false) }
    var containerSize by remember(asset.id) { mutableStateOf(IntSize.Zero) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClose) { Text("关闭") }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showParameters = !showParameters }) { Text("查看参数") }
                    TextButton(onClick = { onShare(asset) }) { Text("分享") }
                    Button(onClick = { onSave(asset) }) { Text("保存") }
                }
            }
            if (showParameters) {
                Text(
                    text = requestSnapshotJson ?: "无参数快照",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview-image-container")
                    .semantics { stateDescription = "缩放 ${(scale * 100).roundToInt()}%" }
                    .onSizeChanged { size ->
                        containerSize = size
                        offset = constrainOffset(offset, scale, size)
                    }
                    .pointerInput(asset.id, containerSize) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale == 1f) {
                                Offset.Zero
                            } else {
                                constrainOffset(offset + pan, nextScale, containerSize)
                            }
                        }
                    },
            ) {
                AsyncImage(
                    uri = asset.localUri,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                    maxDecodeSize = 2048,
                )
            }
        }
    }
}

internal fun constrainOffset(offset: Offset, scale: Float, containerSize: IntSize): Offset {
    if (scale <= 1f || containerSize == IntSize.Zero) return Offset.Zero
    val maxX = containerSize.width * (scale - 1f) / 2f
    val maxY = containerSize.height * (scale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}
