# v1.0.0 正式发布门禁

只有所有必选项完成后才能创建公开 `v1.0.0` Release 或更新官网主下载。

## 已完成的工程门禁

- [x] 遗留目录保持只读，生产工程位于 `production/`。
- [x] 后端 API、访客/短信/合并、刷新令牌、加密历史、设置、SSE、模型降级、
  限流/预算熔断、发布清单和数据库迁移已实现。
- [x] Android 包名、版本、API 26–36、RIME 输入核心、三皮肤、上下文/OCR、
  AI 建议、账号和签名更新清单已接线。
- [x] Android 60 项 Release 单测、Lint、四 ABI 原生编译、R8/资源收缩和正式
  keystore 签名已通过；签名证书指纹已独立复核。
- [x] Windows Weasel TSF、命名管道、WPF 助手、UIA/OCR、AI、账号、更新、
  EXE 安装器链路和 SignPath 深度签名工作流已实现。
- [x] 后端 57 项测试与 Ruff 通过；Windows 83 项测试通过；官网 6 项测试通过。
- [x] 官网只修改下载实现，其他遗留文件 SHA-256 不变；下载区读取签名发布清单。
- [x] 单服务器 Compose、Nginx、日志轮转和本机健康检查契约已验证；香港、
  WireGuard、站外备份和外发告警已从生产拓扑移除。
- [x] `api.langou.tech` Let’s Encrypt 单域名证书、HTTP→HTTPS、`/ready`、
  数据库/Redis 就绪及生产 Swagger 关闭已通过公网验证。
- [x] Android 正式 keystore 与 Ed25519 私钥位于当前 Mac 仓库外，文件权限为
  `0600`，Android 密码已进入 macOS 钥匙串。
- [x] Android/Windows 公开仓库隐私政策与 30 天加密历史、内存 OCR、MiMo、
  短信及默认关闭诊断的真实数据流一致；客户端只链接品牌仓库政策。

## 必须由真实外部环境完成

- [ ] 在 GitHub 组织 `langou-ime` 下创建并启用单一公开主仓库，统一承载
  Android、Windows、backend、website、assets 与 docs，配置保护分支和生产
  Environment 审批。
- [ ] GitHub 单仓库启用后，把 Android keystore 与密码放入受保护的
  Environment secrets，绝不写入仓库。
- [x] Ed25519 发布清单密钥已生成；公钥已固定到 Android、Windows 和后端发布
  流程，私钥只保存在本机仓库外。
- [ ] SignPath Foundation 项目获批并导入
  `windows/.signpath/artifact-configurations/langou-windows-exe.xml`；否则提供有效
  OV 证书受管签名服务。禁止发布未签名 EXE。
- [ ] 创建短信专用阿里云 RAM 用户及新 AccessKey，只允许 `dysms:SendSms`，
  并注入服务器；禁止重新使用旧主账号 AccessKey。
- [ ] 提供当前有效的 `122.51.32.117` root 密码，以复核容器只读根文件系统、
  UID、内部端口、迁移版本、Certbot timer 和遗留服务保留状态。旧文档中的两个
  密码均已失效。
- [x] Android `releaseRuntimeClasspath` CycloneDX SBOM、OSV 与许可证审查通过；
  后端 pip-audit、Windows NuGet/OSV、单仓库源码扫描及 Android/Windows
  上游历史扫描已通过。
- [ ] 完成 Windows 2022 CI 原生 TSF 编译、EXE 安装器打包、SignPath 深签与
  Authenticode 验证。

## RC 真机验收

- [ ] Android API 26/29/30/34/36 自动化矩阵通过；至少一台 Android 11+ 真机
  完成安装、启用输入法、26/9 键、候选学习、权限拒绝/恢复、无障碍截图、OCR、
  AI、账号、断网和卸载验收。
- [ ] Windows 10 21H2 与 Windows 11 x64 完成全新安装、升级、卸载、TSF 提交、
  多屏 DPI、睡眠唤醒、UIA/OCR、AI、账号、断网和清除数据验收。
- [ ] 微信、QQ、企业微信、钉钉、飞书、WhatsApp、Telegram、Discord 双端完成
  提取、分段、重复抑制、建议和点选写入；点选永不自动发送。
- [ ] 密码框、银行/支付、密码管理器、系统安全页和安全窗口证明零采集。
- [ ] 连续 24 小时稳定性、P95 唤起 ≤ 300 ms、AI 首条 ≤ 4 s、RC 崩溃率
  < 0.5%。

## 原子发布

- [x] 用生产密钥重建并验签
  `langou-ime-android-v1.0.0.apk`：99,348,157 字节，
  SHA-256 `b145d3a7133fc3e3c58886f58ee8eef29e47a7784502ba476d0d5a7e769bc0af`；
  本地 RC 发布清单已用 Ed25519 签名并二次验签。
- [ ] 用 SignPath/OV 深度签名并验签 EXE。
- [ ] 从最终文件生成 Android/Windows 签名发布清单，二次验签并写入后端。
- [ ] GitHub Release、单服务器下载目录、后端清单和官网下载区在同一变更窗口切换。
- [ ] 核对两个文件的版本、大小、SHA-256、签名、回滚与断网行为。
- [ ] 观察 48 小时，保留上一版安装包、镜像和数据库兼容回滚路径。
