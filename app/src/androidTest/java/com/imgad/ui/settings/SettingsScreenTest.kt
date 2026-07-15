package com.imgad.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.imgad.domain.model.Provider
import com.imgad.domain.model.ModelProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectionTestIsExplicitAndShowsBillingWarning() {
        var tested = 0
        val provider = Provider("provider", "Provider", "https://example.com", "alias")
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(providers = listOf(provider)),
                actions = SettingsScreenActions(),
                onTestConnection = { tested++ },
            )
        }

        assertEquals(0, tested)
        composeRule.onNodeWithText("测试连接可能产生费用").assertIsDisplayed()
        composeRule.onNodeWithTag("provider-${provider.id}-test").assertIsDisplayed().performClick()
        assertEquals(1, tested)
    }

    @Test
    fun providerApiKeyStartsHidden() {
        composeRule.setContent {
            ProviderEditorScreen(
                initial = null,
                apiKeyVisible = false,
                discovery = ProviderModelDiscoveryUiState(),
                onSave = { _, _, _ -> },
                onFetchModels = { _, _ -> },
                onSearchQueryChanged = {},
                onToggleModel = {},
                onClearDiscoveredModels = {},
                onToggleApiKeyVisibility = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("显示 API Key").assertIsDisplayed()
    }

    @Test
    fun providerEditorFetchesFiltersSelectsAndSavesDiscoveredModels() {
        var discovery by mutableStateOf(ProviderModelDiscoveryUiState())
        var fetchedBaseUrl: String? = null
        var fetchedApiKey: String? = null
        var savedModelIds: Set<String>? = null
        composeRule.setContent {
            ProviderEditorScreen(
                initial = null,
                apiKeyVisible = false,
                discovery = discovery,
                onSave = { _, _, modelIds -> savedModelIds = modelIds },
                onFetchModels = { provider, apiKey ->
                    fetchedBaseUrl = provider.baseUrl
                    fetchedApiKey = apiKey
                    discovery = ProviderModelDiscoveryUiState(
                        modelIds = listOf("gpt-image-1", "flux-pro"),
                        selectedModelIds = setOf("gpt-image-1", "flux-pro"),
                    )
                },
                onSearchQueryChanged = { query -> discovery = discovery.copy(searchQuery = query) },
                onToggleModel = { modelId ->
                    discovery = discovery.copy(
                        selectedModelIds = discovery.selectedModelIds.toMutableSet().apply {
                            if (!add(modelId)) remove(modelId)
                        },
                    )
                },
                onClearDiscoveredModels = { discovery = ProviderModelDiscoveryUiState() },
                onToggleApiKeyVisibility = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("provider-name").performTextInput("OpenAI")
        composeRule.onNodeWithTag("provider-base-url").performTextInput("https://api.example.com/v1")
        composeRule.onNodeWithTag("provider-api-key").performTextInput("secret")
        composeRule.onNodeWithTag("provider-fetch-models").performClick()

        assertEquals("https://api.example.com/v1", fetchedBaseUrl)
        assertEquals("secret", fetchedApiKey)
        composeRule.onNodeWithTag("provider-model-search").performTextInput("flux")
        composeRule.onNodeWithText("flux-pro").assertIsDisplayed()
        composeRule.onAllNodesWithText("gpt-image-1").assertCountEquals(0)

        composeRule.onNodeWithTag("provider-model-flux-pro").performClick()
        composeRule.onNodeWithTag("provider-save").performScrollTo().performClick()
        assertEquals(setOf("gpt-image-1"), savedModelIds)
    }

    @Test
    fun providerEditorShowsFetchFailureAndDisablesLoadingAction() {
        composeRule.setContent {
            ProviderEditorScreen(
                initial = Provider("provider", "Provider", "https://example.com/v1", "alias"),
                apiKeyVisible = false,
                discovery = ProviderModelDiscoveryUiState(isLoading = true, errorMessage = "HTTP 401"),
                onSave = { _, _, _ -> },
                onFetchModels = { _, _ -> },
                onSearchQueryChanged = {},
                onToggleModel = {},
                onClearDiscoveredModels = {},
                onToggleApiKeyVisibility = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("provider-fetch-models").assertIsNotEnabled()
        composeRule.onNodeWithText("HTTP 401").assertIsDisplayed()
    }

    @Test
    fun providerAndModelCrudEntriesAreExposed() {
        var editedProvider: String? = null
        var editedModel: String? = null
        var deletedModel: String? = null
        var defaultModel: Pair<String, String>? = null
        val provider = Provider("provider", "Provider", "https://example.com", "alias")
        val model = ModelProfile("model", provider.id, "model-x", "Model", true, true, false, false)
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                    models = listOf(model),
                ),
                actions = SettingsScreenActions(
                    onEditProvider = { editedProvider = it },
                    onEditModel = { editedModel = it },
                    onDeleteModel = { deletedModel = it },
                    onSetDefaultModel = { providerId, modelId -> defaultModel = providerId to modelId },
                ),
            )
        }
        composeRule.onNodeWithTag("provider-${provider.id}-edit").assertIsDisplayed().performClick()
        assertEquals(provider.id, editedProvider)
        composeRule.onNodeWithText("新增模型").performClick()
        assertEquals(null, editedModel)
        composeRule.onNodeWithTag("model-${model.id}-default").assertIsDisplayed().performClick()
        assertEquals(provider.id to model.id, defaultModel)
        composeRule.onNodeWithTag("model-${model.id}-delete").assertIsDisplayed().performClick()
        assertEquals(model.id, deletedModel)
    }
}
