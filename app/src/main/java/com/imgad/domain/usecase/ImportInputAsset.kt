package com.imgad.domain.usecase

import com.imgad.domain.port.FileStore
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.util.UUID

class ImportInputAsset(private val fileStore: FileStore) {
    operator fun invoke(uri: String, messageId: String? = null): Asset {
        val stored = fileStore.copyInput(uri, messageId)
        val thumbnail = try {
            if (stored.mediaType.startsWith("image/")) {
                fileStore.createThumbnail(stored.localUri, messageId)
            } else {
                null
            }
        } catch (error: Exception) {
            try {
                fileStore.delete(stored.localUri)
            } catch (cleanupFailure: Exception) {
                error.addSuppressed(cleanupFailure)
            }
            throw error
        }
        return Asset(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            localUri = stored.localUri,
            thumbnailUri = thumbnail?.localUri,
            mediaType = stored.mediaType,
            width = stored.width,
            height = stored.height,
            byteSize = stored.byteSize,
            source = AssetSource.INPUT,
        )
    }
}
