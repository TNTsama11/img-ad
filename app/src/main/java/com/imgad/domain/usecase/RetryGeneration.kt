package com.imgad.domain.usecase

import com.imgad.domain.model.GenerationResult
import com.imgad.domain.port.GenerationStore

class RetryGeneration(
    private val store: GenerationStore,
    private val generateImage: GenerateImage,
    private val editImage: EditImage,
) {
    suspend operator fun invoke(messageId: String): GenerationResult {
        val message = requireNotNull(store.getMessage(messageId)) { "Generation message does not exist" }
        val snapshot = requireNotNull(message.requestSnapshotJson) { "Generation snapshot is missing" }
        val request = GenerationSnapshot.decode(snapshot)
        return if (request.isEdit) {
            editImage(message.sessionId, request)
        } else {
            generateImage(message.sessionId, request)
        }
    }
}
