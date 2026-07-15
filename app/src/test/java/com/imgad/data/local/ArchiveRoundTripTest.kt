package com.imgad.data.local

import com.imgad.domain.port.ArchiveAsset
import com.imgad.domain.port.ArchiveProvider
import com.imgad.domain.port.ArchiveSession
import com.imgad.domain.port.ArchiveSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRoundTripTest {
    @Test
    fun roundTripKeepsRelationsAndDoesNotExportKeysByDefault() = runBlocking {
        val asset = ArchiveAsset("asset", "message", "/private/original.png", null, "image/png", null, null, 3, "OUTPUT", "assets/asset.bin")
        val snapshot = ArchiveSnapshot(
            providers = listOf(ArchiveProvider("provider", "Provider", "https://example.com", true, null)),
            sessions = listOf(ArchiveSession("session", "Session", 1, 2, null)),
            assets = listOf(asset),
            assetBytes = mapOf("asset" to byteArrayOf(1, 2, 3)),
            secrets = mapOf("provider" to "secret"),
        )
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ExportArchive().export(snapshot, output) } }

        val noSecrets = snapshot.copy(secrets = emptyMap())
        output.reset()
        ExportArchive().export(noSecrets, output)
        val imported = ImportArchive().import(ByteArrayInputStream(output.toByteArray()))
        assertEquals(1, imported.data.providers.size)
        assertEquals("message", imported.data.assets.single().messageId)
        assertArrayEquals(byteArrayOf(1, 2, 3), imported.assetBytes.getValue("assets/asset.bin"))
        assertNull(imported.data.encryptedSecrets)
    }

    @Test
    fun exportGeneratesEntryNamesForByteBackedAssetsWhenMissing() = runBlocking {
        val snapshot = ArchiveSnapshot(
            assets = listOf(ArchiveAsset("asset", null, "/asset", "/thumbnail", "image/png", null, null, 3L, "OUTPUT", null)),
            assetBytes = mapOf("asset" to byteArrayOf(1, 2, 3)),
            thumbnailBytes = mapOf("asset" to byteArrayOf(4, 5)),
        )
        val output = ByteArrayOutputStream()

        ExportArchive().export(snapshot, output)

        val imported = ImportArchive().import(ByteArrayInputStream(output.toByteArray()))
        val importedAsset = imported.data.assets.single()
        val entryName = requireNotNull(importedAsset.entryName)
        val thumbnailEntryName = requireNotNull(importedAsset.thumbnailEntryName)
        assertTrue(entryName.startsWith("assets/"))
        assertTrue(thumbnailEntryName.startsWith("assets/"))
        assertArrayEquals(byteArrayOf(1, 2, 3), imported.assetBytes.getValue(entryName))
        assertArrayEquals(byteArrayOf(4, 5), imported.assetBytes.getValue(thumbnailEntryName))
    }

    @Test
    fun passwordProtectedSecretsRejectWrongPassword() = runBlocking {
        val snapshot = ArchiveSnapshot(secrets = mapOf("provider" to "secret"))
        val output = ByteArrayOutputStream()
        ExportArchive().export(snapshot, output, "correct".toCharArray())
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ImportArchive().import(ByteArrayInputStream(output.toByteArray()), "wrong".toCharArray()) }
        }
        assertEquals("密码错误或归档已损坏", error.message)
    }

    @Test
    fun zipSlipEntryIsRejected() = runBlocking {
        val output = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../escape"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ImportArchive().import(ByteArrayInputStream(output.toByteArray())) }
        }
        Unit
    }

    @Test
    fun exportRejectsSingleEntryAboveLimit() = runBlocking {
        val bytes = ByteArray((ExportArchive.MAX_ENTRY_BYTES + 1).toInt())
        val snapshot = ArchiveSnapshot(
            assets = listOf(ArchiveAsset("asset", null, "/asset", null, "image/png", null, null, bytes.size.toLong(), "OUTPUT", "assets/asset.bin")),
            assetBytes = mapOf("asset" to bytes),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ExportArchive().export(snapshot, ByteArrayOutputStream()) }
        }
        Unit
    }

    @Test
    fun exportRejectsMoreThanMaximumEntries() = runBlocking {
        val assets = (0 until ExportArchive.MAX_ENTRIES).map { index ->
            val id = "asset-$index"
            ArchiveAsset(id, null, "/$id", null, "image/png", null, null, 1L, "OUTPUT", "assets/$id.bin")
        }
        val snapshot = ArchiveSnapshot(
            assets = assets,
            assetBytes = assets.associate { it.id to byteArrayOf(1) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ExportArchive().export(snapshot, ByteArrayOutputStream()) }
        }
        Unit
    }

    @Test
    fun exportRejectsTotalPayloadAboveLimit() = runBlocking {
        val bytes = ByteArray(20 * 1024 * 1024)
        val assets = (0 until 3).map { index ->
            val id = "asset-$index"
            ArchiveAsset(id, null, "/$id", null, "image/png", null, null, bytes.size.toLong(), "OUTPUT", "assets/$id.bin")
        }
        val snapshot = ArchiveSnapshot(
            assets = assets,
            assetBytes = assets.associate { it.id to bytes },
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ExportArchive().export(snapshot, ByteArrayOutputStream()) }
        }
        Unit
    }

    @Test
    fun importRejectsDuplicateEntries() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ImportArchive().import(ByteArrayInputStream(rawZipWithDuplicateManifest())) }
        }
        Unit
    }

    private fun rawZipWithDuplicateManifest(): ByteArray {
        val local = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        var offset = 0
        listOf(byteArrayOf(1), byteArrayOf(2)).forEach { bytes ->
            val name = "manifest.json".toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(bytes) }.value
            writeInt(local, 0x04034b50)
            writeShort(local, 20)
            writeShort(local, 0)
            writeShort(local, 0)
            writeShort(local, 0)
            writeShort(local, 0)
            writeInt(local, crc)
            writeInt(local, bytes.size.toLong())
            writeInt(local, bytes.size.toLong())
            writeShort(local, name.size)
            writeShort(local, 0)
            local.write(name)
            local.write(bytes)

            writeInt(central, 0x02014b50)
            writeShort(central, 20)
            writeShort(central, 20)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)
            writeInt(central, crc)
            writeInt(central, bytes.size.toLong())
            writeInt(central, bytes.size.toLong())
            writeShort(central, name.size)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)
            writeInt(central, 0)
            writeInt(central, offset.toLong())
            central.write(name)
            offset = local.size()
        }
        val output = ByteArrayOutputStream()
        output.write(local.toByteArray())
        val centralOffset = output.size()
        output.write(central.toByteArray())
        writeInt(output, 0x06054b50)
        writeShort(output, 0)
        writeShort(output, 0)
        writeShort(output, 2)
        writeShort(output, 2)
        writeInt(output, central.size().toLong())
        writeInt(output, centralOffset.toLong())
        writeShort(output, 0)
        return output.toByteArray()
    }

    private fun writeShort(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeInt(output: ByteArrayOutputStream, value: Long) {
        output.write((value and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 24) and 0xff).toInt())
    }
}
