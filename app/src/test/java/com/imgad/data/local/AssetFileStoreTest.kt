package com.imgad.data.local

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetFileStoreTest {
    private val roots = mutableListOf<File>()

    @After
    fun tearDown() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun copyInputKeepsSourceReadableAndUsesUuidFileName() {
        val root = Files.createTempDirectory("imgad-assets").toFile().also(roots::add)
        val source = File(root, "source-name.txt").apply { writeText("input-data") }
        val store = AssetFileStore(
            rootDirectory = root,
            inputStreamProvider = { ByteArrayInputStream(source.readBytes()) },
            mediaTypeProvider = { "text/plain" },
        )

        val stored = store.copyInput(source.absolutePath)

        assertArrayEquals(source.readBytes(), File(stored.localUri).readBytes())
        assertEquals("text/plain", stored.mediaType)
        assertTrue(stored.localUri.substringAfterLast('/').matches(Regex("[0-9a-fA-F-]{36}\\.bin")))
        assertEquals(source.length(), stored.byteSize)
    }

    @Test
    fun unknownOutputMediaTypeFailsClearly() {
        val root = Files.createTempDirectory("imgad-assets").toFile().also(roots::add)
        val store = AssetFileStore(root, { ByteArrayInputStream(byteArrayOf()) }, { null })

        var failure: Throwable? = null
        try {
            store.writeOutput(byteArrayOf(1, 2), "image/tiff")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is AssetStorageException.UnsupportedMediaType)
    }

    @Test
    fun deleteOnlyRemovesFilesInsidePrivateRoot() {
        val root = Files.createTempDirectory("imgad-assets").toFile().also(roots::add)
        val outside = Files.createTempFile("imgad-outside", ".txt").toFile()
        roots += outside
        val store = AssetFileStore(root, { ByteArrayInputStream(byteArrayOf(1)) }, { "text/plain" })
        val stored = store.copyInput("source", messageId = "message")

        store.delete(stored.localUri)

        assertTrue(!File(stored.localUri).exists())
        var failure: Throwable? = null
        try {
            store.delete(outside.absolutePath)
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is AssetStorageException.InvalidPath)
        assertTrue(outside.exists())
    }

    @Test
    fun deleteForMessageRemovesMessageScopedFiles() {
        val root = Files.createTempDirectory("imgad-assets").toFile().also(roots::add)
        val store = AssetFileStore(root, { ByteArrayInputStream(byteArrayOf(1)) }, { "text/plain" })
        val stored = store.copyInput("source", messageId = "message")

        store.deleteForMessage("message")

        assertTrue(!File(stored.localUri).exists())
    }
}
