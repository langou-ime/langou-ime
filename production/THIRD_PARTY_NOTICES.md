# 第三方与许可证说明

- Android 基于 Trime v3.3.10 与 RIME，按 GPL-3.0-or-later 发布，并保留完整上游
  Git 历史、源码许可证和第三方 RIME 数据许可证。
- Windows 基于 Weasel 0.17.4 与 librime 1.13.1，按 GPL-3.0-or-later 发布，并
  保留完整上游 Git 历史、源码许可证和版权资源。
- PP-OCRv6 模型与 Paddle/OpenVINO/OpenCV/ONNX Runtime 的来源、版本、哈希与
  许可证分别记录在 Android `ppocr-sdk` 和 Windows `LangouAssistant/Models`。
- 奶油懒狗角色为本项目生成的原创资产；生成记录、源图、导出图和内部相似性筛查在
  `production/assets/mascot/GENERATION.md`。

发布前必须从最终依赖锁文件生成 SBOM，并人工确认所有 NOTICE、模型和字体许可随
GPL 源码与二进制发行物完整提供。
