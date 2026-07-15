package com.imgad.domain.model

sealed interface GenerationError

enum class ValidationError : GenerationError {
    EMPTY_PROMPT,
    MODEL_REQUIRED,
    GENERATION_NOT_SUPPORTED,
    EDIT_NOT_SUPPORTED,
    UNSUPPORTED_SIZE,
    UNSUPPORTED_QUALITY,
    INVALID_COUNT,
    MULTIPLE_IMAGES_NOT_SUPPORTED,
    EDIT_IMAGE_REQUIRED,
    MASK_NOT_SUPPORTED,
    CORE_FIELD_OVERRIDE,
    INVALID_ADVANCED_JSON,
}

enum class RemoteErrorKind : GenerationError {
    AUTHORIZATION,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    PARSE,
    DOWNLOAD,
    EMPTY_RESPONSE,
    CONFIG,
    RESPONSE_TOO_LARGE,
    UNKNOWN,
}

data class RemoteGenerationError(
    val kind: RemoteErrorKind,
    val message: String,
) : GenerationError

class GenerationFailure(
    val error: RemoteGenerationError,
    cause: Throwable? = null,
) : Exception(error.message, cause)
