# Batch 3 / P0 地图资源质检

执行日期：2026-08-14  
范围：4 套 Tileset、玩家与 6 个地图物件。

## Tileset

| 检查项 | 结果 |
|---|---|
| 文件 | `tileset_grassland_base`、`tileset_waters`、`tileset_thunder`、`tileset_ruins` 均存在 |
| 地图绑定 | `BootScene` 按 6 个 `mapFile` 加载对应图块集 |
| Tiled gid | 6 张地图的 ground 层只使用 gid 1～4，与当前 4 格图块集一致 |
| 视觉预览 | `docs/art/tileset-preview-map.png` 已检查，四个区域主题可区分 |

## 地图物件

| 资源 | 可见像素数 | 结果 |
|---|---:|---|
| `player` | 311 | 通过 |
| `camp` | 337 | 通过 |
| `chest` | 716 | 通过 |
| `gather` | 527 | 通过 |
| `exit` | 431 | 通过 |
| `boss_door` | 726 | 通过 |
| `hidden_spot` | 432 | 通过 |

所有导出文件均为 32×32 PNG，四角 alpha 为 0，且逐张视觉确认主体可辨。绿幕源图和原生透明源图均经同一导出脚本验证。

## 结论

通过。`wild_*.png` 与 `npc.png` 不属于本批次，分别等待 DER-02 宠物图标派生与 Batch 4 NPC 地图模板。
