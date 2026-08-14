# 《宠物精灵》美术资源盘点清单（生成前历史基线）

> 生成日期：2026-08-14
> 依据：需求设计文档 V1.0、技术方案 V1.0、UI 设计文档 V1.0、`AGENTS.md`、`docs/FRONTEND_STANDARDS.md`、全部 `game-config/*.yml` 配置、前端 `frontend/src` 与 `frontend/public` 实际代码与资源。
> 历史定位：这是 Batch 0～8 开始前的缺口盘点。其后续“当前为 0 / 缺失”等描述只反映当时现场，**不可作为当前资源数量、目录或规范的依据**；请改读 `docs/art/README.md` 与 `docs/art/asset-manifest.md`。

---

## 执行状态更新（2026-08-14）

本文件其余「当前」描述为生成前的盘点基线；当前执行状态、资源规格和新增流程以 `docs/art/README.md`、`docs/art/asset-manifest.md` 为准。

- Batch 0～2 已完成：27 张宠物立绘及 81 张 256/128/64 图标派生文件。
- Batch 3 已完成：4 套区域 Tileset，以及 `player`、`camp`、`chest`、`gather`、`exit`、`boss_door`、`hidden_spot` 共 7 个正式地图物件。
- Batch 4 已完成：8 张 Boss 立绘、9 张 NPC 立绘、通用地图 NPC 精灵；Boss 详情和 NPC 对话框已接入对应资源。
- Batch 5 已完成：31 个 VFX 模板（19 属性 + 12 功能）均采用透明 PNG 精灵表（4×`256×256` 横向帧，12 FPS、非循环、中心锚点固定），并已接入 BattleView 的后端事件表现层。
- Batch 6 已完成：9 个属性图标、24 个状态图标、20 个技能类型图标及游戏徽记均已导出为透明 PNG；已接入主导航、新游戏、宠物技能列表和战斗页。
- Batch 7 已完成：35 个透明道具图标、6 个区域战斗背景、主页背景、6 个区域缩略图及 6 张随机事件插画；已接入背包、商店、战斗、主页、大地图与探索事件弹窗。
- Batch 8 已完成：全量资源数量、尺寸与透明角校验通过，前端类型检查与生产构建通过。
- 地图精灵已无运行时占位；仅 `entry.png` 仍为未被引用的历史文件，未删除任何既有文件。

---

## 0. 文档说明与统计口径

- 「已有正式资源」= 可在游戏内直接使用的正式美术图，**当前为 0**（UI 层无任何 `<img>/url()/background-image`，Phaser 层全部为程序生成的占位图）。
- 「占位资源」= 已存在的程序临时绘制文件（13 个精灵 PNG + 1 个 Tileset PNG + 1 个 favicon SVG）。
- 「缺失资源」= 代码/配置需要但当前只有 emoji、色块、文字、空白、临时图承担，应替换为正式资源的部分。
- 「可复用资源」= 不需要独立制作，可由其他资源裁切/缩放/复用/程序生成的部分。
- 「建议制作」= 去重后实际建议新制作的最小资源集合。

---

## 1. 当前美术资源总体情况

### 1.1 现状结论

整个项目**前端 UI 层零位图**：没有任何 `<img>`、`background-image`、`url()` 引用（全局正则零命中）。除 favicon 外，所有「游戏视觉对象」（宠物、属性、技能、状态、道具、NPC、Boss、区域）均由 **emoji / CSS 色块 / 文字首字 / 纯文字** 承担。

唯一真实加载图片的层是 **Phaser 地图层**（`frontend/public/assets/`），且全部为占位：13 个单帧精灵 PNG + 1 个 4 格 Tileset PNG + 6 张 Tiled 地图 JSON。

### 1.2 数量统计

| 指标 | 数量 | 说明 |
|---|---|---|
| 已有正式资源 | **0** | UI 层无正式图；Phaser 层均为占位 |
| 疑似占位资源 | **15** | 13 精灵 PNG + 1 Tileset + 1 favicon SVG |
| 缺失资源（去重后建议制作） | **约 120~140** | 见 §3 详表 |
| 可复用资源 | **约 90+** | 头像←立绘裁切、缩放、模板复用等 |
| 现有临时占位载体 | **约 14 种 emoji + 若干色块/文字** | 见 §10 |

### 1.3 关键数据规模（扫描来源）

| 类别 | 数量 | 来源 |
|---|---|---|
| 宠物 | 27（普通 12 / 稀有 9 / 珍稀 5 / 传说 1） | `pets.yml` |
| 属性 | 9（金木水火土风雷光暗） | `elements.yml` |
| 技能 | 136（主动 85 + 被动 51，其中 41 固有/升级被动（含 27 宠核心特色被动）+ 10 技能书被动） | `skills.yml`、`items.yml` |
| 状态 | 24 | `statuses.yml` |
| 道具 | 45（恢复 4 + 捕捉 3 + 净化 1 + 材料 14 + 技能书 23） | `items.yml` |
| Boss | 8（主 5 + 隐藏 2 + 精英 1，各 3 难度） | `bosses.yml` |
| 地图 / 区域 | 6（1 据点 + 5 区域） | `maps.yml` + 前端 Tiled JSON |
| 随机事件 | 6 | `random-events.yml` |
| 任务 | 25（主线 12 + 支线 10 + 隐藏 3） | `quests.yml` |
| NPC | 9 | `quests.yml` |
| 商店商品 | 19 | `shop.yml` |

---

## 2. 美术资源总览

| 类别 | 已有 | 占位 | 缺失 | 建议制作 | 优先级 |
| --- | -: | -: | -: | -: | --- |
| 宠物 | 0 | 0 | 27 立绘 + 27 头像 | 27 立绘（头像裁切） | P0 |
| 地图 | 0 | 6 图集 + 精灵 | 6 区域 Tileset + Props | 6 Tileset + 12 地图精灵 | P0 |
| UI | 0 | 1 favicon | Logo/面板/按钮/背景等 | 见 §7 | P0/P1 |
| 技能 | 0 | 0 | 技能图标 | 约 24（含属性类型图） | P1 |
| VFX | 0 | 0 | 战斗特效 | 约 30~40 模板 | P1 |
| 状态 | 0 | 0 | 状态图标 | 24 | P1 |
| 道具 | 0 | 0 | 道具图标 | 约 30（强去重） | P1 |
| NPC | 0 | 1（地图占位） | 9 立绘 + 地图精灵 | 9 立绘 | P1 |
| Boss | 0 | 1（地图占位） | 8 形象 | 8 | P0/P1 |
| 属性 | 0 | 0 | 属性图标 | 9（全局共享） | P0 |
| 场景/背景 | 0 | 0 | 战斗/首页背景 | 6 战斗背景 + 首页背景 | P1/P2 |
| 随机事件 | 0 | 0 | 事件插画 | 6 | P2 |

---

## 3. 缺失资源详细清单

> ID 规则：`ART-<类别>-<序号>`。规格需结合项目实际（地图 Tile=32px，见 §6）。

| ID | 资源名称 | 类型 | 使用对象 | 使用位置 | 当前状态 | 建议规格 | 制作方式 | 可复用 | 优先级 |
| -- | -- | -- | -- | -- | -- | -- | -- | -- | --- |
| ART-PET-001~027 | `pet_<id>_portrait` | 宠物立绘 | 27 种族 | 图鉴/详情/战斗/获取展示 | 无（文字/首字） | 1024×1024 透明 PNG | AI 生成 | 否 | P0 |
| ART-PET-ICON | `pet_<id>_icon` | 宠物头像 | 27 种族 | 队伍/HUD/选择/图鉴列表 | 无 | 256×256 PNG | 立绘裁切 | 由立绘生成 | P0 |
| ART-BOSS-001~008 | `boss_<id>_portrait` | Boss 立绘 | 8 Boss | Boss 页/战斗 | 无 | 1024×1024 透明 PNG | AI 生成 | 否 | P0 |
| ART-NPC-001~009 | `npc_<id>_portrait` | NPC 立绘 | 9 NPC | 对话框/任务 | 无 | 512×512 透明 PNG | AI 生成 | 否 | P1 |
| ART-MAP-TS-01~06 | `tileset_<biome>` | 地图 Tileset | 6 区域 | Phaser 地图 | 占位 4 色块 | 32×32 格 Tileset | AI+Tileset Skill | 部分共享 | P0 |
| ART-SPRITE-PLAYER | `player` | 玩家 Sprite | 玩家 | 地图 | 占位圆 | 32×32 PNG | AI 生成 | 否 | P0 |
| ART-SPRITE-WILD | `wild_<behavior>` | 野怪 Sprite | 4 种野怪行为 | 地图 | 占位圆 | 32×32 PNG | AI 生成 | 可复用宠物图标 | P0 |
| ART-SPRITE-01~07 | `camp/chest/gather/exit/boss_door/npc/hidden_spot` | 地图对象 | 交互对象 | 地图 | 占位色块 | 32×32 PNG | AI/程序 | 部分（npc 复用） | P0 |
| ART-VFX | `vfx_<element>_<category>_<level>` | 战斗特效 | 技能 | 战斗 | 无 | PNG Sequence/Sprite Sheet | AI 生成 | 模板复用 | P1 |
| ART-STATUS-001~024 | `status_<id>` | 状态图标 | 24 状态 | 战斗/状态栏 | 文字 | 128×128 PNG | AI 生成 | 类别模板 | P1 |
| ART-SKILL-001~024 | `skill_<id>/skill_type_<x>` | 技能图标 | 技能 | 技能库/战斗 | 文字 | 256×256 PNG | AI 生成 | 类型共用 | P1 |
| ART-ITEM-001~030 | `item_<id>` | 道具图标 | 34 道具 | 背包/商店/掉落 | 文字 | 256×256 PNG | AI 生成 | 类别模板 | P1 |
| ART-ELEMENT-001~009 | `element_<id>` | 属性图标 | 9 属性 | 全局 | emoji/色块 | 128×128 SVG/PNG | SVG/AI | 全局共享 | P0 |
| ART-BATTLE-BG-01~06 | `battle_bg_<biome>` | 战斗背景 | 6 区域 | 战斗 | 无 | 1920×1080 | AI 生成 | 否 | P1 |
| ART-HOME-BG | `home_bg` | 首页背景 | 首页 | Home | 无 | 1920×1080 | AI 生成 | 否 | P2 |
| ART-LOGO | `logo` | 游戏 Logo | 主菜单/标题 | MainLayout | 纯文字 | SVG | SVG/AI | 否 | P1 |
| ART-EVENT-001~006 | `event_<id>` | 随机事件插画 | 6 事件 | 事件对话框 | 无 | 512×512 | AI 生成 | 否 | P2 |
| ART-REGION-001~006 | `region_<id>_thumb` | 区域缩略图 | 6 区域 | 大地图 | 无 | 256×256 | AI 生成 | 否 | P2 |

---

## 4. 宠物资源清单（共 27 只）

> 说明：配置中**无** `body_type/palette/silhouette/combat_role` 字段，形体与配色需依据 `description` 文本 + `element` 推断。**无进化关系**，27 只为独立个体。战斗采用**静态立绘**（BattleView 当前无宠物图），**不新增 Sprite 逐帧动画需求**。

| 宠物 | 属性 | 稀有度 | 立绘 | 头像 | 战斗资源 | 当前状态 |
| -- | -- | -- | -- | -- | -- | -- |
| PET_METAL_001 铄锋 | 金 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_METAL_002 玄铠 | 金 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_METAL_003 鎏刃灵 | 金 | 珍稀 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WOOD_001 藤梦鹿 | 木 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WOOD_002 青芽灵 | 木 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WOOD_003 森蚀灵 | 木 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WATER_001 汐月灵 | 水 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WATER_002 涟歌灵 | 水 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WATER_003 雾澜兽 | 水 | 珍稀 | 需 | 裁切 | 静态立绘 | 无 |
| PET_FIRE_001 烬牙兽 | 火 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_FIRE_002 绯焰灵 | 火 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_FIRE_003 赤曜兽 | 火 | 珍稀 | 需 | 裁切 | 静态立绘 | 无 |
| PET_EARTH_001 岩魁 | 土 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_EARTH_002 岳灵 | 土 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_EARTH_003 磐震兽 | 土 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WIND_001 岚羽 | 风 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WIND_002 逐风灵 | 风 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_WIND_003 空澜兽 | 风 | 珍稀 | 需 | 裁切 | 静态立绘 | 无 |
| PET_THUNDER_001 霆跃兽 | 雷 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_THUNDER_002 鸣霄灵 | 雷 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_THUNDER_003 紫霄灵 | 雷 | 珍稀 | 需 | 裁切 | 静态立绘 | 无 |
| PET_LIGHT_001 曜羽灵 | 光 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_LIGHT_002 曦鹿 | 光 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_LIGHT_003 辉星兽 | 光 | **传说** | 需 | 裁切 | 静态立绘 | 无 |
| PET_DARK_001 幽蚀灵 | 暗 | 普通 | 需 | 裁切 | 静态立绘 | 无 |
| PET_DARK_002 夜缚兽 | 暗 | 稀有 | 需 | 裁切 | 静态立绘 | 无 |
| PET_DARK_003 冥刃灵 | 暗 | 普通 | 需 | 裁切 | 静态立绘 | 无 |

**特殊外观**：`APPEARANCE_SHINY`（异色，全局 0.5%）、`APPEARANCE_GLOW`（辉光，全局 0.3%）为全局概率变体，作用于任意野生宠物。建议 **不单独生成 27×2 变体**，用「异色滤镜 + 辉光特效」程序实现，避免资源爆炸。

---

## 5. 地图资源清单

### 5.1 地图 / 区域（6 个）

| 区域 | 类型 | mapFile | 推荐等级 | 主题色调建议 |
| -- | -- | -- | -- | -- |
| 晨曦村（MAP_START_VILLAGE） | 据点 | start_village | 1 | 暖色村庄 |
| 青草原（MAP_AREA_MEADOW） | 区域 | meadow | 3~8 | 草地 |
| 翠树林（MAP_AREA_FOREST） | 区域 | forest | 8~15 | 森林 |
| 静水湖域（MAP_AREA_WATERS） | 区域 | waters | 15~25 | 水域 |
| 雷鸣高地（MAP_AREA_THUNDER） | 区域 | thunder | 15~25 | 雷域 |
| 远古遗迹（MAP_AREA_RUINS） | 区域 | ruins | 30~50 | 遗迹 |

> 所有地图 **Tile=32px，25×19 格（800×608px）**，Phaser 正交渲染，`pixelArt: true`。当前全部复用同一张 4 色块占位 Tileset（草/路/水/树，gid：水=3、树=4 为阻挡格）。

### 5.2 Tileset（建议）

| 资源 | 覆盖区域 | 规格 | 可共享 |
| -- | -- | -- | -- |
| `tileset_grassland` | 村庄/草原/森林（草地基础） | 32px 格 Tileset | 共享基础草地 |
| `tileset_waters` | 静水湖域 | 32px 格 | 独立 |
| `tileset_thunder` | 雷鸣高地 | 32px 格 | 独立 |
| `tileset_ruins` | 远古遗迹 | 32px 格 | 独立 |

**地块类型需求**：ground（草/泥/石板）、road、water（含岸边/角/河道）、cliff/高低差、tree、rock、building、bridge、fence、flower、sign、decoration。按 UI 文档 §“地图元素视觉风格”补充：篝火营地、宝箱两态、采集光点、Boss 传送门、NPC 任务标记。

### 5.3 地图对象 Sprite（12 个，全部为当前占位）

| 资源 | 用途 | 当前 | 建议 |
| -- | -- | -- | -- |
| `player` | 玩家 | 占位圆 | 玩家角色 |
| `wild_wander` / `wild_timid` / `wild_aggressive` / `wild_rare` | 4 种野怪行为 | 占位圆 | 可用对应宠物图标/通用野怪 |
| `camp` | 营地（篝火） | 色块 | 篝火+光圈 |
| `chest` | 宝箱（两态） | 色块 | 未开/已开 |
| `gather` | 采集点 | 占位圆 | 资源图标 |
| `exit` | 出口 | 色块 | 通道 |
| `boss_door` | Boss 入口 | 色块 | 门/传送门 |
| `npc` | 地图 NPC | 占位圆 | 人形 |
| `hidden_spot` | 隐藏点 | 占位圆 | 隐藏提示 |

> `entry.png` 存在但**未被引用**（入口是逻辑对象），可保留或后续清理。

---

## 6. 技能 / VFX 清单

### 6.1 技能规模

- 主动技能 85 + 被动技能 51 = **136**。
- 被动技能共 51 个：41 个固有/升级被动（不屈/顺风/余烬/再生/厚皮 + 战意/铁躯/迅足/凝神/狂暴本能/复苏/猎获/荆棘反刺/士气昂扬 + 27 宠核心特色被动 PASSIVE_SIG_*）与 10 个技能书被动（先锋/铁壁光环/登场蓄力/稳固/回气/乘胜追击/烬爆/荆棘气场/殊死/复仇）。均复用强化/攻击/回血/荆棘类图标，不单独设计（27 宠核心特色被动同样复用既有图标，不新增美术资源）。

### 6.2 VFX 矩阵（核心去重手段）

> **不要求 1 技能 = 1 特效**。按「属性 × 类别 × 强度档」建立 VFX 模板，技能只引用组合。强度档用 `small / medium / large / ultimate`。

| VFX 模板 | 可服务技能（示例） | 类型 |
| -- | -- | -- |
| `vfx_fire_small/medium/large/ultimate` | 撞击(火)、火牙、烈焰爪 / 烈焰爆发 / 陨星坠、炎灵 | 火焰 |
| `vfx_water_small/medium/large` | 水跃击、汐涌 / 潮涌 / 潮汐 | 水流 |
| `vfx_grass_small/medium/large` | 藤鞭、飞叶 / 缠绕、荆棘 / — | 草木 |
| `vfx_earth_small/medium/large` | 落岩 / 地震 / — | 岩石 |
| `vfx_wind_small/medium/large` | 风刃 / 疾风骤起、旋风 / — | 风 |
| `vfx_thunder_small/medium/large` | 雷击、电光一闪 / 雷暴 / 雷霆万钧 | 雷电 |
| `vfx_light_small/medium/large` | 光耀 / 光爆 / 圣光 | 光 |
| `vfx_dark_small/medium/large` | 暗影球、暗袭 / 诅咒 / 冥刃 | 暗 |
| `vfx_metal_small/medium/large` | 金属爪 / 破甲击 / 刃暴 | 金属 |
| `vfx_heal_small/large` | 治愈之光、净化之水、治愈孢子 / 治愈铃音、圣光 | 治疗 |
| `vfx_shield` | 岩盾、金御、援护 | 护盾 |
| `vfx_buff` / `vfx_debuff` | 战吼、集气、铁壁、疾风步 / 破甲、致盲、诅咒 | 强化/削弱 |
| `vfx_control` | 缠绕、沉默之雾、雷波、嘲讽 | 控制 |
| `vfx_poison` | 毒刺、剧毒 | 中毒 |
| `vfx_leave_one_hp` | 留生一击 | 特殊 |
| `vfx_life_drain` | 吸血之牙 | 吸血 |
| `vfx_dispel` | 驱散之光、破盾击 | 驱散 |
| `vfx_capture` | 捕捉行动 | 捕捉 |
| `vfx_boss_phase` | Boss 阶段机制 | 特殊 |

**建议 VFX 数量**：约 **30~40 个模板**（9 元素 × 2~3 档 + 治疗/护盾/强化/削弱/控制/中毒/特殊 ~10），即可覆盖全部 109 技能，避免无限膨胀。

### 6.3 战斗背景

6 张区域战斗背景（`battle_bg_<biome>`，1920×1080），或至少 1 张通用 + 6 张区域变体。

---

## 7. UI 资源清单

按「必须制作 / 建议制作 / CSS 即可 / SVG 即可」区分。

| 资源 | 建议 | 说明 |
| -- | -- | -- |
| 游戏 Logo / 标题 | 必须制作（SVG/AI） | 当前纯文字 |
| 首页背景 | 建议制作 | 当前无背景 |
| 主菜单背景 | 建议制作 | 当前无 |
| 宠物详情/图鉴背景 | 建议制作（可共享 1 张） | 当前无 |
| 战斗背景 | 必须制作 | 见 §6.3 |
| NPC 对话立绘 | 必须制作 | 见 §3 |
| 面板/卡片/弹窗背景 | CSS 即可 | 当前 CSS 已可 |
| 按钮背景 | CSS 即可 | 当前 CSS 已可 |
| 边框/分割线/Tab | CSS 即可 | 当前 CSS 已可 |
| 血条/经验条 | CSS 即可 | 当前 CSS 色块已实现 |
| 宠物卡框/品质边框 | CSS 即可（稀有度色已定义） | 当前 CSS |
| 属性标签/选中框/悬停 | CSS 即可 | 当前 CSS |
| Loading 动画 | CSS 即可 | 当前文本「加载中...」 |
| 空状态插图 | 建议制作（少量） | 当前文字 |
| Toast 图标 | SVG 即可 | 当前无图标 |
| 关闭/继续/勾选等小图标 | SVG 即可 | 当前 emoji（✕/▸/✓/○） |

**关于属性色**：UI 设计文档已定义 9 属性官方色（金 #C9B037 / 木 #66BB6A / 水 #42A5F5 / 火 #EF5350 / 土 #8D6E63 / 风 #80CBC4 / 雷 #FFCA28 / 光 #FFF176 / 暗 #7E57C2），前端 `getElementColor()` 已内置，属性图标只需 9 个全局共享。

---

## 8. 道具与状态图标清单

### 8.1 道具图标（34 道具 → 去重后约 30）

| 类别 | 数量 | 图标方案 | 去重 |
| -- | -- | -- | -- |
| 恢复药（小型/中型/大型） | 3 | 药瓶基础 + 尺寸/颜色变体 | 3（或 1 模板+3 变体） |
| 复苏药剂 | 1 | 复苏药瓶 | 1 |
| 净化药 | 1 | 药瓶（特殊） | 1 |
| 捕捉球（普通/高级/特级） | 3 | 球 + 颜色/样式 | 3 |
| 元素结晶（9 属性） | 9 | 结晶 + 属性色 | 9（复用属性色） |
| Boss 核心（5 区域） | 5 | 核心模板 + 区域色 | 5（或 1 模板） |
| 技能书（23 本） | 23 | 书底图 + 技能属性图标 | 1 书底图（复用技能图标） |

**合计：约 30 款**（技能书 23→1 底图、结晶 9→复用属性色、核心 5→模板）。技能书被动 10 本同样复用「书底图 + 对应属性/强化图标」，不新增美术资源。

### 8.2 状态图标（24 状态）

| 类别 | 状态 | 图标 |
| -- | -- | -- |
| 持续伤害 | 灼烧 BURN、中毒 POISON | 火苗/毒滴 |
| 持续恢复 | 再生 REGEN | 绿十字 |
| 持续 | 混乱 CONFUSION、隐匿 STEALTH、狂暴 BERSERK | 旋转/隐形/红眼 |
| 控制 | 麻痹 PARALYSIS、缠绕 ROOT、沉默 SILENCE、震慑 CAPTURE_STUN | 专属 |
| 减益 | 致盲 BLIND、浸湿 SOAK、破甲 ARMOR_BREAK、诅咒 CURSE、禁疗 HEAL_BLOCK | 专属 |
| 增益 | 攻击提升 ATK_UP、防御提升 DEF_UP、速度提升 SPD_UP、抗性提升 SPDEF_UP、嘲讽 TAUNT、援护 GUARD、反击 COUNTER | 专属（↑↓ 变体） |
| 标记 | 猎杀印记 HUNT_MARK、雷印 THUNDER_MARK | 专属 |

**去重**：增益/减益类可共用「↑/↓ + 属性符号」模板；共约 **24 款**，其中 4 档增益/减益可用模板 + 变体。

---

## 9. NPC / Boss 资源

### 9.1 NPC（9 个）

| NPC | 建议资源 |
| -- | -- |
| 村长 NPC_VILLAGE_ELDER | 立绘 + 地图精灵 + 对话头像 |
| 草原巡逻员 NPC_MEADOW_SCOUT | 同上 |
| 森林隐士 NPC_FOREST_HERMIT | 同上 |
| 湖域守护者 NPC_WATERS_KEEPER | 同上 |
| 雷鸣贤者 NPC_THUNDER_SAGE | 同上 |
| 遗迹向导 NPC_RUINS_GUIDE | 同上 |
| 迷路旅人 NPC_LOST_TRAVELER | 同上 |
| 神秘影子 NPC_SHADOW_SPIRIT | 同上 |
| 旅行商人 NPC_FOREST_MERCHANT | 同上 |

> 对话头像可由立绘裁切；地图精灵可用通用 NPC 模板 + 角色色。

### 9.2 Boss（8 个）

| Boss | 属性 | 类型 |
| -- | -- | -- |
| 草原守卫·岩甲犀 BOSS_MEADOW_GUARDIAN | 土 | 主 Boss |
| 森林之王·翠牙狼 BOSS_FOREST_KING | 木 | 主 Boss |
| 湖泊守卫者·潮灵蛇 BOSS_WATERS_GUARDIAN | 水 | 主 Boss |
| 雷霆之主·雷翼鹰 BOSS_THUNDER_GUARDIAN | 雷 | 主 Boss |
| 远古守护者·暗影巨像 BOSS_RUINS_GUARDIAN | 暗 | 主 Boss（最终） |
| 月下之影·暗纱狐 BOSS_HIDDEN_SHADOW | 暗 | 隐藏 Boss |
| 林间霸主·翠鳞蟒 BOSS_ELITE_FOREST | 木 | 精英 Boss |
| 远古光灵·星辰鹿 BOSS_HIDDEN_RUINS | 光 | 隐藏 Boss |

**Boss 资源**：立绘（P0，重点）+ 战斗形象（复用立绘）+ 出场特效（P2）+ Boss 专属状态无需（沿用通用状态图标）。Boss 技能特效复用 §6.2 通用 VFX。

---

## 10. 占位资源替换表

### 10.1 代码内的 emoji / 字符占位

| 当前占位 | 使用位置 | 最终应替换为 |
| -- | -- | -- |
| 宠物名首字 / `?` | PokedexView 卡片头像 | `pet_<id>_icon`（立绘裁切） |
| ✨ | 精英个体（Explore/Battle/Pet） | 精英标识徽章/光效 |
| 💫 | 安全捕捉窗口（Battle） | 震慑状态图标 |
| 📖 | 技能书来源（Pet） | 技能书图标 |
| ★ | 特色技能（Pet） | 特色技能标记 |
| 🔒 | Boss 难度锁定（Boss） | 锁 SVG 图标 |
| ▶ / ■ | 自动战斗开关（Battle） | 播放/停止 SVG 图标 |
| ✓ / ○ | 任务目标（QuestDetail） | 勾选 SVG 图标 |
| 🎉 | 通关庆祝（QuestDetail） | 庆祝插画 |
| ⚠ | 放生警告（Storage） | 警告 SVG 图标 |
| ▸ / ✕ / × | 对话继续/关闭 | SVG 图标 |
| 属性中文单字 | 元素徽标（NewGame 等） | `element_<id>` 属性图标 |
| 纯文字血条 | 战斗 HP | CSS 已实现（可保留） |

### 10.2 Phaser 占位 PNG

| 当前占位文件 | 使用位置 | 最终应替换为 |
| -- | -- | -- |
| `assets/sprites/player.png` | 地图玩家 | `player` 正式角色 |
| `assets/sprites/wild_*.png`（4） | 地图野怪 | 野怪/宠物图标 |
| `assets/sprites/camp/chest/gather/exit/boss_door/npc/hidden_spot.png`（7） | 地图对象 | 对应正式对象 |
| `assets/maps/tileset.png` | 全地图地块 | 分区 Tileset |

### 10.3 引用异常的说明

- **资源引用异常**：`sprites/entry.png` 存在但未被 BootScene 加载（非缺失，属冗余可清理）。
- **代码路径均正确**：Phaser 加载路径 `/assets/maps/*.json`、`/assets/maps/tileset.png`、`/assets/sprites/*.png` 与目录一致，无「路径错误」类问题，均为「资源未正式制作」。

---

## 11. 资源复用方案

| 目标资源 | 复用来源 |
| -- | -- |
| 宠物头像 `pet_<id>_icon` | ← 宠物立绘裁切（不单独生成） |
| 小尺寸宠物卡图 | ← 头像缩放 |
| 技能书图标 | ← 技能书底图 + 技能属性图标 |
| 属性图标 | ← 全局共享 9 个 |
| 状态图标（增益/减益档） | ← ↑/↓ 模板 + 属性符号 |
| 普通技能 VFX | ← VFX 模板复用（小/中/大档） |
| Boss 技能 VFX | ← 通用元素 VFX |
| 对话头像 | ← NPC 立绘裁切 |
| 地图野怪精灵 | ← 对应宠物图标/通用野怪 |
| 特殊外观（发光/异色） | ← 程序滤镜/辉光特效 |
| 战斗背景 | ← 每区域 1 张，不逐页重复 |

---

## 12. 资源制作顺序（建议）

| 批次 | 内容 | 依赖 |
| -- | -- | -- |
| 第一批 | 27 宠物立绘（P0） | 需先定 Art Bible / 风格锚点 |
| 第二批 | 8 Boss 立绘（P0） | 依赖宠物风格 |
| 第三批 | 9 属性图标 + 9 区域战斗背景（P0/P1） | 依赖属性色 |
| 第四批 | 地图 Tileset + 地图对象 Sprite（P0） | 依赖 Tile 尺寸/主题 |
| 第五批 | 宠物头像自动裁切（P0） | 依赖宠物立绘 |
| 第六批 | 技能 VFX 模板（P1） | 依赖技能矩阵 |
| 第七批 | 道具 / 状态 / 技能图标（P1） | 独立 |
| 第八批 | 9 NPC 立绘（P1） | 依赖人物风格 |
| 第九批 | Logo / 首页背景 / 区域缩略图（P1/P2） | 独立 |
| 第十批 | 随机事件插画 / 出场特效 / 高级装饰（P2/P3） | 独立 |

---

## 13. AI 批量生成准备（供接入美术 Skill）

### 13.1 批量生成组

| 生成组 | 数量 | 共享风格 | 独立变量 |
| -- | -- | -- | -- |
| `PET_PORTRAIT` | 27 | 元素宠物 + 轻量休闲 + 清新幻想 + 柔和饱和 + 圆润造型 + 轻微手绘 | 宠物 id、名称、属性、稀有度、外观描述、体型推断、主色 |
| `BOSS_PORTRAIT` | 8 | 同上（更威严/大型） | Boss id、名称、属性、类型、描述 |
| `NPC_PORTRAIT` | 9 | 休闲幻想人物 | id、名称、身份、属性倾向 |
| `MAP_TILESET` | 4~6 | 按主题（草地/水域/雷域/遗迹） | 主题、地块清单 |
| `VFX_TEMPLATE` | 30~40 | 粒子/能量特效 | 属性、类别、强度档 |
| `ITEM_ICON` | ~30 | 物品图标 | 类别、属性色 |
| `STATUS_ICON` | 24 | 状态小图标 | 类别、方向 |
| `ELEMENT_ICON` | 9 | 属性符号 | 属性色 |

### 13.2 适合批量 AI 生成

宠物立绘、Boss 立绘、NPC 立绘、VFX、道具/状态/属性图标、区域缩略图。

### 13.3 必须保持统一风格

27 宠物之间（必须经 A0 锚点 + Contact Sheet 校验）、属性色、VFX 的亮度/粒子密度、Tileset 的透视与光源方向。

### 13.4 依赖其他资源

宠物头像←立绘、对话头像←NPC 立绘、地图野怪←宠物图标、技能书图标←技能属性图标、战斗背景←区域主题。

### 13.5 可程序自动生成

宠物头像裁切、异色/辉光变体（滤镜）、UI 按钮/面板/边框/血条（CSS）、Loading、Toast、小 SVG 图标。

### 13.6 应从已有资源裁切

宠物头像、对话头像、小尺寸宠物卡图。

---

## 14. 代码资源替换难度评估（本阶段仅报告，不重构）

当前前端资源引用方式：

- **无 `<img>` / `background-image` / `url()` 硬编码**（UI 层），宠物/属性/技能/状态/道具全部依赖 emoji 与文本，因此**未来接入正式图时引入点集中、无散落路径**。
- 唯一 Phaser 资源加载集中在 `BootScene.preload()`（图集 + 地图 + 精灵数组），替换友好。
- 占位精灵到正式资源的升级，建议在 `BootScene` 的加载数组与 `MapScene` 的 `WILD_TEXTURES` / `OBJECT_VISUALS` 两张映射表中调整即可。

**后续建议（非本阶段执行）**：宠物/属性/技能/状态/道具的正式图接入，建议建立统一资源映射，避免在组件内散落 URL。可选方案：

- `AssetRegistry` / `ResourceManifest`：统一登记全部资源路径与 ID 的映射。
- `PetAssetConfig`：宠物 ID → 立绘/图标路径。
- `VfxRegistry`：技能 → VFX 模板映射。

> 当前 UI 层无图片散落引用，故引入成本低；建议在第一批宠物立绘接入时一并建立 `PetAssetConfig`，其余按需扩展。

---

## 15. 总结

- UI 层零正式美术，全部为 emoji/色块/文字占位；Phaser 层 15 个占位文件。
- 最大缺口：**27 宠物立绘 + 8 Boss + 9 NPC + 地图 Tileset**（P0/P1）。
- 去重后建议新制作资源约 **120~140**（宠物 27 + Boss 8 + NPC 9 + Tileset 4~6 + 地图精灵 12 + VFX 30~40 + 状态 24 + 道具 30 + 技能 24 + 属性 9 + 战斗背景 6 + 事件 6 + 区域缩略图 6 + Logo/首页背景 2）。
- 上述清单可直接作为 `game-art-director` / `pet-character-artist` / `rpg-tileset-designer` / `battle-vfx-artist` / `game-asset-pipeline` 等美术 Skill（见 `pet-spirit-art-skills/`）与《AI 美术资源批量生成提示词》的输入依据。
