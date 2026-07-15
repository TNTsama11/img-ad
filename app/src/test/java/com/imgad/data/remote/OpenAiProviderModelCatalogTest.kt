package com.imgad.data.remote

import com.imgad.domain.model.Provider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiProviderModelCatalogTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchUsesModelsEndpointAndBearerOverrideAndDeduplicatesIds() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"gpt-image-1","owned_by":"openai"},{"id":""},{"id":"flux-1"},{"id":"gpt-image-1"}],"object":"list"}""",
            ),
        )

        val models = catalog(storedKey = "stored-key").fetch(provider(), " form-key ")
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        assertEquals(listOf("gpt-image-1", "flux-1"), models)
        assertEquals("/v1/models", request!!.path)
        assertEquals("Bearer form-key", request.getHeader("Authorization"))
    }

    @Test
    fun blankOverrideFallsBackToStoredKey() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"image-model"}]}"""))

        catalog(storedKey = "stored-key").fetch(provider(), "  ")

        assertEquals("Bearer stored-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun missingKeyFailsBeforeNetworkRequest() = runBlocking {
        val failure = runCatching { catalog(storedKey = null).fetch(provider(), null) }.exceptionOrNull()

        assertTrue(failure!!.message!!.contains("API Key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpFailureIncludesStatusCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val failure = runCatching { catalog().fetch(provider(), "key") }.exceptionOrNull()

        assertTrue(failure!!.message!!.contains("401"))
    }

    @Test
    fun malformedOrEmptyResponsesHaveReadableErrors() = runBlocking {
        server.enqueue(MockResponse().setBody("not-json"))
        val malformed = runCatching { catalog().fetch(provider(), "key") }.exceptionOrNull()
        assertTrue(malformed!!.message!!.contains("格式"))

        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val empty = runCatching { catalog().fetch(provider(), "key") }.exceptionOrNull()
        assertTrue(empty!!.message!!.contains("没有返回模型"))
    }

    private fun provider() = Provider(
        id = "provider",
        name = "Provider",
        baseUrl = server.url("/v1/").toString(),
        apiKeyAlias = "alias",
    )

    private fun catalog(storedKey: String? = null) = OpenAiProviderModelCatalog(
        storedApiKey = { storedKey },
        client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build(),
        policy = RemoteUrlPolicy(allowInsecureHttp = true, allowLoopback = true),
    )
}
