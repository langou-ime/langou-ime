# SignPath 配置

在 SignPath 项目中创建一个 Artifact Configuration，将
`artifact-configurations/langou-windows-exe.xml` 原样导入并保存；把生成的 slug
配置为 GitHub Repository Variable `SIGNPATH_ARTIFACT_CONFIGURATION_SLUG`。

配置根节点是 GitHub `upload-artifact` 生成的 ZIP。SignPath 会在一次可信请求中：

1. 解开 ZIP，并打开 NSIS 安装 EXE；
2. 只签懒狗项目直接生成的 EXE、DLL 与 IME；
3. 保留 `rime.dll`、OpenVINO、OpenCV 和 Microsoft Runtime 等第三方组件的原始发布者身份；
4. 重新封装安装 EXE；
5. 最后对 EXE 本身做 Authenticode 签名。

首次连接时应上传内部 RC EXE 让 SignPath 展开文件树，逐项核对 XML 路径。路径或
Manufacturer/Subject 不匹配时必须修正配置，不能临时改成“签所有 PE”。
