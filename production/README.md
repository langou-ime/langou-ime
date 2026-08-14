# 懒狗输入法 v1.0.0 生产工程

这不是遗留工程的继续堆叠，而是三套保留成熟上游历史的干净产品工程，以及独立的
私有后端、官网下载区和生产基础设施。

| 模块 | 基线 | 当前可重复验证 |
| --- | --- | --- |
| Backend | FastAPI + PostgreSQL + Redis + Alembic | 57 项 pytest、Ruff、pip-audit；单服务器 HTTPS 生产 API 已通过真实 MiMo SSE 冒烟 |
| Android | Trime v3.3.10 + RIME + PP-OCRv6 | 60 项 Release 测试、Lint、四 ABI、R8 和正式 keystore 签名通用 APK 已通过 |
| Windows | Weasel 0.17.4 + .NET 8 助手 + NSIS EXE 安装器 | managed 契约测试通过；EXE 打包/签名链路收敛中，待 Windows runner 真实验收 |
| Website | 品牌化 Next.js 官网 | 官网测试与生产构建通过；首页已完成懒狗主视觉与下载区重做 |
| Infra | `122.51.32.117` 单服务器 + 独立 `api.langou.tech` HTTPS | 6 项隔离契约；公网 ready、证书 SAN、跳转与文档关闭已验证 |

## 本地验证

后端：

```bash
cd production/backend
.venv/bin/pytest -q tests
.venv/bin/ruff check src tests scripts
```

Android（需要 Android Studio JBR、SDK 36、已缓存依赖和发布清单公钥）：

```bash
cd production/android
LANGOU_RELEASE_PUBLIC_KEY_BASE64=... ./gradlew testReleaseUnitTest lintRelease
```

Windows managed 模块（macOS 只能编译 WPF managed 部分，TSF/EXE 正式打包必须用 Windows）：

```powershell
dotnet restore .\LangouAssistant.Tests\LangouAssistant.Tests.csproj --locked-mode
dotnet test .\LangouAssistant.Tests\LangouAssistant.Tests.csproj --no-restore
```

官网：

```bash
cd production/website
npm ci
npm test
npm run build
```

## 不可伪造的发布链

正式 APK 使用本机仓库外的 Android 生产密钥；正式 Windows EXE 需要 SignPath
Foundation Authenticode。
两端还共享一把独立 Ed25519 发布清单公钥。最终签名文件生成后，运行
`production/backend/scripts/release.py` 从文件本体计算大小与 SHA-256 并签清单；
后端、客户端更新和官网使用同一份清单。

任何内部 RC、Debug 签名 APK 或未签名 EXE 都不能进入公开下载目录。
