package com.imgad.data.remote

import com.imgad.data.repository.ProviderRepository
import com.imgad.domain.model.Provider
import com.imgad.domain.port.ConnectionTestResult
import com.imgad.domain.port.TestProviderConnection
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderConnectionTester(
    private val providers: ProviderRepository,
    private val policy: RemoteUrlPolicy = RemoteUrlPolicy(),
    client: OkHttpClient = HttpClientFactory.create(timeoutMillis = 10_000L),
) : TestProviderConnection {
    private val safeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(ValidatingDns(policy))
        .build()

    override suspend fun test(provider: Provider): ConnectionTestResult {
        val apiKey = providers.getApiKey(provider.id)
            ?: return ConnectionTestResult(false, "API_KEY_MISSING")
        return try {
            val base = policy.validate(provider.baseUrl, rejectQueryFragment = true)
            val request = Request.Builder()
                .url(base.newBuilder().addPathSegments("models").build())
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                safeClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) ConnectionTestResult.Success
                    else ConnectionTestResult(false, "HTTP_${response.code}")
                }
            }
        } catch (error: Exception) {
            val code = when (error) {
                is com.imgad.domain.model.GenerationFailure -> "CONFIG"
                is IOException -> "NETWORK"
                else -> "CONNECTION_FAILED"
            }
            ConnectionTestResult(false, code)
        }
    }
}
