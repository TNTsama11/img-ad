package com.imgad.domain.usecase

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GenerationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

internal object GenerationSnapshot {
    private val json = Json

    fun encode(request: GenerationRequest): String = buildJsonObject {
        put("providerId", request.providerId)
        put("model", request.model)
        put("prompt", request.prompt)
        put("size", request.size)
        put("quality", request.quality)
        put("outputFormat", request.outputFormat)
        put("count", request.count)
        request.advancedJson?.let { put("advancedJson", it) }
        put("inputAssets", JsonArray(request.inputAssets.map(::encodeAsset)))
        request.maskAsset?.let { put("maskAsset", encodeAsset(it)) }
    }.toString()

    fun decode(value: String): GenerationRequest {
        val root = json.parseToJsonElement(value).jsonObject
        val assets = (root["inputAssets"] as? JsonArray).orEmpty().map { decodeAsset(it.jsonObject) }
        val mask = (root["maskAsset"] as? JsonObject)?.let(::decodeAsset)
        return GenerationRequest(
            providerId = root.required("providerId"),
            model = root.required("model"),
            prompt = root.required("prompt"),
            size = root.required("size"),
            quality = root.required("quality"),
            outputFormat = root.required("outputFormat"),
            count = root.required("count").toInt(),
            advancedJson = root["advancedJson"]?.jsonPrimitive?.content,
            inputAssets = assets.toList(),
            maskAsset = mask,
        )
    }

    private fun encodeAsset(asset: Asset) = buildJsonObject {
        put("id", asset.id)
        asset.messageId?.let { put("messageId", it) }
        put("localUri", asset.localUri)
        asset.thumbnailUri?.let { put("thumbnailUri", it) }
        put("mediaType", asset.mediaType)
        asset.width?.let { put("width", it) }
        asset.height?.let { put("height", it) }
        asset.byteSize?.let { put("byteSize", it) }
        put("source", asset.source.name)
        put("createdAt", asset.createdAt)
    }

    private fun decodeAsset(value: JsonObject) = Asset(
        id = value.required("id"),
        messageId = value["messageId"]?.jsonPrimitive?.content,
        localUri = value.required("localUri"),
        thumbnailUri = value["thumbnailUri"]?.jsonPrimitive?.content,
        mediaType = value.required("mediaType"),
        width = value["width"]?.jsonPrimitive?.int,
        height = value["height"]?.jsonPrimitive?.int,
        byteSize = value["byteSize"]?.jsonPrimitive?.long,
        source = AssetSource.valueOf(value.required("source")),
        createdAt = value["createdAt"]?.jsonPrimitive?.long ?: 0L,
    )

    private fun JsonObject.required(key: String): String = getValue(key).jsonPrimitive.content
}
