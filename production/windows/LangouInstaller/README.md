# 懒狗输入法 Windows 安装包链路

当前公开发布目标是 Windows 10/11 x64 一键双击安装的 EXE 安装器。仓库里仍保留
WiX 工程作为历史构建资产与迁移参考，但 v1.0.0 对外主发布不再使用 MSI。

公开 EXE 构建输入是干净的打包目录，其中必须同时包含 Weasel/RIME 产物与
自包含的 `LangouAssistant.exe`。安装器负责注册 TSF、部署 RIME 数据、建立开机
启动和开始菜单快捷方式；真实卸载会注销 TSF，并通过助手当前用户进程清除
DPAPI 会话、匿名设备 ID 和本地设置。升级流程默认保留用户数据。

正式发布前必须对第一方 EXE/DLL/IME 与最终公开 EXE 安装器执行 Authenticode
签名。CI 生成的未签名 RC 只能作为内部验收产物，不允许进入公开 Release 或官网。
