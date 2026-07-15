package com.imgad

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SeedCatSessionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun seededGeneratedImageIsVisibleInMessageAndPreview() {
        val app = ApplicationProvider.getApplicationContext<App>()
        val sessionId = "rendered-asset-${UUID.randomUUID()}"
        val title = "Rendered asset ${UUID.randomUUID()}"
        val image = app.filesDir.resolve("imgad-assets/$sessionId/output.png")
        writeTestPng(image)
        val now = System.currentTimeMillis()

        try {
            runBlocking {
                val task = app.generationRepository.beginTask(
                    sessionId = sessionId,
                    title = title,
                    prompt = "render a local image",
                    requestSnapshotJson = "{}",
                    inputAssets = emptyList(),
                    now = now,
                )
                app.generationRepository.markSucceeded(
                    task.messageId,
                    listOf(
                        Asset(
                            id = "rendered-output",
                            localUri = image.path,
                            thumbnailUri = image.path,
                            mediaType = "image/png",
                            width = 64,
                            height = 32,
                            byteSize = image.length(),
                            source = AssetSource.OUTPUT,
                        ),
                    ),
                    now + 1,
                )
            }

            composeRule.onNodeWithText("历史").performClick()
            composeRule.waitUntil(2_000) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(title).performClick()
            composeRule.waitUntil(2_000) {
                composeRule.onAllNodesWithContentDescription("生成图片").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("生成图片").assertIsDisplayed()
            composeRule.onNodeWithText("查看图片").performClick()
            composeRule.onNodeWithContentDescription("图片预览").assertIsDisplayed()
        } finally {
            runBlocking { app.sessionRepository.softDelete(sessionId, System.currentTimeMillis()) }
        }
    }

    private fun writeTestPng(destination: File) {
        destination.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(224, 96, 48))
        }
        try {
            destination.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
