package com.imgad.domain.usecase

import com.imgad.domain.model.AssetSource
import com.imgad.domain.port.FileStore
import com.imgad.domain.port.StoredAssetFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetUseCaseTest {
    @Test
    fun importInputAssetMapsStoredMetadataAndThumbnail() {
        val store = AssetUseCaseFakeFileStore()

        val asset = ImportInputAsset(store).invoke("content://input", "message")

        assertEquals(AssetSource.INPUT, asset.source)
        assertEquals("input-path", asset.localUri)
        assertEquals("thumb-path", asset.thumbnailUri)
        assertEquals("image/png", asset.mediaType)
        assertEquals(120, asset.width)
        assertEquals(80, asset.height)
        assertEquals(123L, asset.byteSize)
    }

    @Test
    fun importInputAssetDeletesOriginalWhenThumbnailFailsAndPreservesFailure() {
        val failure = IllegalStateException("thumbnail failed")
        val cleanupFailure = IllegalStateException("cleanup failed")
        val store = AssetUseCaseFakeFileStore(thumbnailFailure = failure, deleteFailure = cleanupFailure)

        var thrown: Throwable? = null
        try {
            ImportInputAsset(store).invoke("content://input", "message")
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(failure, thrown)
        assertTrue(store.deleted.contains("input-path"))
        assertTrue(thrown!!.suppressed.contains(cleanupFailure))
    }

    @Test
    fun saveOutputAssetMapsStoredMetadataAndSource() {
        val store = AssetUseCaseFakeFileStore()

        val asset = SaveOutputAsset(store).invoke(byteArrayOf(1), "image/png", "message")

        assertEquals(AssetSource.OUTPUT, asset.source)
        assertEquals("input-path", asset.localUri)
        assertNotNull(asset.id)
        assertEquals("message", asset.messageId)
    }

    @Test
    fun saveOutputAssetDeletesOriginalWhenThumbnailFails() {
        val failure = IllegalStateException("thumbnail failed")
        val store = AssetUseCaseFakeFileStore(thumbnailFailure = failure)

        var thrown: Throwable? = null
        try {
            SaveOutputAsset(store).invoke(byteArrayOf(1), "image/png", "message")
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(failure, thrown)
        assertEquals(listOf("input-path"), store.deleted)
    }
}

internal class AssetUseCaseFakeFileStore(
    private val thumbnailFailure: Throwable? = null,
    private val deleteFailure: Throwable? = null,
) : FileStore {
    val deleted = mutableListOf<String>()

    override fun copyInput(uri: String, messageId: String?): StoredAssetFile = stored()

    override fun writeOutput(bytes: ByteArray, mediaType: String, messageId: String?): StoredAssetFile = stored()

    override fun createThumbnail(uri: String, messageId: String?): StoredAssetFile {
        thumbnailFailure?.let { throw it }
        return StoredAssetFile("thumb-path", "image/png", 60, 40, 20L)
    }

    override fun delete(path: String) {
        deleted += path
        deleteFailure?.let { throw it }
    }

    override fun deleteForMessage(messageId: String) = Unit

    private fun stored() = StoredAssetFile("input-path", "image/png", 120, 80, 123L)
}
