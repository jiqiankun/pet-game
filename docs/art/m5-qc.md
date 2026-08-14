# M5 战斗特效质检记录

日期：2026-08-14  
状态：通过

## 固定规格

- 载体：透明 PNG 横向精灵表；
- 单帧：`256×256`，共 4 帧，总尺寸：`1024×256`；
- 锚点：帧中心 `(128, 128)`；12 FPS；全部为非循环单次播放；
- 帧序：起势 → 峰值 → 余波 → 自然消散。

## 覆盖范围

- 属性模板 19 项：火 4、雷 3、水 2、土 2、风 2、光 2、金 2、暗 1、木 1；
- 功能模板 12 项：治疗单体/群体、护盾、增益、减益、控制、中毒、吸血、驱散、留生一击、捕捉、Boss 阶段；
- 源图：`docs/art/vfx-candidates/vfx_*_source.png` 共 31 项；
- 输出：`frontend/public/assets/fx/{elemental,combat}/vfx_*_sheet.png` 共 31 项。

## 检查结果

- 数量：属性 19 + 功能 12 = 31，全部存在；
- 尺寸：全部为 `1024×256`；
- 透明：输出四角 alpha 为 0；源图四角 alpha 不超过 5；
- 帧：每个精灵表的四帧均含可见像素，中心由统一导出脚本固定；
- 视觉抽检：`m5-vfx-contact-sheet.png` 已覆盖全部 31 项；无文字、Logo、UI 边框或非透明背景；
- 群体治疗首个含“+”符号的候选未导出，已用无符号候选替换。

## 接入与验证

- `BattleView` 根据已有 `SKILL_CAST`、`CAPTURE_ATTEMPT`、`STUNNED`、`PHASE_TRANSITION` 等事件播放相应精灵表；前端不计算任何战斗结果；
- `build-vfx-sprite-sheet.ps1` 在导出时校验输出四角透明及每帧存在可见像素；
- 已通过 `vue-tsc -b` 与 Vite 生产构建；Vite 仅保留既有 ExploreView 大包告警。
