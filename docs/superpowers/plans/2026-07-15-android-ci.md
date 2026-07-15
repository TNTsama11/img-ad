# Android CI 自动构建实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为所有 push/PR 提供 JVM 测试、lint、Debug APK 构建与 artifact，并在 main/手动触发时运行 API 35 模拟器测试。

**Architecture:** 单一 GitHub Actions workflow 包含 `build` 和 `instrumentation` 两个 job。`instrumentation` 依赖快速 job 并通过事件条件限制执行；两个 job 都从干净 Runner 独立配置 JDK、Android SDK 和 Gradle 缓存。

**Tech Stack:** GitHub Actions、Gradle、Android Emulator、JDK 17、Ruby YAML parser

---

### Task 1: 定义并验证工作流结构

**Files:**
- Create: `.github/workflows/android-ci.yml`

- [ ] **Step 1: 运行缺失文件检查并确认失败**

```bash
test -f .github/workflows/android-ci.yml
```

Expected: FAIL，文件不存在。

- [ ] **Step 2: 创建工作流**

写入以下完整内容：

```yaml
name: Android CI

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: android-ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Test, lint, and build
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run unit tests, lint, and debug build
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: imgad-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
          retention-days: 14

  instrumentation:
    name: API 35 instrumentation tests
    needs: build
    if: github.event_name == 'workflow_dispatch' || (github.event_name == 'push' && github.ref == 'refs/heads/main')
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run instrumentation tests
        uses: ReactiveCircus/android-emulator-runner@v2
        with:
          api-level: 35
          arch: x86_64
          profile: pixel_5
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          script: ./gradlew connectedDebugAndroidTest

      - name: Upload instrumentation results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-instrumentation-results
          path: |
            app/build/reports/androidTests/connected/
            app/build/outputs/androidTest-results/connected/
          if-no-files-found: ignore
          retention-days: 14
```

- [ ] **Step 3: 解析 YAML 并断言关键结构**

```bash
ruby -e 'require "yaml"; w = YAML.load_file(".github/workflows/android-ci.yml"); abort unless w["permissions"] == {"contents"=>"read"}; abort unless w.dig("jobs", "build"); abort unless w.dig("jobs", "instrumentation", "needs") == "build"'
rg -n 'testDebugUnitTest lintDebug assembleDebug|app-debug.apk|api-level: 35|connectedDebugAndroidTest|retention-days: 14' .github/workflows/android-ci.yml
```

Expected: Ruby 退出码 0，rg 命中快速验证、APK、API 35、仪器测试和保留期限。

### Task 2: 本地验证并提交工作流

**Files:**
- Verify: `.github/workflows/android-ci.yml`
- Verify: Android project

- [ ] **Step 1: 运行本地快速任务**

```bash
JAVA_HOME=/home/idrl/.cache/img-ad-toolchain/jdk17 \
ANDROID_HOME=/home/idrl/.cache/img-ad-toolchain/android-sdk \
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`，JVM 测试 0 失败，lint 0 错误，Debug APK 存在。

- [ ] **Step 2: 检查工作区和 diff**

```bash
git diff --check
git status -sb
git diff -- .github/workflows/android-ci.yml
```

Expected: 仅计划文档和 workflow 为新增文件，diff check 无错误。

- [ ] **Step 3: 提交自动构建**

```bash
git add .github/workflows/android-ci.yml docs/superpowers/plans/2026-07-15-android-ci.md
git commit -m "ci: add Android build workflow"
```

Expected: 提交包含 workflow 和实施计划。

### Task 3: 推送并验证首次 GitHub Actions 运行

**Files:**
- Remote workflow: `TNTsama11/img-ad/actions/workflows/android-ci.yml`

- [ ] **Step 1: 推送 main**

```bash
git push origin main
```

Expected: push 成功并触发 `Android CI`。

- [ ] **Step 2: 确认 GitHub 识别工作流**

```bash
gh workflow view android-ci.yml --repo TNTsama11/img-ad
gh run list --repo TNTsama11/img-ad --workflow android-ci.yml --limit 3
```

Expected: workflow 状态 active，最新运行对应当前 main SHA。

- [ ] **Step 3: 等待首次运行**

```bash
run_id="$(gh run list --repo TNTsama11/img-ad --workflow android-ci.yml --branch main --limit 1 --json databaseId --jq '.[0].databaseId')"
gh run watch "$run_id" --repo TNTsama11/img-ad --exit-status
```

Expected: `build` 和 `instrumentation` 均成功。

- [ ] **Step 4: 复核 job 与 artifact**

```bash
gh run view "$run_id" --repo TNTsama11/img-ad --json conclusion,jobs,url
gh api "repos/TNTsama11/img-ad/actions/runs/$run_id/artifacts" --jq '.artifacts[].name'
```

Expected: conclusion 为 success，job 包含快速构建和 API 35 仪器测试，artifact 包含 `imgad-debug-apk` 和 `android-instrumentation-results`。

- [ ] **Step 5: 最终状态检查**

```bash
git status -sb
test "$(git rev-parse HEAD)" = "$(gh api repos/TNTsama11/img-ad/commits/main --jq .sha)"
```

Expected: 工作区干净，本地与远程 main SHA 一致。
