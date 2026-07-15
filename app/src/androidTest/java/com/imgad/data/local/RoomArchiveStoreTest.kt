package com.imgad.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.MessageEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import com.imgad.domain.port.ArchiveAsset
import com.imgad.domain.port.ArchiveData
import com.imgad.domain.port.ArchiveManifest
import com.imgad.domain.port.ArchiveMessage
import com.imgad.domain.port.ArchiveProvider
import com.imgad.domain.port.ArchiveSession
import com.imgad.domain.port.ImportedArchive
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomArchiveStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var root: File
    private lateinit var store: RoomArchiveStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = context.cacheDir.resolve("room-archive-${System.nanoTime()}").apply { mkdirs() }
        store = RoomArchiveStore(database, root)
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun roundTripRemapsIdsAndRestoresOriginalAndThumbnailFiles() = runBlocking {
        val original = root.resolve("original.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val thumbnail = root.resolve("thumbnail.bin").apply { writeBytes(byteArrayOf(4, 5)) }
        database.sessionDao().upsert(SessionEntity("session", "Session", 1L, 2L))
        database.messageDao().upsert(message("message", "session"))
        database.assetDao().upsert(
            AssetEntity(
                id = "asset",
                messageId = "message",
                localUri = original.path,
                thumbnailUri = thumbnail.path,
                mediaType = "image/png",
                byteSize = 3L,
                source = AssetSource.OUTPUT,
            ),
        )

        val snapshot = store.snapshot()
        assertArrayEquals(byteArrayOf(1, 2, 3), snapshot.assetBytes.getValue("asset"))
        assertArrayEquals(byteArrayOf(4, 5), snapshot.thumbnailBytes.getValue("asset"))

        val archive = java.io.ByteArrayOutputStream().also { store.export(snapshot, it) }
        store.apply(store.import(java.io.ByteArrayInputStream(archive.toByteArray())))

        val importedSession = database.sessionDao().observeActive().first().single { it.id != "session" }
        val importedMessage = database.messageDao().observeBySession(importedSession.id).first().single()
        val importedAsset = database.assetDao().observeByMessage(importedMessage.id).first().single()
        assertNotEquals("asset", importedAsset.id)
        assertTrue(importedAsset.available)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(importedAsset.localUri).readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(requireNotNull(importedAsset.thumbnailUri)).readBytes())
    }

    @Test
    fun roundTripPreservesProviderAndAssetTimestampsAndAssetOrder() = runBlocking {
        database.providerDao().upsert(
            ProviderEntity(
                id = "provider",
                name = "Provider",
                baseUrl = "https://example.com",
                apiKeyAlias = "provider-key",
                createdAt = 10L,
                updatedAt = 20L,
            ),
        )
        database.sessionDao().upsert(SessionEntity("session", "Session", 1L, 2L))
        database.messageDao().upsert(message("message", "session"))
        val later = root.resolve("later.bin").apply { writeBytes(byteArrayOf(2)) }
        val earlier = root.resolve("earlier.bin").apply { writeBytes(byteArrayOf(1)) }
        database.assetDao().upsert(
            AssetEntity("later", "message", later.path, null, "image/png", source = AssetSource.OUTPUT, createdAt = 5L),
        )
        database.assetDao().upsert(
            AssetEntity("earlier", "message", earlier.path, null, "image/png", source = AssetSource.OUTPUT, createdAt = 3L),
        )

        val snapshot = store.snapshot()
        assertEquals(10L, snapshot.providers.single().createdAt)
        assertEquals(20L, snapshot.providers.single().updatedAt)
        assertEquals(listOf(3L, 5L), snapshot.assets.map(ArchiveAsset::createdAt))
        val output = java.io.ByteArrayOutputStream().also { store.export(snapshot, it) }
        store.apply(store.import(java.io.ByteArrayInputStream(output.toByteArray())))

        val importedProvider = database.providerDao().observeAll().first().single { it.id != "provider" }
        assertEquals(10L, importedProvider.createdAt)
        assertEquals(20L, importedProvider.updatedAt)
        val importedSession = database.sessionDao().observeActive().first().single { it.id != "session" }
        val importedMessage = database.messageDao().observeBySession(importedSession.id).first().single()
        val importedAssets = database.assetDao().observeByMessage(importedMessage.id).first()
        assertEquals(listOf(3L, 5L), importedAssets.map(AssetEntity::createdAt))
        assertEquals(listOf(byteArrayOf(1).toList(), byteArrayOf(2).toList()), importedAssets.map { File(it.localUri).readBytes().toList() })
    }

    @Test
    fun missingAssetEntryKeepsMetadataAndMarksAssetUnavailable() = runBlocking {
        val imported = ImportedArchive(
            manifest = ArchiveManifest(exportedAt = 1L),
            data = ArchiveData(
                sessions = listOf(ArchiveSession("session", "Session", 1L, 2L, null)),
                messages = listOf(
                    ArchiveMessage(
                        "message",
                        "session",
                        MessageRole.ASSISTANT.name,
                        "",
                        TaskState.SUCCEEDED.name,
                        null,
                        null,
                        1L,
                        2L,
                    ),
                ),
                assets = listOf(
                    ArchiveAsset(
                        "asset",
                        "message",
                        "/missing/original.png",
                        "/missing/thumbnail.png",
                        "image/png",
                        null,
                        null,
                        3L,
                        AssetSource.OUTPUT.name,
                        "assets/asset.bin",
                        "assets/asset-thumb.bin",
                    ),
                ),
            ),
            assetBytes = emptyMap(),
        )

        store.apply(imported)

        val session = database.sessionDao().observeActive().first().single()
        val message = database.messageDao().observeBySession(session.id).first().single()
        val asset = database.assetDao().observeByMessage(message.id).first().single()
        assertFalse(asset.available)
        assertTrue(asset.localUri == "/missing/original.png")
        assertTrue(asset.thumbnailUri == "/missing/thumbnail.png")
    }

    @Test
    fun missingThumbnailEntryClearsThumbnailWhenOriginalIsRestored() = runBlocking {
        val imported = ImportedArchive(
            manifest = ArchiveManifest(exportedAt = 1L),
            data = ArchiveData(
                sessions = listOf(ArchiveSession("session", "Session", 1L, 2L, null)),
                messages = listOf(
                    ArchiveMessage(
                        "message",
                        "session",
                        MessageRole.ASSISTANT.name,
                        "",
                        TaskState.SUCCEEDED.name,
                        null,
                        null,
                        1L,
                        2L,
                    ),
                ),
                assets = listOf(
                    ArchiveAsset(
                        "asset",
                        "message",
                        "/missing/original.png",
                        "/missing/thumbnail.png",
                        "image/png",
                        null,
                        null,
                        3L,
                        AssetSource.OUTPUT.name,
                        "assets/original.bin",
                        "assets/thumbnail.bin",
                    ),
                ),
            ),
            assetBytes = mapOf("assets/original.bin" to byteArrayOf(1, 2, 3)),
        )

        store.apply(imported)

        val session = database.sessionDao().observeActive().first().single()
        val message = database.messageDao().observeBySession(session.id).first().single()
        val asset = database.assetDao().observeByMessage(message.id).first().single()
        assertTrue(asset.available)
        assertTrue(asset.thumbnailUri == null)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(asset.localUri).readBytes())
    }

    @Test
    fun duplicateIdsAndUnknownReferencesAreRejectedBeforeWrites() = runBlocking {
        val duplicate = ImportedArchive(
            ArchiveManifest(exportedAt = 1L),
            ArchiveData(sessions = listOf(ArchiveSession("same", "one", 1L, 1L, null), ArchiveSession("same", "two", 1L, 1L, null))),
            emptyMap(),
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.apply(duplicate) } }

        val unknownReference = ImportedArchive(
            ArchiveManifest(exportedAt = 1L),
            ArchiveData(
                assets = listOf(
                    ArchiveAsset("asset", "missing-message", "/asset", null, "image/png", null, null, 1L, AssetSource.OUTPUT.name, null),
                ),
            ),
            emptyMap(),
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.apply(unknownReference) } }
        assertTrue(database.sessionDao().observeActive().first().isEmpty())
    }

    @Test
    fun secretWritesAreRolledBackWhenStoreOrDatabaseFails() = runBlocking {
        val putFailureStore = RecordingSecretStore(failPut = true)
        val putFailure = assertThrows(IllegalStateException::class.java) {
            runBlocking { RoomArchiveStore(database, root, secretStore = putFailureStore).apply(archiveWithSecret()) }
        }
        assertEquals("put failed", putFailure.message)
        assertEquals(1, putFailureStore.removed.size)

        val databaseFailureStore = RecordingSecretStore()
        val databaseFailure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                RoomArchiveStore(database, root, secretStore = databaseFailureStore)
                    .apply(archiveWithSecret(messageRole = "INVALID"))
            }
        }
        assertTrue(databaseFailureStore.values.isEmpty())
        assertEquals(1, databaseFailureStore.removed.size)

        val cleanupFailureStore = RecordingSecretStore(failPut = true, failRemove = true)
        val cleanupFailure = assertThrows(IllegalStateException::class.java) {
            runBlocking { RoomArchiveStore(database, root, secretStore = cleanupFailureStore).apply(archiveWithSecret()) }
        }
        assertEquals("put failed", cleanupFailure.message)
        assertEquals(1, cleanupFailure.suppressed.size)
    }

    @Test
    fun missingSecretStoreIsRejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.apply(archiveWithSecret()) } }
        Unit
    }

    @Test
    fun databaseFailureRemovesMovedFilesAndStagingDirectory() = runBlocking {
        val imported = ImportedArchive(
            ArchiveManifest(exportedAt = 1L),
            ArchiveData(
                sessions = listOf(ArchiveSession("session", "Session", 1L, 1L, null)),
                messages = listOf(ArchiveMessage("message", "session", MessageRole.ASSISTANT.name, "", TaskState.SUCCEEDED.name, null, null, 1L, 1L)),
                assets = listOf(ArchiveAsset("asset", "message", "/original", null, "image/png", null, null, 1L, "INVALID", "assets/original.bin")),
            ),
            assetBytes = mapOf("assets/original.bin" to byteArrayOf(1)),
        )

        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.apply(imported) } }
        assertTrue(database.sessionDao().observeActive().first().isEmpty())
        assertTrue(root.resolve("imported").listFiles().orEmpty().isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".import-") })
    }

    private fun archiveWithSecret(messageRole: String = MessageRole.ASSISTANT.name) = ImportedArchive(
        ArchiveManifest(exportedAt = 1L, encryptedSecrets = true),
        ArchiveData(
            providers = listOf(ArchiveProvider("provider", "Provider", "https://example.com", true, null)),
            sessions = listOf(ArchiveSession("session", "Session", 1L, 1L, null)),
            messages = listOf(ArchiveMessage("message", "session", messageRole, "", TaskState.SUCCEEDED.name, null, null, 1L, 1L)),
            encryptedSecrets = "{\"provider\":\"secret\"}",
        ),
        emptyMap(),
    )

    private class RecordingSecretStore(
        private val failPut: Boolean = false,
        private val failRemove: Boolean = false,
    ) : SecretStore {
        val values = mutableMapOf<String, String>()
        val removed = mutableListOf<String>()

        override fun put(alias: String, value: String) {
            if (failPut) error("put failed")
            values[alias] = value
        }

        override fun get(alias: String): String? = values[alias]

        override fun remove(alias: String) {
            removed += alias
            if (failRemove) error("remove failed")
            values.remove(alias)
        }
    }

    private fun message(id: String, sessionId: String) = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = MessageRole.ASSISTANT,
        text = "",
        taskState = TaskState.SUCCEEDED,
        createdAt = 1L,
        updatedAt = 2L,
    )
}
