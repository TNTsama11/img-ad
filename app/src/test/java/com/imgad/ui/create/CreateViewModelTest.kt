package com.imgad.ui.create

import com.imgad.domain.model.Asset
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.GenerationRequest
import com.imgad.domain.model.GenerationResult
import com.imgad.domain.model.Message
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.ModelProfile
import com.imgad.domain.model.Provider
import com.imgad.domain.port.SessionContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun stateEventsUpdateImmutableDraft() {
        val fixture = fixture()
        val asset = Asset("a", localUri = "/a", mediaType = "image/png", source = AssetSource.INPUT)

        fixture.viewModel.selectProvider("provider")
        fixture.viewModel.selectModel("model")
        fixture.viewModel.updatePrompt("prompt")
        fixture.viewModel.updateParameters("512x512", "high", "webp", 2, "{\"style\":\"vivid\"}")
        fixture.viewModel.addAsset(asset)
        fixture.viewModel.setMask(asset.copy(id = "mask", source = AssetSource.MASK))

        val state = fixture.viewModel.uiState.value
        assertEquals("prompt", state.prompt)
        assertEquals(2, state.count)
        assertEquals(listOf(asset), state.inputAssets)
        assertEquals("mask", state.maskAsset?.id)
    }

    @Test
    fun submitValidatesRoutesAndPreventsDuplicates() = runTest(dispatcher) {
        val fixture = fixture(blockGenerate = true)
        fixture.selectValidDraft()

        fixture.viewModel.submit()
        fixture.viewModel.submit()
        runCurrent()

        assertEquals(1, fixture.generateCalls)
        assertTrue(fixture.viewModel.uiState.value.isRunning)
        fixture.generateGate.complete(GenerationResult(emptyList()))
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.isRunning)
    }

    @Test
    fun invalidDraftDoesNotCallActionsAndEditRoutesWithAttachment() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.submit()
        runCurrent()
        assertEquals(0, fixture.generateCalls)
        assertTrue(fixture.viewModel.uiState.value.errorMessage != null)

        fixture.selectValidDraft()
        fixture.viewModel.addAsset(Asset("a", localUri = "/a", mediaType = "image/png", source = AssetSource.INPUT))
        fixture.viewModel.submit()
        advanceUntilIdle()
        assertEquals(1, fixture.editCalls)
    }

    @Test
    fun cancellationShowsNoErrorAndFailurePreservesDraft() = runTest(dispatcher) {
        val fixture = fixture(blockGenerate = true)
        fixture.selectValidDraft()
        fixture.viewModel.submit()
        runCurrent()
        fixture.viewModel.cancel()
        advanceUntilIdle()
        assertNull(fixture.viewModel.uiState.value.errorMessage)
        assertEquals("prompt", fixture.viewModel.uiState.value.prompt)

        val failed = fixture(failure = IllegalStateException("network failed"))
        failed.selectValidDraft()
        failed.viewModel.submit()
        advanceUntilIdle()
        assertEquals("network failed", failed.viewModel.uiState.value.errorMessage)
        assertEquals("prompt", failed.viewModel.uiState.value.prompt)
    }

    @Test
    fun successClearsDraftRetryRunsAndLoadSessionSwitchesCollector() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.selectValidDraft()
        fixture.viewModel.submit()
        advanceUntilIdle()
        assertEquals("", fixture.viewModel.uiState.value.prompt)
        assertTrue(fixture.viewModel.uiState.value.inputAssets.isEmpty())

        fixture.viewModel.retry("message")
        advanceUntilIdle()
        assertEquals(listOf("message"), fixture.retries)

        fixture.viewModel.loadSession("first", "First")
        runCurrent()
        fixture.firstMessages.value = SessionContent(listOf(Message("one", "first", MessageRole.USER, "one")), emptyMap())
        runCurrent()
        fixture.viewModel.loadSession("second", "Second")
        runCurrent()
        fixture.firstMessages.value = SessionContent(listOf(Message("stale", "first", MessageRole.USER, "stale")), emptyMap())
        fixture.secondMessages.value = SessionContent(listOf(Message("two", "second", MessageRole.USER, "two")), emptyMap())
        runCurrent()
        assertEquals(listOf("two"), fixture.viewModel.uiState.value.messages.map(Message::id))
    }

    @Test
    fun sessionContentPublishesOutputAssetsForPreview() = runTest(dispatcher) {
        val fixture = fixture()
        val message = Message("assistant", "first", MessageRole.ASSISTANT, "")
        val output = Asset(
            id = "output",
            messageId = message.id,
            localUri = "/output.png",
            mediaType = "image/png",
            source = AssetSource.OUTPUT,
        )

        fixture.viewModel.loadSession("first", "First")
        runCurrent()
        fixture.firstMessages.value = SessionContent(
            messages = listOf(message),
            assetsByMessage = mapOf(message.id to listOf(output)),
        )
        runCurrent()

        assertEquals(listOf(output), fixture.viewModel.uiState.value.messageAssets[message.id])
    }

    @Test
    fun submittingNewSessionImmediatelyObservesItsPersistedOutput() = runTest(dispatcher) {
        val fixture = fixture(blockGenerate = true)
        fixture.selectValidDraft()

        fixture.viewModel.submit()
        runCurrent()
        val sessionId = fixture.viewModel.uiState.value.currentSessionId!!
        val message = Message("assistant", sessionId, MessageRole.ASSISTANT, "")
        val output = Asset(
            id = "output",
            messageId = message.id,
            localUri = "/output.png",
            mediaType = "image/png",
            source = AssetSource.OUTPUT,
        )
        fixture.secondMessages.value = SessionContent(
            messages = listOf(message),
            assetsByMessage = mapOf(message.id to listOf(output)),
        )
        runCurrent()

        assertEquals(listOf(message), fixture.viewModel.uiState.value.messages)
        assertEquals(listOf(output), fixture.viewModel.uiState.value.messageAssets[message.id])
        fixture.generateGate.complete(GenerationResult(emptyList()))
        advanceUntilIdle()
    }

    @Test
    fun catalogSelectsPersistedProviderAndProviderDefaultModel() {
        val fixture = fixture()
        val provider = Provider(
            "default-provider",
            "Default",
            "https://example.com",
            "alias",
            defaultModelId = "default-model",
        )
        val model = ModelProfile(
            "default-model",
            provider.id,
            "model-x",
            "Model",
            true,
            true,
            false,
            false,
            setOf("1024x1024"),
            setOf("standard"),
        )

        fixture.viewModel.updatePrompt("draft")
        fixture.viewModel.updateCatalog(listOf(provider), listOf(model), provider.id)

        assertEquals(provider.id, fixture.viewModel.uiState.value.selectedProviderId)
        assertEquals(model.id, fixture.viewModel.uiState.value.selectedModelId)
        assertEquals("1024x1024", fixture.viewModel.uiState.value.size)
        assertEquals("standard", fixture.viewModel.uiState.value.quality)
        assertEquals("draft", fixture.viewModel.uiState.value.prompt)
    }

    @Test
    fun selectingModelAppliesItsParameterDefaults() {
        val fixture = fixture()

        fixture.viewModel.selectProvider("provider")
        fixture.viewModel.selectModel("model")

        assertEquals("512x512", fixture.viewModel.uiState.value.size)
        assertEquals("high", fixture.viewModel.uiState.value.quality)
    }

    @Test
    fun resetToNewSessionClearsSessionAndDraftState() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.loadSession("first", "Old")
        fixture.viewModel.updatePrompt("draft")
        fixture.viewModel.addAsset(Asset("input", localUri = "/input", mediaType = "image/png", source = AssetSource.INPUT))
        fixture.viewModel.setMask(Asset("mask", localUri = "/mask", mediaType = "image/png", source = AssetSource.MASK))
        advanceUntilIdle()

        fixture.viewModel.resetToNewSession()

        val state = fixture.viewModel.uiState.value
        assertNull(state.currentSessionId)
        assertEquals("", state.title)
        assertEquals("", state.prompt)
        assertTrue(state.messages.isEmpty())
        assertTrue(state.inputAssets.isEmpty())
        assertNull(state.maskAsset)
    }

    @Test
    fun sessionContentRestoresPersistedTitle() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.loadSession("first")
        runCurrent()
        fixture.firstMessages.value = SessionContent(emptyList(), emptyMap(), title = "Persisted title")
        runCurrent()

        assertEquals("Persisted title", fixture.viewModel.uiState.value.title)
    }

    @Test
    fun unknownProviderAndCrossProviderModelNeverSubmit() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.selectProvider("unknown")
        fixture.viewModel.selectModel("model")
        fixture.viewModel.updatePrompt("prompt")
        fixture.viewModel.submit()
        runCurrent()
        assertEquals(0, fixture.generateCalls)

        fixture.viewModel.selectProvider("provider")
        fixture.viewModel.selectModel("foreign-model")
        fixture.viewModel.submit()
        runCurrent()
        assertEquals(0, fixture.generateCalls)
        assertNull(fixture.viewModel.uiState.value.selectedModelId)
    }

    @Test
    fun immediateCancelAlwaysFinishesRunningState() = runTest(dispatcher) {
        val fixture = fixture(blockGenerate = true)
        fixture.selectValidDraft()

        fixture.viewModel.submit()
        fixture.viewModel.cancel()
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.isRunning)
        assertNull(fixture.viewModel.uiState.value.currentTaskId)
        assertNull(fixture.viewModel.uiState.value.errorMessage)
    }

    @Test
    fun switchingSessionWhileRunningDoesNotClearNewSessionDraft() = runTest(dispatcher) {
        val fixture = fixture(blockGenerate = true)
        fixture.selectValidDraft()
        fixture.viewModel.submit()

        fixture.viewModel.loadSession("second", "Second")
        fixture.viewModel.updatePrompt("new-session-draft")
        fixture.generateGate.complete(GenerationResult(emptyList()))
        advanceUntilIdle()

        assertEquals("second", fixture.viewModel.uiState.value.currentSessionId)
        assertEquals("new-session-draft", fixture.viewModel.uiState.value.prompt)
        assertFalse(fixture.viewModel.uiState.value.isRunning)
    }

    @Test
    fun retrySuccessDoesNotClearCurrentDraft() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.loadSession("session", "Session")
        runCurrent()
        fixture.viewModel.updatePrompt("new draft")

        fixture.viewModel.retry("old-message")
        advanceUntilIdle()

        assertEquals("new draft", fixture.viewModel.uiState.value.prompt)
        assertEquals(listOf("old-message"), fixture.retries)
    }

    private fun fixture(blockGenerate: Boolean = false, failure: Throwable? = null): Fixture {
        val providers = listOf(
            Provider("provider", "Provider", "https://example.com", "alias"),
            Provider("foreign", "Foreign", "https://foreign.example.com", "foreign-alias"),
        )
        val models = listOf(
            ModelProfile("model", "provider", "model-x", "Model", true, true, true, true, setOf("512x512"), setOf("high")),
            ModelProfile("foreign-model", "foreign", "foreign-x", "Foreign", true, true, false, false, setOf("512x512"), setOf("high")),
        )
        val gate = CompletableDeferred<GenerationResult>()
        val first = MutableStateFlow(SessionContent(emptyList(), emptyMap()))
        val second = MutableStateFlow(SessionContent(emptyList(), emptyMap()))
        val fixture = Fixture(gate, first, second)
        fixture.viewModel = CreateViewModel(
            providers,
            models,
            GenerateAction { _, _ ->
                fixture.generateCalls++
                failure?.let { throw it }
                if (blockGenerate) gate.await() else GenerationResult(emptyList())
            },
            EditAction { _, _ -> fixture.editCalls++; GenerationResult(emptyList()) },
            RetryAction { id -> fixture.retries += id; GenerationResult(emptyList()) },
            SessionMessages { id -> if (id == "first") first else second },
        )
        return fixture
    }

    private class Fixture(
        val generateGate: CompletableDeferred<GenerationResult>,
        val firstMessages: MutableStateFlow<SessionContent>,
        val secondMessages: MutableStateFlow<SessionContent>,
    ) {
        lateinit var viewModel: CreateViewModel
        var generateCalls = 0
        var editCalls = 0
        val retries = mutableListOf<String>()

        fun selectValidDraft() {
            viewModel.selectProvider("provider")
            viewModel.selectModel("model")
            viewModel.updatePrompt("prompt")
            viewModel.updateParameters("512x512", "high", "png", 1, null)
        }
    }
}
