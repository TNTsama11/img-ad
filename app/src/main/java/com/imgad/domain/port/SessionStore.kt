package com.imgad.domain.port

import com.imgad.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionStore {
    fun observeActive(query: String = ""): Flow<List<Session>>

    suspend fun rename(id: String, title: String, now: Long)

    suspend fun softDelete(id: String, now: Long)
}
