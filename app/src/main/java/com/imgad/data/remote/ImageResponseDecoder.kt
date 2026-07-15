package com.imgad.data.remote

import com.imgad.domain.model.GeneratedImage
import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.RemoteGenerationError
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ImageResponseDecoder(
    private val download: suspend (String) -> DownloadedImage,
    private val maxJsonBytes: Int = 10 * 1024 * 1024,
    private val maxImageBytes: Int = 25 * 1024 * 1024,
    private val maxTotalImageBytes: Long = 50L * 1024L * 1024L,
) {
    init {
        require(maxJsonBytes > 0 && maxImageBytes > 0 && maxTotalImageBytes > 0)
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun decode(body: String): GenerationResult {
        if (body.toByteArray(Charsets.UTF_8).size > maxJsonBytes) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Image response is too large"))
        }
        val response = try {
            json.decodeFromString<ImageGenerationResponseDto>(body)
        } catch (error: SerializationException) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.PARSE, "Malformed image response"), error)
        }
        if (response.data.isEmpty()) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.EMPTY_RESPONSE, "Image response contained no images"))
        }
        if (response.data.size > 10) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Too many generated images"))
        }
        var totalBytes = 0L
        val images = response.data.map { item ->
            val image =
            when {
                item.b64Json != null -> decodeBase64(item.b64Json)
                item.url != null -> decodeUrl(item.url)
                else -> throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.PARSE, "Image response item had no image data"))
            }
            if (image.bytes.size > maxImageBytes) {
                throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Downloaded image is too large"))
            }
            totalBytes += image.bytes.size.toLong()
            if (totalBytes > maxTotalImageBytes) {
                throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Generated images are too large"))
            }
            image
        }
        return GenerationResult(images, response.id)
    }

    private fun decodeBase64(value: String): GeneratedImage = try {
        val maxEncodedLength = ((maxImageBytes.toLong() + 2L) / 3L) * 4L
        if (value.length.toLong() > maxEncodedLength) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Encoded image is too large"))
        }
        val decoded = Base64.getDecoder().decode(value)
        if (decoded.size > maxImageBytes) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Decoded image is too large"))
        }
        GeneratedImage(decoded)
    } catch (error: IllegalArgumentException) {
        throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.PARSE, "Invalid base64 image"), error)
    }

    private suspend fun decodeUrl(value: String): GeneratedImage {
        return try {
            val uri = java.net.URI(value)
            if (uri.scheme !in setOf("http", "https")) {
                throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Image URL scheme is not allowed"))
            }
            val downloaded = download(value)
            GeneratedImage(downloaded.bytes, downloaded.mediaType)
        } catch (error: GenerationFailure) {
            throw error
        } catch (error: Exception) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Unable to download generated image"), error)
        }
    }
}
