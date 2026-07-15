package com.imgad.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetFileStoreInstrumentedTest {
    @Test
    fun thumbnailIsAtMost512AndOriginalDimensionsAreRetained() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "asset-source-${System.nanoTime()}.png")
        val store = AssetFileStore(context)
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        try {
            val original = store.copyInput(Uri.fromFile(source).toString(), messageId = "thumbnail-test")
            val thumbnail = store.createThumbnail(original.localUri, messageId = "thumbnail-test")

            assertEquals(1200, original.width)
            assertEquals(600, original.height)
            assertTrue(maxOf(thumbnail.width ?: 0, thumbnail.height ?: 0) <= 512)
        } finally {
            store.deleteForMessage("thumbnail-test")
            source.delete()
        }
    }

    @Test
    fun malformedImageRaisesStructuredDecodeFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "bad-image-${System.nanoTime()}.png")
            .apply { writeText("not-an-image") }
        val store = AssetFileStore(context)
        try {
            var failure: Throwable? = null
            try {
                store.copyInput(Uri.fromFile(source).toString(), messageId = "bad-image-test")
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is AssetStorageException.ImageDecodeFailed)
        } finally {
            store.deleteForMessage("bad-image-test")
            source.delete()
        }
    }
}
