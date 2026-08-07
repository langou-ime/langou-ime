# 懒狗输入法 for Windows

懒狗输入法是面向 Windows 10/11 x64 的免费中文输入法。输入核心基于
[RIME](https://rime.im/) 与
[Weasel 0.17.4](https://github.com/rime/weasel/tree/0.17.4)，保留完整上游
Git 历史；“懒狗助手”负责本地上下文识别、PP-OCRv6、AI 建议、账号、设置和
签名更新，不重新实现 TSF。

## v1.0.0

- 全拼、9 键、英文、数字、符号、Emoji、模糊拼音和本地词频学习。
- 奶油懒狗、樱桃汽水、月光软糖三款内置视觉。
- 微信、QQ、企业微信、钉钉、飞书、WhatsApp、Telegram、Discord 上下文识别。
- UIA 优先，失败时只在本机内存中截图并使用 PP-OCRv6；截图不上传、不落盘。
- AI 自动生成最多三条建议，只有用户点击后才写入当前输入框，永不自动发送。
- 游客可使用普通输入和 AI；登录仅用于历史与设置同步。

密码框、支付/银行、密码管理器、系统安全桌面和受保护窗口一律禁止采集。AI 或
网络故障不会阻塞 RIME 的离线输入。

## 架构

- `WeaselTSF` / `WeaselServer`：上游 TSF、IPC、RIME 和候选窗。
- `LangouAssistant`：.NET 8 WPF 助手，UIA、内存截图、PP-OCRv6 和账号界面。
- `LangouAssistant.Core`：可跨平台测试的协议、隐私、API 与更新验证。
- `\\.\pipe\Langou.Ime.v1`：同一用户命名管道，版本化 JSON；只允许
  `commit_text` 和固定枚举的 `set_theme`。
- `LangouInstaller`：Windows 10/11 x64、per-machine MSI。

## 构建

正式构建只在 Windows 2022 GitHub-hosted runner 上进行：

1. 固定 Boost 1.84.0 和 librime 1.13.1。
2. 编译 x86/x64 TSF、RIME 和原生命名管道测试。
3. 运行 .NET 安全/契约测试并发布 self-contained x64 助手。
4. 从白名单文件组成干净 payload，再生成 MSI。
5. 内部 CI 产物名称包含 `UNSIGNED-INTERNAL`，保留 7 天，禁止公开发布。

公开版必须经过 [签名策略](CODE_SIGNING_POLICY.md) 中的 SignPath 或 OV
Authenticode 流程；未签名 MSI 不进入 GitHub Release、服务器或官网。

## 开源与上游

本仓库遵循 [GPL-3.0](LICENSE.txt)。原始 Weasel/RIME 版权、内部注册名、
上游提交历史和许可声明均保留；“懒狗输入法”品牌、助手和原创角色是本项目的
新增部分。输入数据与 OCR 模型的固定版本和哈希分别记录在
`rime-data/UPSTREAM.md` 和 `LangouAssistant/Models/PP-OCRv6-tiny/UPSTREAM.md`。

主要上游组件：

- Weasel：GPL-3.0；librime：BSD-3-Clause
- Boost：Boost Software License 1.0
- WTL：Microsoft Public License
- PP-OCRv6 / PaddleOCR：Apache-2.0
- OpenVINO：Apache-2.0
- OpenCvSharp：Apache-2.0
- Bouncy Castle：MIT
- WiX Toolset 5.0.2：Microsoft Reciprocal License；仅用于隔离 CI 中生成
  MSI，不进入最终安装包

完整许可证文本随源码和安装包提供。
