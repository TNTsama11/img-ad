package com.imgad.domain.model

data class GeneratedImage(
    val bytes: ByteArray,
    val mediaType: String? = null,
)

data class GenerationResult(
    val images: List<GeneratedImage>,
    val requestId: String? = null,
)
