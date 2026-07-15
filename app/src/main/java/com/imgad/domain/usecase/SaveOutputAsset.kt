package com.imgad.domain.usecase

import com.imgad.domain.port.FileStore
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.util.UUID

class SaveOutputAsset(private val fileStore: FileStore) {
    operator fun invoke(bytes: ByteArray, mediaType: String, messageId: String? = null): Asset {
        val stored = fileStore.writeOutput(bytes, mediaType, messageId)
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
            source = AssetSource.OUTPUT,
        )
    }

    fun delete(asset: Asset) {
        var failure: Exception? = null
        asset.thumbnailUri?.let { thumbnail ->
            try {
                fileStore.delete(thumbnail)
            } catch (error: Exception) {
                failure = error
            }
        }
        try {
            fileStore.delete(asset.localUri)
        } catch (error: Exception) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { throw it }
    }
}
