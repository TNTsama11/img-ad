package com.imgad.domain.model

import kotlin.ConsistentCopyVisibility
import kotlin.ExperimentalStdlibApi

@OptIn(ExperimentalStdlibApi::class)
@ConsistentCopyVisibility
data class ModelProfile private constructor(
    val id: String,
    val providerId: String,
    val modelName: String,
    val displayName: String,
    val supportsGeneration: Boolean,
    val supportsEdit: Boolean,
    val supportsMask: Boolean,
    val supportsMultipleImages: Boolean,
    val supportedSizes: Set<String>,
    val supportedQualities: Set<String>,
    val enabled: Boolean = true,
) {
    fun snapshotCopy(
        id: String = this.id,
        providerId: String = this.providerId,
        modelName: String = this.modelName,
        displayName: String = this.displayName,
        supportsGeneration: Boolean = this.supportsGeneration,
        supportsEdit: Boolean = this.supportsEdit,
        supportsMask: Boolean = this.supportsMask,
        supportsMultipleImages: Boolean = this.supportsMultipleImages,
        supportedSizes: Set<String> = this.supportedSizes,
        supportedQualities: Set<String> = this.supportedQualities,
        enabled: Boolean = this.enabled,
    ): ModelProfile = create(
        id,
        providerId,
        modelName,
        displayName,
        supportsGeneration,
        supportsEdit,
        supportsMask,
        supportsMultipleImages,
        supportedSizes,
        supportedQualities,
        enabled,
    )

    companion object {
        operator fun invoke(
            id: String,
            providerId: String,
            modelName: String,
            displayName: String,
            supportsGeneration: Boolean,
            supportsEdit: Boolean,
            supportsMask: Boolean,
            supportsMultipleImages: Boolean,
            supportedSizes: Set<String> = emptySet(),
            supportedQualities: Set<String> = emptySet(),
            enabled: Boolean = true,
        ): ModelProfile = create(
            id,
            providerId,
            modelName,
            displayName,
            supportsGeneration,
            supportsEdit,
            supportsMask,
            supportsMultipleImages,
            supportedSizes,
            supportedQualities,
            enabled,
        )

        fun create(
            id: String,
            providerId: String,
            modelName: String,
            displayName: String,
            supportsGeneration: Boolean,
            supportsEdit: Boolean,
            supportsMask: Boolean,
            supportsMultipleImages: Boolean,
            supportedSizes: Set<String> = emptySet(),
            supportedQualities: Set<String> = emptySet(),
            enabled: Boolean = true,
        ): ModelProfile = ModelProfile(
            id,
            providerId,
            modelName,
            displayName,
            supportsGeneration,
            supportsEdit,
            supportsMask,
            supportsMultipleImages,
            supportedSizes.toSet(),
            supportedQualities.toSet(),
            enabled,
        )
    }
}
