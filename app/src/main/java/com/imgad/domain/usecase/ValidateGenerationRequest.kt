package com.imgad.domain.usecase

import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.ValidationError
import com.imgad.domain.model.effectiveQualities
import com.imgad.domain.model.effectiveSizes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.SerializationException

class ValidateGenerationRequest {
    private val json = Json
    private val protectedFields = setOf(
        "model",
        "prompt",
        "image",
        "mask",
        "size",
        "quality",
        "output_format",
        "n",
    )

    operator fun invoke(
        request: GenerationRequest,
        modelProfile: ModelProfile,
    ): ValidationError? {
        if (request.prompt.isBlank()) return ValidationError.EMPTY_PROMPT
        if (request.model.isBlank()) return ValidationError.MODEL_REQUIRED
        if (request.isEdit && !modelProfile.supportsEdit) return ValidationError.EDIT_NOT_SUPPORTED
        if (!request.isEdit && !modelProfile.supportsGeneration) return ValidationError.GENERATION_NOT_SUPPORTED
        if (request.size !in modelProfile.effectiveSizes()) return ValidationError.UNSUPPORTED_SIZE
        if (request.quality !in modelProfile.effectiveQualities()) return ValidationError.UNSUPPORTED_QUALITY
        if (request.count !in 1..10) return ValidationError.INVALID_COUNT
        if (request.inputAssets.size > 1 && !modelProfile.supportsMultipleImages) {
            return ValidationError.MULTIPLE_IMAGES_NOT_SUPPORTED
        }
        if (request.maskAsset != null && request.inputAssets.isEmpty()) {
            return ValidationError.EDIT_IMAGE_REQUIRED
        }
        if (request.maskAsset != null && !modelProfile.supportsMask) {
            return ValidationError.MASK_NOT_SUPPORTED
        }

        val advanced = request.advancedJson?.trim()
        if (advanced.isNullOrEmpty()) return null

        val objectValue = try {
            val element = json.parseToJsonElement(advanced)
            element as? JsonObject ?: return ValidationError.INVALID_ADVANCED_JSON
        } catch (_: SerializationException) {
            return ValidationError.INVALID_ADVANCED_JSON
        }
        if (objectValue.keys.any(protectedFields::contains)) {
            return ValidationError.CORE_FIELD_OVERRIDE
        }
        return null
    }
}
