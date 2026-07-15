package com.imgad.data.remote

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAssetReaderTest {
    private val roots = mutableListOf<File>()

    @After
    fun tearDown() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun readerAllowsOnlyImageFilesInsideConfiguredRoot() {
        val root = Files.createTempDirectory("imgad-upload").toFile().also(roots::add)
        val image = File(root, "image.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val outside = Files.createTempFile("imgad-outside", ".png").toFile().also(roots::add)
        val reader = RootedUploadAssetReader(root)

        val upload = reader.read(Asset("id", localUri = image.path, mediaType = "image/png", source = AssetSource.INPUT))
        assertArrayEquals(byteArrayOf(1, 2), upload.bytes)

        var failure: Throwable? = null
        try {
            reader.read(Asset("id", localUri = outside.path, mediaType = "image/png", source = AssetSource.INPUT))
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is com.imgad.domain.model.GenerationFailure)
    }

    @Test
    fun readerRejectsNonImageMalformedMimeAndConfiguredSizeLimit() {
        val root = Files.createTempDirectory("imgad-upload").toFile().also(roots::add)
        val file = File(root, "image.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val reader = RootedUploadAssetReader(root, maxBytes = 3)

        listOf("text/plain", "image/").forEach { mime ->
            var failure: Throwable? = null
            try {
                reader.read(Asset("id", localUri = file.path, mediaType = mime, source = AssetSource.INPUT))
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is com.imgad.domain.model.GenerationFailure)
        }
        var tooLarge: Throwable? = null
        try {
            reader.read(Asset("id", localUri = file.path, mediaType = "image/png", source = AssetSource.INPUT))
        } catch (error: Throwable) {
            tooLarge = error
        }
        assertTrue(tooLarge is com.imgad.domain.model.GenerationFailure)
    }
}
