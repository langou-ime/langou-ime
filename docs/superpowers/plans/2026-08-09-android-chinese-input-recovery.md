# Android Chinese Input Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make clean Android installs deploy complete RIME Chinese data and expose usable full-pinyin and pinyin-9-key keyboards with production-safe labels.

**Architecture:** The Gradle checksum manifest is the single source of truth used by `DataManager.sync()`, so it must merge static assets with generated RIME assets. Runtime validation then proves both schemes are deployed, while theme key presets provide localized display labels without changing RIME key semantics.

**Tech Stack:** Kotlin, Gradle convention plugins, Kotest, RIME/Trime YAML, Android instrumentation, adb.

---

### Task 1: Include generated RIME assets in the deployment manifest

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/build-logic/convention/src/main/kotlin/DataChecksumsPlugin.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/build.gradle.kts`
- Test: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/data/base/BundledRimeDataTest.kt`

- [ ] **Step 1: Keep the regression assertion for generated schemes**

```kotlin
checksums.keys shouldContain "shared/default.yaml"
checksums.keys shouldContain "shared/luna_pinyin.schema.yaml"
checksums.keys shouldContain "shared/langou_t9.schema.yaml"
checksums.keys shouldContain "shared/essay.txt"
```

- [ ] **Step 2: Run the test against a static-only checksum manifest**

Run: `./gradlew :app:testDebugUnitTest --tests '*BundledRimeDataTest*'`

Expected: FAIL because `shared/default.yaml` or another generated file is absent.

- [ ] **Step 3: Merge static and generated asset roots in the checksum task**

```kotlin
val assetRoots = project.files(
    project.layout.projectDirectory.dir("src/main/assets"),
    project.layout.buildDirectory.dir("generated/rimeAssets"),
)
```

The task must use normalized relative paths, reject duplicate paths with different content, and write one deterministic sorted `checksums.json`.

- [ ] **Step 4: Make unit tests depend on checksum generation**

```kotlin
tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("generateDataChecksums"))
}
```

- [ ] **Step 5: Verify build logic and Android regression**

Run: `./gradlew :build-logic:convention:test :app:testDebugUnitTest --tests '*BundledRimeDataTest*'`

Expected: all tests PASS and generated `checksums.json` contains both schemes.

- [ ] **Step 6: Commit**

```bash
git add build-logic/convention/src/main/kotlin/DataChecksumsPlugin.kt app/build.gradle.kts app/src/test/java/com/osfans/trime/data/base/BundledRimeDataTest.kt
git commit -m "fix: deploy generated Chinese input data"
```

### Task 2: Validate full-pinyin, pinyin-9-key, and key labels

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/assets/shared/trime.yaml`
- Test: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/data/base/BundledRimeDataTest.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/theme/LangouKeyboardLabelsTest.kt`

- [ ] **Step 1: Add failing assertions for scheme and key presentation**

```kotlin
yaml shouldContain "langou_t9"
yaml shouldNotContain "label: BackSpace"
yaml shouldNotContain "label: Shift_L"
```

- [ ] **Step 2: Run targeted tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*BundledRimeDataTest*' --tests '*LangouKeyboardLabelsTest*'`

Expected: FAIL for the unlocalized function-key presentation.

- [ ] **Step 3: Define short localized key presets**

```yaml
preset_keys:
  Shift_L: {label: "⇧", send: Shift_L}
  BackSpace: {label: "⌫", repeatable: true, send: BackSpace}
  Return: {label: "发送", send: Return}
  langou_t9: {label: "9键", select: langou_t9}
  luna_pinyin: {label: "拼音", select: luna_pinyin}
```

Preserve editor-specific enter behavior and existing schema-switch semantics.

- [ ] **Step 4: Verify YAML and unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*BundledRimeDataTest*' --tests '*LangouKeyboardLabelsTest*'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/shared/trime.yaml app/src/test/java/com/osfans/trime/data/base/BundledRimeDataTest.kt app/src/test/java/com/osfans/trime/langou/theme/LangouKeyboardLabelsTest.kt
git commit -m "fix: expose polished pinyin keyboards"
```

### Task 3: Clean-build and verify on the API 36 phone

**Files:**
- Verify: `/Users/sommerzhao/Documents/懒狗/production/android/app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Build from clean committed state**

Run: `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Reinstall and complete setup**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 3: Verify private RIME deployment**

Run: `adb shell run-as tech.langou.ime.debug find files/shared -maxdepth 1 -type f`

Expected: output includes `default.yaml`, `luna_pinyin.schema.yaml`, `langou_t9.schema.yaml`, and their dictionaries.

- [ ] **Step 4: Run IME instrumentation**

Run: `./gradlew :app:connectedDebugAndroidTest`

Expected: all input method service tests PASS.

- [ ] **Step 5: Perform visual Chinese input checks**

In WeChat and QQ, type `nihao` with 26-key and `64426` with 9-key; both must offer `你好`. Confirm Shift, backspace, and enter fit without displaying internal key names.

- [ ] **Step 6: Record evidence and commit any test-only evidence metadata**

Run: `git status --short`

Expected: clean worktree after any intentional evidence update is committed.
