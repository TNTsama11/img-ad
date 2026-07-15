package com.imgad.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imgad.data.local.AppDatabase
import com.imgad.data.local.entity.SessionEntity
import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: GenerationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = GenerationRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun existingSessionKeepsCreatedAtAndSuccessAssociatesAssistantAssets() = runBlocking {
        database.sessionDao().upsert(SessionEntity("session", "old", createdAt = 10L, updatedAt = 10L))
        val input = Asset("caller-id", localUri = "/input", mediaType = "image/png", source = AssetSource.INPUT)

        val task = repository.beginTask("session", "new", "prompt", "{}", listOf(input), now = 20L)
        repository.markSucceeded(
            task.messageId,
            listOf(Asset("output-caller-id", localUri = "/output", mediaType = "image/png", source = AssetSource.OUTPUT)),
            now = 30L,
        )

        assertEquals(10L, database.sessionDao().getById("session")?.createdAt)
        assertEquals(20L, database.sessionDao().getById("session")?.updatedAt)
        val messages = repository.messagesForSession("session")
        assertTrue(messages.any { it.role == MessageRole.USER })
        val assistant = messages.single { it.role == MessageRole.ASSISTANT }
        val output = database.assetDao().observeByMessage(assistant.id).first().single()
        val persistedInput = database.assetDao().observeByMessage(task.messageId).first().single()
        val content = repository.observeSessionContent("session").first()
        assertNotEquals("output-caller-id", output.id)
        assertEquals(30L, output.createdAt)
        assertNotEquals("caller-id", persistedInput.id)
        assertEquals(20L, persistedInput.createdAt)
        assertEquals(messages.map { it.id }, content.messages.map { it.id })
        assertEquals(output.localUri, content.assetsByMessage.getValue(assistant.id).single().localUri)
        assertEquals(persistedInput.localUri, content.assetsByMessage.getValue(task.messageId).single().localUri)
    }

    @Test
    fun softDeletedSessionRejectsNewTask() = runBlocking {
        database.sessionDao().upsert(SessionEntity("deleted", "old", createdAt = 1L, updatedAt = 1L, deletedAt = 2L))

        var failure: Throwable? = null
        try {
            repository.beginTask("deleted", "new", "prompt", "{}", emptyList(), now = 3L)
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure != null)
        assertTrue(database.messageDao().observeBySession("deleted").first().isEmpty())
    }

    @Test
    fun assetInsertFailureRollsBackSessionMessageAndAssets() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_asset_insert BEFORE INSERT ON assets BEGIN SELECT RAISE(ABORT, 'asset failure'); END",
        )
        val input = Asset("input", localUri = "/input", mediaType = "image/png", source = AssetSource.INPUT)

        var failure: Throwable? = null
        try {
            repository.beginTask("rollback", "title", "prompt", "{}", listOf(input), now = 40L)
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure != null)
        assertNull(database.sessionDao().getById("rollback"))
        assertTrue(database.messageDao().observeBySession("rollback").first().isEmpty())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM assets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
