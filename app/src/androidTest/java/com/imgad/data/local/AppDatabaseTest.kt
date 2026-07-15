package com.imgad.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.MessageEntity
import com.imgad.data.local.entity.ModelProfileEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun activeSessionsAreOrderedByMostRecentlyUpdated() = runBlocking {
        database.sessionDao().upsert(session("older", updatedAt = 10L))
        database.sessionDao().upsert(session("newer", updatedAt = 20L))

        assertEquals(
            listOf("newer", "older"),
            database.sessionDao().observeActive().first().map(SessionEntity::id),
        )
    }

    @Test
    fun searchTreatsLikeMetacharactersLiterallyAndHidesDeletedSessions() = runBlocking {
        database.sessionDao().upsert(session("percent", title = "100% complete"))
        database.sessionDao().upsert(session("plain", title = "100x complete"))
        database.sessionDao().upsert(session("deleted", title = "100% deleted", deletedAt = 30L))

        assertEquals(
            listOf("percent"),
            database.sessionDao().searchActiveByTitle("%").first().map(SessionEntity::id),
        )
    }

    @Test
    fun deletingSessionCascadesMessagesAndAssets() = runBlocking {
        database.sessionDao().upsert(session("session"))
        database.messageDao().upsert(
            MessageEntity(
                id = "message",
                sessionId = "session",
                role = MessageRole.USER,
                text = "prompt",
                taskState = TaskState.PENDING,
                requestSnapshotJson = null,
                errorJson = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.assetDao().upsert(
            AssetEntity(
                id = "asset",
                messageId = "message",
                localUri = "content://asset",
                thumbnailUri = null,
                mediaType = "image/png",
                width = 1,
                height = 1,
                byteSize = 1L,
                source = AssetSource.OUTPUT,
                createdAt = 1L,
            ),
        )

        database.sessionDao().deleteById("session")

        assertEquals(emptyList<MessageEntity>(), database.messageDao().observeBySession("session").first())
        assertNull(database.assetDao().getById("asset"))
    }

    @Test
    fun softDeletedSessionIsHiddenFromActiveQuery() = runBlocking {
        database.sessionDao().upsert(session("session"))

        database.sessionDao().softDelete("session", deletedAt = 50L, updatedAt = 50L)

        assertEquals(emptyList<SessionEntity>(), database.sessionDao().observeActive().first())
    }

    @Test
    fun deletingProviderCascadesModelProfiles() = runBlocking {
        database.providerDao().upsert(provider("provider"))
        database.modelProfileDao().upsert(model("model", "provider"))

        database.providerDao().deleteById("provider")

        assertEquals(emptyList<ModelProfileEntity>(), database.modelProfileDao().observeByProvider("provider").first())
    }

    @Test
    fun defaultModelCanBeUpdatedAndRead() = runBlocking {
        database.providerDao().upsert(provider("provider"))
        database.modelProfileDao().upsert(model("first", "provider"))
        database.modelProfileDao().upsert(model("second", "provider"))

        database.providerDao().updateDefaultModel("provider", "second", updatedAt = 20L)

        assertEquals("second", database.modelProfileDao().getDefaultForProvider("provider")?.id)
    }

    @Test
    fun deletingDefaultModelClearsProviderReference() = runBlocking {
        database.providerDao().upsert(provider("provider"))
        database.modelProfileDao().upsert(model("model", "provider"))
        database.providerDao().updateDefaultModel("provider", "model", updatedAt = 20L)

        database.modelProfileDao().deleteById("model")

        assertNull(database.providerDao().getById("provider")?.defaultModelId)
        assertNull(database.modelProfileDao().getDefaultForProvider("provider"))
    }

    private fun session(
        id: String,
        title: String = id,
        updatedAt: Long = 1L,
        deletedAt: Long? = null,
    ) = SessionEntity(id, title, createdAt = 1L, updatedAt = updatedAt, deletedAt = deletedAt)

    private fun provider(id: String) = ProviderEntity(
        id = id,
        name = id,
        baseUrl = "https://example.com",
        apiKeyAlias = "internal-alias",
        enabled = true,
        defaultModelId = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun model(id: String, providerId: String) = ModelProfileEntity(
        id = id,
        providerId = providerId,
        modelName = id,
        displayName = id,
        supportsGeneration = true,
        supportsEdit = false,
        supportsMask = false,
        supportsMultipleImages = false,
        supportedSizes = setOf("1024x1024"),
        supportedQualities = setOf("standard"),
        enabled = true,
    )
}
