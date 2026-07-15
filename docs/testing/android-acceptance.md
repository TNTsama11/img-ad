# Android 验收清单

## 当前状态

- 已在用户缓存目录建立非全局 JDK 17.0.19 与 Android API 35 工具链，未修改系统 Java 或全局环境变量。
- `testDebugUnitTest` 已执行 115 个 JVM 单元测试，0 失败、0 跳过。
- 已创建并启动 `imgad_api35`（Pixel 5、Android 15 / API 35、Google APIs x86_64）模拟器。
- `connectedDebugAndroidTest` 已在该模拟器实际执行 51 个仪器/UI/验收测试，0 失败、0 跳过。
- `lintDebug`、`assembleDebug` 和启用 R8/资源压缩的 `assembleRelease` 均已通过。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`。Release 产物尚未签名，不能作为正式商店发布包。

## Android 10（API 29）

- [ ] 首次启动和数据库迁移 1 -> 2，确认 `assets.available` 默认值为真。
- [ ] 配置供应方、API Key、模型和默认项；重启后配置仍存在。
- [ ] Photo Picker/系统文件选择器导入参考图和蒙版，拒绝越权路径。
- [ ] 文生图 Base64 响应生成、历史记录和输出缩略图。
- [ ] 参考图编辑 multipart 请求、失败后重试和任务中断恢复。
- [ ] 预览、缩放、MediaStore 保存、分享目标和 FileProvider URI 授权。
- [ ] 应用切后台后返回，运行中任务转为“请求被应用中断”的失败任务。
- [ ] 离线、超时、401/429/5xx、畸形响应和图片下载失败提示。
- [ ] 低存储空间下写入失败后的临时文件、数据库和 MediaStore 回滚。
- [ ] 深色模式和小屏布局无文字重叠、按钮越界或不可访问控件。
- [ ] ZIP 导出/导入、密码错误、缺失资源提示和私有缓存清理。

## Android 14（API 34）

- [ ] 首次启动、升级启动和进程被系统回收后的恢复流程。
- [ ] Photo Picker、通知/权限对话框和文件 URI 授权行为符合系统限制。
- [ ] 文生图、编辑、重试、历史恢复及不可用资源展示。
- [ ] MediaStore `IS_PENDING` 提交/失败清理和分享目标选择器。
- [ ] Keystore 加密密钥读写、导出密码清理和导入密钥回滚。
- [ ] 后台切换、离线、低存储、深色模式和小屏布局。
- [ ] ZIP 大小/entry 数量限制、重复 entry、Zip Slip 和未知引用拒绝。

## Android 15 模拟器（API 35）

- [x] 应用冷启动进入 `MainActivity`，Compose 首屏正常显示且没有重复系统 ActionBar。
- [x] Room 建库、迁移、排序、搜索、级联删除和软删除测试通过。
- [x] Keystore 密钥写入、覆盖、读取和删除测试通过。
- [x] 文生图、编辑、失败重试、历史记录、归档导入导出端到端测试通过。
- [x] MediaStore 提交/失败回滚和 FileProvider 分享边界测试通过。
- [x] 创作、图片预览缩放、历史、供应方/模型、存储设置 Compose UI 测试通过。
- [x] Debug 构建仅对 `localhost` 和 `127.0.0.1` 放行明文流量，用于 MockWebServer；Release 不包含该放行规则。

## 发布前记录

- [x] `assembleRelease` 成功，R8 与资源压缩启用。
- [x] Release APK 未发现测试密钥、调试日志或跨平台运行时；业务权限仅声明 `INTERNET`，另有 AndroidX 自动生成的非导出动态接收器权限。
- [x] 记录 APK 体积、方法数、模拟器启动时间和主要依赖；真机启动时间仍需补测。
- [ ] 记录设备型号、系统版本、测试日期、失败步骤和相关日志脱敏结果。

## 自动化发布基线（2026-07-14）

- Debug APK：63,240,709 bytes（约 60.3 MiB）。
- Release unsigned APK：2,972,589 bytes（约 2.84 MiB）。
- Release DEX 方法引用：17,693。
- Release SHA-256：`7b66a13f27e48b55b265226b6df9f451883f0f9159b6e5a0cedc0b45c5d1188a`。
- 主要运行时依赖：Jetpack Compose/Material 3、Lifecycle、Navigation、Room、OkHttp、Kotlin Coroutines、Kotlin Serialization、AndroidX Security Crypto。
- 自动化验证命令：`./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug :app:assembleRelease`。
- API 35 模拟器测试：51 项通过；冷启动 `TotalTime` 1,355 ms。
- 模拟器检查期间发现并修复：JUnit 非 `Unit` 测试签名、脆弱的精确缩放断言、Debug MockWebServer 明文策略，以及系统 ActionBar 与 Compose TopAppBar 重复显示。
