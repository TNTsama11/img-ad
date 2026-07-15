package com.imgad.domain.port

import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult

interface ImageGenerationGateway {
    suspend fun generate(request: GenerationRequest): GenerationResult

    suspend fun edit(request: GenerationRequest): GenerationResult
}
