# 懒狗输入法 Android RC 出包手册

最后更新：2026-08-12

这份手册用于当前 `langou-ime/android` 仓库在 GitHub Actions 上产出可测试的 RC 签名包，绕开本机 Codex 沙箱对 Gradle / Android Studio 的限制。

## 适用场景

- 本机终端构建被 `file-lock/socket` 沙箱限制卡住。
- Android Studio 在当前代理环境中无法正常启动或会因 `sysctl` / `DirectoryLock` 失败崩溃。
- 需要尽快拿到包含最新修复的 APK，在真机上验收：
  - 中文输入
  - 26 键 / 9 键切换
  - AI 默认开启
  - 聊天上下文读取
  - 首条建议速度

## 前提

1. GitHub 组织 `langou-ime` 已存在。
2. 仓库远端为：
   - `origin = https://github.com/langou-ime/android.git`
   - `upstream = https://github.com/osfans/trime.git`
3. GitHub 仓库已经配置 Android release 所需 Secrets：
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
4. 当前分支包含待测试修复。

## 1. 刷新 GitHub CLI 登录

当前已知问题：本机 `gh auth status -h github.com` 显示 token 无效。

在仓库根目录执行：

```bash
gh auth refresh -h github.com
```

如果提示网页登录，浏览器确认即可。

验证：

```bash
gh auth status -h github.com
```

期望结果：不再出现 `The token ... is invalid.`

## 2. 推送当前分支

当前工作分支示例：

```bash
git branch --show-current
```

如果仍是本轮修复分支，例如：

```text
codex/android-ci-builder-fallback
```

则推送：

```bash
git push -u origin codex/android-ci-builder-fallback
```

## 3. 在 GitHub 手动触发 RC 构建

仓库页面进入：

- `Actions`
- 选择 `Langou Android signed release`
- 点击 `Run workflow`

输入项：

- `release_mode = rc`

触发后，工作流会：

- 在 GitHub runner 上执行 `testReleaseUnitTest`
- 执行 `lintRelease`
- 执行 `assembleRelease`
- 对签名 APK 做 `apksigner verify`
- 生成 `SHA256SUMS`
- 上传 RC 产物

RC 产物命名规则：

```text
langou-ime-android-rc-<GITHUB_SHA>.apk
```

## 4. 下载 RC 产物

工作流成功后，在该次运行的 Artifacts 中下载：

- `signed-langou-ime-android-rc-<GITHUB_SHA>.apk`

其中包含：

- `langou-ime-android-rc-<GITHUB_SHA>.apk`
- `SHA256SUMS`

## 5. 安装到 Android 真机

如果本机正常可用 `adb`，执行：

```bash
adb install -r /path/to/langou-ime-android-rc-<GITHUB_SHA>.apk
```

如果 `adb` 当前环境仍受限制，就手动把 APK 发到手机安装。

## 6. 真机验收清单

至少验证：

1. 中文输入正常，不再像“英文键盘硬凑拼音”。
2. 26 键和 9 键能双向切换。
3. 首次设置向导尽量少点：
   - 启用输入法
   - 选中输入法
   - 开启聊天理解
4. 微信、QQ、企业微信、飞书等输入框能正常抓到上下文。
5. AI 默认开启。
6. 不输入草稿时，AI 也能基于聊天上下文出建议。
7. 首条建议等待时间明显优于旧包。
8. 密码页、支付页、银行页、安全页不采集。

## 7. RC 通过后再做的事

只有在 RC 通过后，才继续：

1. 打正式 tag：

```bash
git tag v1.0.0
git push origin v1.0.0
```

2. 触发正式工作流产物：

```text
langou-ime-android-v1.0.0.apk
```

3. 用同一份正式 APK 更新：
   - 官网下载链接
   - API release manifest
   - GitHub Release

## 当前已知现实

- 当前 Codex 运行环境会拦截：
  - Gradle file-lock contention socket
  - Android Studio `DirectoryLock/sysctl`
  - `adb` daemon 监听
- 所以 GitHub runner 是当前最稳的 Android 出包路径。
