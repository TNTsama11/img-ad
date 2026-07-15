package com.imgad.data.repository

import com.imgad.data.local.dao.SessionDao
import com.imgad.data.local.dao.AssetDao
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.Session
import com.imgad.domain.port.FileStore
import com.imgad.domain.port.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URI
import java.nio.file.Paths

class SessionRepository(
    private val sessionDao: SessionDao,
    private val assetDao: AssetDao? = null,
    private val fileStore: FileStore? = null,
) : SessionStore {
    override fun observeActive(query: String): Flow<List<Session>> =
        (if (query.isBlank()) sessionDao.observeActive() else sessionDao.searchActiveByTitle(query))
            .map { sessions -> sessions.map(SessionEntity::toDomain) }

    override suspend fun rename(id: String, title: String, now: Long) {
        require(title.isNotBlank()) { "标题不能为空" }
        val current = requireNotNull(sessionDao.getById(id)) { "会话不存在" }
        sessionDao.upsert(current.copy(title = title.trim(), updatedAt = now))
    }

    override suspend fun softDelete(id: String, now: Long) {
        val assets = assetDao?.observeBySession(id)?.first().orEmpty()
        val sharedPaths = assetDao?.getActiveLocalUrisExcludingSession(id).orEmpty()
            .flatMap { row -> listOfNotNull(row.localUri, row.thumbnailUri) }
            .mapNotNull(::canonicalPath)
            .toSet()
        val pathsToDelete = assets
            .flatMap { asset -> listOfNotNull(asset.localUri, asset.thumbnailUri) }
            .distinctBy { canonicalPath(it) ?: it }
            .filter { path -> canonicalPath(path) !in sharedPaths }
        fileStore?.let { store -> pathsToDelete.forEach(store::delete) }
        sessionDao.softDelete(id, deletedAt = now, updatedAt = now)
    }

    private fun canonicalPath(path: String): String? = runCatching {
        val uri = runCatching { URI(path) }.getOrNull()
        val file = if (uri?.scheme.equals("file", ignoreCase = true)) Paths.get(requireNotNull(uri)).toFile() else File(path)
        file.canonicalPath
    }.getOrNull()
}

private fun SessionEntity.toDomain() = Session(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
