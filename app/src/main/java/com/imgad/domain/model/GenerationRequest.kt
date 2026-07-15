package com.imgad.domain.model

import kotlin.ConsistentCopyVisibility
import kotlin.ExperimentalStdlibApi

@OptIn(ExperimentalStdlibApi::class)
@ConsistentCopyVisibility
data class GenerationRequest private constructor(
    val providerId: String,
    val model: String,
    val prompt: String,
    val size: String,
    val quality: String,
    val outputFormat: String,
    val count: Int,
    val advancedJson: String? = null,
    val inputAssets: List<Asset>,
    val maskAsset: Asset? = null,
) {
    val isEdit: Boolean
        get() = inputAssets.isNotEmpty() || maskAsset != null

    fun snapshotCopy(
        providerId: String = this.providerId,
        model: String = this.model,
        prompt: String = this.prompt,
        size: String = this.size,
        quality: String = this.quality,
        outputFormat: String = this.outputFormat,
        count: Int = this.count,
        advancedJson: String? = this.advancedJson,
        inputAssets: List<Asset> = this.inputAssets,
        maskAsset: Asset? = this.maskAsset,
    ): GenerationRequest = create(
        providerId,
        model,
        prompt,
        size,
        quality,
        outputFormat,
        count,
        advancedJson,
        inputAssets,
        maskAsset,
    )

    companion object {
        operator fun invoke(
            providerId: String,
            model: String,
            prompt: String,
            size: String,
            quality: String,
            outputFormat: String,
            count: Int,
            advancedJson: String? = null,
            inputAssets: List<Asset> = emptyList(),
            maskAsset: Asset? = null,
        ): GenerationRequest = create(
            providerId,
            model,
            prompt,
            size,
            quality,
            outputFormat,
            count,
            advancedJson,
            inputAssets,
            maskAsset,
        )

        fun create(
            providerId: String,
            model: String,
            prompt: String,
            size: String,
            quality: String,
            outputFormat: String,
            count: Int,
            advancedJson: String? = null,
            inputAssets: List<Asset> = emptyList(),
            maskAsset: Asset? = null,
        ): GenerationRequest = GenerationRequest(
            providerId,
            model,
            prompt,
            size,
            quality,
            outputFormat,
            count,
            advancedJson,
            inputAssets.toList(),
            maskAsset,
        )
    }
}
