package com.imgad.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetShareManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun createsReadOnlyContentShareIntentForPrivateAsset() {
        val file = File(context.filesDir, "imgad-assets/test/share.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val intent = AssetShareManager(context).createShareIntent(asset(file))
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals("content", uri?.scheme)
        assertFalse(uri.toString().startsWith("file:"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun rejectsFilesOutsidePrivateAssetDirectory() {
        val outside = File(context.cacheDir, "outside.png").apply { writeBytes(byteArrayOf(1)) }

        assertThrows(SecurityException::class.java) {
            AssetShareManager(context).createShareIntent(asset(outside))
        }
    }

    private fun asset(file: File) = Asset(
        id = "asset",
        localUri = file.path,
        mediaType = "image/png",
        source = AssetSource.OUTPUT,
    )
}
