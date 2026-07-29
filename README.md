<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community
SPDX-FileCopyrightText: 2026 Langou Input Method contributors

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 懒狗输入法 Android

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android CI](https://github.com/langou-ime/android/actions/workflows/langou-android-ci.yml/badge.svg)](https://github.com/langou-ime/android/actions/workflows/langou-android-ci.yml)

懒狗输入法是面向 Android 8.0 及以上系统的免费中文输入法。项目以
[Trime v3.3.10](https://github.com/osfans/trime/tree/v3.3.10) 和
[RIME](https://rime.im/) 为输入核心，保留 Trime 上游 Git 历史、版权及许可；
懒狗品牌、AI 上下文助手、三款皮肤、账号与安全更新能力为本项目新增部分。

正式版本只从 [langou.tech](https://langou.tech/) 和本仓库的 GitHub Release
提供。应用 ID 固定为 `tech.langou.ime`。任何名称含
`DEBUG-SIGNED-INTERNAL` 的文件都不是正式发布包。

## v1.0.0 能力

- 全拼、9 键、英文、数字、符号、Emoji、模糊拼音、候选翻页与本地词频学习。
- 奶油懒狗、樱桃汽水、月光软糖三款内置皮肤。
- 在聊天上下文变化后生成最多三条 AI 建议；点击只写入输入框，永不自动发送。
- Android 11+ 使用无障碍截图；Android 8–10 使用经用户授权的
  MediaProjection；OCR 在设备本地完成。
- 游客可使用离线输入与 AI；登录只用于历史和设置同步。

密码框、银行/支付、密码管理器和系统安全页面一律禁止采集。截图只存在于内存，
不上传、不落盘，OCR 后立即释放。网络或 AI 故障不会阻塞 RIME 的离线输入。

## 构建

需要 Android Studio JBR、Android SDK 36、NDK/CMake 及 Git 子模块：

```sh
git clone --recurse-submodules https://github.com/langou-ime/android.git
cd android
./gradlew testDebugUnitTest assembleDebug
```

OpenCC 1.4.1 的可移植词典已按来源和哈希固定在
`app/src/main/opencc-1.4.1/`，普通构建不需要 Python。正式 Release 还需要仓库
外的 Android keystore 和 Ed25519 发布清单公钥；签名材料不得提交到 Git。

```properties
# keystore.properties（仅示例；请勿提交）
storeFile=/absolute/path/to/android-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```sh
LANGOU_RELEASE_PUBLIC_KEY_BASE64=... \
  ./gradlew testReleaseUnitTest lintRelease assembleRelease
```

公开版本的完整检查由 `.github/workflows/langou-android-release.yml` 执行。生产
APK 的固定文件名为 `langou-ime-android-v1.0.0.apk`。

## 开源、隐私与上游

本仓库遵循 [GPL-3.0](LICENSE)。Trime/RIME 原始版权、内部类名、上游提交历史
和许可声明均保留。第三方组件及许可证由 AboutLibraries 在 Release 构建中生成；
固定输入数据的来源记录在 `third_party/` 与各 `PROVENANCE.md` 文件中。

主要上游组件：

- Trime：GPL-3.0-or-later
- librime：BSD-3-Clause
- OpenCC：Apache-2.0
- Boost：Boost Software License 1.0
- PP-OCRv6 / PaddleOCR：Apache-2.0
- ONNX Runtime：MIT
- OpenCV：Apache-2.0
- Bouncy Castle：MIT

感谢 [osfans/trime](https://github.com/osfans/trime) 的维护者与历年贡献者，以及
RIME、OpenCC、PaddleOCR 等开源社区。懒狗输入法的发行问题请提交到本仓库，
不要向 Trime 上游社区请求懒狗专属功能支持。
