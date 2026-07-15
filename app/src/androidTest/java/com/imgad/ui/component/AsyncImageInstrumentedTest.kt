package com.imgad.ui.component

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AsyncImageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validPngFromAbsolutePathIsDisplayed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "async-image-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        try {
            composeRule.setContent {
                AsyncImage(
                    uri = source.path,
                    contentDescription = "测试图片",
                    modifier = Modifier.size(100.dp),
                )
            }

            composeRule.onNodeWithContentDescription("测试图片").assertIsDisplayed()
        } finally {
            source.delete()
        }
    }
}
