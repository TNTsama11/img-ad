package com.imgad.domain.model

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyAlias: String,
    val enabled: Boolean = true,
    val defaultModelId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
