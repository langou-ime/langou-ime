# 懒狗输入法 Windows MSI

本工程使用固定版本 WiX Toolset 5.0.2 生成 Windows 10/11 x64、
per-machine MSI。WiX 6/7 引入了 Open Source Maintenance Fee 协议，
不得在未取得产品所有者明确授权的情况下升级或接受该协议。WiX 5 仅在隔离的
临时 CI runner 中作为构建工具运行，不进入安装包；升级构建工具前需要重新做
许可证和供应链评审。

构建输入是干净的 `payload/` 目录，其中必须同时包含 Weasel/RIME 产物与
自包含的 `LangouAssistant.exe`。MSI 会注册 TSF、部署 RIME 数据、建立开机
启动和开始菜单快捷方式；真实卸载会注销 TSF，并通过助手当前用户进程清除
DPAPI 会话、匿名设备 ID 和本地设置。Major Upgrade 不清除用户数据。

正式发布前必须对 EXE/DLL 及最终 MSI 执行 Authenticode 签名。CI 生成的未签名
RC 只能作为内部验收产物，不允许进入公开 Release 或官网。
