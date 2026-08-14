# 美术资源管理规范

## 新增资源登记

提交新需求只需提供以下五项；其余信息按本文和对应模板补齐：

```text
资源类型：宠物 / Boss / NPC / 地图 props / tileset / VFX / UI 图标 / 道具 / 背景 / 事件
资源名称或配置 ID：
用途（页面、地图或战斗事件）：
数量：
特殊要求（如属性、透明、不可裁切、必须新增视觉语义）：
```

处理人首先查 `backend/src/main/resources/game-config/`、`frontend/public/assets/`、`frontend/src/game-assets.ts` 和 [asset-manifest.md](asset-manifest.md)。未提供或不存在配置 ID 的内容不进入正式资源目录。

## 资源分类与正式位置

下表的 `assets/...` 均相对于 `frontend/public/`；候选图和 QC 记录不属于运行时静态目录。

| 类型 | 正式目录与命名 | 当前规格 | Alpha / 裁切 | 复用与使用点 |
|---|---|---|---|---|
| 宠物肖像 / 图标 | `assets/pets/portraits/pet_<PET_ID>_portrait.png`；`assets/pets/icons/pet_<PET_ID>_icon_<256|128|64>.png` | 肖像 `1024²`；图标 256/128/64 | 透明；肖像不可裁主体 | 肖像用于图鉴，图标用于仓库等；图标由肖像派生。 |
| Boss / NPC | `assets/bosses/portraits/boss_<BOSS_ID>_portrait.png`；`assets/npc/portraits/npc_<NPC_ID>_portrait.png` | `1024²` / `512²` | 透明；不可裁主体 | `BossView`、`DialogueBox`。 |
| 地图 tileset / props | `assets/maps/tilesets/tileset_<area>.png`；`assets/sprites/<id>.png` | `128×32`（4 个 32px 格）/ `32²` | tileset 依实际图层；props 透明且不可裁 | `BootScene.ts` 统一加载；野怪优先从宠物图标派生。 |
| VFX | `assets/fx/{elemental|combat}/vfx_<template>_sheet.png` | `1024×256`，4 帧 `256²` | 透明；中心锚点、不可裁 | `BattleView.vue` 事件映射；按视觉语义复用模板。 |
| UI 图标 | `assets/ui/elements/icon_element_<lower>.png`、`statuses/icon_status_<lower>.png`、`skill-types/icon_skilltype_<name>.png`、`logo_game.png` | `128²` / `128²` / `256²` / `512²` | 透明；不可裁主体 | `game-assets.ts` 共享 URL helper 和各页面。 |
| 道具 | `assets/items/item_<ITEM_ID>.png` | `256²` | 透明；不可裁主体 | `itemIconUrl()`、背包、商店、战斗奖励。 |
| 背景 / 场景 | `assets/backgrounds/battle_bg_<area>.png`、`bg_home.png`、`region_<area>_thumb.png`、`assets/events/event_<EVENT_ID>_cg.png` | `1920×1080`、`256²`、`512²` | 不透明；允许为构图裁切 | `BattleView`、`HomeView`、`WorldMapView`、`ExploreView`。 |

路径中的 `<...>` 只能使用对应游戏配置 ID，保留现有大小写；禁止中文、空格、括号、`final`、`new`、日期或无意义版本后缀。`entry.png` 是未引用历史文件，不作为新增命名范例。

## Source、Derived 与程序资源

- `SOURCE`：需要 AI/人工生成的最小独立画面，候选图留在现有 `docs/art/*-candidates/`（如 `pets` 使用 `b01-candidates`，VFX 使用 `vfx-candidates`）。
- `DERIVED`：从已审核资源通过现有脚本导出，例如宠物三档图标、野怪图标、道具变体、UI 切片、VFX 帧表和区域缩略图。
- `NO_ASSET_REQUIRED`：CSS、SVG 或 Phaser tween 足以表达的面板、边框、数字反馈和简单交互反馈。不得为了“完整”生成无实际加载点的位图。

候选文件可用 `<id>_v01.png`、`<id>_v02.png` 比较；正式文件永远使用上表的稳定名字。替换由 Git 记录版本，不在正式文件名中递增。

## 状态、版本与清单

统一状态为 `PLANNED`、`GENERATING`、`GENERATED`、`REVIEWED`、`INTEGRATED`、`VERIFIED`、`REJECTED`，含义与阶段转换见 [art-generation-workflow.md](art-generation-workflow.md)。

每个新增 `SOURCE`，或一组可独立追踪的 `DERIVED`，在 `asset-manifest.md` 追加下列最小记录；不另建 YAML、数据库或表格系统：

```text
resource_id: PET_ICE_001（或 VFX_ICE_SMALL）
resource_type: SOURCE / DERIVED / NO_ASSET_REQUIRED
usage: PokedexView / BattleView / BootScene 等
status: VERIFIED
file_path: frontend/public/assets/...
source: docs/art/...-candidates/<file> 或父资源 ID
prompt_reference: art-prompt-templates.md#...
generation: 模型名称与版本、比例、透明/绿幕、关键可变字段
version: v01（候选版本）/ Git 提交或本次变更日期（正式资源）
review: QC 文件或实机截图路径
notes: 派生脚本、复用模板或已知限制
```

仅记录能复现结果的关键条件，临时试错和无关聊天不入库。批量资源可在一行中列出 ID 范围及共用参数，但必须能找回每个正式文件。

## 实际接入点

| 资源 | 代码入口 |
|---|---|
| 元素、状态、技能类型、道具图标 | `frontend/src/game-assets.ts`；由 `MainLayout`、`NewGameView`、`PetView`、`BattleView`、`InventoryView`、`ShopView` 调用。 |
| 地图 tileset 与 props | `frontend/src/game/scenes/BootScene.ts` 加载；`MapScene.ts` 使用稳定纹理键。 |
| 战斗背景与 VFX | `frontend/src/views/Battle/BattleView.vue`。 |
| 宠物、Boss、NPC | `PokedexView` / `StorageView`、`BossView`、`DialogueBox`。 |
| 主页、区域缩略图、事件图 | `HomeView`、`WorldMapView`、`ExploreView`。 |

新增素材前先找共享入口；例如新增元素图标应优先走 `elementIconUrl()`，新增技能特效先走 `BattleView` 的模板映射。禁止在多个页面复制路径字符串。
