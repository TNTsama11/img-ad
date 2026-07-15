package com.imgad.data.remote

import com.imgad.domain.model.RemoteErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteErrorParserTest {
    @Test
    fun mapsHttpStatusAndSanitizesSensitiveDetails() {
        val error = RemoteErrorParser.parse(
            statusCode = 401,
            body = "{\"error\":{\"message\":\"bad Authorization Bearer secret-token\",\"type\":\"auth\",\"code\":\"invalid_api_key\"}}",
        )

        assertEquals(RemoteErrorKind.AUTHORIZATION, error.kind)
        assertFalse(error.message.contains("secret-token"))
        assertFalse(error.message.contains("Authorization"))
    }

    @Test
    fun mapsRateLimitServerNotFoundAndPlainTextErrors() {
        assertEquals(RemoteErrorKind.NOT_FOUND, RemoteErrorParser.parse(404, "missing").kind)
        assertEquals(RemoteErrorKind.RATE_LIMITED, RemoteErrorParser.parse(429, "slow down").kind)
        assertEquals(RemoteErrorKind.SERVER, RemoteErrorParser.parse(500, "failure").kind)
        assertEquals(RemoteErrorKind.FORBIDDEN, RemoteErrorParser.parse(403, "forbidden").kind)
        assertTrue(RemoteErrorParser.parse(null, "timeout", IOException("timeout")).kind == RemoteErrorKind.NETWORK)
    }

    @Test
    fun sanitizesKeyCookieTokenAndSecretFieldsInPlainText() {
        val error = RemoteErrorParser.parse(null, "key=abc cookie: xyz token=123 secret=456")

        assertFalse(error.message.contains("abc"))
        assertFalse(error.message.contains("xyz"))
        assertFalse(error.message.contains("123"))
        assertFalse(error.message.contains("456"))
    }

    @Test
    fun sanitizesNestedJsonArraysWithoutReturningRawBody() {
        val error = RemoteErrorParser.parse(null, "{\"nested\":[{\"api_key\":\"abc\"},{\"token\":\"秘密\"}]}")

        assertFalse(error.message.contains("abc"))
        assertFalse(error.message.contains("秘密"))
        assertTrue(error.message.contains("REDACTED"))
    }

    private class IOException(message: String) : Exception(message)
}
