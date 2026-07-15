# GitHub 双语 README 与公开发布实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ImgAd 创建中英文 README、公开发布所需的安全忽略规则和截图资源，并将当前项目作为新的公开 GitHub 仓库 `img-ad` 首次推送。

**Architecture:** 文档层由中文主页 `README.md`、英文副本 `README_EN.md` 和仓库内截图组成；`.gitignore` 将源码与本地构建、凭据和发布产物严格分离。发布层使用本地 Git 管理 `main` 分支，使用 GitHub CLI 认证、检测同名仓库冲突并创建公开远程。

**Tech Stack:** Git、GitHub CLI、Markdown、Android/Gradle、Shell

---

### Task 1: 建立公开仓库忽略边界

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: 写入 Android/Kotlin 忽略规则**

创建 `.gitignore`，内容必须覆盖以下规则：

```gitignore
# Gradle and Kotlin
.gradle/
.kotlin/
**/build/

# Android SDK and IDE state
local.properties
.idea/
*.iml
captures/
.externalNativeBuild/
.cxx/

# Build and release artifacts
*.apk
*.aab
*.ap_
*.dex
mapping.txt

# Signing and secrets
*.jks
*.keystore
*.pem
*.p12
*.pfx
*.key
.env
.env.*
secrets.properties

# OS, logs, and temporary files
.DS_Store
Thumbs.db
*.log
*.tmp
*.swp
```

- [ ] **Step 2: 验证生成目录和敏感文件被忽略**

Run:

```bash
git init -b main
git check-ignore -v .gradle/file-system.probe app/build/outputs/apk/debug/app-debug.apk build/emulator/history-fixed-return.png
```

Expected: 三个路径均匹配 `.gitignore`，命令退出码为 0。

### Task 2: 创建双语 README 和仓库截图

**Files:**
- Create: `README.md`
- Create: `README_EN.md`
- Create: `docs/images/create-session.png`
- Create: `docs/images/image-preview.png`
- Create: `docs/images/history.png`

- [ ] **Step 1: 复制已验证的模拟器截图**

Run:

```bash
mkdir -p docs/images
cp build/emulator/cat-create-fixed.png docs/images/create-session.png
cp build/emulator/cat-restored-preview.png docs/images/image-preview.png
cp build/emulator/history-fixed-return.png docs/images/history.png
file docs/images/*.png
```

Expected: 三个文件均为 `1080 x 2340` PNG。

- [ ] **Step 2: 创建中文主页**

`README.md` 必须使用以下章节和事实：

```markdown
# ImgAd

[English](README_EN.md)

ImgAd 是一个 Android 10 及以上版本可用的本地优先 AI 图片客户端。它支持配置多个 OpenAI Images API 兼容供应方与模型，完成文生图、参考图编辑、历史管理、图片预览、保存、分享和本地归档。

## 主要功能

- 配置多个 OpenAI 兼容供应方，安全保存 API Key。
- 从供应方获取模型，并选择默认供应方和默认模型。
- 文生图、参考图编辑、蒙版和多图能力按模型配置启用。
- 长耗时生成、取消、失败详情和重试。
- 会话历史、图片缩略图、全屏预览和缩放。
- 保存到系统图库、通过 Android 分享面板分享。
- 导出和导入本地数据；默认归档不包含 API Key。

## 界面

<p align="center">
  <img src="docs/images/create-session.png" width="30%" alt="创作详情" />
  <img src="docs/images/image-preview.png" width="30%" alt="图片预览" />
  <img src="docs/images/history.png" width="30%" alt="历史记录" />
</p>

## 系统要求

- Android 10 / API 29 或更高版本
- JDK 17
- Android SDK 35

## 构建与安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 配置供应方

1. 打开“设置”，选择“新增供应方”。
2. 填写供应方名称、OpenAI 兼容 API 地址和 API Key。
3. 点击“获取模型”，选择需要导入的图片模型并保存。
4. 设置默认供应方和默认模型，然后返回“创作”。

不同供应方对尺寸、质量、编辑、蒙版和多图参数的支持可能不同，请以供应方接口文档为准。

## 测试

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

项目已在 Android 15 / API 35 模拟器上验证。真机仍建议覆盖 Android 10、Android 14/15、图片选择器、系统图库、分享和后台恢复流程。

## 隐私与安全

- API Key 使用 Android Keystore 支持的加密存储，不写入普通 Room 字段。
- 图片、会话和配置默认保存在应用本地。
- 默认导出不包含 API Key；只有用户明确选择时才会使用密码加密密钥段。
- 生成请求会发送到用户自行配置的供应方，请在使用前确认其隐私政策和计费规则。

## 当前限制

- 仓库不提供已签名的正式 Release APK。
- 不包含账号、云同步或社区功能。
- 兼容性依赖供应方是否实现 OpenAI 风格的图片接口。
- 本项目当前未声明开源许可证。
```

- [ ] **Step 3: 创建英文副本**

`README_EN.md` 必须与中文主页章节一一对应，使用以下完整内容：

```markdown
# ImgAd

[简体中文](README.md)

ImgAd is a local-first AI image client for Android 10 and later. It supports multiple OpenAI Images API-compatible providers and models, text-to-image generation, reference-image editing, local history, image preview, saving, sharing, and archive import/export.

## Features

- Configure multiple OpenAI-compatible providers and store API keys securely.
- Fetch provider models and select default providers and models.
- Enable generation, reference editing, masks, and multiple images according to model capabilities.
- Support long-running requests, cancellation, failure details, and retries.
- Browse session history, thumbnails, full-screen previews, and zoomable images.
- Save through Android MediaStore and share through the system chooser.
- Export and import local data; API keys are excluded by default.

## Screenshots

<p align="center">
  <img src="docs/images/create-session.png" width="30%" alt="Creation session" />
  <img src="docs/images/image-preview.png" width="30%" alt="Image preview" />
  <img src="docs/images/history.png" width="30%" alt="History" />
</p>

## Requirements

- Android 10 / API 29 or later
- JDK 17
- Android SDK 35

## Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configure a provider

1. Open Settings and select Add provider.
2. Enter a provider name, an OpenAI-compatible API base URL, and an API key.
3. Select Fetch models, choose the image models to import, and save.
4. Set the default provider and model, then return to Create.

Provider support for sizes, quality levels, editing, masks, and multiple images varies. Consult the provider documentation before use.

## Tests

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

The project has been verified on an Android 15 / API 35 emulator. Physical-device testing should still cover Android 10, Android 14/15, the photo picker, MediaStore, sharing, and background recovery.

## Privacy and security

- API keys use Android Keystore-backed encrypted storage and are not stored in ordinary Room columns.
- Images, sessions, and configuration remain local by default.
- API keys are excluded from exports by default. Secret export requires explicit selection and password-based encryption.
- Generation requests are sent to the provider configured by the user. Review that provider's privacy policy and billing terms before use.

## Current limitations

- The repository does not provide a signed production APK.
- Accounts, cloud sync, and community features are out of scope.
- Compatibility depends on the provider implementing OpenAI-style image endpoints.
- No open-source license has been declared yet.
```

- [ ] **Step 4: 验证双语链接和图片引用**

Run:

```bash
test -f README.md
test -f README_EN.md
test -f docs/images/create-session.png
test -f docs/images/image-preview.png
test -f docs/images/history.png
rg -n 'README_EN.md|docs/images/' README.md
rg -n 'README.md|docs/images/' README_EN.md
```

Expected: 所有文件存在，两份 README 均包含语言切换链接和三个仓库相对截图路径。

### Task 3: 发布前安全与质量检查

**Files:**
- Inspect: entire repository
- Verify: `README.md`, `README_EN.md`, `.gitignore`

- [ ] **Step 1: 检查候选提交清单和大文件**

Run:

```bash
git status --short
git ls-files --others --exclude-standard
find . -type f -not -path './.git/*' -not -path './.gradle/*' -not -path './.kotlin/*' -not -path '*/build/*' -size +10M -print
```

Expected: 候选文件仅为源码、Gradle 配置、Room schema、文档和三张截图；没有超过 10 MiB 的候选文件。

- [ ] **Step 2: 扫描密钥和签名材料**

Run:

```bash
rg -n --hidden -g '!.git/**' -g '!.gradle/**' -g '!.kotlin/**' -g '!**/build/**' 'sk-[A-Za-z0-9_-]{20,}' .
rg -n --hidden -g '!.git/**' -g '!.gradle/**' -g '!.kotlin/**' -g '!**/build/**' 'BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY' .
find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '*.pem' -o -name '*.p12' -o -name '*.pfx' \) -not -path './.git/*' -print
```

Expected: 三个命令均不返回候选提交中的敏感内容。若命中，停止发布并逐项确认，不能仅依赖 `.gitignore`。

- [ ] **Step 3: 运行完整验证**

Run:

```bash
JAVA_HOME=/home/idrl/.cache/img-ad-toolchain/jdk17 \
ANDROID_HOME=/home/idrl/.cache/img-ad-toolchain/android-sdk \
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`，所有 JVM 和 Android 测试 0 失败，lint 0 错误，Debug APK 构建成功。

### Task 4: 安装并认证 GitHub CLI

**Files:**
- Modify: system package database through APT
- Verify: GitHub CLI credential store outside the project

- [ ] **Step 1: 安装 GitHub CLI**

Run:

```bash
sudo apt-get update
sudo apt-get install -y gh
gh --version
```

Expected: `gh version` 正常输出。若 `sudo` 需要密码，暂停并由用户完成授权。

- [ ] **Step 2: 登录 GitHub**

Run:

```bash
gh auth login --hostname github.com --git-protocol https --web
gh auth status
```

Expected: 浏览器或设备码授权成功，`gh auth status` 显示已登录 GitHub，Git 协议为 HTTPS。

- [ ] **Step 3: 检查远程仓库冲突**

Run:

```bash
owner="$(gh api user --jq .login)"
gh repo view "$owner/img-ad" --json nameWithOwner,visibility
```

Expected: 命令以“仓库不存在”失败。若仓库已存在，停止，不覆盖或推送现有仓库。

### Task 5: 初始化、提交并创建公开仓库

**Files:**
- Create: `.git/`
- Stage: all non-ignored project files
- Remote: `<authenticated-owner>/img-ad`

- [ ] **Step 1: 初始化仓库并设置本地提交身份**

Run:

```bash
git init -b main
login="$(gh api user --jq .login)"
user_id="$(gh api user --jq .id)"
git config user.name "$(gh api user --jq '.name // .login')"
git config user.email "${user_id}+${login}@users.noreply.github.com"
```

Expected: 当前分支为 `main`，用户名和 noreply 邮箱仅写入本仓库配置。

- [ ] **Step 2: 暂存并复核首次提交范围**

Run:

```bash
git add --all
git status --short
git diff --cached --stat
git diff --cached --check
```

Expected: 不包含 `build/`、`app/build/`、`.gradle/`、`.kotlin/`、APK、密钥或签名文件；`git diff --cached --check` 无错误。

- [ ] **Step 3: 创建首次提交**

Run:

```bash
git commit -m "Initial Android AI image client"
git status --short
```

Expected: 提交成功，工作区为空。

- [ ] **Step 4: 创建公开 GitHub 仓库并推送**

Run:

```bash
gh repo create img-ad --public --source=. --remote=origin --push
git remote -v
git status -sb
gh repo view --json nameWithOwner,url,visibility,defaultBranchRef
```

Expected: `origin` 指向当前认证用户的 `img-ad`，可见性为 `PUBLIC`，默认分支为 `main`，本地 `main` 跟踪 `origin/main` 且无未提交文件。
