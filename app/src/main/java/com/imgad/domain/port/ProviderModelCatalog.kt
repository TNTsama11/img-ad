package com.imgad.domain.port

import com.imgad.domain.model.Provider

fun interface FetchProviderModels {
    suspend fun fetch(provider: Provider, apiKeyOverride: String?): List<String>
}
