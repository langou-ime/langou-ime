# RIME 首发数据来源

本目录是懒狗输入法 v1.0.0 在 Windows 端随包提供的离线输入数据。它与
Android 端使用同一组固定上游版本，避免两端拼音、模糊音、Emoji 和本地学习
行为漂移。

- `rime-prelude`：`082425ea0684bca36474415d4a0e8db9b016487e`
- `rime-luna-pinyin`：`56b934b099dfbeab842320f13aa8b461a6ab3e42`
- `rime-essay`：`e9b1a374a6ea015fca5bdd04318924b4483ac35a`
- `rime-emoji`：`d1dbb424124fc50452a179300c7f287dbcc0db64`

`langou_t9.schema.yaml`、`luna_pinyin.custom.yaml` 和 `default.yaml` 是懒狗
配置层；其他文件来自上述 RIME 项目。各上游许可证原文保存在 `licenses/`。

抽样 SHA-256：

- `luna_pinyin.dict.yaml`：
  `75bcf6eb3ff62b129882ed89cc22b2d80a5347aa72bcfa2ccc839bac298e7314`
- `essay.txt`：
  `a6f8409c261e5d21bd78e6cbcde8f8e1ef7f68c07ff1c2692c07dd4ff4151cea`
- `opencc/emoji.json`：
  `26fe9074c1596aebe08f19f5f66f47833fa9be940abcaa49d24a790256c09848`
