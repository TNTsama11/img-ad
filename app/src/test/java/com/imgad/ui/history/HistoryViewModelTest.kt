package com.imgad.ui.history

import com.imgad.domain.model.Session
import com.imgad.domain.port.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sessionsStaySortedAndSearchUsesLiteralTitleMatch() = runTest(dispatcher) {
        val store = FakeSessionStore(
            listOf(
                Session("old", "A [draft]", updatedAt = 10),
                Session("new", "B draft", updatedAt = 20),
            ),
        )
        val viewModel = HistoryViewModel(store, now = { 30 }, ioDispatcher = dispatcher)

        advanceUntilIdle()
        assertEquals(listOf("new", "old"), viewModel.uiState.value.sessions.map(Session::id))

        viewModel.updateQuery("[")
        advanceUntilIdle()

        assertEquals(listOf("old"), viewModel.uiState.value.sessions.map(Session::id))
    }

    @Test
    fun deleteRequiresConfirmationAndThenSoftDeletes() = runTest(dispatcher) {
        val store = FakeSessionStore(listOf(Session("session", "Title", updatedAt = 1)))
        val viewModel = HistoryViewModel(store, now = { 50 }, ioDispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.requestDelete("session")
        assertEquals("session", viewModel.uiState.value.pendingDeleteId)
        assertTrue(store.deleted.isEmpty())

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("session"), store.deleted)
        assertNull(viewModel.uiState.value.pendingDeleteId)
    }

    private class FakeSessionStore(initial: List<Session>) : SessionStore {
        private val state = MutableStateFlow(initial)
        val deleted = mutableListOf<String>()

        override fun observeActive(query: String): Flow<List<Session>> = state.map { sessions ->
            sessions.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        }

        override suspend fun rename(id: String, title: String, now: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(title = title, updatedAt = now) else it }
        }

        override suspend fun softDelete(id: String, now: Long) {
            deleted += id
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
