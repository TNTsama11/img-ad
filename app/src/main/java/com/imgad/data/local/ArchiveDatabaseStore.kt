package com.imgad.data.local

import androidx.room.withTransaction
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.MessageEntity
import com.imgad.data.local.entity.ModelProfileEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import com.imgad.domain.port.ArchiveAsset
import com.imgad.domain.port.ArchiveData
import com.imgad.domain.port.ArchiveMessage
import com.imgad.domain.port.ArchiveModel
import com.imgad.domain.port.ArchiveProvider
import com.imgad.domain.port.ArchiveSession
import com.imgad.domain.port.ArchiveSnapshot
import com.imgad.domain.port.ArchiveExporter
import com.imgad.domain.port.ArchiveImporter
import com.imgad.domain.port.ArchiveStore
import com.imgad.domain.port.ImportedArchive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RoomArchiveStore(
    private val database: AppDatabase,
    private val root: File,
    private val archive: ArchiveExporter = ExportArchive(),
    private val importer: ArchiveImporter = ImportArchive(),
    private val secretStore: SecretStore? = null,
) : ArchiveStore {
    override suspend fun export(snapshot: ArchiveSnapshot, output: java.io.OutputStream, password: CharArray?) =
        archive.export(snapshot, output, password)

    suspend fun snapshot(includeSecrets: Boolean = false): ArchiveSnapshot {
        val snapshot = database.withTransaction {
            val providers = database.providerDao().observeAll().first()
            val providerModels = providers.flatMap { database.modelProfileDao().observeByProvider(it.id).first() }
            val sessions = database.sessionDao().observeActive().first()
            val messages = sessions.flatMap { database.messageDao().observeBySession(it.id).first() }
            val assets = sessions.flatMap { database.assetDao().observeBySession(it.id).first() }
            validateReferences(providers, providerModels, sessions, messages, assets)
            val archiveAssets = assets.map { asset ->
                ArchiveAsset(
                    asset.id, asset.messageId, asset.localUri, asset.thumbnailUri, asset.mediaType,
                    asset.width, asset.height, asset.byteSize, asset.source.name,
                    "assets/${UUID.randomUUID()}.bin", asset.thumbnailUri?.let { "assets/${UUID.randomUUID()}-thumb.bin" }, asset.available, asset.createdAt,
                )
            }
            ArchiveSnapshot(
                providers = providers.map { ArchiveProvider(it.id, it.name, it.baseUrl, it.enabled, it.defaultModelId, it.createdAt, it.updatedAt) },
                models = providerModels.map { ArchiveModel(it.id, it.providerId, it.modelName, it.displayName, it.supportsGeneration, it.supportsEdit, it.supportsMask, it.supportsMultipleImages, it.supportedSizes, it.supportedQualities, it.enabled) },
                sessions = sessions.map { ArchiveSession(it.id, it.title, it.createdAt, it.updatedAt, it.deletedAt) },
                messages = messages.map { ArchiveMessage(it.id, it.sessionId, it.role.name, it.text, it.taskState.name, it.requestSnapshotJson, it.errorJson, it.createdAt, it.updatedAt) },
                assets = archiveAssets,
                assetBytes = archiveAssets.mapNotNull { asset ->
                    val file = File(asset.localUri)
                    if (file.isFile) asset.id to file.readBytes() else null
                }.toMap(),
                thumbnailBytes = archiveAssets.mapNotNull { asset ->
                    val file = asset.thumbnailUri?.let(::File)
                    if (file?.isFile == true) asset.id to file.readBytes() else null
                }.toMap(),
            )
        }
        if (!includeSecrets) return snapshot
        val providerEntities = database.providerDao().observeAll().first()
        val secrets = providerEntities.mapNotNull { provider ->
            secretStore?.get(provider.apiKeyAlias)?.let { provider.id to it }
        }.toMap()
        return snapshot.copy(secrets = secrets)
    }

    override suspend fun import(input: java.io.InputStream, password: CharArray?): ImportedArchive = importer.import(input, password)

    private fun validateReferences(
        providers: List<ProviderEntity>,
        models: List<ModelProfileEntity>,
        sessions: List<SessionEntity>,
        messages: List<MessageEntity>,
        assets: List<AssetEntity>,
    ) {
        requireUnique(providers.map(ProviderEntity::id), "provider")
        requireUnique(models.map(ModelProfileEntity::id), "model")
        requireUnique(sessions.map(SessionEntity::id), "session")
        requireUnique(messages.map(MessageEntity::id), "message")
        requireUnique(assets.map(AssetEntity::id), "asset")
        val providerIds = providers.map(ProviderEntity::id).toSet()
        val modelsById = models.associateBy(ModelProfileEntity::id)
        val sessionIds = sessions.map(SessionEntity::id).toSet()
        val messageIds = messages.map(MessageEntity::id).toSet()
        require(models.all { it.providerId in providerIds }) { "Model references an unknown provider" }
        require(messages.all { it.sessionId in sessionIds }) { "Message references an unknown session" }
        require(assets.all { it.messageId == null || it.messageId in messageIds }) { "Asset references an unknown message" }
        providers.forEach { provider ->
            provider.defaultModelId?.let { defaultId ->
                val model = requireNotNull(modelsById[defaultId]) { "Default model does not exist" }
                require(model.providerId == provider.id) { "Default model belongs to another provider" }
            }
        }
    }

    private fun requireUnique(ids: List<String>, label: String) {
        require(ids.size == ids.toSet().size) { "Duplicate $label id" }
    }

    suspend fun apply(archiveData: ImportedArchive): Unit {
        validateImportedReferences(archiveData.data)
        val importRoot = root.resolve(".import-${UUID.randomUUID()}").apply { mkdirs() }
        val moved = mutableListOf<File>()
        val writtenSecretAliases = mutableListOf<String>()
        try {
            val providerIds = archiveData.data.providers.associate { it.id to UUID.randomUUID().toString() }
            val modelIds = archiveData.data.models.associate { it.id to UUID.randomUUID().toString() }
            val sessionIds = archiveData.data.sessions.associate { it.id to UUID.randomUUID().toString() }
            val messageIds = archiveData.data.messages.associate { it.id to UUID.randomUUID().toString() }
            val assetIds = archiveData.data.assets.associate { it.id to UUID.randomUUID().toString() }
            val localPlans = archiveData.data.assets.mapNotNull { asset ->
                val bytes = asset.entryName?.let(archiveData.assetBytes::get)
                if (bytes == null) null else {
                    val finalFile = root.resolve("imported/${assetIds.getValue(asset.id)}.bin").canonicalFile
                    require(finalFile.path.startsWith(root.canonicalPath + File.separator))
                    val staging = importRoot.resolve("${assetIds.getValue(asset.id)}.bin").apply { writeBytes(bytes) }
                    asset.id to (staging to finalFile)
                }
            }.toMap()
            val thumbnailPlans = archiveData.data.assets.mapNotNull { asset ->
                val bytes = asset.thumbnailEntryName?.let(archiveData.assetBytes::get)
                if (bytes == null) null else {
                    val finalFile = root.resolve("imported/${assetIds.getValue(asset.id)}-thumb.bin").canonicalFile
                    require(finalFile.path.startsWith(root.canonicalPath + File.separator))
                    val staging = importRoot.resolve("${assetIds.getValue(asset.id)}-thumb.bin").apply { writeBytes(bytes) }
                    asset.id to (staging to finalFile)
                }
            }.toMap()
            val secrets = archiveData.data.encryptedSecrets?.let { Json.decodeFromString<Map<String, String>>(it) }.orEmpty()
            require(secrets.isEmpty() || secretStore != null) { "Secret storage is unavailable" }
            val secretsStore = secretStore
            val aliases = providerIds.mapValues { (_, newId) -> "imported-$newId" }
            database.withTransaction {
                secrets.forEach { (oldProviderId, value) ->
                    val alias = requireNotNull(aliases[oldProviderId]) { "Secret references an unknown provider" }
                    writtenSecretAliases += alias
                    requireNotNull(secretsStore).put(alias, value)
                }
                database.providerDao().upsertAll(archiveData.data.providers.map { provider ->
                    ProviderEntity(providerIds.getValue(provider.id), provider.name, provider.baseUrl, aliases.getValue(provider.id), provider.enabled, null, provider.createdAt, provider.updatedAt)
                })
                database.modelProfileDao().upsertAll(archiveData.data.models.map { model ->
                    ModelProfileEntity(modelIds.getValue(model.id), providerIds.getValue(model.providerId), model.modelName, model.displayName, model.supportsGeneration, model.supportsEdit, model.supportsMask, model.supportsMultipleImages, model.supportedSizes, model.supportedQualities, model.enabled)
                })
                archiveData.data.providers.forEach { provider ->
                    database.providerDao().updateDefaultModel(
                        providerIds.getValue(provider.id),
                        provider.defaultModelId?.let(modelIds::get),
                        provider.updatedAt,
                    )
                }
                database.sessionDao().upsertAll(archiveData.data.sessions.map { session ->
                    SessionEntity(sessionIds.getValue(session.id), session.title, session.createdAt, session.updatedAt, session.deletedAt)
                })
                database.messageDao().upsertAll(archiveData.data.messages.map { message ->
                    MessageEntity(messageIds.getValue(message.id), sessionIds.getValue(message.sessionId), MessageRole.valueOf(message.role), message.text, TaskState.valueOf(message.taskState), message.requestSnapshotJson, message.errorJson, message.createdAt, message.updatedAt)
                })
                (localPlans.values + thumbnailPlans.values).forEach { (staging, finalFile) ->
                    finalFile.parentFile?.mkdirs()
                    Files.move(staging.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    moved += finalFile
                }
                database.assetDao().upsertAll(archiveData.data.assets.map { asset ->
                    val localFile = localPlans[asset.id]?.second
                    val thumbnailFile = thumbnailPlans[asset.id]?.second
                    AssetEntity(
                        assetIds.getValue(asset.id),
                        asset.messageId?.let(messageIds::get),
                        localFile?.path ?: asset.localUri,
                        when {
                            thumbnailFile != null -> thumbnailFile.path
                            localFile != null -> null
                            else -> asset.thumbnailUri
                        },
                        asset.mediaType,
                        asset.width,
                        asset.height,
                        asset.byteSize,
                        AssetSource.valueOf(asset.source),
                        asset.createdAt,
                        asset.available && localFile != null,
                    )
                })
            }
            importRoot.deleteRecursively()
        } catch (error: Throwable) {
            moved.forEach { it.delete() }
            writtenSecretAliases.forEach { alias ->
                runCatching { secretStore?.remove(alias) }
                    .onFailure { cleanupError ->
                        if (cleanupError !== error) error.addSuppressed(cleanupError)
                    }
            }
            importRoot.deleteRecursively()
            throw error
        }
    }

    private fun validateImportedReferences(data: ArchiveData) {
        requireUnique(data.providers.map(ArchiveProvider::id), "provider")
        requireUnique(data.models.map(ArchiveModel::id), "model")
        requireUnique(data.sessions.map(ArchiveSession::id), "session")
        requireUnique(data.messages.map(ArchiveMessage::id), "message")
        requireUnique(data.assets.map(ArchiveAsset::id), "asset")
        val providerIds = data.providers.map(ArchiveProvider::id).toSet()
        val models = data.models.associateBy(ArchiveModel::id)
        val sessionIds = data.sessions.map(ArchiveSession::id).toSet()
        val messageIds = data.messages.map(ArchiveMessage::id).toSet()
        require(data.models.all { it.providerId in providerIds }) { "Unknown provider reference" }
        require(data.messages.all { it.sessionId in sessionIds }) { "Unknown session reference" }
        require(data.assets.all { it.messageId == null || it.messageId in messageIds }) { "Unknown message reference" }
        data.providers.forEach { provider ->
            provider.defaultModelId?.let { id ->
                val model = requireNotNull(models[id]) { "Unknown default model" }
                require(model.providerId == provider.id) { "Default model mismatch" }
            }
        }
    }
}
