package com.imgad.ui.settings

import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.usecase.ValidateGenerationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelEditorTest {
    @Test
    fun defaultOptionsProduceValidGenerationModel() {
        val model = ModelProfile(
            id = "model",
            providerId = "provider",
            modelName = "model-x",
            displayName = "Model",
            supportsGeneration = true,
            supportsEdit = false,
            supportsMask = false,
            supportsMultipleImages = false,
            supportedSizes = parseModelOptions("1024x1024"),
            supportedQualities = parseModelOptions("standard"),
        )
        val request = GenerationRequest("provider", "model-x", "prompt", "1024x1024", "standard", "png", 1)

        assertEquals(setOf("1024x1024"), model.supportedSizes)
        assertNull(ValidateGenerationRequest()(request, model))
    }
}
