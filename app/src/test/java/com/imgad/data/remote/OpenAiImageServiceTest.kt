package com.imgad.data.remote

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.usecase.BuildImageRequest
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiImageServiceTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generationUsesNormalizedPathBearerAndDecodesBase64() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"id\":\"req-1\",\"data\":[{\"b64_json\":\"${Base64.getEncoder().encodeToString(byteArrayOf(1, 2))}\"}],\"ignored\":true}",
            ),
        )
        val service = service()

        val result = service.generate(request())
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)

        assertEquals("/v1/images/generations", recorded!!.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals("req-1", result.requestId)
        assertArrayEquals(byteArrayOf(1, 2), result.images.single().bytes)
    }

    @Test
    fun urlResponseIsDownloadedAndEditUsesMultipartFields() = runBlocking {
        val imageFile = File.createTempFile("imgad-input", ".png").apply { writeBytes(byteArrayOf(9, 8)) }
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[{\"url\":\"${server.url("/image.png")}\"}]}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("downloaded"))
            val result = service().generate(request())
            assertArrayEquals("downloaded".toByteArray(), result.images.single().bytes)
            assertEquals("/v1/images/generations", server.takeRequest().path)
            assertEquals("/image.png", server.takeRequest().path)

            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[{\"b64_json\":\"${Base64.getEncoder().encodeToString(byteArrayOf(3))}\"}]}"))
            val edit = service(imageFile.parentFile).edit(request(input = imageFile, mask = imageFile, advanced = "{\"caption\":\"quote\\\"value\\\\path\\nline\"}"))
            val editRequest = server.takeRequest()

            assertEquals("/v1/images/edits", editRequest.path)
            assertTrue(editRequest.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            val body = editRequest.body.readUtf8()
            assertTrue(body.contains("name=\"prompt\""))
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("name=\"image\""))
            assertTrue(body.contains("name=\"mask\""))
            assertTrue(body.contains("name=\"size\""))
            assertTrue(body.contains("name=\"quality\""))
            assertTrue(body.contains("name=\"output_format\""))
            assertTrue(body.contains("name=\"n\""))
            assertTrue(body.contains("quote\"value"))
            assertTrue(body.contains("line"))
            assertEquals(1, edit.images.size)
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun httpErrorsAndEmptyOrMalformedResponsesMapToStableFailures() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val rateFailure = runCatching { service().generate(request()) }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.RATE_LIMITED, rateFailure.error.kind)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":[]}"))
        val emptyFailure = runCatching { service().generate(request()) }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.EMPTY_RESPONSE, emptyFailure.error.kind)

        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val parseFailure = runCatching { service().generate(request()) }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.PARSE, parseFailure.error.kind)
    }

    @Test
    fun serviceMapsAuthorizationNotFoundAndServerStatuses() = runBlocking {
        listOf(401 to RemoteErrorKind.AUTHORIZATION, 403 to RemoteErrorKind.FORBIDDEN, 404 to RemoteErrorKind.NOT_FOUND, 500 to RemoteErrorKind.SERVER)
            .forEach { (status, kind) ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("error"))
                val failure = runCatching { service().generate(request()) }.exceptionOrNull() as GenerationFailure
                assertEquals(kind, failure.error.kind)
            }
    }

    @Test
    fun timeoutMapsToNetworkAndCancellationCanStopRequest() = runBlocking {
        server.enqueue(MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody("{}"))
        val failure = runCatching { service(timeoutMillis = 20).generate(request()) }.exceptionOrNull() as GenerationFailure
        assertEquals(RemoteErrorKind.NETWORK, failure.error.kind)
        server.takeRequest(1, TimeUnit.SECONDS)

        server.enqueue(MockResponse().setBodyDelay(5, TimeUnit.SECONDS).setBody("{}"))
        val job = launch(Dispatchers.IO) { runCatching { service(timeoutMillis = 10_000).generate(request()) } }
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)
        job.cancelAndJoin()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun buildImageRequestDoesNotAllowCoreOverrides() {
        val built = BuildImageRequest()(request(advanced = "{\"model\":\"evil\",\"style\":\"vivid\"}"))

        assertEquals("model-x", built["model"]?.toString()?.trim('"'))
        assertEquals("\"vivid\"", built["style"].toString())
    }

    private fun service(assetRoot: File? = null, timeoutMillis: Long = 30_000L): OpenAiImageService = OpenAiImageService(
        baseUrl = server.url("/v1/").toString(),
        apiKey = "test-key",
        client = OkHttpClient.Builder().readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build(),
        urlPolicy = RemoteUrlPolicy(allowInsecureHttp = true, allowLoopback = true),
        uploadAssetReader = assetRoot?.let(::RootedUploadAssetReader) ?: RejectingUploadAssetReader(),
    )

    private fun request(
        input: File? = null,
        mask: File? = null,
        advanced: String? = null,
    ) = GenerationRequest(
        providerId = "provider",
        model = "model-x",
        prompt = "draw",
        size = "1024x1024",
        quality = "standard",
        outputFormat = "png",
        count = 1,
        advancedJson = advanced,
        inputAssets = input?.let {
            listOf(Asset("asset", localUri = it.absolutePath, mediaType = "image/png", source = AssetSource.INPUT))
        } ?: emptyList(),
        maskAsset = mask?.let {
            Asset("mask", localUri = it.absolutePath, mediaType = "image/png", source = AssetSource.MASK)
        },
    )
}
