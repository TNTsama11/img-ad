# Model Discovery and Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make model selection explicit on the create screen and support fetching, selecting, and importing OpenAI-compatible provider models.

**Architecture:** Add a focused `FetchProviderModels` domain port with an OkHttp implementation, then expose discovery/import state through `SettingsViewModel`. Keep transient form and selection UI in Compose while persisting providers and model profiles through the existing settings store.

**Tech Stack:** Kotlin 2.x, Jetpack Compose Material 3, coroutines/StateFlow, OkHttp, kotlinx.serialization, JUnit, MockWebServer, Compose UI tests.

---

### Task 1: OpenAI-Compatible Model Catalog

**Files:**
- Create: `app/src/main/java/com/imgad/domain/port/ProviderModelCatalog.kt`
- Create: `app/src/main/java/com/imgad/data/remote/OpenAiProviderModelCatalog.kt`
- Create: `app/src/test/java/com/imgad/data/remote/OpenAiProviderModelCatalogTest.kt`

- [ ] **Step 1: Write failing catalog tests**

Cover `GET /v1/models`, `Authorization: Bearer ...`, ordered de-duplication of nonblank `data[].id`, existing-key fallback, HTTP status errors, malformed JSON, and empty data.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.imgad.data.remote.OpenAiProviderModelCatalogTest`

Expected: compilation fails because `OpenAiProviderModelCatalog` and `FetchProviderModels` do not exist.

- [ ] **Step 3: Implement the minimal port and catalog**

Use this contract:

```kotlin
fun interface FetchProviderModels {
    suspend fun fetch(provider: Provider, apiKeyOverride: String?): List<String>
}
```

The implementation must normalize the base URL through `RemoteUrlPolicy`, append `models`, resolve the override or repository key, use the shared safe OkHttp pattern, and parse only `data[].id` with kotlinx.serialization.

- [ ] **Step 4: Verify GREEN**

Run the focused test command and expect all catalog tests to pass.

### Task 2: Discovery and Import State

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/imgad/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

Add tests proving fetching is explicit, loading/error state is stable, query filtering is case-insensitive, candidates can be toggled, and save/import writes the provider before deterministic model profiles.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.imgad.ui.settings.SettingsViewModelTest`

Expected: compilation fails for the new state and actions.

- [ ] **Step 3: Implement minimal state and actions**

Add these state concepts without storing secrets in state: `discoveredModelIds`, `selectedDiscoveredModelIds`, `modelSearchQuery`, `isFetchingModels`, and `modelFetchError`. Add `fetchModels(provider, apiKey)`, `toggleDiscoveredModel(id)`, `updateModelSearchQuery(query)`, `clearDiscoveredModels()`, and `saveProviderWithModels(provider, apiKey, ids, onSaved)`.

Build imported profiles with `UUID.nameUUIDFromBytes("${provider.id}:$modelId".toByteArray())`, display/model name equal to the remote ID, `supportsGeneration = true`, and other capabilities disabled.

- [ ] **Step 4: Verify GREEN and refactor**

Run the focused tests, then remove duplicated save/error update logic while keeping the suite green.

### Task 3: Explicit Create-Screen Model Picker

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/create/CreateScreen.kt`
- Modify: `app/src/androidTest/java/com/imgad/ui/create/CreateScreenTest.kt`

- [ ] **Step 1: Write failing Compose tests**

Verify the screen displays `当前模型`, provider plus model, no ambiguous top-bar `供应方` button, and one model click invokes both provider and model callbacks.

- [ ] **Step 2: Verify RED on the emulator**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.create.CreateScreenTest`

Expected: new assertions fail against the old top-bar menus.

- [ ] **Step 3: Implement the picker**

Replace both `SelectionMenu` actions with a full-width model selector below `TopAppBar`. Open a `ModalBottomSheet`, group enabled models by provider, show radio selection, and invoke `onSelectProvider(providerId)` immediately before `onSelectModel(modelId)`.

- [ ] **Step 4: Verify GREEN**

Run the focused connected test and expect all `CreateScreenTest` cases to pass.

### Task 4: Provider Editor Discovery UI and App Wiring

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/settings/ProviderEditorScreen.kt`
- Modify: `app/src/main/java/com/imgad/App.kt`
- Modify: `app/src/androidTest/java/com/imgad/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Write failing Compose tests**

Verify `获取模型` is visible, click forwards current form credentials, loading disables repeat fetch, search filters candidates, checkbox clicks update selection, fetch errors display, and save forwards selected model IDs.

- [ ] **Step 2: Verify RED on the emulator**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.settings.SettingsScreenTest`

Expected: compilation or assertions fail because discovery UI parameters do not exist.

- [ ] **Step 3: Implement the editor and dependency wiring**

Create `OpenAiProviderModelCatalog` in `App`, inject it into `SettingsViewModel`, pass discovery state/actions through navigation, render search plus checkbox rows, clear stale results when URL/key changes, and call `saveProviderWithModels` from the primary action.

- [ ] **Step 4: Verify GREEN**

Run both focused JVM and connected Compose test classes and expect zero failures.

### Task 5: Full Verification and Emulator Handoff

**Files:**
- Modify only if a verification failure identifies an in-scope defect.

- [ ] **Step 1: Run all JVM tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: zero failures.

- [ ] **Step 2: Run all Android tests**

Run: `./gradlew :app:connectedDebugAndroidTest`

Expected: zero failures on `emulator-5554`.

- [ ] **Step 3: Run static and build checks**

Run: `./gradlew :app:lintDebug :app:assembleDebug`

Expected: build succeeds with no lint errors.

- [ ] **Step 4: Install and launch**

Run: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` and `adb -s emulator-5554 shell am force-stop com.imgad && adb -s emulator-5554 shell monkey -p com.imgad 1`.

Expected: install succeeds and `com.imgad/.MainActivity` is the resumed activity. No commit, push, branch, or worktree operation is part of this plan.
