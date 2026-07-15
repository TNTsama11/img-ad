package com.imgad.data.remote

import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.RemoteGenerationError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

object RemoteErrorParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(statusCode: Int?, body: String?, cause: Throwable? = null): RemoteGenerationError {
        val kind = when {
            statusCode == 401 -> RemoteErrorKind.AUTHORIZATION
            statusCode == 403 -> RemoteErrorKind.FORBIDDEN
            statusCode == 404 -> RemoteErrorKind.NOT_FOUND
            statusCode == 429 -> RemoteErrorKind.RATE_LIMITED
            statusCode != null && statusCode in 500..599 -> RemoteErrorKind.SERVER
            statusCode == null && cause != null -> RemoteErrorKind.NETWORK
            else -> RemoteErrorKind.UNKNOWN
        }
        val message = extractMessage(body).ifBlank { cause?.message ?: "Remote request failed" }
        return RemoteGenerationError(kind, sanitize(message))
    }

    private fun extractMessage(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val sanitizedRoot = runCatching { sanitizeJson(json.parseToJsonElement(body)) }.getOrNull()
        val root = sanitizedRoot as? JsonObject
        val error = root?.get("error") as? JsonObject
        return listOf(error, root).asSequence()
            .filterNotNull()
            .flatMap { objectValue -> sequenceOf("message", "type", "code").mapNotNull { key -> primitiveContent(objectValue[key]) } }
            .firstOrNull()
            ?: sanitizedRoot?.toString()
            ?: sanitize(body)
    }

    private fun primitiveContent(value: JsonElement?): String? = (value as? JsonPrimitive)?.content

    private fun sanitizeJson(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> buildJsonObject {
            value.forEach { (key, child) ->
                put(key, if (isSensitiveKey(key)) JsonPrimitive("[REDACTED]") else sanitizeJson(child))
            }
        }
        is JsonArray -> JsonArray(value.map(::sanitizeJson))
        else -> value
    }

    private fun isSensitiveKey(key: String): Boolean =
        Regex("(?i)(authorization|cookie|api[_-]?key|key|token|secret)").containsMatchIn(key)

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "[redacted]")
        .replace(Regex("(?i)([\\\"']?)(authorization|cookie|api[_-]?key|key|token|secret)([\\\"']?)(\\s*[:=]\\s*)([\\\"']?)([^,;\\s}\\\"']+)([\\\"']?)"), "$1$2$3$4[REDACTED]$7")
        .replace(Regex("(?i)authorization|cookie|api[_-]?key|key|token|secret"), "[REDACTED]")
}
