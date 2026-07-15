package com.imgad.ui.create

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.Message
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.model.TaskState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyPromptDisablesGenerate() {
        composeRule.setContent { CreateScreen(CreateUiState(), CreateScreenActions()) }

        composeRule.onNodeWithText("生成").assertIsNotEnabled()
    }

    @Test
    fun sessionUsesStableCompactTopBarInsteadOfLongPromptTitle() {
        val longTitle = "A very long generated image prompt that must not expand or clip the top application bar"
        composeRule.setContent {
            CreateScreen(
                CreateUiState(currentSessionId = "session", title = longTitle),
                CreateScreenActions(),
            )
        }

        composeRule.onNodeWithText("创作详情").assertIsDisplayed()
        composeRule.onAllNodesWithText(longTitle).assertCountEquals(0)
    }

    @Test
    fun attachmentShowsEditAndRunningShowsCancel() {
        val asset = Asset("input", localUri = "/input", mediaType = "image/png", source = AssetSource.INPUT)
        val state = mutableStateOf(CreateUiState(prompt = "edit", inputAssets = listOf(asset)))
        composeRule.setContent {
            CreateScreen(state.value, CreateScreenActions())
        }
        composeRule.onNodeWithText("编辑").assertIsEnabled()

        composeRule.runOnIdle { state.value = state.value.copy(isRunning = true) }
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun failedMessageShowsDetailsAndRetry() {
        var retried: String? = null
        val message = Message("failed", "session", MessageRole.USER, "prompt", TaskState.FAILED, errorJson = "network")
        composeRule.setContent {
            CreateScreen(
                CreateUiState(messages = listOf(message)),
                CreateScreenActions(onRetry = { retried = it }),
            )
        }

        composeRule.onNodeWithText("network").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertEquals("failed", retried)
    }

    @Test
    fun successfulImageOpensPreviewAndParameterSheetApplies() {
        var previewed: String? = null
        var updatedSize: String? = null
        val message = Message("assistant", "session", MessageRole.ASSISTANT, "", TaskState.SUCCEEDED)
        val output = Asset("output", messageId = "assistant", localUri = "/output", mediaType = "image/png", source = AssetSource.OUTPUT)
        composeRule.setContent {
            CreateScreen(
                CreateUiState(messages = listOf(message), messageAssets = mapOf("assistant" to listOf(output))),
                CreateScreenActions(
                    onPreview = { previewed = it.id },
                    onParametersChanged = { size, _, _, _, _ -> updatedSize = size },
                ),
            )
        }

        composeRule.onNodeWithText("查看图片").performClick()
        assertEquals("output", previewed)
        composeRule.onNodeWithText("参数").performClick()
        composeRule.onNodeWithText("应用").performClick()
        assertEquals("", updatedSize)
    }

    @Test
    fun imageModelWithoutStoredOptionsShowsSelectableDefaults() {
        var updatedSize: String? = null
        var updatedQuality: String? = null
        val model = ModelProfile(
            "model",
            "provider",
            "gpt-image-2",
            "GPT Image 2",
            supportsGeneration = true,
            supportsEdit = false,
            supportsMask = false,
            supportsMultipleImages = false,
        )
        composeRule.setContent {
            CreateScreen(
                CreateUiState(
                    providers = listOf(Provider("provider", "Provider", "https://example.com", "alias")),
                    models = listOf(model),
                    selectedProviderId = "provider",
                    selectedModelId = model.id,
                    size = "1024x1024",
                    quality = "auto",
                ),
                CreateScreenActions(
                    onParametersChanged = { size, quality, _, _, _ ->
                        updatedSize = size
                        updatedQuality = quality
                    },
                ),
            )
        }

        composeRule.onNodeWithText("参数").performClick()
        composeRule.onNodeWithText("1536x1024").performClick()
        composeRule.onNodeWithText("high").performClick()
        composeRule.onNodeWithText("应用").performClick()

        assertEquals("1536x1024", updatedSize)
        assertEquals("high", updatedQuality)
    }

    @Test
    fun unavailableOutputShowsPlaceholderAndDoesNotOpenPreview() {
        val message = Message("assistant", "session", MessageRole.ASSISTANT, "", TaskState.SUCCEEDED)
        val output = Asset(
            "output",
            messageId = message.id,
            localUri = "/missing-output",
            mediaType = "image/png",
            source = AssetSource.OUTPUT,
            available = false,
        )
        composeRule.setContent {
            CreateScreen(
                CreateUiState(messages = listOf(message), messageAssets = mapOf(message.id to listOf(output))),
                CreateScreenActions(),
            )
        }

        composeRule.onNodeWithText("资源不可用").assertIsDisplayed()
        composeRule.onAllNodesWithText("查看图片").assertCountEquals(0)
    }

    @Test
    fun unsupportedModelHidesMaskAndMultipleImageActions() {
        val model = ModelProfile(
            "model",
            "provider",
            "model-x",
            "Model",
            supportsGeneration = true,
            supportsEdit = true,
            supportsMask = false,
            supportsMultipleImages = false,
        )
        composeRule.setContent {
            CreateScreen(
                CreateUiState(
                    models = listOf(model),
                    selectedProviderId = "provider",
                    selectedModelId = model.id,
                ),
                CreateScreenActions(),
            )
        }

        composeRule.onAllNodesWithText("添加多图").assertCountEquals(0)
        composeRule.onAllNodesWithText("选择蒙版").assertCountEquals(0)
    }

    @Test
    fun explicitModelSelectorShowsContextAndSwitchesProviderWithModel() {
        val firstProvider = Provider("provider-a", "OpenAI", "https://a.example.com", "alias-a")
        val secondProvider = Provider("provider-b", "自定义供应方", "https://b.example.com", "alias-b")
        val firstModel = ModelProfile("model-a", firstProvider.id, "gpt-image-1", "GPT Image 1", true, true, false, false)
        val secondModel = ModelProfile("model-b", secondProvider.id, "flux-pro", "Flux Pro", true, false, false, false)
        var selectedProvider: String? = null
        var selectedModel: String? = null
        composeRule.setContent {
            CreateScreen(
                CreateUiState(
                    providers = listOf(firstProvider, secondProvider),
                    models = listOf(firstModel, secondModel),
                    selectedProviderId = firstProvider.id,
                    selectedModelId = firstModel.id,
                ),
                CreateScreenActions(
                    onSelectProvider = { selectedProvider = it },
                    onSelectModel = { selectedModel = it },
                ),
            )
        }

        composeRule.onNodeWithText("当前模型").assertIsDisplayed()
        composeRule.onNodeWithText("OpenAI").assertIsDisplayed()
        composeRule.onNodeWithText("GPT Image 1").assertIsDisplayed()
        composeRule.onAllNodesWithText("供应方").assertCountEquals(0)

        composeRule.onNodeWithTag("model-selector").performClick()
        composeRule.onNodeWithTag("model-option-${secondModel.id}").performClick()

        assertEquals(secondProvider.id, selectedProvider)
        assertEquals(secondModel.id, selectedModel)
    }

    @Test
    fun failedMessageCopiesErrorDetails() {
        var copied: String? = null
        val message = Message("failed", "session", MessageRole.USER, "prompt", TaskState.FAILED, errorJson = "details")
        composeRule.setContent {
            CreateScreen(
                CreateUiState(messages = listOf(message)),
                CreateScreenActions(onCopyError = { copied = it }),
            )
        }

        composeRule.onNodeWithText("复制详情").performClick()

        assertEquals("details", copied)
    }

    @Test
    fun previewInvokesSaveAndShareAndShowsParameterSnapshot() {
        var saved: String? = null
        var shared: String? = null
        val asset = outputAsset("first")
        composeRule.setContent {
            ImagePreviewScreen(
                asset = asset,
                requestSnapshotJson = "{\"size\":\"1024x1024\"}",
                onClose = {},
                onSave = { saved = it.id },
                onShare = { shared = it.id },
            )
        }

        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("分享").performClick()
        composeRule.onNodeWithText("查看参数").performClick()

        assertEquals(asset.id, saved)
        assertEquals(asset.id, shared)
        composeRule.onNodeWithText("{\"size\":\"1024x1024\"}").assertIsDisplayed()
    }

    @Test
    fun changingPreviewAssetResetsZoom() {
        val asset = mutableStateOf(outputAsset("first"))
        composeRule.setContent {
            ImagePreviewScreen(asset.value, null, {}, {}, {})
        }
        val zoom100 = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 100%")
        val zoomChanged = SemanticsMatcher("zoom changed from 100%") { node ->
            node.config[SemanticsProperties.StateDescription] != "缩放 100%"
        }
        val preview = composeRule.onNodeWithTag("preview-image-container")
        preview.assert(zoom100)
        preview.performTouchInput {
            val middle = center
            pinch(
                start0 = middle - Offset(10f, 0f),
                start1 = middle + Offset(10f, 0f),
                end0 = middle - Offset(100f, 0f),
                end1 = middle + Offset(100f, 0f),
            )
        }
        preview.assert(zoomChanged)

        composeRule.runOnIdle { asset.value = outputAsset("second") }

        preview.assert(zoom100)
    }

    @Test
    fun pickedPhotoUriIsForwardedToImportCallback() {
        var picked: List<String> = emptyList()

        dispatchPickedUri(Uri.parse("content://picker/image")) { picked = it }

        assertEquals(listOf("content://picker/image"), picked)
    }

    private fun outputAsset(id: String) = Asset(
        id = id,
        localUri = "/$id.png",
        mediaType = "image/png",
        source = AssetSource.OUTPUT,
    )
}
