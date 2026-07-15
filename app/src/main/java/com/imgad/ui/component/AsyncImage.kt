package com.imgad.ui.component

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface AsyncImageState {
    data object Loading : AsyncImageState
    data object Empty : AsyncImageState
    data object Error : AsyncImageState
    data class Ready(val bitmap: android.graphics.Bitmap) : AsyncImageState
}

@Composable
fun AsyncImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDecodeSize: Int = 1024,
) {
    val context = LocalContext.current.applicationContext
    var state by remember(uri, maxDecodeSize) {
        mutableStateOf<AsyncImageState>(if (uri.isNullOrBlank()) AsyncImageState.Empty else AsyncImageState.Loading)
    }
    LaunchedEffect(uri, maxDecodeSize) {
        if (uri.isNullOrBlank()) {
            state = AsyncImageState.Empty
            return@LaunchedEffect
        }
        state = AsyncImageState.Loading
        state = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampledBitmap(
                    sourceProvider = { openSource(context, uri) },
                    maxSide = maxDecodeSize,
                )
                bitmap?.let(AsyncImageState::Ready) ?: AsyncImageState.Error
            }.getOrDefault(AsyncImageState.Error)
        }
    }
    val bitmap = (state as? AsyncImageState.Ready)?.bitmap
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val imageState = state) {
            AsyncImageState.Loading -> CircularProgressIndicator()
            AsyncImageState.Empty -> Text("无图片")
            AsyncImageState.Error -> Text("加载失败", color = MaterialTheme.colorScheme.error)
            is AsyncImageState.Ready -> Image(
                bitmap = imageState.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

internal fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
    require(maxSide > 0) { "maxSide must be positive" }
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    val longestSide = maxOf(width, height).toLong()
    while ((longestSide + sampleSize - 1L) / sampleSize > maxSide) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun decodeSampledBitmap(
    sourceProvider: () -> InputStream?,
    maxSide: Int,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsSource = sourceProvider() ?: return null
    boundsSource.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
    }
    return sourceProvider()?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun openSource(context: android.content.Context, source: String): InputStream? {
    val uri = Uri.parse(source)
    return if (uri.scheme == null || uri.scheme == "file") {
        FileInputStream(uri.path ?: source)
    } else {
        context.contentResolver.openInputStream(uri)
    }
}
