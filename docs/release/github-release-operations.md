# 懒狗输入法 GitHub Release 操作说明

## 目标

使用一个公开仓库承载 Android、Windows、backend、website，并在同一个 GitHub Release
里发布：

- `langou-ime-android-v1.0.0.apk`
- `langou-ime-windows-x64-v1.0.0.exe`
- 对应 SHA-256 摘要
- 版本说明

## 发布事实源

正式发布时，下列四处必须一致：

1. GitHub Release 资产
2. `api.langou.tech` 的 `/v1/releases/android/latest` 与 `/v1/releases/windows/latest`
3. `langou.tech` 官网下载页
4. 服务器下载目录中的正式文件

## 推荐发布顺序

1. 在单仓库根目录确认版本与变更说明。
2. 构建并验收 Android APK。
3. 构建并验收 Windows EXE 安装器。
4. 计算两个正式文件的 SHA-256。
5. 用后端发布工具生成并验签 Android/Windows manifest。
6. 创建 GitHub draft Release，上传 APK、EXE、SHA-256 文件。
7. 将同一组版本、大小、SHA-256 和下载地址写入官网与 API manifest。
8. 同一发布窗口公开 GitHub Release，并切换官网正式下载。

## 文件命名

正式版固定使用：

- `langou-ime-android-v1.0.0.apk`
- `langou-ime-windows-x64-v1.0.0.exe`

不得公开使用旧的 MSI 文件名，也不得把内部 RC 产物直接挂到正式下载入口。

## RC 规则

- 内部 RC 可以是私有 GitHub Actions artifact。
- RC 不进入官网正式下载。
- RC 不得冒充正式签名版本。

## 发布前检查

- Android APK 已真机验收
- Windows EXE 已真机验收
- 官网 `download` 页链接与 GitHub Release 一致
- `/v1/releases/{platform}/latest` 返回的 URL、size、sha256 与正式文件一致
- 安装包签名与哈希已复核
