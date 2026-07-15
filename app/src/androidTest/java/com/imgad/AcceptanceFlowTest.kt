package com.imgad

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imgad.data.local.AppDatabase
import com.imgad.data.local.AssetFileStore
import com.imgad.data.local.AssetShareManager
import com.imgad.data.local.MediaStoreGateway
import com.imgad.data.local.MediaStoreSaver
import com.imgad.data.local.RoomArchiveStore
import com.imgad.data.local.SecretStore
import com.imgad.data.local.PrivateAssetStorage
import com.imgad.data.remote.DynamicImageGateway
import com.imgad.data.remote.OpenAiImageService
import com.imgad.data.remote.RemoteUrlPolicy
import com.imgad.data.remote.RootedUploadAssetReader
import com.imgad.data.repository.GenerationRepository
import com.imgad.data.repository.ProviderRepository
import com.imgad.data.repository.SessionRepository
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.model.TaskState
import com.imgad.domain.port.DefaultProviderStore
import com.imgad.domain.usecase.EditImage
import com.imgad.domain.usecase.GenerateImage
import com.imgad.domain.usecase.RetryGeneration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcceptanceFlowTest {
    private lateinit var database: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var root: File
    private lateinit var providerRepository: ProviderRepository
    private lateinit var generationRepository: GenerationRepository
    private lateinit var generateImage: GenerateImage
    private lateinit var editImage: EditImage
    private lateinit var retryGeneration: RetryGeneration
    private lateinit var sessionRepository: SessionRepository
    private val pngBytes = Base64.getDecoder().decode(PNG_BASE64)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().also { it.start() }
        root = context.cacheDir.resolve("acceptance-${System.nanoTime()}").apply { mkdirs() }
        providerRepository = ProviderRepository(
            database.providerDao(),
            database.modelProfileDao(),
            MemorySecretStore(),
            MemoryDefaultProviderStore(),
        )
        val provider = Provider("provider", "Mock", server.url("/v1/").toString(), "provider-key")
        runBlocking {
            providerRepository.saveProvider(provider, "test-key")
            providerRepository.saveModel(
                ModelProfile("model", provider.id, "image-model", "Image model", true, true, false, true),
            )
        }
        val fileStore = AssetFileStore(
            root,
            inputStreamProvider = { FileInputStream(it) },
            mediaTypeProvider = { "image/png" },
        )
        val gateway = DynamicImageGateway(providerRepository, root) { baseUrl, apiKey, assetRoot ->
            OpenAiImageService(
                baseUrl = baseUrl,
                apiKey = apiKey,
                client = OkHttpClient(),
                urlPolicy = RemoteUrlPolicy(allowInsecureHttp = true, allowLoopback = true),
                uploadAssetReader = RootedUploadAssetReader(assetRoot),
            )
        }
        generationRepository = GenerationRepository(database)
        generateImage = GenerateImage(gateway, generationRepository, fileStore)
        editImage = EditImage(gateway, generationRepository, fileStore)
        retryGeneration = RetryGeneration(generationRepository, generateImage, editImage)
        sessionRepository = SessionRepository(database.sessionDao(), database.assetDao(), fileStore)
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        root.deleteRecursively()
    }

    @Test
    fun providerModelGenerateHistoryEditRetryArchiveAndMediaBoundaries() = runBlocking {
        server.enqueue(response())
        val request = request()
        val generated = generateImage("session", request)
        assertEquals(pngBytes.toList(), generated.images.single().bytes.toList())
        val generatedContent = generationRepository.observeSessionContent("session").first()
        val output = generatedContent.assetsByMessage.values.flatten().single { it.source == AssetSource.OUTPUT }
        assertTrue(sessionRepository.observeActive().first().any { it.id == "session" })

        server.enqueue(response())
        editImage("session", request.snapshotCopy(inputAssets = listOf(output)))

        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":{\"message\":\"temporary\"}}"))
        runCatching { generateImage("session", request) }
        val failed = generationRepository.messagesForSession("session").last { it.taskState == TaskState.FAILED }
        server.enqueue(response())
        retryGeneration(failed.id)
        assertTrue(generationRepository.messagesForSession("session").any { it.taskState == TaskState.SUCCEEDED })

        val archiveStore = RoomArchiveStore(database, root)
        val snapshot = archiveStore.snapshot()
        val archive = ByteArrayOutputStream().also { archiveStore.export(snapshot, it) }
        assertNotNull(archiveStore.import(ByteArrayInputStream(archive.toByteArray())))
        val usage = PrivateAssetStorage(
            root = root,
            referencedPaths = {
                database.assetDao().getAllLocalUris()
                    .flatMap { row -> listOfNotNull(row.localUri, row.thumbnailUri) }
                    .toSet()
            },
        )
        assertTrue(usage.readUsage().files > 0)

        val media = RecordingMediaStore()
        assertNotNull(MediaStoreSaver(media).save(pngBytes, "image/png"))
        assertEquals(0, media.lastUpdate?.getAsInteger(android.provider.MediaStore.Images.Media.IS_PENDING))

        val shareFile = File(ApplicationProvider.getApplicationContext<Context>().filesDir, "imgad-assets/acceptance-share.png")
            .apply { parentFile?.mkdirs(); writeBytes(pngBytes) }
        val shareIntent = AssetShareManager(ApplicationProvider.getApplicationContext()).createShareIntent(
            Asset("share", localUri = shareFile.path, mediaType = "image/png", source = AssetSource.OUTPUT),
        )
        assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
        assertEquals("image/png", shareIntent.type)
        shareFile.delete()
        Unit
    }

    private fun request() = GenerationRequest(
        providerId = "provider",
        model = "image-model",
        prompt = "a test image",
        size = "1024x1024",
        quality = "standard",
        outputFormat = "png",
        count = 1,
    )

    private fun response() = MockResponse()
        .setResponseCode(200)
        .setBody("{\"data\":[{\"b64_json\":\"$PNG_BASE64\"}]}")

    private class MemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override fun put(alias: String, value: String) { values[alias] = value }
        override fun get(alias: String): String? = values[alias]
        override fun remove(alias: String) { values.remove(alias) }
    }

    private class MemoryDefaultProviderStore : DefaultProviderStore {
        private var value: String? = null
        override fun get(): String? = value
        override fun set(providerId: String?) { value = providerId }
    }

    private class RecordingMediaStore : MediaStoreGateway {
        private val uri = Uri.parse("content://acceptance/image")
        private val output = ByteArrayOutputStream()
        var lastUpdate: ContentValues? = null

        override fun insert(values: ContentValues): Uri = uri
        override fun openOutputStream(uri: Uri): OutputStream = output
        override fun update(uri: Uri, values: ContentValues): Int {
            lastUpdate = values
            return 1
        }
        override fun delete(uri: Uri): Int = 1
    }

    private companion object {
        const val PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
