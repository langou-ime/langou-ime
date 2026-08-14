# 懒狗输入法单仓库目录说明

## 目标布局

```text
production/
  android/
  windows/
  backend/
  website/
  infra/
  assets/
docs/
  release/
  superpowers/
```

## 目录职责

### `production/android`

Android 输入法客户端。负责：

- 26 键 / 9 键普通输入
- 本地词频学习
- Android 侧上下文识别、OCR、AI 建议接线
- Android 发布清单公钥校验

### `production/windows`

Windows 输入法客户端。负责：

- TSF / RIME 输入核心
- Windows 助手进程
- UIA / OCR / AI 建议
- EXE 安装器与 Windows 更新元数据

### `production/backend`

后端服务。负责：

- 账号与短信登录
- 历史、设置与同步
- AI SSE 建议接口
- Android / Windows release manifest

### `production/website`

官网前端。负责：

- 首页品牌展示
- 下载页
- 隐私页
- 帮助页
- 与 release manifest 对齐的公开下载入口

### `production/assets`

品牌与视觉资产。负责：

- 原始 mascot 记录
- 导出的 app icon / mascot
- ChatGPT Images 2.0 生成的官网视觉图

### `docs/release`

对人类维护者的发布说明。负责：

- GitHub Release 操作顺序
- 单仓库布局说明
- 公开发布命名与事实源约束
