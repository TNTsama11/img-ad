package com.imgad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityUsesNoActionBarTheme() {
        assertNull(composeRule.activity.actionBar)
    }

    @Test
    fun draftPromptSurvivesBottomNavigationRoundTrip() {
        val draft = "draft-${UUID.randomUUID()}"
        composeRule.onNodeWithText("提示词").performTextInput(draft)

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("创作").performClick()

        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText(draft).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(draft).assertIsDisplayed()
    }

    @Test
    fun seededRepositorySessionOpensFromHistoryIntoCreateWithPreviewAsset() {
        val app = ApplicationProvider.getApplicationContext<App>()
        val sessionId = "instrumented-${UUID.randomUUID()}"
        runBlocking {
            val task = app.generationRepository.beginTask(sessionId, "Seed session", "seed prompt", "{}", emptyList(), 1L)
            app.generationRepository.markSucceeded(
                task.messageId,
                listOf(Asset("asset", localUri = "/missing.png", mediaType = "image/png", source = AssetSource.OUTPUT)),
                2L,
            )
        }
        composeRule.onNodeWithText("历史").performClick()
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("Seed session").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Seed session").performClick()
        composeRule.onNodeWithText("seed prompt").assertIsDisplayed()
        composeRule.onNodeWithText("查看图片").assertIsDisplayed()
    }

    @Test
    fun historyTabReturnsToHistoryAfterOpeningSession() {
        val app = ApplicationProvider.getApplicationContext<App>()
        val sessionId = "history-return-${UUID.randomUUID()}"
        val title = "History return ${UUID.randomUUID()}"
        runBlocking {
            app.generationRepository.beginTask(sessionId, title, "history prompt", "{}", emptyList(), 1L)
        }

        composeRule.onNodeWithText("历史").performClick()
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithText("history prompt").assertIsDisplayed()

        composeRule.onNodeWithText("历史").performClick()

        composeRule.onNodeWithText("搜索标题").assertIsDisplayed()
    }
}
