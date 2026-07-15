package com.imgad.domain.model

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
