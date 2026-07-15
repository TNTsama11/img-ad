package com.imgad.data.remote

import com.imgad.data.repository.ProviderRepository
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.port.ImageGenerationGateway
import java.io.File

class DynamicImageGateway(
    private val providers: ProviderRepository,
    private val assetRoot: File,
    private val serviceFactory: (String, String, File) -> ImageGenerationGateway = { baseUrl, apiKey, root ->
        OpenAiImageService(
            baseUrl = baseUrl,
            apiKey = apiKey,
            uploadAssetReader = RootedUploadAssetReader(root),
        )
    },
) : ImageGenerationGateway {
    override suspend fun generate(request: GenerationRequest): GenerationResult =
        service(request.providerId).generate(request)

    override suspend fun edit(request: GenerationRequest): GenerationResult =
        service(request.providerId).edit(request)

    private suspend fun service(providerId: String): ImageGenerationGateway {
        val provider = requireNotNull(providers.getProvider(providerId)) { "供应方不存在" }
        val apiKey = requireNotNull(providers.getApiKey(providerId)) { "请先配置 API Key" }
        return serviceFactory(provider.baseUrl, apiKey, assetRoot)
    }
}
