package com.imgad.domain.port

interface DefaultProviderStore {
    fun get(): String?
    fun set(providerId: String?)
}
