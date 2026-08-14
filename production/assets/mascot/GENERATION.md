# 懒狗角色资产生成记录

- 生成日期：2026-07-26
- 生成方式：Codex 内置 Image Generation（gpt-image-2 路径）
- 输入参考图：无
- 用途：Android / Windows 图标、AI 状态、键盘彩蛋
- 原始输出：`langou-mascot-master-chroma.png`
- 透明母版：`langou-mascot-master.png`
- 背景处理：官方 `remove_chroma_key.py`，border 自动取色、soft matte、despill

## 最终提示词

```text
Use case: stylized-concept
Asset type: original master mascot for the Android and Windows app “懒狗输入法”; will be cropped into launcher icons, AI thinking status, and keyboard easter eggs
Primary request: create one completely original, adorable slightly world-weary lazy dog mascot wearing an oversized peach-pink hoodie; the character should feel comforting, cute, feminine-friendly, and emotionally expressive without resembling any existing commercial mascot or anime IP
Subject: a small round cream-colored puppy with floppy caramel ears, half-lidded gentle eyes, tiny dark-brown nose, subtle peach blush, short rounded paws tucked close, and an oversized pink hoodie with simple drawstrings; calm “I’m tired but I’ll help you reply” expression; no accessories and no logos
Style/medium: polished Japanese-inspired kawaii 3D sticker illustration, soft clay/vinyl surface rather than realistic fur, rounded forms, premium mobile app mascot quality, clean readable silhouette at 48 px
Composition/framing: centered full body, front three-quarter view, generous even padding, no crop, square canvas
Lighting/mood: soft diffused studio lighting on the character only; sweet, cozy, slightly deadpan
Color palette: cream white #FFF8EE, peach pink #F6A7B8, pale lavender #C9B8F4, caramel brown #B98062, dark cocoa facial details; do not use green on the subject
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, or lighting variation
Constraints: entirely original character design; one character only; crisp closed silhouette and clean edges; no cast shadow, contact shadow, reflection, text, letters, symbols, logo, watermark, UI frame, extra props, extra limbs, clothing graphics, or background objects
Avoid: realistic fur strands, photorealism, overly childish baby proportions, sexualized styling, famous cartoon/anime character resemblance, dog breeds strongly associated with existing mascots
```

## 完整性与相似性初筛

- 未使用搜狗或其他产品的角色、皮肤、图标作为输入参考。
- 输出不包含第三方名称、文字、Logo、服装图案或可识别品牌元素。
- 人工检查了轮廓、耳形、表情、服装和色彩组合；未发现与本仓库旧资产或已知输入法角色的直接复制关系。
- 角色母版与所有机械导出均保留 SHA-256，可追溯到同一次生成。
- 本记录是工程侧初筛，不等同于覆盖全球作品与商标库的法律检索；商业推广前仍需由权利人完成最终商标与版权清查。

## SHA-256

```text
e9a4092fd4b1633ae1ce3a9410fcbe357f36033b963a526c3bb9301c8efdb4bb  langou-mascot-master-chroma.png
202e2bd5e5643a7bad04a6ecbf9e3ca7663bfb2f03d10caa7f2417aab8359987  langou-mascot-master.png
e2757839d9a82fbc80d23494d248f3c5dbd21e1868c7b7b0c9c9c2075d7c43c5  ../exports/langou-app-icon-512.png
1b3c6f5413339a20a41b7db897e4d9bc9ce26e9175b206cacaad7c009d7b0b6e  ../exports/langou-app-icon-round-512.png
c7eed7eb92ccea58a22c8c278b4d9aaef3e012690dd47411dfda3c60d7066742  ../exports/langou-keyboard-easter-egg.png
```
