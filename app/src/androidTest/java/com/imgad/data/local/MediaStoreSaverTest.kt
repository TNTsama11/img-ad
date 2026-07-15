package com.imgad.data.local

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreSaverTest {
    @Test
    fun successfulSaveClearsPendingFlag() {
        val gateway = FakeMediaStoreGateway()
        val saver = MediaStoreSaver(gateway)

        val uri = saver.save(byteArrayOf(1, 2, 3), "image/png")

        assertEquals(FakeMediaStoreGateway.URI, uri)
        assertTrue(gateway.inserted.getAsString(MediaStore.Images.Media.DISPLAY_NAME)
            .matches(Regex("[0-9a-fA-F-]{36}\\.png")))
        assertEquals("image/png", gateway.inserted.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures/ImgAd", gateway.inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, gateway.inserted.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertEquals(0, gateway.updated.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertArrayEquals(byteArrayOf(1, 2, 3), gateway.written.toByteArray())
        assertTrue(gateway.deleted.isEmpty())
    }

    @Test
    fun failedWriteDeletesPendingMediaRow() {
        val gateway = FakeMediaStoreGateway(failWrites = true)
        val saver = MediaStoreSaver(gateway)

        var failure: Throwable? = null
        try {
            saver.save(byteArrayOf(1, 2, 3), "image/jpeg")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure != null)
        assertEquals(listOf(FakeMediaStoreGateway.URI), gateway.deleted)
    }

    @Test
    fun deleteReturningZeroIsAttachedAsCleanupFailure() {
        val gateway = FakeMediaStoreGateway(failWrites = true, deleteResult = 0)

        var failure: Throwable? = null
        try {
            MediaStoreSaver(gateway).save(byteArrayOf(1), "image/webp")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure != null)
        assertTrue(failure!!.suppressed.any { it.message?.contains("pending MediaStore") == true })
    }

    @Test
    fun deleteExceptionIsAttachedAsCleanupFailure() {
        val gateway = FakeMediaStoreGateway(failWrites = true, throwOnDelete = true)

        var failure: Throwable? = null
        try {
            MediaStoreSaver(gateway).save(byteArrayOf(1), "image/jpeg")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure != null)
        assertTrue(failure!!.suppressed.any { it.message == "simulated delete failure" })
    }

    @Test
    fun nonImageMediaTypeIsRejectedBeforeInsert() {
        val gateway = FakeMediaStoreGateway()

        var failure: Throwable? = null
        try {
            MediaStoreSaver(gateway).save(byteArrayOf(1), "text/plain")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is AssetStorageException.UnsupportedMediaType)
        assertTrue(gateway.inserted.size() == 0)
    }

    private class FakeMediaStoreGateway(
        private val failWrites: Boolean = false,
        private val deleteResult: Int = 1,
        private val throwOnDelete: Boolean = false,
    ) : MediaStoreGateway {
        var inserted = ContentValues()
        var updated = ContentValues()
        val deleted = mutableListOf<Uri>()
        var written = ByteArrayOutputStream()

        override fun insert(values: ContentValues): Uri {
            inserted = ContentValues(values)
            return URI
        }

        override fun openOutputStream(uri: Uri): OutputStream =
            if (failWrites) error("simulated media write failure") else written

        override fun update(uri: Uri, values: ContentValues): Int {
            updated = ContentValues(values)
            return 1
        }

        override fun delete(uri: Uri): Int {
            deleted += uri
            if (throwOnDelete) error("simulated delete failure")
            return deleteResult
        }

        companion object {
            val URI: Uri = Uri.parse("content://media/external/images/1")
        }
    }
}
