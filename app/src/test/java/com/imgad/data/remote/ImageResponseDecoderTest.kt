package com.imgad.data.remote

import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.model.RemoteErrorKind
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageResponseDecoderTest {
    @Test
    fun rejectsDownloaderResultOverSingleImageLimit() = runBlocking {
        val decoder = ImageResponseDecoder({ DownloadedImage(ByteArray(5)) }, maxImageBytes = 4)
        val failure = runCatching { decoder.decode("{\"data\":[{\"url\":\"https://example.com/image\"}]}") }
            .exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.RESPONSE_TOO_LARGE, failure.error.kind)
    }

    @Test
    fun rejectsElevenImagesAndAggregateLimit() = runBlocking {
        val eleven = (1..11).joinToString(",") { "{\"b64_json\":\"${Base64.getEncoder().encodeToString(byteArrayOf(1))}\"}" }
        val countFailure = runCatching { ImageResponseDecoder({ error("unused") }).decode("{\"data\":[$eleven]}") }
            .exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.RESPONSE_TOO_LARGE, countFailure.error.kind)

        val decoder = ImageResponseDecoder({ DownloadedImage(ByteArray(3)) }, maxImageBytes = 3, maxTotalImageBytes = 5)
        val totalFailure = runCatching {
            decoder.decode("{\"data\":[{\"url\":\"https://example.com/1\"},{\"url\":\"https://example.com/2\"}]}")
        }.exceptionOrNull() as GenerationFailure
        assertTrue(totalFailure.error.kind == RemoteErrorKind.RESPONSE_TOO_LARGE)
    }

    @Test
    fun rejectsOversizedJsonAndBase64() = runBlocking {
        val jsonFailure = runCatching {
            ImageResponseDecoder({ error("unused") }, maxJsonBytes = 4).decode("{\"data\":[]}")
        }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.RESPONSE_TOO_LARGE, jsonFailure.error.kind)

        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(1, 2))
        val base64Failure = runCatching {
            ImageResponseDecoder({ error("unused") }, maxImageBytes = 1).decode("{\"data\":[{\"b64_json\":\"$encoded\"}]}")
        }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.RESPONSE_TOO_LARGE, base64Failure.error.kind)
    }
}
