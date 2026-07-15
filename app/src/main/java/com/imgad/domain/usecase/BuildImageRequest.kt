package com.imgad.domain.usecase

import com.imgad.domain.model.GenerationRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BuildImageRequest {
    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(request: GenerationRequest): JsonObject {
        val advanced = request.advancedJson?.trim().takeUnless { it.isNullOrEmpty() }?.let {
            try {
                json.parseToJsonElement(it) as? JsonObject
                    ?: throw InvalidAdvancedJsonException()
            } catch (error: SerializationException) {
                throw InvalidAdvancedJsonException(error)
            }
        } ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("model", request.model)
            put("prompt", request.prompt)
            put("size", request.size)
            put("quality", request.quality)
            put("output_format", request.outputFormat)
            put("n", request.count)
            advanced.forEach { (key, value) -> if (key !in CORE_FIELDS) put(key, value) }
        }
    }

    class InvalidAdvancedJsonException(cause: Throwable? = null) : IllegalArgumentException("Invalid advanced JSON", cause)

    private companion object {
        val CORE_FIELDS = setOf("model", "prompt", "image", "mask", "size", "quality", "output_format", "n")
    }
}
