package com.imgad.domain.usecase

import com.imgad.domain.model.GenerationFailure
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object ErrorSnapshotEncoder {
    fun encode(error: Exception): String = buildJsonObject {
        when (error) {
            is GenerationFailure -> {
                put("kind", error.error.kind.name)
                put("message", error.error.message)
            }
            else -> {
                put("kind", "UNKNOWN")
                put("message", error.message ?: "Generation failed")
            }
        }
    }.toString()
}
