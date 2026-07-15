package com.imgad.domain

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.ValidationError
import com.imgad.domain.usecase.ValidateGenerationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidateGenerationRequestTest {
    private val validator = ValidateGenerationRequest()
    private val modelProfile = ModelProfile(
        id = "profile-1",
        providerId = "provider-1",
        modelName = "image-model",
        displayName = "Image Model",
        supportsGeneration = true,
        supportsEdit = true,
        supportsMask = true,
        supportsMultipleImages = true,
        supportedSizes = setOf("1024x1024"),
        supportedQualities = setOf("standard"),
        enabled = true,
    )

    @Test
    fun imageModelWithoutStoredOptionsAcceptsSafeDefaults() {
        val model = ModelProfile(
            "image-model",
            "provider",
            "gpt-image-2",
            "GPT Image 2",
            true,
            false,
            false,
            false,
        )
        val request = GenerationRequest(
            providerId = "provider",
            model = "gpt-image-2",
            prompt = "draw",
            size = "1024x1024",
            quality = "auto",
            outputFormat = "png",
            count = 1,
        )

        assertNull(ValidateGenerationRequest()(request, model))
    }

    @Test
    fun `rejects blank prompt`() {
        val result = validator(request().snapshotCopy(prompt = "   "), modelProfile)

        assertEquals(ValidationError.EMPTY_PROMPT, result)
    }

    @Test
    fun `rejects missing model`() {
        val result = validator(request().snapshotCopy(model = ""), modelProfile)

        assertEquals(ValidationError.MODEL_REQUIRED, result)
    }

    @Test
    fun `returns empty prompt before missing model`() {
        val result = validator(request().snapshotCopy(prompt = "", model = ""), modelProfile)

        assertEquals(ValidationError.EMPTY_PROMPT, result)
    }

    @Test
    fun `returns missing model before unsupported size`() {
        val result = validator(
            request().snapshotCopy(model = "", size = "512x512"),
            modelProfile,
        )

        assertEquals(ValidationError.MODEL_REQUIRED, result)
    }

    @Test
    fun `rejects unsupported size`() {
        val result = validator(request().snapshotCopy(size = "512x512"), modelProfile)

        assertEquals(ValidationError.UNSUPPORTED_SIZE, result)
    }

    @Test
    fun `rejects unsupported quality`() {
        val result = validator(request().snapshotCopy(quality = "high"), modelProfile)

        assertEquals(ValidationError.UNSUPPORTED_QUALITY, result)
    }

    @Test
    fun `rejects generation when model only supports editing`() {
        val result = validator(request(), modelProfile.snapshotCopy(supportsGeneration = false))

        assertEquals(ValidationError.GENERATION_NOT_SUPPORTED, result)
    }

    @Test
    fun `rejects editing when model only supports generation`() {
        val result = validator(
            request().snapshotCopy(inputAssets = listOf(asset(AssetSource.INPUT))),
            modelProfile.snapshotCopy(supportsEdit = false),
        )

        assertEquals(ValidationError.EDIT_NOT_SUPPORTED, result)
    }

    @Test
    fun `rejects multiple inputs when model does not support them`() {
        val result = validator(
            request().snapshotCopy(
                inputAssets = listOf(asset(AssetSource.INPUT), asset(AssetSource.INPUT).copy(id = "asset-input-2")),
            ),
            modelProfile.snapshotCopy(supportsMultipleImages = false),
        )

        assertEquals(ValidationError.MULTIPLE_IMAGES_NOT_SUPPORTED, result)
    }

    @Test
    fun `rejects count below one`() {
        val result = validator(request().snapshotCopy(count = 0), modelProfile)

        assertEquals(ValidationError.INVALID_COUNT, result)
    }

    @Test
    fun `rejects count above ten`() {
        val result = validator(request().snapshotCopy(count = 11), modelProfile)

        assertEquals(ValidationError.INVALID_COUNT, result)
    }

    @Test
    fun `accepts count one`() {
        assertNull(validator(request().snapshotCopy(count = 1), modelProfile))
    }

    @Test
    fun `accepts count ten`() {
        assertNull(validator(request().snapshotCopy(count = 10), modelProfile))
    }

    @Test
    fun `returns unsupported size before invalid count`() {
        val result = validator(request().snapshotCopy(size = "512x512", count = 0), modelProfile)

        assertEquals(ValidationError.UNSUPPORTED_SIZE, result)
    }

    @Test
    fun `returns invalid count before missing edit image`() {
        val result = validator(
            request().snapshotCopy(
                count = 0,
                maskAsset = asset(AssetSource.MASK),
            ),
            modelProfile,
        )

        assertEquals(ValidationError.INVALID_COUNT, result)
    }

    @Test
    fun `rejects edit request without an input image`() {
        val result = validator(
            request().snapshotCopy(maskAsset = asset(AssetSource.MASK)),
            modelProfile,
        )

        assertEquals(ValidationError.EDIT_IMAGE_REQUIRED, result)
    }

    @Test
    fun `rejects mask when model does not support masks`() {
        val result = validator(
            request().snapshotCopy(
                inputAssets = listOf(asset(AssetSource.INPUT)),
                maskAsset = asset(AssetSource.MASK),
            ),
            modelProfile.snapshotCopy(supportsMask = false),
        )

        assertEquals(ValidationError.MASK_NOT_SUPPORTED, result)
    }

    @Test
    fun `returns missing edit image before unsupported mask`() {
        val result = validator(
            request().snapshotCopy(maskAsset = asset(AssetSource.MASK)),
            modelProfile.snapshotCopy(supportsMask = false),
        )

        assertEquals(ValidationError.EDIT_IMAGE_REQUIRED, result)
    }

    @Test
    fun `returns unsupported mask before invalid advanced json`() {
        val result = validator(
            request().snapshotCopy(
                advancedJson = "{",
                inputAssets = listOf(asset(AssetSource.INPUT)),
                maskAsset = asset(AssetSource.MASK),
            ),
            modelProfile.snapshotCopy(supportsMask = false),
        )

        assertEquals(ValidationError.MASK_NOT_SUPPORTED, result)
    }

    @Test
    fun `returns invalid advanced json before core field override`() {
        val result = validator(
            request().snapshotCopy(advancedJson = "{\"model\":\"override\""),
            modelProfile,
        )

        assertEquals(ValidationError.INVALID_ADVANCED_JSON, result)
    }

    @Test
    fun `returns basic validation error before invalid advanced json`() {
        val result = validator(
            request().snapshotCopy(prompt = "", advancedJson = "{"),
            modelProfile,
        )

        assertEquals(ValidationError.EMPTY_PROMPT, result)
    }

    @Test
    fun `rejects every protected advanced json field`() {
        val fields = listOf("model", "prompt", "image", "mask", "size", "quality", "output_format", "n")

        fields.forEach { field ->
            val result = validator(
                request().snapshotCopy(advancedJson = "{\"$field\":\"override\"}"),
                modelProfile,
            )

            assertEquals(field, ValidationError.CORE_FIELD_OVERRIDE, result)
        }
    }

    @Test
    fun `rejects invalid advanced json`() {
        val result = validator(
            request().snapshotCopy(advancedJson = "{"),
            modelProfile,
        )

        assertEquals(ValidationError.INVALID_ADVANCED_JSON, result)
    }

    @Test
    fun `rejects non object advanced json`() {
        listOf("[]", "\"text\"", "42", "null").forEach { json ->
            val result = validator(request().snapshotCopy(advancedJson = json), modelProfile)

            assertEquals(json, ValidationError.INVALID_ADVANCED_JSON, result)
        }
    }

    @Test
    fun `snapshots model profile collections`() {
        val sizes = mutableSetOf("1024x1024")
        val qualities = mutableSetOf("standard")
        val profile = modelProfile.snapshotCopy(supportedSizes = sizes, supportedQualities = qualities)
        sizes.clear()
        qualities.clear()

        assertEquals(setOf("1024x1024"), profile.supportedSizes)
        assertEquals(setOf("standard"), profile.supportedQualities)
    }

    @Test
    fun `snapshots request input assets`() {
        val assets = mutableListOf(asset(AssetSource.INPUT))
        val request = request().snapshotCopy(inputAssets = assets)
        assets.clear()

        assertEquals(1, request.inputAssets.size)
    }

    @Test
    fun `domain models preserve structural equality`() {
        val firstProfile = modelProfile.snapshotCopy()
        val secondProfile = modelProfile.snapshotCopy()
        val firstRequest = request().snapshotCopy()
        val secondRequest = request().snapshotCopy()

        assertEquals(firstProfile, secondProfile)
        assertEquals(firstProfile.hashCode(), secondProfile.hashCode())
        assertEquals(firstRequest, secondRequest)
        assertEquals(firstRequest.hashCode(), secondRequest.hashCode())
    }

    @Test
    fun `domain models differ when fields differ`() {
        assertNotEquals(modelProfile, modelProfile.snapshotCopy(modelName = "other-model"))
        assertNotEquals(request(), request().snapshotCopy(prompt = "another prompt"))
    }

    private fun request() = GenerationRequest(
        providerId = "provider-1",
        model = "image-model",
        prompt = "A mountain lake",
        size = "1024x1024",
        quality = "standard",
        outputFormat = "png",
        count = 1,
        advancedJson = null,
        inputAssets = emptyList(),
        maskAsset = null,
    )

    private fun asset(source: AssetSource) = Asset(
        id = "asset-${source.name.lowercase()}",
        messageId = null,
        localUri = "file:///tmp/image.png",
        thumbnailUri = null,
        mediaType = "image/png",
        width = 1024,
        height = 1024,
        byteSize = 1024,
        source = source,
        createdAt = 1,
    )
}
