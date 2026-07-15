# Android CI 自动构建设计

## 目标

为公开 GitHub 仓库增加分层 Android CI。所有 push 和 Pull Request 获得快速构建反馈及可下载 Debug APK；`main` 推送和手动触发额外运行 API 35 模拟器测试。

## 触发条件

- 任意分支 push；
- Pull Request；
- 手动 `workflow_dispatch`。

同一工作流、同一分支或 Pull Request 只保留最新运行，后续运行取消尚未结束的旧运行。

## 权限与安全

工作流仅声明 `contents: read`。构建不读取 API Key、GitHub Secrets、签名文件或生产配置。Debug APK 使用 Android 默认调试签名，仅作为测试产物。

## 快速验证任务

Ubuntu Runner 使用 JDK 17 和 Gradle 官方缓存 Action，依次执行：

```text
testDebugUnitTest lintDebug assembleDebug
```

构建成功后上传 `app/build/outputs/apk/debug/app-debug.apk`，artifact 名称为 `imgad-debug-apk`，保留 14 天。lint 或测试失败时任务失败，不上传成功产物。

## 模拟器任务

模拟器任务依赖快速验证任务成功，只在以下情况执行：

- push 到 `main`；
- 手动 `workflow_dispatch`。

任务使用 Android API 35、x86_64 镜像和关闭动画的无头模拟器，执行 `connectedDebugAndroidTest`。Pull Request 和非 `main` 分支 push 不启动模拟器。

无论模拟器测试成功或失败，上传 `app/build/reports/androidTests/connected/` 和 `app/build/outputs/androidTest-results/connected/`，artifact 名称为 `android-instrumentation-results`，保留 14 天；目录不存在时不使上传步骤失败。

## 工作流边界

- 单一文件 `.github/workflows/android-ci.yml`；
- 不发布 GitHub Release，不签名 Release APK；
- 不在 CI 内调用真实图片供应方；
- 不修改应用源码或 Gradle 依赖；
- JVM 测试、lint 和 Debug 构建在一个快速任务中共享 Gradle 缓存；
- 模拟器任务使用独立 Runner，避免依赖前一任务的本地构建目录。

## 验证

- 使用 Ruby 标准 YAML 解析器检查语法；
- 检查 workflow 权限、触发器、条件表达式和 artifact 路径；
- 本地重新运行 `testDebugUnitTest lintDebug assembleDebug`；
- 推送后通过 GitHub Actions API 确认工作流被识别并观察首次 `main` 运行；
- 首次运行失败时读取具体 job 日志并修复，不以仅成功创建 workflow 文件作为完成标准。
