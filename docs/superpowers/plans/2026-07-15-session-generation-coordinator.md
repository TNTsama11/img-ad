# 会话中心与后台生成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持多个会话并发生成、会话中心运行标识、生成等待指示器、提示词复制、最近模型展示和 MIT 授权，同时保证页面切换不取消任务或丢失会话草稿。

**Architecture:** `GenerationCoordinator` 由 `App` 持有并按会话管理进程内 Job；Room 消息仍是最终任务状态来源。`CreateViewModel` 管理当前会话和按会话草稿，`HistoryViewModel` 组合持久化会话摘要与协调器运行集合，Compose 页面仅渲染状态和转发事件。

**Tech Stack:** Kotlin、Coroutines/StateFlow、Jetpack Compose Material 3、Room、JUnit、AndroidX Compose UI Test

---

### Task 1: 添加 MIT 许可证并更新双语 README

**Files:**
- Create: `LICENSE`
- Modify: `README.md`
- Modify: `README_EN.md`

- [ ] **Step 1: 写入标准 MIT 文本**

创建 `LICENSE`：

```text
MIT License

Copyright (c) 2026 TNTsama11

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: 更新 README 许可证章节**

中文 README 删除“尚未声明开源许可证”，新增：

```markdown
## 许可证

本项目使用 [MIT License](LICENSE)。
```

英文 README 删除“No open-source license has been declared yet.”，新增：

```markdown
## License

This project is licensed under the [MIT License](LICENSE).
```

- [ ] **Step 3: 验证文本和链接**

Run:

```bash
rg -n 'Copyright \(c\) 2026 TNTsama11|Permission is hereby granted' LICENSE
rg -n 'MIT License.*LICENSE' README.md README_EN.md
```

Expected: LICENSE 命中版权和授权文本，两份 README 均链接到 LICENSE。

- [ ] **Step 4: 提交授权变更**

```bash
git add LICENSE README.md README_EN.md
git commit -m "docs: add MIT license"
```

### Task 2: 让会话摘要包含最近使用模型

**Files:**
- Create: `app/src/main/java/com/imgad/data/local/dao/SessionHistoryRow.kt`
- Modify: `app/src/main/java/com/imgad/domain/model/Session.kt`
- Modify: `app/src/main/java/com/imgad/data/local/dao/SessionDao.kt`
- Modify: `app/src/main/java/com/imgad/data/repository/SessionRepository.kt`
- Modify: `app/src/androidTest/java/com/imgad/data/local/AppDatabaseTest.kt`
- Modify: `app/src/test/java/com/imgad/data/repository/SessionRepositoryTest.kt`

- [ ] **Step 1: 写 DAO 失败测试**

在 `AppDatabaseTest` 插入一个会话和三条请求消息：较早模型 `model-a`、较晚模型 `model-b`、同时间戳但更大 ID 的 `model-c`。断言新查询返回 `model-c` 对应快照：

```kotlin
val row = database.sessionDao().observeActiveHistory().first().single()
assertEquals("{\"model\":\"model-c\"}", row.latestRequestSnapshotJson)
```

- [ ] **Step 2: 运行测试确认缺少 API**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL，`observeActiveHistory` 或 `latestRequestSnapshotJson` 未定义。

- [ ] **Step 3: 添加 Room 投影与查询**

定义：

```kotlin
data class SessionHistoryRow(
    @Embedded val session: SessionEntity,
    val latestRequestSnapshotJson: String?,
)
```

在 `SessionDao` 增加 `observeActiveHistory()` 和 `searchActiveHistory(query)`。两个查询都使用相关子查询：

```sql
SELECT s.*,
       (SELECT m.requestSnapshotJson
          FROM messages m
         WHERE m.sessionId = s.id
           AND m.requestSnapshotJson IS NOT NULL
         ORDER BY m.createdAt DESC, m.id DESC
         LIMIT 1) AS latestRequestSnapshotJson
  FROM sessions s
 WHERE s.deletedAt IS NULL
 ORDER BY s.updatedAt DESC
```

搜索版本额外加入 `instr(lower(s.title), lower(:query)) > 0`。

- [ ] **Step 4: 写 Repository 失败测试**

为 `SessionRepositoryTest` 的 fake DAO 返回有效、缺失和损坏快照，断言：

```kotlin
assertEquals("gpt-image-2", sessions[0].latestModel)
assertNull(sessions[1].latestModel)
assertNull(sessions[2].latestModel)
```

- [ ] **Step 5: 映射最近模型并安全降级**

给 `Session` 增加 `val latestModel: String? = null`。`SessionRepository.observeActive` 改用历史投影查询，通过 `Json.parseToJsonElement(value).jsonObject["model"]?.jsonPrimitive?.contentOrNull` 解析，并用 `runCatching { ... }.getOrNull()?.takeIf(String::isNotBlank)` 降级。

- [ ] **Step 6: 运行数据层测试**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.data.local.AppDatabaseTest
```

Expected: JVM 测试和 AppDatabaseTest 全部通过。

- [ ] **Step 7: 提交模型摘要**

```bash
git add app/src/main/java/com/imgad/domain/model/Session.kt app/src/main/java/com/imgad/data/local/dao/SessionDao.kt app/src/main/java/com/imgad/data/local/dao/SessionHistoryRow.kt app/src/main/java/com/imgad/data/repository/SessionRepository.kt app/src/test/java/com/imgad/data/repository/SessionRepositoryTest.kt app/src/androidTest/java/com/imgad/data/local/AppDatabaseTest.kt
git commit -m "feat: record latest model in session summaries"
```

### Task 3: 建立应用级 GenerationCoordinator

**Files:**
- Create: `app/src/main/java/com/imgad/domain/usecase/GenerationCoordinator.kt`
- Create: `app/src/test/java/com/imgad/domain/usecase/GenerationCoordinatorTest.kt`

- [ ] **Step 1: 写协调器并发失败测试**

使用两个 `CompletableDeferred<Unit>` 阻塞任务，验证：

```kotlin
assertTrue(coordinator.launch("one") { first.await() })
assertTrue(coordinator.launch("two") { second.await() })
assertFalse(coordinator.launch("one") { error("duplicate") })
assertEquals(setOf("one", "two"), coordinator.runningSessionIds.value)
```

再分别验证 `cancel("one")` 不影响 `two`，异常任务不取消其他任务，完成后运行集合为空。

- [ ] **Step 2: 运行测试确认类型不存在**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.domain.usecase.GenerationCoordinatorTest'
```

Expected: FAIL，`GenerationCoordinator` 未定义。

- [ ] **Step 3: 实现协调器**

实现公开契约：

```kotlin
class GenerationCoordinator(private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _runningSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val runningSessionIds: StateFlow<Set<String>> = _runningSessionIds.asStateFlow()

    fun launch(sessionId: String, task: suspend () -> Unit): Boolean
    fun cancel(sessionId: String): Boolean
    fun isRunning(sessionId: String): Boolean
}
```

`launch` 创建 LAZY Job，使用 `putIfAbsent` 原子拒绝重复会话；注册成功后发布不可变 key 集合并启动。Job 在 `finally` 中用 `jobs.remove(sessionId, currentJob)` 清理和发布状态。异常由 `SupervisorJob` 父作用域隔离，`CancellationException` 正常传播。

- [ ] **Step 4: 运行协调器测试**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.domain.usecase.GenerationCoordinatorTest'
```

Expected: 所有协调器测试通过。

- [ ] **Step 5: 提交协调器**

```bash
git add app/src/main/java/com/imgad/domain/usecase/GenerationCoordinator.kt app/src/test/java/com/imgad/domain/usecase/GenerationCoordinatorTest.kt
git commit -m "feat: coordinate generation by session"
```

### Task 4: 重构 CreateViewModel 并保存按会话草稿

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/create/CreateUiState.kt`
- Modify: `app/src/main/java/com/imgad/ui/create/CreateViewModel.kt`
- Modify: `app/src/test/java/com/imgad/ui/create/CreateViewModelTest.kt`

- [ ] **Step 1: 写页面切换不取消任务的失败测试**

给 fixture 注入真实测试协调器和阻塞生成动作。提交会话 `first` 后调用 `loadSession("second")`、`resetToNewSession()`，断言生成 Deferred 仍未取消，`runningSessionIds` 仍包含 `first`。

- [ ] **Step 2: 写跨会话并发与同会话拒绝测试**

提交 `first` 后打开 `second` 并提交，断言两个动作均启动；返回 `first` 再次 submit，断言调用次数不增加且当前会话保持运行。

- [ ] **Step 3: 写按会话草稿失败测试**

在 `first` 输入提示词和参考图，切换 `second` 输入不同提示词，再切回，断言两个会话分别恢复自己的 `prompt`、`inputAssets`、参数和模型选择。

- [ ] **Step 4: 运行测试确认现有取消行为失败**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.ui.create.CreateViewModelTest'
```

Expected: 新增测试 FAIL，现有 `loadSession`/`resetToNewSession` 取消 Job 且状态为单会话。

- [ ] **Step 5: 接入协调器并移除页面 Job**

构造函数新增 `GenerationCoordinator`。删除 `runningJob` 和 `runTask`；`submit`/`retry` 通过：

```kotlin
coordinator.launch(sessionId) {
    if (request.isEdit) edit.execute(sessionId, request) else generate.execute(sessionId, request)
}
```

`cancel()` 调用 `currentSessionId?.let(coordinator::cancel)`。ViewModel 收集 `runningSessionIds`，仅当当前会话 ID 在集合中时设置 `isRunning=true`。

- [ ] **Step 6: 实现进程内草稿映射**

新增不可变 `CreateDraft`，包含 prompt、附件、蒙版、供应方/模型和生成参数。以 `currentSessionId ?: NEW_SESSION_DRAFT` 为 key 保存；切换前保存，切换后恢复。消息、标题、运行状态不进入草稿。

- [ ] **Step 7: 运行 CreateViewModel 测试**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.ui.create.CreateViewModelTest'
```

Expected: 原有和新增测试全部通过。

- [ ] **Step 8: 提交 ViewModel 重构**

```bash
git add app/src/main/java/com/imgad/ui/create/CreateUiState.kt app/src/main/java/com/imgad/ui/create/CreateViewModel.kt app/src/test/java/com/imgad/ui/create/CreateViewModelTest.kt
git commit -m "feat: keep generation active across sessions"
```

### Task 5: 将历史页升级为会话中心

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/imgad/ui/history/HistoryScreen.kt`
- Modify: `app/src/test/java/com/imgad/ui/history/HistoryViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/imgad/ui/history/HistoryScreenTest.kt`

- [ ] **Step 1: 写 ViewModel 运行状态与删除保护测试**

注入 `MutableStateFlow(setOf("running"))`，断言 `HistoryUiState.runningSessionIds` 更新。调用 `requestDelete("running")` 后断言 `pendingDeleteId == null` 且错误为“请先取消生成”。

- [ ] **Step 2: 写 Compose 会话卡片失败测试**

渲染一个 `Session(latestModel="gpt-image-2")` 且 ID 在运行集合中的状态，断言存在“会话”“模型：gpt-image-2”“生成中”和 testTag `session-running-indicator-running`。点击卡片 testTag `session-row-running` 触发打开回调。

- [ ] **Step 3: 运行测试确认缺少状态和标签**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.ui.history.HistoryViewModelTest' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.history.HistoryScreenTest
```

Expected: 新测试 FAIL。

- [ ] **Step 4: 实现会话状态组合**

`HistoryViewModel` 构造函数新增 `runningSessionIds: StateFlow<Set<String>>`，与 store flow 分别收集到 `HistoryUiState.runningSessionIds`。`requestDelete` 先检查集合，运行中则只更新错误。

- [ ] **Step 5: 重构会话卡片**

页面标题改为“会话”。卡片主 Surface/Row 使用 `Modifier.testTag("session-row-${session.id}").clickable`；标题区有模型时显示 `模型：$latestModel`，否则显示严格文案“模型未知”。运行状态使用 `Color(0xFF2E7D32)` 的 `Box` 圆点、`testTag` 和“生成中”。更多菜单仍独立处理重命名/删除。

- [ ] **Step 6: 运行会话中心测试**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.imgad.ui.history.HistoryViewModelTest' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.history.HistoryScreenTest
```

Expected: ViewModel 和 Compose 测试全部通过。

- [ ] **Step 7: 提交会话中心**

```bash
git add app/src/main/java/com/imgad/ui/history/HistoryViewModel.kt app/src/main/java/com/imgad/ui/history/HistoryScreen.kt app/src/test/java/com/imgad/ui/history/HistoryViewModelTest.kt app/src/androidTest/java/com/imgad/ui/history/HistoryScreenTest.kt
git commit -m "feat: turn history into session manager"
```

### Task 6: 添加生成等待指示器与提示词复制

**Files:**
- Modify: `app/src/main/java/com/imgad/ui/create/CreateScreen.kt`
- Modify: `app/src/main/java/com/imgad/ui/create/MessageList.kt`
- Modify: `app/src/main/java/com/imgad/ui/create/PromptComposer.kt`
- Modify: `app/src/androidTest/java/com/imgad/ui/create/CreateScreenTest.kt`

- [ ] **Step 1: 写运行消息指示器失败测试**

渲染 `TaskState.RUNNING` 用户消息，断言 `onNodeWithTag("message-progress-running")` 显示且“生成中”存在。

- [ ] **Step 2: 写复制图标与长按失败测试**

为成功用户消息传入 `onCopyPrompt` 记录值。点击 contentDescription“复制提示词”并对 `prompt-text-<messageId>` 执行 `performTouchInput { longClick() }`，两次均断言得到完整提示词。

- [ ] **Step 3: 运行 Compose 测试确认失败**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.create.CreateScreenTest
```

Expected: 缺少进度 testTag 和复制语义导致 FAIL。

- [ ] **Step 4: 实现消息进度状态**

`MessageList` 对 RUNNING 消息渲染：

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp).testTag("message-progress-${message.id}"),
        strokeWidth = 2.dp,
    )
    Text("生成中")
}
```

- [ ] **Step 5: 实现两种复制交互**

`CreateScreenActions` 新增 `onCopyPrompt: (String) -> Unit`。用户消息正文增加 `testTag("prompt-text-${message.id}")` 并使用 `combinedClickable(onClick = {}, onLongClick = { onCopyPrompt(message.text) })`；非空提示词旁使用 `IconButton` + `Icons.Default.ContentCopy`，contentDescription 为“复制提示词”。

- [ ] **Step 6: 调整编辑器运行提示**

当前会话运行时保留全宽取消按钮，并在按钮内容前加入 18dp `CircularProgressIndicator`，按钮文字为“取消生成”。其他会话运行不影响当前编辑器。

- [ ] **Step 7: 运行创作页测试**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.ui.create.CreateScreenTest
```

Expected: 原有和新增 Compose 测试全部通过。

- [ ] **Step 8: 提交创作交互**

```bash
git add app/src/main/java/com/imgad/ui/create/CreateScreen.kt app/src/main/java/com/imgad/ui/create/MessageList.kt app/src/main/java/com/imgad/ui/create/PromptComposer.kt app/src/androidTest/java/com/imgad/ui/create/CreateScreenTest.kt
git commit -m "feat: show progress and copy prompts"
```

### Task 7: 在 App 中装配协调器、会话中心与 Snackbar

**Files:**
- Modify: `app/src/main/java/com/imgad/App.kt`
- Modify: `app/src/androidTest/java/com/imgad/AppLaunchTest.kt`

- [ ] **Step 1: 写导航集成失败测试**

扩展 `AppLaunchTest`：底部存在“会话”且不存在“历史”；从会话打开运行中的创作，再切设置、切会话并返回，运行指示仍显示。复制提示词后断言 Snackbar“提示词已复制”。

- [ ] **Step 2: 运行测试确认旧装配失败**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.AppLaunchTest
```

Expected: “会话”、协调器状态或复制 Snackbar 断言 FAIL。

- [ ] **Step 3: 在 App 创建进程级协调器**

`App` 创建：

```kotlin
private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
val generationCoordinator by lazy { GenerationCoordinator(generationScope) }
```

Factory 将同一个 coordinator 注入 `CreateViewModel` 和 `HistoryViewModel`。

- [ ] **Step 4: 更新导航与复制回调**

底部 label 改为“会话”，route 仍保持 `history` 以避免无意义导航迁移。`onCopyPrompt` 写剪贴板 label“提示词”，并在 scope 中显示 Snackbar“提示词已复制”。错误复制保持现有行为。

- [ ] **Step 5: 运行 App 集成测试**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.imgad.AppLaunchTest
```

Expected: AppLaunchTest 全部通过。

- [ ] **Step 6: 提交应用装配**

```bash
git add app/src/main/java/com/imgad/App.kt app/src/androidTest/java/com/imgad/AppLaunchTest.kt
git commit -m "feat: wire background sessions into app"
```

### Task 8: 完整验证与模拟器并发验收

**Files:**
- Modify: `docs/testing/android-acceptance.md`

- [ ] **Step 1: 运行完整自动化验证**

```bash
JAVA_HOME=/home/idrl/.cache/img-ad-toolchain/jdk17 \
ANDROID_HOME=/home/idrl/.cache/img-ad-toolchain/android-sdk \
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`，所有 JVM/Android 测试 0 失败，lint 0 错误，Debug APK 生成。

- [ ] **Step 2: 在 API 35 模拟器验证两个会话**

使用可控的 MockWebServer 仪器测试或两个阻塞 fake 任务执行：会话 A 提交后显示转圈和绿色状态；切到会话 B 提交；访问设置；回会话中心确认 A/B 均显示生成中；取消 A 不影响 B；完成 B 后仅 B 绿色状态消失并显示结果。

- [ ] **Step 3: 验证复制与删除保护**

从创作页和会话中心进入的创作详情分别点击复制图标、长按提示词，确认剪贴板与 Snackbar；对运行会话点击删除，确认提示“请先取消生成”且会话和任务仍存在。

- [ ] **Step 4: 更新验收记录**

在 `docs/testing/android-acceptance.md` 记录执行日期、模拟器版本、自动化测试数量，以及多会话并发、状态恢复、复制和删除保护结果，不写入供应方地址或密钥。

- [ ] **Step 5: 提交验收记录**

```bash
git add docs/testing/android-acceptance.md
git commit -m "test: verify concurrent session generation"
```

- [ ] **Step 6: 推送前复核**

```bash
git status -sb
git log --oneline origin/main..HEAD
git diff origin/main...HEAD --check
```

Expected: 工作区干净，提交仅覆盖本计划文件，diff check 无错误。推送必须在用户再次明确确认后执行。
