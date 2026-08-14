# 美术资源清单与批次追溯

状态：Batch 8 已通过（全量资源 QC 与前端构建）；核心场景实机截图仍随阶段 14 总验收执行。  
当前用途：本文件是正式资源、候选源图、批次与 QC 的**当前追溯清单**。新增资源按 `docs/art/README.md`、`art-resource-management.md` 和 `art-generation-workflow.md` 执行后，在本文件追加记录；历史执行矩阵仅见 `docs/art-resource-generation-execution-plan.md`。

## 当前资源索引

| 类型 | 正式位置 | 已验证规模 / 规格 | 实际接入 |
|---|---|---|---|
| 宠物 | `assets/pets/portraits/`、`assets/pets/icons/` | 27 张 `1024²` 肖像；81 张 256/128/64 派生图标 | 图鉴、仓库等 |
| Boss / NPC | `assets/bosses/portraits/`、`assets/npc/portraits/` | 8 张 `1024²` Boss；9 张 `512²` NPC | Boss 详情、对话框 |
| 地图 | `assets/maps/tilesets/`、`assets/sprites/` | 4 套 `128×32` tileset；13 个 `32²` 地图精灵 | `BootScene` / `MapScene` |
| VFX | `assets/fx/elemental/`、`assets/fx/combat/` | 19 属性 + 12 功能表；每项 `1024×256`、4 帧 | `BattleView` 事件层 |
| UI / 道具 | `assets/ui/`、`assets/items/` | 9 属性、24 状态、20 技能类型图标、1 徽记；35 道具 | `game-assets.ts` 与各页面 |
| 场景 | `assets/backgrounds/`、`assets/events/` | 6 战斗背景、1 主页、6 缩略图、6 事件图 | 战斗、主页、大地图、探索 |

新增记录最小字段见 [art-resource-management.md](art-resource-management.md#状态版本与清单)。候选版本可留在 `docs/art/*-candidates/`；正式版本由 Git 追踪，不在文件名追加 `final2` 等后缀。

## A0 风格锚点

| 资源 | 设定 | 状态 |
|---|---|---|
| `pet_PET_FIRE_001_portrait.png` | 烬牙兽：黑色炭纹、赤红火牙、四足物理爆发、普通 | exported |
| `pet_PET_WATER_001_portrait.png` | 汐月灵：月牙潮汐、水元素生命、治疗净化、稀有 | exported |
| `pet_PET_EARTH_001_portrait.png` | 岩魁：岩块土晶、厚重巨灵、坦克嘲讽、普通 | exported |

## 宠物生产批次

| 批次 | 资源 |
|---|---|
| B01 | `PET_METAL_001` 铄锋、`PET_WOOD_002` 青芽灵、`PET_THUNDER_001` 霆跃兽 |
| B02 | `PET_DARK_001` 幽蚀灵、`PET_WIND_001` 岚羽、`PET_LIGHT_001` 曜羽灵 |
| B03 | `PET_METAL_002` 玄铠、`PET_WATER_002` 涟歌灵、`PET_FIRE_002` 绯焰灵 |
| B04 | `PET_WOOD_001` 藤梦鹿、`PET_EARTH_002` 岳灵、`PET_DARK_002` 夜缚兽 |
| B05 | `PET_METAL_003` 鎏刃灵、`PET_WIND_002` 逐风灵、`PET_THUNDER_002` 鸣霄灵 |
| B06 | `PET_WOOD_003` 森蚀灵、`PET_WATER_003` 雾澜兽、`PET_FIRE_003` 赤曜兽 |
| B07 | `PET_EARTH_003` 磐震兽、`PET_WIND_003` 空澜兽、`PET_THUNDER_003` 紫霄灵 |
| B08 | `PET_LIGHT_002` 曦鹿、`PET_LIGHT_003` 辉星兽、`PET_DARK_003` 冥刃灵 |

## 已导出批次

| 批次 | 已导出资源 | 验收 |
|---|---|---|
| A0 | 火、水、土三张风格锚点 | 通过 |
| B01 | 金、木、雷三张宠物立绘 | 通过 |
| B02 | 暗、风、光三张宠物立绘 | 通过 |
| B03 | 金、水、火三张宠物立绘 | 通过 |
| B04 | 木、土、暗三张宠物立绘 | 通过 |
| B05 | 金、风、雷三张宠物立绘 | 通过 |
| B06 | 木、水、火三张宠物立绘 | 通过 |
| B07 | 土、风、雷三张宠物立绘 | 通过 |
| B08 | 光、光、暗三张宠物立绘 | 通过 |

## Batch 3 / P0 地图资源

| 资源 | 已导出内容 | 验收 |
|---|---|---|
| Tileset | `tileset_grassland_base`、`tileset_waters`、`tileset_thunder`、`tileset_ruins` | 通过 |
| 地图物件 | `player`、`camp`、`chest`、`gather`、`exit`、`boss_door`、`hidden_spot` | 通过 |

- 物件源图保存在 `docs/art/map-candidates/`，导出使用 `frontend/scripts/remove-chroma-key.ps1`；绿幕源图与原生透明源图均已校验。
- `wild_*.png` 已按 DER-02 从宠物图标派生，`npc.png` 已由 NPC 地图模板导出；仅 `entry.png` 保留为未被引用的历史文件。

## Batch 4 / 角色资源

| 资源 | 已导出内容 | 验收 |
|---|---|---|
| Boss 立绘 | 8 个 `boss_BOSS_*_portrait.png` | 通过 |
| NPC 立绘 | 9 个 `npc_NPC_*_portrait.png` | 通过 |
| 地图 NPC 模板 | `sprites/npc.png` | 通过 |

- Boss 立绘已接入 `BossView`，NPC 立绘已接入 `DialogueBox`。
- Boss 源图保存在 `docs/art/boss-candidates/`，NPC 源图保存在 `docs/art/npc-candidates/`；全部由透明源图统一导出。

## Batch 5 / 战斗特效

- 载体已裁决为透明 PNG 精灵表：每项为横向 4 帧，单帧 `256×256`，总尺寸 `1024×256`，中心锚点 `(128, 128)`，12 FPS、非循环。
- 第 1 帧为起势、第 2 帧为峰值、第 3 帧为余波、第 4 帧为自然消散；所有帧保持透明背景与固定中心。
- 源图保存于 `docs/art/vfx-candidates/`，由 `frontend/scripts/build-vfx-sprite-sheet.ps1` 统一导出，避免逐帧生成造成中心漂移。
- 已导出 31 项：19 个属性模板位于 `assets/fx/elemental/`，12 个功能模板位于 `assets/fx/combat/`；详见 `docs/art/m5-qc.md` 与 `m5-vfx-contact-sheet.png`。
- `BattleView` 已按现有后端事件播放对应精灵表；该层只消费事件，不参与伤害、捕捉或状态计算。
- 技能池扩充至 85 主动、24 个被动（14 个固有/升级 + 10 个技能书）后，新增 24 个主动技能均映射至既有属性强度档或功能模板；新增 9 个固有被动和 10 个技能书被动复用被动徽记与事件型特效，不新增重复精灵表。

## Batch 6 / UI 图标

- 已导出 9 个属性图标（`128×128`）、24 个状态图标（`128×128`）、20 个技能类型图标（`256×256`）及游戏徽记（`512×512`），均为透明 PNG。
- 源图保存在 `docs/art/ui-candidates/`，使用 `frontend/scripts/split-ui-icon-sheet.ps1` 按网格裁切并校验透明角落，详见 `docs/art/m6-qc.md` 与 `m6-ui-contact-sheet.png`。
- `MainLayout`、新游戏宠物选择、宠物技能列表和战斗属性/状态/技能按钮已使用这些资源；文字保留为可访问标签和说明，不承担图标表达。

## Batch 7 / 道具、背景与事件

- 已导出 35 个 `256×256` 透明道具图标；7 个底图源文件保留在 `docs/art/item-candidates/bases/`，最终变体位于 `assets/items/`。
- 已导出 6 张 `1920×1080` 区域战斗背景与 1 张同规格主页背景，并由战斗背景派生 6 张 `256×256` 区域缩略图。
- 已导出 6 张 `512×512` 随机事件插画；源图分别保留在 `docs/art/background-candidates/` 与 `docs/art/event-candidates/`。
- `BattleView` 按当前地图切换背景，`HomeView` 使用主页背景，`WorldMapView` 使用区域缩略图，`ExploreView` 在随机事件弹窗中显示对应插画。
- 视觉检查见 `m7-items-contact-sheet.png`、`m7-background-contact-sheet.png`、`m7-event-contact-sheet.png` 与 `m7-qc.md`。

## Batch 8 / 全量 QA

- 已核验所有计划内资源数量、尺寸与应透明文件的四角 Alpha；完整记录见 `m8-qc.md`。
- `vue-tsc -b` 与 Vite 生产构建均已通过；仅保留既有的 ExploreView 体积警告。
- `frontend/scripts/verify-skill-art-adaptation.ps1` 已覆盖新增技能池的配置数量、UI 图标、VFX 文件及前端映射。

## 范围与阻塞项

- Source：161 项；Derived：约 80 项；程序资源：约 20 项，均遵循执行计划 §7、§9。
- 27 宠物立绘和 256/128/64 三档图标（81 张派生）已完成；图鉴与仓库已接入图标。
- Batch 0～8 的计划内 Source 与 Derived 资源均已导出、接入并通过静态 QA；阶段 14 的响应式兼容、数值实测与核心场景总验收仍按项目计划独立进行。
- 内建生成器使用绿幕输出，统一由 `frontend/scripts/remove-chroma-key.ps1` 生成透明 PNG；A0 的 QC 详见 `docs/art/a0-qc.md`。
