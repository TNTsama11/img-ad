package com.imgad.domain.usecase

import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.port.FileStore
import com.imgad.domain.port.GenerationStore
import com.imgad.domain.port.ImageGenerationGateway
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class GenerateImage(
    private val gateway: ImageGenerationGateway,
    private val store: GenerationStore,
    fileStore: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val saveOutputAsset = SaveOutputAsset(fileStore)

    suspend operator fun invoke(
        sessionId: String,
        request: GenerationRequest,
        title: String = request.prompt.take(MAX_TITLE_LENGTH),
    ): GenerationResult {
        require(!request.isEdit) { "GenerateImage does not accept input assets" }
        val now = clock()
        val task = store.beginTask(
            sessionId = sessionId,
            title = title,
            prompt = request.prompt,
            requestSnapshotJson = GenerationSnapshot.encode(request),
            inputAssets = emptyList(),
            now = now,
        )
        val savedAssets = mutableListOf<com.imgad.domain.model.Asset>()
        return try {
            val result = gateway.generate(request)
            result.images.forEach { image ->
                savedAssets += saveOutputAsset(image.bytes, image.mediaType ?: DEFAULT_IMAGE_MEDIA_TYPE, task.messageId)
            }
            store.markSucceeded(task.messageId, savedAssets.toList(), clock())
            result
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                cleanup(savedAssets, cancellation)
                try {
                    store.markCanceled(task.messageId, clock())
                } catch (cleanupFailure: Exception) {
                    cancellation.addSuppressed(cleanupFailure)
                }
            }
            throw cancellation
        } catch (error: Exception) {
            cleanup(savedAssets, error)
            try {
                store.markFailed(task.messageId, ErrorSnapshotEncoder.encode(error), clock())
            } catch (stateFailure: Exception) {
                error.addSuppressed(stateFailure)
            }
            throw error
        }
    }

    private fun cleanup(assets: List<com.imgad.domain.model.Asset>, original: Throwable) {
        assets.forEach { asset ->
            try {
                saveOutputAsset.delete(asset)
            } catch (cleanupFailure: Exception) {
                original.addSuppressed(cleanupFailure)
            }
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 80
        const val DEFAULT_IMAGE_MEDIA_TYPE = "image/png"
    }
}
