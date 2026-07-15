# Android AI 图片客户端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Android 10+ 的轻量本地优先 AI 图片客户端，支持 OpenAI 兼容供应方配置、文生图、参考图/编辑、会话历史、图片预览保存、错误处理和数据导入导出。

**Architecture:** 单一 `app` Gradle 模块，采用 Kotlin、Compose、ViewModel、Room 和 OkHttp。UI 仅依赖领域用例，网络、数据库、Keystore 和文件存储由 data 层封装；文生图与参考图/编辑共享任务状态和历史记录。

**Tech Stack:** Kotlin 2.0.21、Android Gradle Plugin 8.7.3、Compose BOM 2024.12.01、Material 3、Room 2.6.1、OkHttp 4.12.0、Kotlin Coroutines 1.9.0、AndroidX Security Crypto 1.1.0-alpha06、MockWebServer 4.12.0。

---

## 执行约束

- 当前目录是空目录，不假设已有 Android 工程或本地模板。
- 最低 SDK 为 29，目标 SDK 使用安装时可用的稳定 Android SDK；编译 SDK 固定为 35。
- 所有实现步骤先写失败测试，再写最小实现，再运行对应测试。
- 不执行 `git init`、`git add`、`git commit` 或 `git push`；用户未授权 Git 操作。
- 每个任务完成后运行该任务列出的命令，并保留失败输出用于修复。
- API Key 不能出现在 Room 普通字段、参数快照、日志、错误复制文本或默认导出文件中。

## 文件结构总览

将创建以下核心目录和文件：

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/imgad/MainActivity.kt
app/src/main/java/com/imgad/App.kt
app/src/main/java/com/imgad/data/local/...
app/src/main/java/com/imgad/data/remote/...
app/src/main/java/com/imgad/data/repository/...
app/src/main/java/com/imgad/domain/model/...
app/src/main/java/com/imgad/domain/usecase/...
app/src/main/java/com/imgad/ui/create/...
app/src/main/java/com/imgad/ui/history/...
app/src/main/java/com/imgad/ui/settings/...
app/src/main/java/com/imgad/ui/component/...
app/src/test/java/com/imgad/...
app/src/androidTest/java/com/imgad/...
```

### Task 1: 建立可编译的 Android 工程骨架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/imgad/App.kt`
- Create: `app/src/main/java/com/imgad/MainActivity.kt`
- Create: `app/src/main/java/com/imgad/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/imgad/BuildSmokeTest.kt`

- [ ] **Step 1: 写工程构建配置和最小启动 Activity**

  `settings.gradle.kts` 使用 `pluginManagement` 和 `dependencyResolutionManagement`，仓库只配置 `google()`、`mavenCentral()`；根工程声明 AGP 8.7.3、Kotlin 2.0.21 和 Compose Compiler 插件。`app/build.gradle.kts` 开启 Compose、Kotlin serialization，设置 `namespace = "com.imgad"`、`minSdk = 29`、`compileSdk = 35`、`targetSdk = 35`，加入 Compose、Lifecycle、Room、OkHttp、Security Crypto 和测试依赖。

  Manifest 只声明 `INTERNET`，不声明读写外部存储权限。`MainActivity` 使用 `setContent { ImgAdApp() }`，`ImgAdApp` 先显示空的 Material 3 Surface。`Theme.kt` 提供浅色、深色和动态色适配。

- [ ] **Step 2: 写构建冒烟测试**

  在 `BuildSmokeTest.kt` 中验证测试运行时可加载 `BuildConfig.APPLICATION_ID`，并断言值为 `com.imgad`。

- [ ] **Step 3: 运行验证**

  Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

  Expected: `BUILD SUCCESSFUL`，生成 `app/build/outputs/apk/debug/app-debug.apk`。

### Task 2: 建立领域模型和参数校验

**Files:**
- Create: `app/src/main/java/com/imgad/domain/model/Provider.kt`
- Create: `app/src/main/java/com/imgad/domain/model/ModelProfile.kt`
- Create: `app/src/main/java/com/imgad/domain/model/Session.kt`
- Create: `app/src/main/java/com/imgad/domain/model/Message.kt`
- Create: `app/src/main/java/com/imgad/domain/model/Asset.kt`
- Create: `app/src/main/java/com/imgad/domain/model/GenerationRequest.kt`
- Create: `app/src/main/java/com/imgad/domain/model/GenerationError.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/ValidateGenerationRequest.kt`
- Test: `app/src/test/java/com/imgad/domain/ValidateGenerationRequestTest.kt`

- [ ] **Step 1: 写校验失败测试**

  覆盖以下输入：空提示词、缺少模型、尺寸不在模型允许列表、`n` 不在 1..10、编辑请求缺少图片、蒙版模型未启用，以及高级 JSON 覆盖 `model`/`prompt` 时必须拒绝。每个测试断言具体的 `ValidationError` 枚举值。

- [ ] **Step 2: 定义不可变领域模型**

  使用 `data class` 和枚举定义 `Provider`、`ModelProfile`、`Session`、`Message`、`Asset`、`GenerationRequest`。`GenerationRequest` 包含 `providerId`、`model`、`prompt`、`size`、`quality`、`outputFormat`、`count`、`advancedJson`、`inputAssets` 和可选 `maskAsset`。任务状态定义为 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELED`。

- [ ] **Step 3: 实现最小校验器**

  `ValidateGenerationRequest.invoke(request, modelProfile)` 按固定顺序返回第一个领域错误；不要在校验器内访问数据库或网络。高级 JSON 使用 Kotlin serialization `JsonObject` 解析，禁止覆盖核心字段集合 `model`、`prompt`、`image`、`mask`、`size`、`quality`、`output_format`、`n`。

- [ ] **Step 4: 运行领域测试**

  Run: `./gradlew :app:testDebugUnitTest --tests '*ValidateGenerationRequestTest'`

  Expected: 所有校验测试通过。

### Task 3: 实现 Room 持久化和 Keystore 密钥存储

**Files:**
- Create: `app/src/main/java/com/imgad/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/imgad/data/local/entity/ProviderEntity.kt`
- Create: `app/src/main/java/com/imgad/data/local/entity/ModelProfileEntity.kt`
- Create: `app/src/main/java/com/imgad/data/local/entity/SessionEntity.kt`
- Create: `app/src/main/java/com/imgad/data/local/entity/MessageEntity.kt`
- Create: `app/src/main/java/com/imgad/data/local/entity/AssetEntity.kt`
- Create: `app/src/main/java/com/imgad/data/local/dao/ProviderDao.kt`
- Create: `app/src/main/java/com/imgad/data/local/dao/ModelProfileDao.kt`
- Create: `app/src/main/java/com/imgad/data/local/dao/SessionDao.kt`
- Create: `app/src/main/java/com/imgad/data/local/dao/MessageDao.kt`
- Create: `app/src/main/java/com/imgad/data/local/dao/AssetDao.kt`
- Create: `app/src/main/java/com/imgad/data/local/KeystoreSecretStore.kt`
- Create: `app/src/main/java/com/imgad/data/repository/ProviderRepository.kt`
- Test: `app/src/androidTest/java/com/imgad/data/local/AppDatabaseTest.kt`
- Test: `app/src/test/java/com/imgad/data/local/KeystoreSecretStoreTest.kt`

- [ ] **Step 1: 写 Room DAO 测试**

  使用 in-memory Room 数据库插入会话、消息和 Asset，验证会话按 `updatedAt DESC` 返回、搜索只匹配标题、删除会级联清理消息和 Asset、仍保留软删除语义。验证 Provider 与 ModelProfile 的外键关系和默认模型查询。

- [ ] **Step 2: 定义 Entity、DAO 和迁移版本 1**

  Entity 字段映射领域模型；`SessionEntity` 使用 `deletedAt`；Message 和 Asset 外键采用 `onDelete = CASCADE`。DAO 只暴露 `Flow<List<...>>` 查询和明确的挂起写入方法。`AppDatabase` 提供所有 DAO，数据库名固定为 `img_ad.db`。

- [ ] **Step 3: 写 Keystore 失败测试**

  测试 `put(alias, value)` 后 `get(alias)` 返回原值，覆盖更新和删除；断言底层偏好存储中不存在明文 API Key。测试使用独立 alias 前缀，结束时清理。

- [ ] **Step 4: 实现 KeystoreSecretStore**

  使用 Android Keystore 生成 AES-GCM 主密钥；密文、随机 IV 和认证标签写入 `EncryptedSharedPreferences` 或等价的加密 DataStore。对外只提供 `put/get/remove`，Repository 只保存 `apiKeyAlias`。

- [ ] **Step 5: 实现 Repository 映射**

  `ProviderRepository` 负责 Entity 与领域模型转换、供应方 CRUD、默认模型切换和 API Key 读写。UI 和 use case 不直接引用 DAO 或 Android Context。

- [ ] **Step 6: 运行持久化测试**

  Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

  Expected: Room 集成测试、Keystore 测试和已有领域测试全部通过。

### Task 4: 实现图片文件与 MediaStore 边界

**Files:**
- Create: `app/src/main/java/com/imgad/data/local/AssetFileStore.kt`
- Create: `app/src/main/java/com/imgad/data/local/MediaStoreSaver.kt`
- Create: `app/src/main/java/com/imgad/data/local/ThumbnailGenerator.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/ImportInputAsset.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/SaveOutputAsset.kt`
- Test: `app/src/test/java/com/imgad/data/local/AssetFileStoreTest.kt`
- Test: `app/src/androidTest/java/com/imgad/data/local/MediaStoreSaverTest.kt`

- [ ] **Step 1: 写输入复制和缩略图测试**

  使用测试 URI 或临时文件验证输入复制到应用私有目录后可读、返回媒体类型/宽高/大小元数据；生成缩略图的长边不超过 512 px，原图尺寸不变。

- [ ] **Step 2: 实现 AssetFileStore**

  提供 `copyInput(uri)`, `writeOutput(bytes, mediaType)`, `createThumbnail(uri)` 和 `deleteForMessage(messageId)`；文件名使用随机 UUID，不能使用用户输入直接拼接路径。解析图片尺寸失败时返回结构化存储错误。

- [ ] **Step 3: 实现 MediaStoreSaver**

  使用 `MediaStore.Images` 插入 `DISPLAY_NAME`、`MIME_TYPE` 和 `RELATIVE_PATH = Pictures/ImgAd`，写入完成后清除 `IS_PENDING`。失败时删除未提交的 MediaStore 行。

- [ ] **Step 4: 运行图片存储测试**

  Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

  Expected: 私有目录写入、缩略图限制、MediaStore 保存和失败清理测试通过。

### Task 5: 实现 OpenAI 兼容网络网关和错误映射

**Files:**
- Create: `app/src/main/java/com/imgad/data/remote/OpenAiImageService.kt`
- Create: `app/src/main/java/com/imgad/data/remote/RemoteDto.kt`
- Create: `app/src/main/java/com/imgad/data/remote/RemoteErrorParser.kt`
- Create: `app/src/main/java/com/imgad/data/remote/HttpClientFactory.kt`
- Create: `app/src/main/java/com/imgad/data/remote/ImageResponseDecoder.kt`
- Create: `app/src/main/java/com/imgad/domain/model/GenerationResult.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/BuildImageRequest.kt`
- Test: `app/src/test/java/com/imgad/data/remote/OpenAiImageServiceTest.kt`
- Test: `app/src/test/java/com/imgad/data/remote/RemoteErrorParserTest.kt`

- [ ] **Step 1: 写 MockWebServer 失败测试**

  覆盖：`/images/generations` JSON 请求路径和 Bearer Header；`/images/edits` multipart 中的 prompt/model/image/mask；`data[].url` 和 `data[].b64_json`；401、404、429、500、纯文本错误、畸形 JSON、空 data；请求取消和超时。

- [ ] **Step 2: 定义 DTO 和网关接口**

  `ImageGenerationGateway` 只暴露 `suspend fun generate(request)` 与 `suspend fun edit(request)`。DTO 使用 Kotlin serialization，未知字段忽略。`GenerationResult` 只包含解码后的图片引用和可选 request ID，不暴露 OkHttp Response。

- [ ] **Step 3: 实现请求构建**

  `BuildImageRequest` 规范化 Base URL，追加固定路径；JSON 请求只序列化允许字段；编辑请求使用 `MultipartBody` 添加图片文件和可选 mask。核心字段来自领域请求，高级 JSON 经过 Task 2 的冲突校验后追加。

- [ ] **Step 4: 实现响应解码和错误脱敏**

  `ImageResponseDecoder` 优先读取 `url` 或 `b64_json`，空列表返回 `EmptyResponse`。`RemoteErrorParser` 从 `error.message/type/code` 或纯文本提取信息；复制详情经过敏感字段过滤，删除 Authorization、Cookie 和名称含 `key`/`token`/`secret` 的字段。

- [ ] **Step 5: 运行网络测试**

  Run: `./gradlew :app:testDebugUnitTest --tests '*OpenAiImageServiceTest' --tests '*RemoteErrorParserTest'`

  Expected: 所有 MockWebServer 场景通过，错误分类与状态码一致。

### Task 6: 实现生成、编辑、取消和恢复用例

**Files:**
- Create: `app/src/main/java/com/imgad/domain/usecase/GenerateImage.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/EditImage.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/RetryGeneration.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/RecoverInterruptedTasks.kt`
- Create: `app/src/main/java/com/imgad/data/repository/GenerationRepository.kt`
- Create: `app/src/main/java/com/imgad/ui/create/CreateViewModel.kt`
- Test: `app/src/test/java/com/imgad/domain/GenerationUseCaseTest.kt`
- Test: `app/src/test/java/com/imgad/ui/create/CreateViewModelTest.kt`

- [ ] **Step 1: 写任务状态失败测试**

  使用 fake gateway、fake repository 和 fake asset store，验证文生图无附件时调用 `generate`，参考图存在时调用 `edit`；开始前写用户消息并置 `RUNNING`，成功后写助手消息和输出 Asset，失败保存结构化错误，取消调用 gateway 的 coroutine cancellation 并置 `CANCELED`。

- [ ] **Step 2: 实现 GenerationRepository**

  提供创建会话、追加消息、更新任务状态、保存输出 Asset、按会话读取消息和查询运行中任务。所有跨表操作使用 Room transaction，保证失败时用户消息仍保留。

- [ ] **Step 3: 实现 GenerateImage/EditImage**

  两个用例共享参数校验、任务状态和资产保存逻辑；根据附件数量和模型能力选择 gateway 动作。网络调用在调用方 CoroutineScope 中执行，不创建后台 Service。

- [ ] **Step 4: 实现重试和中断恢复**

  `RetryGeneration` 从 `requestSnapshotJson` 重建不可变请求，保留原消息并创建新的任务消息。`RecoverInterruptedTasks` 在应用启动时把 `RUNNING` 更新为可重试的 `FAILED`，错误原因固定为“请求被应用中断”。

- [ ] **Step 5: 实现 CreateViewModel 状态**

  ViewModel 暴露 `StateFlow<CreateUiState>`，包含当前会话、消息列表、输入文字、附件、参数、当前任务和错误。事件包含 `selectProvider`、`selectModel`、`addAsset`、`removeAsset`、`submit`、`cancel`、`retry`。

- [ ] **Step 6: 运行用例测试**

  Run: `./gradlew :app:testDebugUnitTest --tests '*GenerationUseCaseTest' --tests '*CreateViewModelTest'`

  Expected: 状态流、动作分流、取消、重试和中断恢复测试通过。

### Task 7: 实现创作页和图片预览

**Files:**
- Create: `app/src/main/java/com/imgad/ui/create/CreateScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/create/MessageList.kt`
- Create: `app/src/main/java/com/imgad/ui/create/PromptComposer.kt`
- Create: `app/src/main/java/com/imgad/ui/create/ParameterSheet.kt`
- Create: `app/src/main/java/com/imgad/ui/create/ImagePreviewScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/component/AsyncImage.kt`
- Test: `app/src/androidTest/java/com/imgad/ui/create/CreateScreenTest.kt`

- [ ] **Step 1: 写 Compose UI 测试**

  使用 fake `CreateViewModel` 验证：空提示词禁用生成、添加参考图后显示“参考图 / 编辑”、生成中显示取消、失败显示重试和错误详情、成功结果可打开预览、参数抽屉能修改尺寸/质量/格式/数量。

- [ ] **Step 2: 实现 PromptComposer 和 ParameterSheet**

  输入区固定在底部，图片选择使用 `ActivityResultContracts.PickVisualMedia`，多图使用 `PickMultipleVisualMedia`；根据 `ModelProfile` 能力隐藏不支持的蒙版和多图入口。按钮使用 Material Icons，并对不熟悉图标提供 content description。

- [ ] **Step 3: 实现 MessageList 和任务卡片**

  按消息时间展示提示词、附件缩略图、参数摘要、加载状态、成功图片和错误卡片。长文本可滚动，错误消息与后续内容不重叠。

- [ ] **Step 4: 实现 ImagePreviewScreen**

  使用可缩放图片容器，提供保存、分享、查看参数和关闭操作。分享使用 `FileProvider` content URI，不暴露 `file://` 路径。

- [ ] **Step 5: 运行 UI 测试**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests '*CreateScreenTest'`

  Expected: 创作流程和预览入口通过；截图或布局检查确认小屏设备不发生文字/按钮重叠。

### Task 8: 实现历史页、设置页和供应方管理

**Files:**
- Create: `app/src/main/java/com/imgad/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/imgad/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/settings/ProviderEditorScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/settings/ModelEditorScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/settings/StorageSettingsScreen.kt`
- Create: `app/src/main/java/com/imgad/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/imgad/MainActivity.kt`
- Test: `app/src/androidTest/java/com/imgad/ui/history/HistoryScreenTest.kt`
- Test: `app/src/androidTest/java/com/imgad/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: 写历史页测试**

  验证最近更新时间排序、搜索标题、重命名、删除确认和进入会话。删除确认文案必须说明会删除应用私有图片，但不会删除系统图片库副本。

- [ ] **Step 2: 实现 HistoryViewModel/Screen**

  ViewModel 将 DAO Flow 转换为 UI 状态；Screen 使用 LazyColumn 和单条会话菜单，删除先弹确认对话框，再调用 repository 和 AssetFileStore。

- [ ] **Step 3: 写设置页测试**

  验证新增/编辑/删除供应方、API Key 输入默认隐藏、模型能力勾选、默认供应方切换、测试连接结果，以及删除当前供应方必须先重新选择默认项。

- [ ] **Step 4: 实现供应方和模型编辑**

  表单校验名称、Base URL、模型名和能力标记；API Key 只通过 `KeystoreSecretStore` 写入。测试连接以明确按钮触发并在 UI 标示可能产生费用。

- [ ] **Step 5: 接入 Navigation Compose**

  在 `MainActivity` 中建立 `create`、`history`、`settings` 三条路由；会话详情通过 `sessionId` 参数导航，返回时恢复创作页上下文。

- [ ] **Step 6: 运行 UI 测试**

  Run: `./gradlew :app:connectedDebugAndroidTest --tests '*HistoryScreenTest' --tests '*SettingsScreenTest'`

  Expected: 历史、供应方、模型和导航测试通过。

### Task 9: 实现导出、导入和存储清理

**Files:**
- Create: `app/src/main/java/com/imgad/data/local/ExportArchive.kt`
- Create: `app/src/main/java/com/imgad/data/local/ImportArchive.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/ExportLocalData.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/ImportLocalData.kt`
- Create: `app/src/main/java/com/imgad/domain/usecase/ClearAssetCache.kt`
- Test: `app/src/test/java/com/imgad/data/local/ArchiveRoundTripTest.kt`
- Test: `app/src/test/java/com/imgad/domain/ImportLocalDataTest.kt`

- [ ] **Step 1: 写归档往返测试**

  构造包含两个供应方、一个会话、输入图和输出图的 fixture，导出 ZIP 后重新导入，断言 manifest 版本、记录数量、图片校验和及引用关系一致；断言默认归档没有 API Key。

- [ ] **Step 2: 实现 manifest 和 ZIP 写入**

  `manifest.json` 包含格式版本和导出时间；`data.json` 包含非敏感 Provider、ModelProfile、Session、Message 和 Asset；`assets/` 使用随机文件名。API Key 默认排除。

- [ ] **Step 3: 实现可选密码保护**

  用户选择包含密钥时，使用 PBKDF2 派生 AES-GCM 密钥加密密钥段；密码只驻留内存，不写入数据库或归档。错误密码返回明确的导入错误，不创建部分记录。

- [ ] **Step 4: 实现导入冲突和缺失图片处理**

  导入前解析预览数量；导入时为所有记录生成新 ID，使用旧 ID 到新 ID 的映射重建关系。缺失图片保留 Asset 元数据并标记不可用，不阻塞其他记录。

- [ ] **Step 5: 实现缓存清理**

  提供无引用缓存清理和指定会话清理，清理前返回字节数与文件数，完成后刷新设置页统计。

- [ ] **Step 6: 运行归档测试**

  Run: `./gradlew :app:testDebugUnitTest --tests '*ArchiveRoundTripTest' --tests '*ImportLocalDataTest'`

  Expected: 往返、密码保护、冲突映射、缺失图片和清理测试通过。

### Task 10: 集成验证、Release 构建和验收

**Files:**
- Modify: `app/src/main/java/com/imgad/App.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/androidTest/java/com/imgad/AcceptanceFlowTest.kt`
- Create: `docs/testing/android-acceptance.md`

- [ ] **Step 1: 接入应用启动恢复**

  `App.kt` 初始化数据库、Keystore、OkHttp、Repository 和 use case；启动时执行 `RecoverInterruptedTasks`。任何运行时依赖通过构造函数传递给 ViewModel，生产环境和测试环境使用不同实现。

- [ ] **Step 2: 写端到端验收测试**

  使用本地 MockWebServer 完成：配置供应方、创建会话、提交文生图、解析 Base64、打开预览、保存到 MediaStore、失败后重试、添加参考图并提交编辑、重启后恢复历史。

- [ ] **Step 3: 写真机验收清单**

  在 `docs/testing/android-acceptance.md` 记录 Android 10、Android 14 两台设备的检查项：Photo Picker、MediaStore、Keystore、后台切换、离线、低存储空间、深色模式、小屏布局、分享目标和导入导出。

- [ ] **Step 4: 执行完整验证**

  Run: `./gradlew clean testDebugUnitTest connectedDebugAndroidTest assembleRelease`

  Expected: 所有单元测试、仪器测试和 Release 构建通过；检查 APK 中不存在跨平台 runtime、调试日志和不必要权限。

- [ ] **Step 5: 记录发布基线**

  使用 `apkanalyzer` 记录 APK 体积、方法数和主要依赖，写入验收文档。只记录构建结果，不执行任何 Git 提交或远程发布。

## 计划自审

- 规格覆盖：供应方/模型配置由 Tasks 2、3、8 覆盖；文生图和编辑由 Tasks 5、6、7 覆盖；会话与历史由 Tasks 3、6、8 覆盖；图片预览/保存由 Tasks 4、7 覆盖；错误与重试由 Tasks 5、6 覆盖；导入导出和清理由 Task 9 覆盖；安全与 Release 验收由 Tasks 3、9、10 覆盖。
- 占位符扫描：计划不包含常见的占位标记或未定义的“稍后补充”步骤。
- 类型一致性：`GenerationRequest`、`GenerationResult`、`ImageGenerationGateway`、任务状态和核心字段集合在 Tasks 2、5、6 中保持同名；Room Entity 与领域模型的转换仅在 Repository 中发生。
- 风险边界：测试连接可能计费、参考图接口能力差异、导出密钥风险、API Key 日志泄露和应用中断状态均有明确处理。
