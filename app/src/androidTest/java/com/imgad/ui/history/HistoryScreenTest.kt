package com.imgad.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.imgad.domain.model.Session
import com.imgad.domain.port.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sessionOpensAndDeleteShowsPrivateImageImpact() {
        var opened: String? = null
        val viewModel = HistoryViewModel(FakeSessionStore())
        composeRule.setContent {
            HistoryScreen(viewModel, HistoryScreenActions { opened = it })
        }
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("Session").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Session").performClick()
        assertEquals("session", opened)

        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("将删除应用私有图片，但不会删除系统图库副本。").assertIsDisplayed()
    }

    @Test
    fun renameEntryOpensRenameDialog() {
        composeRule.setContent {
            HistoryScreen(HistoryViewModel(FakeSessionStore()), HistoryScreenActions())
        }
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("Session").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("重命名").performClick()
        composeRule.onNodeWithText("重命名会话").assertIsDisplayed()
    }

    private class FakeSessionStore : SessionStore {
        override fun observeActive(query: String): Flow<List<Session>> =
            flowOf(listOf(Session("session", "Session", updatedAt = 2)))

        override suspend fun rename(id: String, title: String, now: Long) = Unit
        override suspend fun softDelete(id: String, now: Long) = Unit
    }
}
