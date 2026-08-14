# 懒狗输入法单仓库发布与官网重设计方案

## 背景

当前工作区已经包含四套可复用资产：

- `production/android/`：以 Trime 为基础的 Android 输入法工程
- `production/windows/`：以 Weasel 为基础的 Windows 输入法工程，且已存在 NSIS `exe` 安装器脚本
- `production/backend/`：单服务器可运行的 FastAPI 后端与发布清单接口
- `production/website/`：Next.js 官网实现，但当前视觉弱、信息密度低、下载链路仍围绕旧发布模型展开

新的正式目标是把这些内容收束成一个面向发布的产品体系，而不是继续维持“多仓库 + Windows MSI + 仅下载区改动”的旧方案。

## 目标

把懒狗输入法收束为一个可持续发布的成熟产品形态：

1. GitHub 使用单仓库承载 Android、Windows、后端与官网。
2. 多版本通过 GitHub Releases 统一分发，组织方式参考 OpenClaw 的“单仓库多资产发布”思路。
3. Windows 官网主下载产物改为可双击安装的 `.exe` 安装器，而不是 `.msi`。
4. `langou.tech` 官网从纯文字落地页升级为完整视觉版，补齐品牌图、场景图、功能图和下载展示。
5. 官网、后端发布清单、GitHub Release、客户端更新元数据四处必须指向同一组正式文件。

## 非目标

本轮不做以下事情：

- 不新增 iOS、macOS 或鸿蒙客户端
- 不在本轮做支付上线
- 不在本轮做皮肤商店
- 不因为单仓库而抹掉 Android/Windows 上游历史；保留上游引用与许可证说明即可

## 方案选择

### 方案 A：继续多仓库 + 只统一 Release 页面

优点：

- 迁移最少

缺点：

- 用户刚明确否定多仓库
- 官网、下载页、发布说明和版本管理会继续分散
- Android、Windows、后端、官网的版本同步成本高

### 方案 B：单仓库聚合，保留子目录边界

优点：

- 符合用户目标
- 既能统一发布，又不需要重写各子系统
- 方便统一版本、统一 changelog、统一 release 资产、统一官网链接
- 便于后续 GitHub Actions 统一生成 APK / EXE / manifest

缺点：

- 需要重新整理 git 远端与目录说明
- 需要改现有 CI / Release tooling / 官网链接

### 方案 C：单仓库但运行时全部并表重构

优点：

- 形式最“整齐”

缺点：

- 风险高，收益低
- 会打断已经存在的 Android / Windows / backend 产物

## 推荐方案

采用方案 B：单仓库聚合，保留 Android、Windows、后端、官网的目录边界。

这样能在最小破坏下满足新目标，同时尽快推进真正的可发布状态。

## 目标仓库结构

目标 GitHub 仓库建议为：

```text
langou-ime/
  android/
  windows/
  backend/
  website/
  assets/
  releases/
  docs/
```

对应到当前工作区时：

- `production/android` → `android`
- `production/windows` → `windows`
- `production/backend` → `backend`
- `production/website` → `website`
- `production/assets` → `assets`
- `production/releases` → `releases`

本地可以先在当前根仓库内完成目录与脚本收束，再推送到新的单仓库远端。

## GitHub Releases 设计

正式版采用单仓库多资产发布：

- Android：`langou-ime-android-v1.0.0.apk`
- Windows：`langou-ime-windows-x64-v1.0.0.exe`
- 哈希：`SHA256SUMS.txt`
- 发布说明：Release Notes
- 如有需要可追加：
  - `android.json`
  - `windows.json`
  - `checksums.sig`

GitHub Release 是对外发布事实源之一，但不是唯一事实源。后端 `/v1/releases/{platform}/latest` 仍保留，作为客户端检查更新与官网动态下载信息来源。

## Windows 发布设计

### 当前现状

当前 Windows 工程并不是只能产出 MSI。代码里已存在 NSIS 安装器链路，能生成 `.exe` 安装器：

- `production/windows/xbuild.bat`
- `production/windows/output/install.nsi`

这意味着目标不是“从零把 MSI 改成 EXE”，而是：

1. 把正式发布产物切换到 NSIS `exe`
2. 把 backend release tooling 从校验 `.msi` 改为校验 `.exe`
3. 把官网、下载文案、Windows 更新说明改成 `.exe`
4. 后续如仍需企业分发，可保留 MSI 为内部产物，但不对外主推

### 正式 Windows 文件名

```text
langou-ime-windows-x64-v1.0.0.exe
```

### 安装体验目标

- 用户下载后双击安装
- 安装器尽量减少路径/组件选择步骤
- 安装完成后自动：
  - 部署必要文件
  - 启动输入法服务
  - 给出“下一步去系统里启用输入法”的明确引导

### 风险

- Authenticode 签名链仍需真实 Windows 构建与签名验证
- Windows 输入法启用本身仍涉及系统设置，不能承诺完全零步骤，但安装器内部步骤要最少

## 官网重设计方案

## 设计方向

官网风格采用：

- 交互结构参考成熟输入法产品：信息分层清楚、下载入口直接、功能展示强
- 视觉气质采用“奶油懒狗 + 二次元可爱 + 女生友好”

不是直接复制搜狗的资产或界面，而是借鉴其：

- 导航结构
- 版块组织方式
- 键盘/功能展示逻辑
- 下载引导清晰度

## 视觉资产范围

官网需要新增和替换的资产分为四类：

1. 品牌资产
   - 懒狗主视觉
   - 站点 hero 主插画
   - 图标延展

2. 产品展示资产
   - Android 键盘 UI 展示图
   - Windows 候选栏 / 助手界面展示图
   - AI 回复建议场景图

3. 场景资产
   - 女生向聊天场景
   - 高效办公场景
   - 轻松社交场景

4. 页面装饰资产
   - 背景软糖形状
   - 奶油按钮高光
   - 可爱贴纸元素

这些素材可以用 ChatGPT Images 2.0 生成，然后人工挑选与裁切后放入 `assets/website/`。

## 官网页面范围

本轮不只改下载区，而是至少覆盖以下核心界面：

1. 首页
2. 下载页
3. 功能页 / 功能分区
4. 隐私页（保持真实策略）
5. 帮助/安装说明入口

“82个界面”的要求，在当前代码语境下更合理的解释不是做 82 个独立网页，而是把现有官网相关展示界面、组件状态、模块化内容从“纯文字”全面升级为带视觉、带卡片、带场景图的完整产品站体验。实现上应采用组件化页面与多模块展示，而不是机械制作 82 个互相重复的静态页面。

## 官网信息结构

首页至少应包含：

1. Hero 区
   - 品牌口号
   - 安卓 / Windows 下载按钮
   - 主视觉图

2. 核心价值区
   - 真正的 AI 上下文理解
   - 低操作成本
   - 输入与回复一体化

3. 重点功能区
   - 26 键 / 9 键
   - 自动上下文理解
   - AI 建议卡片
   - 离线普通输入

4. 安装与启用区
   - Android 如何启用
   - Windows 如何启用

5. 下载区
   - Android APK
   - Windows EXE
   - 版本号
   - SHA-256

6. 隐私与安全区
   - 敏感场景零采集
   - OCR 仅本地内存处理
   - 历史保存策略

## 发布事实源设计

正式版发布时，四处信息必须一致：

1. GitHub Release 资产
2. 服务器上的正式安装包
3. `/v1/releases/android/latest` 与 `/v1/releases/windows/latest`
4. 官网展示的版本、大小、SHA-256 与下载地址

后端继续提供 release manifest，但 Windows manifest 中的 URL 与文件名必须切换为 `.exe`。

## 代码改动边界

### Backend

需要修改：

- `production/backend/src/langou_backend/release_tooling.py`
- 对应 release tooling tests
- 可能涉及 release schema / fixture / manifest 生成脚本

目标：

- Windows 产物后缀从 `.msi` 改为 `.exe`
- 文件名规范更新
- 测试样例更新

### Website

需要重做或大改：

- `production/website/app/page.tsx`
- `production/website/app/globals.css`
- 翻译消息文件
- 下载区与安装指引文案
- 视觉资源引用方式

目标：

- 从“纯文字科技风简页”改成“完整品牌视觉官网”
- 下载信息动态读取 release manifest
- 明确 Windows 为 EXE 安装包

### Windows

需要确认与完善：

- NSIS 构建输出文件名
- GitHub Actions 是否产出 `.exe`
- EXE 签名与哈希流程

### GitHub / Release

需要从“多个 origin / 多个品牌仓库”改成单仓库方案。即使本地暂时保留子工程历史，也要对外形成统一发布入口。

## 测试与验收

### 必须通过

1. Backend release tooling tests
2. Website rendering / release manifest tests
3. Windows 产物文件名与 manifest 对齐
4. 官网下载区明确展示 Windows EXE
5. GitHub Release 方案文档清楚

### 发布前人工验收

1. Android 下载链接正确
2. Windows EXE 可下载
3. 官网视觉资产正常加载
4. 中英文页面至少在首页与下载区不破版
5. 隐私内容与真实策略一致

## 实施顺序

1. 先改文档与方案，冻结新发布模型
2. 改 backend release tooling，使 Windows 正式以 `.exe` 为准
3. 改 website 的下载逻辑与文案
4. 重做官网视觉与素材接入
5. 收束单仓库目录与发布说明
6. 再进入 Android / Windows 最终构建与上线验收

## 结论

当前最优路径不是继续修补旧的多仓库/Microsoft Installer 方案，而是：

- 用单仓库统一产品叙事与版本发布
- 复用现有 NSIS 安装器，直接把 Windows 主发布切到 `.exe`
- 把官网升级成真正的品牌化产品站

这样最符合新的正式目标，也最接近“用户下载就愿意装、装了就能理解产品价值”的成熟产品状态。
