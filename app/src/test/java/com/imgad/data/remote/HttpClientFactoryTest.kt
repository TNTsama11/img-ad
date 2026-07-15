package com.imgad.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpClientFactoryTest {
    @Test
    fun generationClientAllowsLongInferenceWithoutLongConnectHang() {
        val client = HttpClientFactory.createForImageGeneration()

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(120_000, client.writeTimeoutMillis)
        assertEquals(600_000, client.readTimeoutMillis)
        assertEquals(600_000, client.callTimeoutMillis)
    }
}
