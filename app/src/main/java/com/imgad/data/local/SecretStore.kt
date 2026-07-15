package com.imgad.data.local

interface SecretStore {
    fun put(alias: String, value: String)

    fun get(alias: String): String?

    fun remove(alias: String)
}
