package com.imgad.data.local

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAssetStorageTest {
    @Test
    fun clearUnusedKeepsCanonicalReferenceAndDeletesOrphanAndTemporaryFiles() {
        val root = createTempDirectory("imgad-storage-").toFile()
        try {
            val referenced = File(root, "referenced.png").apply { writeBytes(byteArrayOf(1)) }
            val orphan = File(root, "orphan.png").apply { writeBytes(byteArrayOf(1, 2)) }
            val temporary = File(root, ".write.tmp").apply { writeBytes(byteArrayOf(3)) }
            File(root, "child").mkdirs()
            val aliasedReference = File(root, "child/../referenced.png").path
            val now = listOf(referenced, orphan, temporary).maxOf(File::lastModified) + 1L
            val storage = PrivateAssetStorage(root, { setOf(aliasedReference) }, clock = { now }, gracePeriodMillis = 0L)

            val usage = storage.clearUnused()

            assertTrue(referenced.exists())
            assertFalse(orphan.exists())
            assertFalse(temporary.exists())
            assertEquals(1, usage.files)
            assertEquals(1L, usage.bytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun freshOrphanAndTemporaryFilesSurviveGracePeriod() {
        val root = createTempDirectory("imgad-storage-fresh-").toFile()
        try {
            val orphan = File(root, "orphan.png").apply { writeBytes(byteArrayOf(1)) }
            val temporary = File(root, ".fresh.tmp").apply { writeBytes(byteArrayOf(2)) }
            val storage = PrivateAssetStorage(root, { emptySet() }, clock = { orphan.lastModified() }, gracePeriodMillis = 60 * 60 * 1000L)

            storage.clearUnused()

            assertTrue(orphan.exists())
            assertTrue(temporary.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
