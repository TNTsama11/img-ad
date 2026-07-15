package com.imgad.data.remote

import com.imgad.domain.model.Provider
import com.imgad.domain.port.FetchProviderModels
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenAiProviderModelCatalog(
    private val storedApiKey: suspend (String) -> String?,
    policy: RemoteUrlPolicy = RemoteUrlPolicy(),
    client: OkHttpClient = HttpClientFactory.create(timeoutMillis = 10_000L),
) : FetchProviderModels {
    private val json = Json { ignoreUnknownKeys = true }
    private val urlPolicy = policy
    private val safeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(ValidatingDns(policy))
        .build()

    override suspend fun fetch(provider: Provider, apiKeyOverride: String?): List<String> {
        val apiKey = apiKeyOverride?.trim()?.takeIf(String::isNotEmpty)
            ?: storedApiKey(provider.id)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("请先填写 API Key")
        val baseUrl = urlPolicy.validate(provider.baseUrl, rejectQueryFragment = true)
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("models").build())
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val responseBody = try {
            withContext(Dispatchers.IO) {
                safeClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("获取模型失败（HTTP ${response.code}）")
                    }
                    response.body?.string().orEmpty()
                }
            }
        } catch (error: IOException) {
            throw IllegalStateException("获取模型失败：网络错误", error)
        }

        val response = try {
            json.decodeFromString<ModelListResponse>(responseBody)
        } catch (error: SerializationException) {
            throw IllegalStateException("模型列表响应格式错误", error)
        }
        return response.data
            .map(ModelEntry::id)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .ifEmpty { throw IllegalStateException("供应方没有返回模型") }
    }
}

@Serializable
private data class ModelListResponse(
    val data: List<ModelEntry> = emptyList(),
)

@Serializable
private data class ModelEntry(
    @SerialName("id") val id: String,
)
