package com.imgad.data.remote

import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.RemoteGenerationError
import com.imgad.domain.port.ImageGenerationGateway
import com.imgad.domain.usecase.BuildImageRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OpenAiImageService(
    baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = HttpClientFactory.createForImageGeneration(),
    private val requestBuilder: BuildImageRequest = BuildImageRequest(),
    private val urlPolicy: RemoteUrlPolicy = RemoteUrlPolicy(),
    private val uploadAssetReader: UploadAssetReader = RejectingUploadAssetReader(),
) : ImageGenerationGateway {
    private val baseHttpUrl = urlPolicy.validate(baseUrl, rejectQueryFragment = true)
    private val safeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(ValidatingDns(urlPolicy))
        .build()
    private val decoder = ImageResponseDecoder(::download)

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        val payload = requestBuilder(request)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        return executeGeneration(
            Request.Builder()
                .url(baseHttpUrl.newBuilder().addPathSegments("images/generations").build())
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .post(body)
                .build(),
        )
    }

    override suspend fun edit(request: GenerationRequest): GenerationResult {
        require(request.inputAssets.isNotEmpty()) { "Edit request requires at least one input image" }
        val payload = requestBuilder(request)
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", request.model)
            .addFormDataPart("prompt", request.prompt)
            .addFormDataPart("size", request.size)
            .addFormDataPart("quality", request.quality)
            .addFormDataPart("output_format", request.outputFormat)
            .addFormDataPart("n", request.count.toString())

        request.inputAssets.forEach { asset ->
            val upload = uploadAssetReader.read(asset)
            multipart.addFormDataPart(
                "image",
                upload.fileName,
                upload.bytes.toRequestBody(upload.mediaType.toMediaTypeOrNull()),
            )
        }
        request.maskAsset?.let { asset ->
            val upload = uploadAssetReader.read(asset)
            multipart.addFormDataPart(
                "mask",
                upload.fileName,
                upload.bytes.toRequestBody(upload.mediaType.toMediaTypeOrNull()),
            )
        }
        payload.forEach { (key, value) ->
            if (key !in CORE_FIELDS) multipart.addFormDataPart(key, formValue(value))
        }

        return executeGeneration(
            Request.Builder()
                .url(baseHttpUrl.newBuilder().addPathSegments("images/edits").build())
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .post(multipart.build())
                .build(),
        )
    }

    suspend fun download(url: String): DownloadedImage {
        val parsed = urlPolicy.validate(url)
        val request = Request.Builder().url(parsed).get().build()
        val response = try {
            awaitResponse(safeClient.newCall(request))
        } catch (error: IOException) {
            throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.NETWORK, "Image download failed"), error)
        }
        response.use {
            if (!it.isSuccessful) {
                val error = if (it.code in 300..399) {
                    RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Image redirect is not allowed")
                } else {
                    val errorBody = try {
                        readBody(it, MAX_JSON_BYTES)
                    } catch (error: IOException) {
                        throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Image error response could not be read"), error)
                    }
                    RemoteErrorParser.parse(it.code, errorBody.toString(Charsets.UTF_8))
                }
                throw GenerationFailure(error)
            }
            val body = it.body ?: throw GenerationFailure(
                RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Image download returned no body"),
            )
            val bytes = try {
                readBody(body, MAX_IMAGE_BYTES)
            } catch (error: IOException) {
                throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.DOWNLOAD, "Image download failed"), error)
            }
            return DownloadedImage(bytes, body.contentType()?.toString())
        }
    }

    private suspend fun executeGeneration(request: Request): GenerationResult {
        val response = try {
            awaitResponse(safeClient.newCall(request))
        } catch (error: IOException) {
            throw GenerationFailure(RemoteErrorParser.parse(null, null, error), error)
        }
        response.use {
            val body = try {
                it.body?.let { responseBody -> readBody(responseBody, MAX_JSON_BYTES).toString(Charsets.UTF_8) }.orEmpty()
            } catch (error: IOException) {
                throw GenerationFailure(RemoteGenerationError(RemoteErrorKind.NETWORK, "Remote response could not be read"), error)
            }
            if (!it.isSuccessful) {
                throw GenerationFailure(RemoteErrorParser.parse(it.code, body))
            }
            return decoder.decode(body)
        }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }
        })
    }

    private fun formValue(value: JsonElement): String = when (value) {
        is JsonPrimitive -> if (value.isString) value.content else value.toString()
        JsonNull -> "null"
        else -> value.toString()
    }

    private fun readBody(response: Response, limit: Int): ByteArray = response.body?.let { readBody(it, limit) } ?: ByteArray(0)

    private fun readBody(body: okhttp3.ResponseBody, limit: Int): ByteArray {
        val length = body.contentLength()
        if (length > limit) throw GenerationFailure(
            RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Remote response is too large"),
        )
        return body.byteStream().use { readLimited(it, limit) }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw GenerationFailure(
                RemoteGenerationError(RemoteErrorKind.RESPONSE_TOO_LARGE, "Remote response is too large"),
            )
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        val CORE_FIELDS = setOf("model", "prompt", "image", "mask", "size", "quality", "output_format", "n")
        const val MAX_JSON_BYTES = 10 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 25 * 1024 * 1024
    }
}
