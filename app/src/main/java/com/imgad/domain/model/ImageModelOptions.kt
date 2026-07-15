package com.imgad.domain.model

fun defaultImageSizes(modelName: String): Set<String> =
    if (modelName.startsWith("gpt-image", ignoreCase = true)) {
        linkedSetOf("1024x1024", "1536x1024", "1024x1536")
    } else {
        linkedSetOf("1024x1024")
    }

fun defaultImageQualities(modelName: String): Set<String> =
    if (modelName.startsWith("gpt-image", ignoreCase = true)) {
        linkedSetOf("auto", "low", "medium", "high")
    } else {
        linkedSetOf("standard")
    }

fun ModelProfile.effectiveSizes(): Set<String> = supportedSizes.ifEmpty { defaultImageSizes(modelName) }

fun ModelProfile.effectiveQualities(): Set<String> = supportedQualities.ifEmpty { defaultImageQualities(modelName) }
