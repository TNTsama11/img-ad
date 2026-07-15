package com.imgad.domain.model

enum class AssetSource {
    INPUT,
    MASK,
    OUTPUT,
}

data class Asset(
    val id: String,
    val messageId: String? = null,
    val localUri: String,
    val thumbnailUri: String? = null,
    val mediaType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long? = null,
    val source: AssetSource,
    val createdAt: Long = 0L,
    val available: Boolean = true,
)
