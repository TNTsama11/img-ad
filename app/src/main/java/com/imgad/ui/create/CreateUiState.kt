package com.imgad.ui.create

import com.imgad.domain.model.Asset
import com.imgad.domain.model.Message
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider

data class CreateUiState(
    val currentSessionId: String? = null,
    val title: String = "",
    val messages: List<Message> = emptyList(),
    val messageAssets: Map<String, List<Asset>> = emptyMap(),
    val prompt: String = "",
    val inputAssets: List<Asset> = emptyList(),
    val maskAsset: Asset? = null,
    val providers: List<Provider> = emptyList(),
    val models: List<ModelProfile> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val size: String = "",
    val quality: String = "",
    val outputFormat: String = "png",
    val count: Int = 1,
    val advancedJson: String? = null,
    val isRunning: Boolean = false,
    val currentTaskId: String? = null,
    val runningSessionId: String? = null,
    val errorMessage: String? = null,
)
