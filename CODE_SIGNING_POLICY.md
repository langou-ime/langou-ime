# Code signing policy

懒狗输入法的公开 Windows 二进制必须使用 Authenticode 签名。首选
[SignPath Foundation](https://signpath.org/) 的开源签名服务；如果申请在
v1.0.0 RC 截止前没有获批，则使用项目所有者持有的 OV 代码签名证书。

Free code signing provided by SignPath.io, certificate by SignPath Foundation.

## Team roles

- Committers and reviewers: [langou-ime organization members](https://github.com/orgs/langou-ime/people),
  currently [@t5gy5475pg-cmyk](https://github.com/t5gy5475pg-cmyk).
- Approvers: [langou-ime organization owners](https://github.com/orgs/langou-ime/people?query=role%3Aowner),
  currently [@t5gy5475pg-cmyk](https://github.com/t5gy5475pg-cmyk). Every signing request
  requires manual approval and cannot be approved by an automated workflow.

## Privacy

[Privacy policy](PRIVACY.md). 普通 RIME 输入离线运行。用户可在懒狗输入法设置中分别关闭
自动 AI、云端历史和匿名诊断；安装器在安装前显示隐私说明。AI 启用时才会将脱敏后的
聊天文字发送到 `api.langou.tech` 及所声明的模型处理方；本地 OCR 截图从不上传。

## Build integrity

- 只有 GitHub-hosted Windows runner 可以生成提交给 SignPath 的产物。
- Workflow 固定源码提交、Git submodule、NuGet lock file、Boost 和 librime
  版本；发布构建不接受来自 fork 的 secret。
- SignPath GitHub App 校验 workflow 来源，签名策略需要项目 approver 人工批准。
- 使用仓库内 `.signpath/artifact-configurations/langou-windows-msi.xml`
  进行深度签名：SignPath 先签 MSI 内明确列出的第一方 EXE/DLL/IME，重新封装后
  再签 MSI；所有签名使用 SHA-256 和可信时间戳。
- `rime.dll`、OpenVINO、OpenCV、Microsoft Runtime 等第三方组件不得冒充
  懒狗输入法发布者；SignPath 配置只允许签明确列出的第一方文件。

## Release gate

公开发布前必须同时满足：

1. Authenticode 状态为 `Valid`，证书主体符合已批准的 SignPath/OV 身份。
2. MSI SHA-256 与后端签名发布清单、GitHub Release、官网下载区完全一致。
3. 安装、升级、卸载和更新回滚在 Windows 10/11 x64 真机通过。
4. 未签名或签名失效的产物只能作为短期内部 RC，名称必须包含
   `UNSIGNED-INTERNAL`，不得公开下载。

签名私钥不得进入仓库、日志或普通 CI secret。SignPath 的私钥保存在其 HSM；
OV 证书使用 CA 要求的硬件令牌或受管签名服务。
