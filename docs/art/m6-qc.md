# Batch 6 UI 图标 QC

- 属性图标：9 个 `128×128` 透明 PNG，位于 `assets/ui/elements/`。
- 状态图标：24 个 `128×128` 透明 PNG，位于 `assets/ui/statuses/`。
- 技能类型图标：20 个 `256×256` 透明 PNG，位于 `assets/ui/skill-types/`。
- 游戏徽记：`logo_game.png`，`512×512` 透明 PNG，位于 `assets/ui/`。
- 所有输出均已校验数量、尺寸、可见像素和透明角落；`m6-ui-contact-sheet.png` 已按 24px 可读性复核。
- `MainLayout`、新游戏初始宠物、宠物技能列表及战斗中的属性/状态/技能按钮均已接入对应资源。

首个技能图集候选检测到不透明背景，未导出到项目；其余通过透明校验的源图保存在 `ui-candidates/`。
