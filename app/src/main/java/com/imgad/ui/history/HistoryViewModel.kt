package com.imgad.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imgad.domain.model.Session
import com.imgad.domain.port.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val query: String = "",
    val sessions: List<Session> = emptyList(),
    val pendingDeleteId: String? = null,
    val pendingRenameId: String? = null,
    val errorMessage: String? = null,
)

class HistoryViewModel(
    private val store: SessionStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        observeSessions("")
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, errorMessage = null) }
        observeSessions(query)
    }

    fun requestDelete(sessionId: String) {
        _uiState.update { it.copy(pendingDeleteId = sessionId, errorMessage = null) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDeleteId = null) }
    }

    fun confirmDelete() {
        val id = _uiState.value.pendingDeleteId ?: return
        viewModelScope.launch {
            runCatching { kotlinx.coroutines.withContext(ioDispatcher) { store.softDelete(id, now()) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "删除失败") } }
            _uiState.update { it.copy(pendingDeleteId = null) }
        }
    }

    fun requestRename(sessionId: String) {
        _uiState.update { it.copy(pendingRenameId = sessionId, errorMessage = null) }
    }

    fun cancelRename() {
        _uiState.update { it.copy(pendingRenameId = null) }
    }

    fun rename(sessionId: String, title: String) {
        viewModelScope.launch {
            runCatching { kotlinx.coroutines.withContext(ioDispatcher) { store.rename(sessionId, title, now()) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "重命名失败") } }
            _uiState.update { it.copy(pendingRenameId = null) }
        }
    }

    private fun observeSessions(query: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            store.observeActive(query).collect { sessions ->
                _uiState.update {
                    it.copy(sessions = sessions.sortedByDescending(Session::updatedAt))
                }
            }
        }
    }
}
