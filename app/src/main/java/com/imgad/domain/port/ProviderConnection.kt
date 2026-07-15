package com.imgad.domain.port

import com.imgad.domain.model.Provider

data class ConnectionTestResult(val success: Boolean, val errorCode: String? = null) {
    companion object {
        val Success = ConnectionTestResult(success = true)
    }
}

fun interface TestProviderConnection {
    suspend fun test(provider: Provider): ConnectionTestResult
}
