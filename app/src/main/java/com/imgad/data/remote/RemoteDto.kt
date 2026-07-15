package com.imgad.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ImageGenerationResponseDto(
    val data: List<ImageDataDto> = emptyList(),
    val id: String? = null,
)

@Serializable
internal data class ImageDataDto(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
)

data class DownloadedImage(
    val bytes: ByteArray,
    val mediaType: String? = null,
)
