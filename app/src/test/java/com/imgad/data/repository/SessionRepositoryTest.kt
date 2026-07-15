package com.imgad.data.repository

import com.imgad.data.local.dao.AssetDao
import com.imgad.data.local.dao.AssetPathRow
import com.imgad.data.local.dao.SessionDao
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.AssetSource
import com.imgad.domain.port.FileStore
import com.imgad.domain.port.StoredAssetFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun softDeleteRemovesOnlyFilesNotSharedByOtherActiveSessions() = runTest {
        val sessions = FakeSessionDao()
        val assets = FakeAssetDao(
            targetAssets = listOf(asset("shared", "/shared.png"), asset("unique", "/unique.png", "/unique-thumb.png")),
            sharedPaths = listOf(AssetPathRow("/shared.png", null)),
        )
        val files = FakeFileStore()

        SessionRepository(sessions, assets, files).softDelete("target", 10)

        assertEquals(listOf("/unique.png", "/unique-thumb.png"), files.deleted)
        assertEquals(10L, sessions.getById("target")?.deletedAt)
    }

    @Test
    fun fileDeleteFailureKeepsSessionVisibleAndPropagates() = runTest {
        val sessions = FakeSessionDao()
        val assets = FakeAssetDao(listOf(asset("asset", "/failure.png")), emptyList())
        val files = FakeFileStore(failPath = "/failure.png")

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                SessionRepository(sessions, assets, files).softDelete("target", 10)
            }
        }

        assertNull(sessions.getById("target")?.deletedAt)
    }

    private fun asset(id: String, local: String, thumbnail: String? = null) = AssetEntity(
        id = id,
        messageId = "target-message",
        localUri = local,
        thumbnailUri = thumbnail,
        mediaType = "image/png",
        source = AssetSource.OUTPUT,
    )
}

private class FakeSessionDao : SessionDao {
    private val state = MutableStateFlow(listOf(SessionEntity("target", "Target")))
    override suspend fun upsert(session: SessionEntity) { state.value = state.value.filterNot { it.id == session.id } + session }
    override suspend fun upsertAll(sessions: List<SessionEntity>) { sessions.forEach { upsert(it) } }
    override suspend fun getById(id: String): SessionEntity? = state.value.firstOrNull { it.id == id }
    override fun observeById(id: String): Flow<SessionEntity?> = state.map { rows -> rows.firstOrNull { it.id == id } }
    override fun observeActive(): Flow<List<SessionEntity>> = state.map { it.filter { row -> row.deletedAt == null } }
    override fun searchActiveByTitle(query: String): Flow<List<SessionEntity>> = observeActive()
    override suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = deletedAt, updatedAt = updatedAt) else it }
    }
    override suspend fun deleteById(id: String) { state.value = state.value.filterNot { it.id == id } }
}

private class FakeAssetDao(
    private val targetAssets: List<AssetEntity>,
    private val sharedPaths: List<AssetPathRow>,
) : AssetDao {
    override suspend fun upsert(asset: AssetEntity) = Unit
    override suspend fun upsertAll(assets: List<AssetEntity>) = Unit
    override suspend fun getById(id: String): AssetEntity? = targetAssets.firstOrNull { it.id == id }
    override fun observeByMessage(messageId: String): Flow<List<AssetEntity>> = MutableStateFlow(targetAssets)
    override fun observeBySession(sessionId: String): Flow<List<AssetEntity>> = MutableStateFlow(targetAssets)
    override fun getAllLocalUris(): List<AssetPathRow> = targetAssets.map { AssetPathRow(it.localUri, it.thumbnailUri) }
    override fun getActiveLocalUris(): List<AssetPathRow> = getAllLocalUris() + sharedPaths
    override fun getActiveLocalUrisExcludingSession(sessionId: String): List<AssetPathRow> = sharedPaths
    override suspend fun deleteById(id: String) = Unit
}

private class FakeFileStore(private val failPath: String? = null) : FileStore {
    val deleted = mutableListOf<String>()
    override fun copyInput(uri: String, messageId: String?): StoredAssetFile = error("unused")
    override fun writeOutput(bytes: ByteArray, mediaType: String, messageId: String?): StoredAssetFile = error("unused")
    override fun createThumbnail(uri: String, messageId: String?): StoredAssetFile = error("unused")
    override fun delete(path: String) {
        if (path == failPath) throw IllegalStateException("delete failed")
        deleted += path
    }
    override fun deleteForMessage(messageId: String) = Unit
}
