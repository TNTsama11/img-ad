package com.imgad.domain

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GeneratedImage
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.model.Message
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import com.imgad.domain.port.FileStore
import com.imgad.domain.port.GenerationStore
import com.imgad.domain.port.GenerationTask
import com.imgad.domain.port.ImageGenerationGateway
import com.imgad.domain.port.SessionContent
import com.imgad.domain.port.StoredAssetFile
import com.imgad.domain.usecase.EditImage
import com.imgad.domain.usecase.GenerateImage
import com.imgad.domain.usecase.RecoverInterruptedTasks
import com.imgad.domain.usecase.RetryGeneration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationUseCaseTest {
    @Test
    fun generateRoutesToGenerateAndCompletesWithOutputAsset() = runBlocking {
        val gateway = FakeGateway()
        val store = FakeStore()
        val useCase = GenerateImage(gateway, store, FakeFileStore())

        useCase("session", request())

        assertEquals(1, gateway.generateCalls)
        assertEquals(0, gateway.editCalls)
        assertEquals(TaskState.SUCCEEDED, store.messages.values.single { it.role == MessageRole.USER }.taskState)
        assertEquals(1, store.messages.values.count { it.role == MessageRole.ASSISTANT })
        assertEquals(1, store.savedAssets.size)
    }

    @Test
    fun editRoutesToEditAndPreservesInputAssets() = runBlocking {
        val gateway = FakeGateway()
        val store = FakeStore()
        val input = Asset("input", localUri = "/tmp/input", mediaType = "image/png", source = AssetSource.INPUT)

        EditImage(gateway, store, FakeFileStore())("session", request(inputAssets = listOf(input)))

        assertEquals(0, gateway.generateCalls)
        assertEquals(1, gateway.editCalls)
        assertEquals(listOf(input.id), store.startedInputs.map(Asset::id))
    }

    @Test
    fun gatewayFailureMarksTaskFailedAndKeepsUserMessage() = runBlocking {
        val gateway = FakeGateway(failure = IllegalStateException("failed"))
        val store = FakeStore()

        var thrown: Throwable? = null
        try {
            GenerateImage(gateway, store, FakeFileStore())("session", request())
        } catch (error: Throwable) {
            thrown = error
        }

        assertEquals("failed", thrown?.message)
        assertEquals(MessageRole.USER, store.messages.values.single().role)
        assertEquals(TaskState.FAILED, store.messages.values.single().taskState)
    }

    @Test
    fun cancellationMarksCanceledAndRethrowsCancellation() = runBlocking {
        val store = FakeStore()
        var thrown: Throwable? = null
        try {
            GenerateImage(FakeGateway(cancellation = true), store, FakeFileStore())("session", request())
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
        assertEquals(TaskState.CANCELED, store.messages.values.single().taskState)
    }

    @Test
    fun retryRebuildsSnapshotAndCreatesAnotherTask() = runBlocking {
        val gateway = FakeGateway()
        val store = FakeStore()
        val generate = GenerateImage(gateway, store, FakeFileStore())
        generate("session", request(prompt = "original"))
        val originalId = store.messages.values.single { it.role == MessageRole.USER }.id

        RetryGeneration(store, generate, EditImage(gateway, store, FakeFileStore()))(originalId)

        assertEquals(2, gateway.generateCalls)
        assertEquals(4, store.messages.size)
        assertEquals(2, store.messages.values.count { it.role == MessageRole.USER && it.text == "original" })
    }

    @Test
    fun recoverMarksEveryRunningTaskWithFixedError() = runBlocking {
        val store = FakeStore()
        store.messages["running"] = Message("running", "session", MessageRole.USER, "prompt", TaskState.RUNNING)

        RecoverInterruptedTasks(store)()

        assertEquals(TaskState.FAILED, store.messages["running"]?.taskState)
        assertTrue(store.messages["running"]?.errorJson?.contains("请求被应用中断") == true)
    }

    @Test
    fun laterOutputFailureCleansPreviouslySavedOutputsAndEncodesErrorJson() = runBlocking {
        val fileStore = FakeFileStore(failWriteAt = 2)
        val store = FakeStore()
        var thrown: Throwable? = null
        try {
            GenerateImage(FakeGateway(images = listOf(GeneratedImage(byteArrayOf(1)), GeneratedImage(byteArrayOf(2)))), store, fileStore)("session", request())
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(thrown != null)
        assertTrue(fileStore.deleted.contains("stored-1"))
        assertTrue(fileStore.deleted.contains("thumb-stored-1"))
        Json.parseToJsonElement(store.messages.values.single().errorJson!!)
        Unit
    }

    @Test
    fun markSucceededFailureCleansAllSavedOutputs() = runBlocking {
        val fileStore = FakeFileStore()
        val store = FakeStore(failSuccess = true)
        var thrown: Throwable? = null
        try {
            GenerateImage(FakeGateway(images = listOf(GeneratedImage(byteArrayOf(1)), GeneratedImage(byteArrayOf(2)))), store, fileStore)("session", request())
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(thrown != null)
        assertTrue(fileStore.deleted.contains("stored-1"))
        assertTrue(fileStore.deleted.contains("thumb-stored-1"))
        assertTrue(fileStore.deleted.contains("stored-2"))
        assertTrue(fileStore.deleted.contains("thumb-stored-2"))
    }

    @Test
    fun beginRunningAndOutputSaveOccurBeforeGatewayAndSuccess() = runBlocking {
        val events = mutableListOf<String>()
        GenerateImage(FakeGateway(events = events), FakeStore(events = events), FakeFileStore(events = events))("session", request())

        assertTrue(events.indexOf("begin:RUNNING") >= 0)
        assertTrue(events.indexOf("gateway:generate") >= 0)
        assertTrue(events.indexOf("output:save") >= 0)
        assertTrue(events.indexOf("store:succeeded") >= 0)
        assertTrue(events.indexOf("begin:RUNNING") < events.indexOf("gateway:generate"))
        assertTrue(events.indexOf("output:save") < events.indexOf("store:succeeded"))
    }

    @Test
    fun cancellationKeepsOriginalAndSuppressesMarkCanceledFailure() = runBlocking {
        val gateway = FakeGateway(cancellation = true)
        val store = FakeStore(failCancel = true)
        var thrown: Throwable? = null
        try {
            GenerateImage(gateway, store, FakeFileStore())("session", request())
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(gateway.cancellationException, thrown)
        assertTrue(thrown!!.suppressed.any { it.message == "cancel state failed" })
    }

    @Test
    fun cancellationAfterOutputsCleansSavedFiles() = runBlocking {
        val fileStore = FakeFileStore()
        val store = FakeStore(cancelOnSuccess = true)
        var thrown: Throwable? = null
        try {
            GenerateImage(FakeGateway(), store, fileStore)("session", request())
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
        assertTrue(fileStore.deleted.contains("stored-1"))
        assertTrue(fileStore.deleted.contains("thumb-stored-1"))
        assertEquals(TaskState.CANCELED, store.messages.values.single().taskState)
    }

    @Test
    fun retryEditRebuildsCompleteDefensiveSnapshotAndRoutesToEdit() = runBlocking {
        val input = Asset("input-id", "old-message", "/input", "/input-thumb", "image/png", 10, 20, 30L, AssetSource.INPUT, 40L)
        val mask = Asset("mask-id", "old-message", "/mask", "/mask-thumb", "image/png", 11, 21, 31L, AssetSource.MASK, 41L)
        val request = GenerationRequest("provider-x", "model-x", "edit prompt", "512x512", "high", "webp", 2, "{\"style\":\"vivid\"}", listOf(input), mask)
        val gateway = FakeGateway()
        val store = FakeStore()
        val edit = EditImage(gateway, store, FakeFileStore())
        edit("session", request)
        val originalMessage = store.messages.values.single { it.role == MessageRole.USER }
        val snapshotBefore = originalMessage.requestSnapshotJson

        RetryGeneration(store, GenerateImage(gateway, store, FakeFileStore()), edit)(originalMessage.id)

        val retried = gateway.requests.last()
        assertEquals(0, gateway.generateCalls)
        assertEquals(2, gateway.editCalls)
        assertEquals("provider-x", retried.providerId)
        assertEquals("model-x", retried.model)
        assertEquals("edit prompt", retried.prompt)
        assertEquals("512x512", retried.size)
        assertEquals("high", retried.quality)
        assertEquals("webp", retried.outputFormat)
        assertEquals(2, retried.count)
        assertEquals("{\"style\":\"vivid\"}", retried.advancedJson)
        assertEquals(input, retried.inputAssets.single())
        assertEquals(mask, retried.maskAsset)
        assertNotSame(request.inputAssets, retried.inputAssets)
        assertNotSame(input, retried.inputAssets.single())
        assertEquals(snapshotBefore, store.messages[originalMessage.id]?.requestSnapshotJson)
    }

    private fun request(
        prompt: String = "draw",
        inputAssets: List<Asset> = emptyList(),
    ) = GenerationRequest("provider", "model", prompt, "1024x1024", "standard", "png", 1, inputAssets = inputAssets)
}

private class FakeGateway(
    private val failure: Throwable? = null,
    private val cancellation: Boolean = false,
    private val images: List<GeneratedImage> = listOf(GeneratedImage(byteArrayOf(1), "image/png")),
    private val events: MutableList<String> = mutableListOf(),
) : ImageGenerationGateway {
    val cancellationException = CancellationException("cancelled")
    val requests = mutableListOf<GenerationRequest>()
    var generateCalls = 0
    var editCalls = 0

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        generateCalls++
        events += "gateway:generate"
        requests += request
        if (cancellation) throw cancellationException
        failure?.let { throw it }
        return GenerationResult(images, "id")
    }

    override suspend fun edit(request: GenerationRequest): GenerationResult {
        editCalls++
        events += "gateway:edit"
        requests += request
        failure?.let { throw it }
        return GenerationResult(images, "id")
    }
}

private class FakeFileStore(
    private val events: MutableList<String> = mutableListOf(),
) : FileStore {
    private var writeCount = 0
    val deleted = mutableListOf<String>()

    constructor(failWriteAt: Int?, events: MutableList<String> = mutableListOf()) : this(events) {
        this.failWriteAt = failWriteAt
    }

    private var failWriteAt: Int? = null

    override fun copyInput(uri: String, messageId: String?) = error("unused")
    override fun writeOutput(bytes: ByteArray, mediaType: String, messageId: String?): StoredAssetFile {
        events += "output:save"
        val path = "stored-${++writeCount}"
        if (writeCount == failWriteAt) throw IllegalStateException("write failed")
        return StoredAssetFile(path, mediaType, 1, 1, bytes.size.toLong())
    }
    override fun createThumbnail(uri: String, messageId: String?) = StoredAssetFile("thumb-$uri", "image/png", 1, 1, 1)
    override fun delete(path: String) { deleted += path }
    override fun deleteForMessage(messageId: String) = Unit
}

private class FakeStore(
    private val failSuccess: Boolean = false,
    private val failCancel: Boolean = false,
    private val cancelOnSuccess: Boolean = false,
    private val events: MutableList<String> = mutableListOf(),
) : GenerationStore {
    val messages = linkedMapOf<String, Message>()
    val savedAssets = mutableListOf<Asset>()
    val startedInputs = mutableListOf<Asset>()

    override suspend fun beginTask(sessionId: String, title: String, prompt: String, requestSnapshotJson: String, inputAssets: List<Asset>, now: Long): GenerationTask {
        val id = "message-${messages.size + 1}"
        messages[id] = Message(id, sessionId, MessageRole.USER, prompt, TaskState.RUNNING, requestSnapshotJson, createdAt = now, updatedAt = now)
        events += "begin:RUNNING"
        startedInputs += inputAssets
        return GenerationTask(id, sessionId)
    }

    override suspend fun markSucceeded(messageId: String, outputAssets: List<Asset>, now: Long) {
        if (cancelOnSuccess) throw CancellationException("cancel during success")
        if (failSuccess) throw IllegalStateException("state failed")
        events += "store:succeeded"
        messages[messageId] = messages.getValue(messageId).copy(taskState = TaskState.SUCCEEDED, updatedAt = now)
        val assistantId = "assistant-${messages.size + 1}"
        messages[assistantId] = Message(assistantId, messages.getValue(messageId).sessionId, MessageRole.ASSISTANT, "", TaskState.SUCCEEDED, createdAt = now, updatedAt = now)
        savedAssets += outputAssets
    }

    override suspend fun markFailed(messageId: String, errorJson: String, now: Long) {
        messages[messageId] = messages.getValue(messageId).copy(taskState = TaskState.FAILED, errorJson = errorJson, updatedAt = now)
    }

    override suspend fun markCanceled(messageId: String, now: Long) {
        if (failCancel) throw IllegalStateException("cancel state failed")
        messages[messageId] = messages.getValue(messageId).copy(taskState = TaskState.CANCELED, updatedAt = now)
    }

    override suspend fun getMessage(messageId: String): Message? = messages[messageId]

    override suspend fun messagesForSession(sessionId: String): List<Message> = messages.values.filter { it.sessionId == sessionId }

    override fun observeSessionContent(sessionId: String): Flow<SessionContent> = flowOf(
        SessionContent(messages.values.filter { it.sessionId == sessionId }, emptyMap()),
    )

    override suspend fun runningTasks(): List<Message> = messages.values.filter { it.taskState == TaskState.RUNNING }
}
