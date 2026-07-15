package com.imgad.domain.port

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveManifest(val formatVersion: Int = 1, val exportedAt: Long, val encryptedSecrets: Boolean = false)

@Serializable
data class ArchiveProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
    val defaultModelId: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ArchiveModel(
    val id: String,
    val providerId: String,
    val modelName: String,
    val displayName: String,
    val supportsGeneration: Boolean,
    val supportsEdit: Boolean,
    val supportsMask: Boolean,
    val supportsMultipleImages: Boolean,
    val supportedSizes: Set<String>,
    val supportedQualities: Set<String>,
    val enabled: Boolean,
)

@Serializable
data class ArchiveSession(val id: String, val title: String, val createdAt: Long, val updatedAt: Long, val deletedAt: Long?)

@Serializable
data class ArchiveMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val taskState: String,
    val requestSnapshotJson: String?,
    val errorJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ArchiveAsset(
    val id: String,
    val messageId: String?,
    val localUri: String,
    val thumbnailUri: String?,
    val mediaType: String,
    val width: Int?,
    val height: Int?,
    val byteSize: Long?,
    val source: String,
    val entryName: String?,
    val thumbnailEntryName: String? = null,
    val available: Boolean = true,
    val createdAt: Long = 0L,
)

@Serializable
data class ArchiveData(
    val providers: List<ArchiveProvider> = emptyList(),
    val models: List<ArchiveModel> = emptyList(),
    val sessions: List<ArchiveSession> = emptyList(),
    val messages: List<ArchiveMessage> = emptyList(),
    val assets: List<ArchiveAsset> = emptyList(),
    val encryptedSecrets: String? = null,
)

data class ArchiveSnapshot(
    val providers: List<ArchiveProvider> = emptyList(),
    val models: List<ArchiveModel> = emptyList(),
    val sessions: List<ArchiveSession> = emptyList(),
    val messages: List<ArchiveMessage> = emptyList(),
    val assets: List<ArchiveAsset> = emptyList(),
    val assetBytes: Map<String, ByteArray> = emptyMap(),
    val thumbnailBytes: Map<String, ByteArray> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
)

data class ImportedArchive(val manifest: ArchiveManifest, val data: ArchiveData, val assetBytes: Map<String, ByteArray>)

data class ArchiveImportPreview(val providers: Int, val sessions: Int, val assets: Int)

interface ArchiveExporter {
    suspend fun export(snapshot: ArchiveSnapshot, output: java.io.OutputStream, password: CharArray? = null)
}

interface ArchiveImporter {
    suspend fun import(input: java.io.InputStream, password: CharArray? = null): ImportedArchive
}

interface ArchiveStore : ArchiveExporter, ArchiveImporter
