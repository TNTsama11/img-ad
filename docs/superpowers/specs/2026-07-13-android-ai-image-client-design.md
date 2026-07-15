# Android AI 图片客户端设计规格

## 1. 目标

构建一个面向 Android 10（API 29）及以上设备的轻量 AI 图片客户端。应用允许用户配置多个 OpenAI Images API 兼容供应方和模型，完成文生图、参考图生成与图片编辑，并在设备本地管理会话、请求历史和图片。

首版采用本地优先策略，不包含账号、云同步或社区功能。除用户主动发起的模型请求、远程图片下载和图片分享外，应用不向其他服务发送数据。

## 2. 产品原则

- **轻量**：原生 Android 单应用模块，不引入跨平台运行时、插件系统或后台常驻服务。
- **清晰**：创作、历史和设置三个一级页面覆盖完整工作流。
- **兼容**：支持 OpenAI 风格的 `images/generations` 和 `images/edits` 接口，容忍未知响应字段。
- **本地优先**：供应方配置、会话、历史记录和图片保存在本机。
- **安全**：API Key 加密存储，日志、错误详情和默认导出文件不包含密钥。
- **可恢复**：失败请求保留提示词、附件和参数，可直接重试。

## 3. 首版范围

### 3.1 包含

- 配置多个 OpenAI 兼容供应方。
- 为供应方手动配置多个图片模型及能力。
- 文生图、参考图生成、图片编辑和可选蒙版。
- 图片文件选择、预览、缩放、保存与分享。
- 会话创建、搜索、重命名、删除和历史浏览。
- 常用生成参数和高级 JSON 参数。
- 请求取消、失败重试和结构化错误详情。
- 本地数据导出、导入和存储清理。
- URL 和 Base64 两类图片响应。

### 3.2 不包含

- 原生 Gemini、Stability AI、Replicate 等非 OpenAI 协议。
- 账号、云同步、多设备同步和团队协作。
- 图片社区、提示词市场和在线模板。
- 批量工作流、后台持续生成和自动任务队列。
- iOS、桌面或 Web 客户端。
- 动态插件、远程配置和模型能力自动探测。

## 4. 技术方案

- Kotlin。
- Jetpack Compose 与 Material 3。
- 单 Activity 与 Navigation Compose。
- ViewModel、Kotlin Coroutines 和 StateFlow。
- Room 保存结构化数据。
- Android Keystore 保护 API Key。
- OkHttp 处理 JSON、multipart、图片下载、超时和取消。
- 系统 Photo Picker 选择图片，MediaStore 保存到系统图片库。
- 单一 `app` Gradle 模块，按职责分包。

不为首版引入跨平台框架、复杂多模块工程或动态参数表单引擎。

## 5. 信息架构

### 5.1 创作页

- 顶栏显示当前会话、供应方和模型，并允许快速切换。
- 内容区按时间显示用户提示词、参考图、参数摘要、任务状态、生成结果和错误卡片。
- 底部输入区包含图片选择、提示词输入、生成按钮和运行时的取消按钮。
- 常用参数通过底部抽屉编辑：尺寸、质量、输出格式和生成数量。
- 高级 JSON 位于折叠区域，默认隐藏。
- 无图片时进入“文生图”模式；有图片时进入“参考图 / 编辑”模式。
- 点击结果进入沉浸式预览，可缩放、保存、分享和查看参数快照。
- 首次提交请求时创建会话，未提交的空白会话不写入历史。

### 5.2 历史页

- 按最近更新时间倒序显示会话。
- 支持搜索、重命名和删除。
- 进入历史会话后返回创作页并恢复其上下文。
- 删除会话时明确提示会同时删除应用私有目录中的关联图片；系统图片库中的已保存副本不受影响。

### 5.3 设置页

- 供应方和模型管理。
- 默认供应方、默认模型和默认生成参数。
- 测试连接。
- 应用私有图片占用统计和清理。
- 数据导出与导入。
- 应用版本和开源许可信息。

## 6. 供应方与模型配置

### 6.1 Provider

- `id`
- `name`
- `baseUrl`
- `apiKeyAlias`：Keystore 加密存储项的引用，不保存明文。
- `enabled`
- `defaultModelId`
- `createdAt`
- `updatedAt`

Base URL 保存前去除末尾 `/`。请求路径固定追加 `/images/generations` 或 `/images/edits`。用户应填写包含 API 版本前缀的地址，例如 `https://api.openai.com/v1`。

### 6.2 ModelProfile

- `id`
- `providerId`
- `modelName`
- `displayName`
- `supportsGeneration`
- `supportsEdit`
- `supportsMask`
- `supportsMultipleImages`
- `supportedSizes`
- `supportedQualities`
- `enabled`

模型由用户手动配置，不依赖 `/models` 接口。能力标记用于约束 UI 和本地校验，服务端仍是最终校验方。

“测试连接”发送一个成本可控的最小请求。由于部分服务不提供无成本探测接口，测试界面必须提示该操作可能产生费用；只有用户明确触发后才发送请求。

## 7. 数据模型

### 7.1 Session

- `id`
- `title`
- `createdAt`
- `updatedAt`
- `deletedAt`：空值表示正常，非空表示软删除。

### 7.2 Message

- `id`
- `sessionId`
- `role`：`USER`、`ASSISTANT` 或 `SYSTEM`。
- `text`
- `taskState`：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` 或 `CANCELED`。
- `requestSnapshotJson`
- `errorJson`
- `createdAt`
- `updatedAt`

### 7.3 Asset

- `id`
- `messageId`
- `localUri`
- `thumbnailUri`
- `mediaType`
- `width`
- `height`
- `byteSize`
- `source`：`INPUT`、`MASK` 或 `OUTPUT`。
- `createdAt`

用户选择的输入图片在发起请求前复制到应用私有目录，避免外部 URI 授权过期。输出图片保存原始文件并生成缩略图。Room 只保存 URI 和元数据，不保存图片二进制内容。

## 8. 请求与状态流

### 8.1 文生图

1. 校验供应方、模型、提示词和参数。
2. 创建或更新会话，写入用户消息和参数快照。
3. 将任务状态设为 `RUNNING`。
4. 发送 `POST {baseUrl}/images/generations` JSON 请求。
5. 解析 `data[].url` 或 `data[].b64_json`。
6. 下载或解码图片到应用私有目录，生成缩略图。
7. 写入助手消息和输出 Asset，将状态设为 `SUCCEEDED`。

### 8.2 参考图 / 编辑

1. 复制输入图和可选蒙版到应用私有目录。
2. 校验模型的编辑、蒙版和多图能力。
3. 写入用户消息、附件元数据和参数快照。
4. 发送 `POST {baseUrl}/images/edits` multipart 请求。
5. 使用与文生图相同的响应保存流程。

参考图模式与传统局部编辑共用 edits 接口。是否支持多张参考图、蒙版及具体字段格式取决于供应方和模型配置。

### 8.3 中断恢复

- 用户主动取消时，取消 OkHttp Call，并将任务标记为 `CANCELED`。
- 应用重启后发现 `RUNNING` 任务时，将其转换为可重试的 `FAILED`，错误原因标记为“请求被应用中断”。
- 首版不在后台继续生成，也不自动重复可能计费的请求。

## 9. 参数处理

常用参数包含：

- `model`
- `prompt`
- `size`
- `quality`
- `output_format`
- `n`

参考图 / 编辑请求额外包含图片文件和可选蒙版。所有请求都保存不可变的参数快照，以便历史查看和重试。

高级 JSON 只允许添加常用表单未覆盖的顶层字段。以下核心字段不可通过高级 JSON 覆盖：`model`、`prompt`、`image`、`mask`、`size`、`quality`、`output_format` 和 `n`。字段冲突在发送前提示用户，避免请求内容与界面显示不一致。

## 10. 网络与响应兼容

业务层只依赖统一接口：

```kotlin
interface ImageGenerationGateway {
    suspend fun generate(request: GenerationRequest): GenerationResult
    suspend fun edit(request: EditRequest): GenerationResult
}
```

OpenAI 兼容实现负责：

- Bearer Token 认证。
- JSON 文生图请求。
- multipart 图片编辑请求。
- 超时、取消和远程图片下载。
- `data[].url` 与 `data[].b64_json` 解析。
- 忽略未知 JSON 字段。
- 从常见的 `error.message`、`error.type`、`error.code` 和纯文本响应中提取错误。

API DTO 不进入 UI；远程响应统一映射为领域结果或领域错误。

## 11. 错误处理

错误分类：

- 配置错误：Base URL、API Key 或模型缺失。
- 参数错误：参数、JSON、图片或蒙版不合法。
- 网络错误：离线、DNS、连接、超时或 TLS 失败。
- HTTP 错误：保存状态码、request ID、供应方错误码和原始错误消息。
- 响应错误：空结果、无效 JSON、无效 Base64 或图片下载失败。
- 存储错误：空间不足、文件写入失败或 URI 授权失效。
- 用户取消：正常的终止状态，不显示为失败。

错误卡片显示简短说明、原始供应方消息、状态码、复制详情和可用时的重试按钮。`401/403` 引导检查密钥，`404` 引导检查 Base URL，`429` 提示限流，`5xx` 建议稍后重试。

复制出的诊断详情必须移除 API Key、Authorization Header、Cookie 和名称疑似包含 `key`、`token`、`secret` 的敏感字段。默认不持久化完整请求或响应正文。

## 12. 图片与存储

- 私有图片存储在应用专属目录，不申请广泛存储权限。
- 会话列表使用缩略图，预览页加载原图。
- 用户保存图片时通过 MediaStore 写入系统图片库。
- 分享通过 content URI 和临时读权限完成。
- 设置页显示私有图片和缩略图占用。
- 清理操作按“无引用缓存”和“指定会话数据”区分，执行前显示影响范围。

## 13. 导出与导入

导出文件为带格式版本号的 ZIP：

- `manifest.json`：格式版本和导出时间。
- `data.json`：供应方非敏感配置、模型、会话、消息和 Asset 映射。
- `assets/`：关联输入图、蒙版、输出图和必要缩略图。

默认排除 API Key。用户主动选择包含密钥时，必须设置导出密码，并使用基于密码派生密钥的 AES-GCM 加密敏感内容。应用不得保存导出密码。

导入前展示供应方、会话和图片数量。所有导入记录生成新 ID，通过映射恢复引用，不覆盖本地同 ID 数据。缺失图片作为不可用附件保留元数据并显示提示，不阻止其他记录导入。

## 14. 工程边界

```text
app/
  data/
    local/        Room、加密配置、文件存储
    remote/       OpenAI 兼容 API、DTO、错误解析
    repository/   供应方、会话、生成任务仓库
  domain/
    model/        领域模型
    usecase/      生成、编辑、重试、导入导出
  ui/
    create/       创作与图片预览
    history/      会话历史
    settings/     供应方、模型、存储与迁移
    component/    公共控件
```

- UI 不直接访问 Room、文件系统或 HTTP。
- 生成和编辑共用业务用例，只在请求构建阶段分流。
- 图片保存、缩略图生成和导入导出各自保持单一职责。
- 依赖通过构造函数注入；首版可使用轻量 DI 方案，但不为未来场景预建复杂抽象。

## 15. 测试策略

### 15.1 单元测试

- Base URL 规范化和请求路径。
- 模型能力与参数校验。
- 高级 JSON 合并和冲突拒绝。
- URL/Base64 响应映射与错误脱敏。
- 导入 ID 映射和冲突处理。
- 中断任务恢复。

### 15.2 数据与网络集成测试

- Room 会话排序、搜索、软删除和资源清理。
- MockWebServer 覆盖 JSON 文生图、multipart 编辑、URL/Base64 响应。
- 覆盖 401、403、404、429、5xx、畸形 JSON、空响应、超时和取消。

### 15.3 UI 与真机测试

- 新建会话、切换供应方和模型。
- 添加参考图、蒙版和参数。
- 生成、取消、失败重试和历史恢复。
- 图片缩放、MediaStore 保存和分享。
- Keystore、系统照片选择器、应用后台切换和低存储空间。

## 16. MVP 验收标准

- 可配置多个 OpenAI 兼容供应方和模型，并选择默认项。
- 可完成文生图和基于参考图的编辑，按模型能力提供可选蒙版与多图。
- 可解析 URL 和 Base64 图片响应。
- 可查看、搜索、重命名和删除会话，并从历史参数重试。
- 可预览、缩放、保存和分享生成图片。
- 请求可取消，失败可重试，供应方错误可查看和复制。
- 重启后历史记录完整；中断任务转为可重试失败状态。
- 可导出和导入本地数据，默认导出不包含 API Key。
- Android 10 及以上可运行，不申请不必要权限。
- Release APK 不包含未使用的跨平台运行时、后台服务或大型非必要依赖；体积基线在首个可发布构建产生后记录。

## 17. 实施顺序建议

1. 建立 Android 工程、设计系统和导航骨架。
2. 实现供应方、模型配置和安全密钥存储。
3. 实现 Room 会话、消息、Asset 与任务恢复。
4. 实现 OpenAI 兼容文生图和错误映射。
5. 实现参考图 / 编辑 multipart 请求。
6. 完成创作、历史、预览、保存和分享流程。
7. 实现导出、导入和存储清理。
8. 补齐自动化测试、Release 构建检查和真机验收。
