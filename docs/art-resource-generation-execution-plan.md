# 《宠物精灵》AI 美术资源生成执行计划（历史执行矩阵）

> 版本：V1.0
> 生成日期：2026-08-14
> 依据：需求设计文档 V1.0（含 §142～151 补充）、UI 设计文档 V1.0、`docs/art-resource-inventory.md`、`plans/《宠物精灵》AI 美术资源批量生成提示词.md`、全部 `backend/src/main/resources/game-config/*.yml` 实际配置、`frontend/public/assets` 与前端代码现状、Git 仓库状态。
> 历史定位：这是 2026-08-14 Batch 0～8 的执行矩阵和决策证据，保留用于追溯；**不可作为新增资源的当前执行依据**。当前入口为 `docs/art/README.md`，流程、规范、模板、验收和清单分别见该目录下对应文档。

> 执行状态更新（2026-08-14）：已完成 Batch 0～7 的资源生成、派生与页面接入，并完成 Batch 8 静态 QA；游戏内实机截图随阶段 14 核心场景总验收执行。资源清单、接入点和 QA 结果见 `docs/art/asset-manifest.md`、`docs/art/m7-qc.md`、`docs/art/m8-qc.md`。

---

## 1. 文档目的

将「游戏功能」→「视觉表现需求」→「真正需要创建的独立美术文件」三层归并完成：

1. 从需求文档、AI 美术提示词、资源盘点三份核心资料中提取全部原始美术需求；
2. 与 `game-config` 实际配置交叉核对，修正盘点文档中的过时/错误数字；
3. 按视觉语义去重，区分 **Source Asset（AI 生成）** / **Derived Asset（脚本派生）** / **NO_ASSET_REQUIRED（程序实现）**；
4. 输出可直接执行的批次计划、命名规范、QA 标准与统计结果。

本文件完成后曾用于执行本轮批次；后续新增资源应按 `docs/art/` 当前工作流重新登记、查重和验收，不直接复用本轮一次性批次编号。

---

## 2. 分析依据

| 资料 | 有效版本判定 | 在本任务中的角色 |
|---|---|---|
| `宠物精灵游戏第一阶段需求设计文档 V1.0.md` | 仓库唯一版本；`AGENTS.md` 权威文档清单引用；含 §142～151 技能/状态补充（REV 回归已完成） | **最高优先级**，玩法规则与内容规模的唯一依据 |
| `宠物精灵游戏第一阶段UI设计文档 V1.0.md` | 仓库唯一版本，色彩体系已在前端 `getElementColor()` 落地 | UI 视觉规范、属性色/稀有度色、占位规范 |
| `plans/《宠物精灵》AI 美术资源批量生成提示词.md` | 仓库唯一版本 | AI 生成流程约束（A0 锚点、批次、QC、命名） |
| `docs/art-resource-inventory.md` | 2026-08-14 生成，仓库唯一版本；经与实际配置核对，**大部分准确，5 处需修正**（见 §16） | 现状盘点基线，需按本计划修正后使用 |
| `game-config/*.yml`（pets/skills/statuses/items/maps/bosses/quests/events/achievements/elements） | 后端实际加载的内容配置，**实际数量的最终来源** | 资源需求的真实规模依据 |
| `frontend/public/assets/`、`frontend/src/views/*` | 当前代码实况 | 占位资源与替换点清单 |

**版本有效性说明（Git 佐证）**：`git log` 最新提交为阶段 10（自动战斗策略）；工作区存在阶段 11 未提交内容（achievements/statistics/completion 模块、`V10__phase11_tables.sql`、`achievements.yml`）。因此：成就系统配置（27 条）**已存在但未验收**，其美术需求按「低优先级 + 待确认」处理；其余阶段 1～10 内容视为已定稿。

**实际数据规模（以配置为准，修正 inventory 后）**：

| 类别 | 数量 | 来源 | 与 inventory 差异 |
|---|---:|---|---|
| 宠物 | 27（普通 12 / 稀有 9 / 珍稀 5 / 传说 1） | `pets.yml` | 一致 |
| 属性 | 9 | `elements.yml` | 一致 |
| 技能定义 | 99（主动 85 + 被动 14） | `skills.yml` | 一致 |
| 状态 | 24 | `statuses.yml` | 一致 |
| 道具 | **35**（恢复 4 + 捕捉 3 + 净化 1 + 材料 14 + 技能书 **13**） | `items.yml` | inventory 记 34 / 12 书，**错误** |
| Boss | 8（主 5 + 隐藏 2 + 精英 1，各 3 难度） | `bosses.yml` | 一致 |
| 地图 / 区域 | 6（1 据点 + 5 区域） | `maps.yml` + Tiled JSON | 一致 |
| NPC | 9 | `quests.yml` | 一致 |
| 随机事件 | 6 | `random-events.yml` | 一致 |
| 任务 | 25（主线 12 + 支线 10 + 隐藏 3） | `quests.yml` | 一致 |
| 成就 | 27（阶段 11 未验收） | `achievements.yml` | inventory 未含 |

---

## 3. 当前美术资源现状

- **UI 层零位图**：全部页面（Vue）无任何 `<img>` / `background-image` / `url()` 引用；宠物/属性/技能/状态/道具/稀有度全部由 emoji、CSS 色块、文字首字承担（`BattleView`/`PetView`/`BossView`/`StorageView`/`QuestDetail`/`ExploreView` 等均有 ✨💫📖🎉🔒⚠ 等 emoji 占位）。
- **Phaser 层 15 个占位文件**（`frontend/public/assets/`）：
  - `maps/tileset.png`：1 张 4 色块占位 Tileset（草/路/水/树），被 6 张地图 JSON 共用；
  - `sprites/`：`player / wild_wander / wild_timid / wild_aggressive / wild_rare / camp / chest / gather / exit / boss_door / npc / hidden_spot` 共 12 个占位 PNG；
  - `sprites/entry.png`：**存在但未被任何代码引用**（冗余，随正式资源接入时清理）；
  - `public/pet-icon.svg`：favicon。
- **地图规格**：Tile 32px、25×19 格（800×608）、Phaser 正交、`pixelArt: true`。6 张 Tiled JSON（start_village/meadow/forest/waters/thunder/ruins）结构已就绪，仅替换图块即可。
- **战斗呈现方式**：战斗页为 Vue DOM（`BattleView.vue`），**无 Phaser 战斗场景**。战斗特效的载体格式（APNG / sprite sheet / CSS 动画 / WebM）为待确认项（§17-4）。
- **资源引入点集中**：Phaser 加载集中在 `BootScene.preload()`；UI 层未来接入建议建立统一 `AssetRegistry`（inventory §14），替换成本低。

---

## 4. 美术资源需求提取方法

提取分两步：

1. **第一层（功能 → 视觉表现）**：逐份文档扫描涉及视觉的内容（需求 §59/§114～122、UI 文档各页面、配置全量条目）；
2. **第二层（视觉表现 → 原始需求条目）**：每个使用场景记录一条需求，暂不去重，形成 §4.1 原始需求池。

判定规则：

- 需求文档 > 实际配置 > inventory 结论；
- 配置中存在但 inventory 遗漏的 → 新增需求；
- inventory 中存在但需求/配置已不需要或可程序实现的 → 废弃或降级；
- 「文档中提到的示例能力但配置未实现」（如成就徽章、命运天平、连锁闪电等 §145～147 技能）→ **不为未实装内容设计资源**，记入待确认。

### 4.1 原始资源需求池（未去重）

> 每条记录格式：原始需求｜来源（文档/章节）｜使用场景｜对应系统/实体｜现状｜是否要求独立资源。
> 共 62 组记录，覆盖约 **500 个**原始资源点位（27 宠物 × 多用途、99 技能 × 特效/图标等按点位计）。

**A. 宠物资源（162 点位）**

| # | 原始需求 | 来源 | 使用场景 | 现状 | 独立资源要求 |
|---|---|---|---|---|---|
| A-01 | 27 宠物完整立绘 | 需求 §59/§122；`pets.yml` 27 条 description | 图鉴/详情/战斗/获取展示 | 无（文字首字） | 是，每只独立 |
| A-02 | 27 宠物头像（队伍/HUD/选择器） | UI 文档 §5/§6；需求 §117 | Team/Battle/Pokedex 列表 | 无 | 否（可自立绘裁切） |
| A-03 | 27 宠物小尺寸缩略图（仓库/图鉴网格） | 需求 §52/§97 | Storage/Pokedex 网格 | 无 | 否（缩放） |
| A-04 | 27 宠物地图形象（遭遇前可见） | 需求 §70/§72 | Phaser MapScene 野怪 | 4 个占位圆 | 否（见 §6.2 派生方案） |
| A-05 | 特殊外观（异色 0.5% / 辉光 0.3%） | 需求 §57/§122；AGENTS §7 | Explore/Battle/Pet 标识 | emoji ✨ | 否（需求 §122 裁决：换色+局部特效，程序实现） |
| A-06 | 精英个体地图/战斗标识 | 需求 §57 | Explore/Battle/Pet | emoji ✨ | 否（SVG/程序徽章） |
| A-07 | 属性视觉标识（9 属性全局） | 需求 §5；UI 文档 §1.2 | 全局属性标签/徽标 | 中文单字+色块 | 是（9 个共享） |

**B. 地图与场景（27 点位）**

| # | 原始需求 | 来源 | 使用场景 | 现状 | 独立资源要求 |
|---|---|---|---|---|---|
| B-01 | 6 地图 Tileset（若按地图数） | 需求 §69/§70；`maps.yml` | Phaser 地图渲染 | 1 张 4 色块占位 | 否（按环境组件化，见 §6.2） |
| B-02 | 玩家角色 Sprite | 需求 §70/§123 | MapScene 玩家 | 占位圆 | 是（1 个） |
| B-03 | 野怪 4 行为 Sprite（游荡/胆小/主动/稀有） | 需求 §71 | MapScene `WILD_TEXTURES` | 4 占位圆 | 否（宠物图标派生+行为徽章） |
| B-04 | 营地（篝火）Sprite | 需求 §75；UI 文档地图节 | MapScene `OBJECT_VISUALS` | 色块 | 是 |
| B-05 | 宝箱（未开/已开两态） | 需求 §73 | MapScene | 色块 | 是（1 源图+开态派生） |
| B-06 | 采集点 Sprite | 需求 §73；`maps.yml` gathers | MapScene | 占位圆 | 是 |
| B-07 | 出口通道 Sprite | `maps.yml` exits | MapScene | 色块 | 是 |
| B-08 | Boss 入口 Sprite | 需求 §77；ExploreView boss:touch | MapScene | 色块 | 是 |
| B-09 | 隐藏点 Sprite | 需求 §72（隐藏遭遇） | MapScene | 占位圆 | 是（弱光可程序） |
| B-10 | 地图 NPC 形象 | 需求 §70；`quests.yml` 9 NPC | MapScene | 1 占位圆 | 否（1 模板+程序色变） |
| B-11 | 6 区域战斗背景 | UI 文档 §7 | BattleView 背景 | 无 | 是（6 张） |
| B-12 | 首页背景 | UI 文档 §3 | HomeView | 无 | 是（1 张，P2） |
| B-13 | 区域缩略图（大地图） | 需求 §116 | WorldMapView | 无 | 否（战斗背景裁切派生） |

**C. 战斗特效（100 点位）**

| # | 原始需求 | 来源 | 使用场景 | 现状 | 独立资源要求 |
|---|---|---|---|---|---|
| C-01 | 85 主动技能攻击/功能特效（按技能数） | `skills.yml` | BattleView 技能演出 | 无 | **否**（按视觉语义归并为 31 个 VFX 模板，见 §6.3） |
| C-02 | 24 状态附加/持续表现 | `statuses.yml` | BattleView 状态区 | 文字标签 | 否（图标承担；动效 CSS） |
| C-03 | 捕捉表现（投球/成功/失败） | 需求 §46～50 | BattleView 捕捉面板 | 无 | 是（1 套） |
| C-04 | 留生一击保护表现 | 需求 §142 | BattleView | 无 | 是（1 个） |
| C-05 | 吸血/驱散/破盾/反击/换宠/行动顺序反馈 | 需求 §143～146；skills.yml BOOK 技能 | BattleView | 无 | 部分（吸血/驱散各 1；其余复用或 CSS） |
| C-06 | Boss 阶段机制表现（护盾/强化/转阶段） | 需求 §81；`bosses.yml` phases | Boss 战斗 | 无 | 是（1 个转阶段警示，其余复用护盾/Buff VFX） |
| C-07 | 命中/暴击/伤害数字/受击闪烁 | 需求 §39/§122 | BattleView | 文字数字已有 | 否（引擎/CSS 程序实现） |

**D. UI 美术（约 160 点位）**

| # | 原始需求 | 来源 | 使用场景 | 现状 | 独立资源要求 |
|---|---|---|---|---|---|
| D-01 | 游戏 Logo / 标题 | UI 文档 | MainLayout/NewGame | 纯文字 | 是（SVG/AI） |
| D-02 | 9 属性图标 | UI 文档 §1.2 | 全局 | 中文单字 | 是（9 个全局共享） |
| D-03 | 24 状态图标（含回合/层数展示） | 需求 §119（混乱/震慑/隐匿/狂暴必须明显不同）；`statuses.yml` | BattleView/PetView | 文字 | 是（24 个，Buff/Debuff 档可模板化） |
| D-04 | 99 技能图标 | UI 文档 §15.2（旧估 80~100）；`skills.yml` | PetView/BattleView 技能按钮 | 文字 | **否**（类型底图+属性色组合，20 底图） |
| D-05 | 35 道具图标 | UI 文档 §15.2（旧估 40~60）；`items.yml` | Inventory/Shop/掉落结算 | 文字 | **否**（7 底图 + recolor/组合变体） |
| D-06 | 货币图标（金币） | 需求 §92 | Home/Shop/结算 | 文字/色块 | 否（1 个小图标或 SVG） |
| D-07 | 功能小图标（关闭/继续/勾选/警告/锁/播放/停止/收藏/锁定） | inventory §10.1 emoji 清单 | 多页面 | emoji ✕▸✓○🔒▶■⚠ | 否（SVG 程序绘制） |
| D-08 | 稀有度视觉标识 | UI 文档 §1.2 稀有度色 | 卡片边框/标签 | CSS 色已实现 | 否（CSS） |
| D-09 | 面板/按钮/边框/Tab/血条/经验条 | UI 文档 §1.5 | 全局 | CSS 已实现 | 否（CSS，保持） |
| D-10 | 空状态插图（仓库/图鉴/任务空列表） | inventory §7 | Storage/Pokedex/Quest | 文字 | 少量（1~2 张，P2） |
| D-11 | 新游戏玩家预设头像 | 需求 §123 | NewGameView | 无（仅名称） | 待确认（§17-1） |

**E. 道具与物品（35 点位）**

| # | 原始需求 | 来源 | 现状 | 独立资源要求 |
|---|---|---|---|---|
| E-01 | 恢复药 3 档图标 | `items.yml` | 文字 | 否（1 底图+3 尺寸/色变体） |
| E-02 | 复苏药剂图标 | `items.yml` | 文字 | 是（1） |
| E-03 | 净化药图标 | `items.yml`（预留启用） | 文字 | 是（1） |
| E-04 | 捕捉球 3 档图标（另用于捕捉 UI） | `items.yml`；需求 §47/§48 | 文字 | 否（1 底图+3 色变体） |
| E-05 | 9 属性结晶图标 | `items.yml` | 文字 | 否（1 结晶底图+9 属性色） |
| E-06 | 5 Boss 核心图标 | `items.yml` | 文字 | 否（1 核心底图+5 区域色） |
| E-07 | 13 技能书图标 | `items.yml`（**13 本**） | 文字 | 否（1 书底图+技能属性徽章组合） |
| E-08 | 任务物品图标 | 需求 §92 | `quests.yml` 奖励仅复用既有道具 | 否（当前无独立任务物品） |

**F. NPC / Boss / 世界物件（51 点位）**

| # | 原始需求 | 来源 | 现状 | 独立资源要求 |
|---|---|---|---|---|
| F-01 | 8 Boss 立绘（含战斗形象） | `bosses.yml`；需求 §77 | 无（文字） | 是（8，战斗复用立绘） |
| F-02 | 9 NPC 对话立绘 | `quests.yml` npcs | 无 | 是（9） |
| F-03 | 9 NPC 对话头像 | UI 文档对话节 | 无 | 否（立绘裁切） |
| F-04 | Boss 出场特效 | inventory §9.2（建议 P2） | 无 | 否（可选，不入基线） |
| F-05 | 6 随机事件插画 | `random-events.yml` | 无 | 是（6，P2） |
| F-06 | 成就徽章/头像/称号展示 | 需求 §110 vs `achievements.yml`（未实装） | 文字 | 否（配置未实装，§16-4） |
| F-07 | 教学浮层/对话框装饰 | 需求 §125；quests.yml tutorials | CSS 已实现 | 否（CSS） |

**原始需求合计：62 组记录 / 约 500 个资源点位。**

---

## 5. 去重规则

| 原则 | 本项目执行方式 |
|---|---|
| 1. 同一资源多处引用只保留一个 | 宠物头像=图鉴头像=队伍头像=仓库缩略图，全部由 `pet_<id>_portrait` 裁切/缩放派生，不生成第二张 |
| 2. 优先复用不重复生成 | 头像裁切、图标 recolor、异色滤镜、CSS 面板、SVG 小图标、Phaser tween 命中反馈，一律不交 AI |
| 3. 技能按视觉语义去重 | 85 主动技能 → **31 个 VFX 模板**（属性×强度档 + 功能类），技能只引用模板（§8.1） |
| 4. 状态按状态语义去重 | 24 状态各 1 图标（需求 §119 要求可区分），Buff/Debuff 4 档采用「箭头+属性符号」模板变体；状态附加动效用 CSS，不做 24 套状态 VFX |
| 5. 地图组件化 | 6 地图 → **4 套 Tileset**（村庄/草原/森林共享草地基础）+ 通用 Props；不按地图数生成 |
| 6. UI 组件化 | 不为任何页面生成整图；面板/按钮/边框/血条全部 CSS；仅图标/背景/Logo 走图 |
| 7. 宠物保持独立性 | 27 只立绘不因外形接近合并；共享仅限 UI 框、状态图标、属性特效、通用 VFX |
| 8. 不为未实装功能设计资源 | 命运天平/连锁闪电/星陨等 §145~147 机制、成就徽章、商店限购等当前无对应视觉资源需求 |

---

## 6. 最终资源分类（去重后）

### 6.1 Pets（含 Boss）

- **27 宠物立绘**（Source，A 类单张生成，P0）：规格 1024×1024 透明 PNG，全身完整、统一构图/视角/光源（遵循提示词 §六）。每只至少 1 轮廓特征+1 结构特征+1 配色+1 记忆点；稀有度差异用细节精致度与光效强度表达，不做尺寸/构图差异。
- **8 Boss 立绘**（Source，P0）：同规格，更威严/大型体量；战斗形象直接复用立绘。
- **特殊外观**：NO_ASSET_REQUIRED（异色=色相滤镜，辉光=Phaser glow/叠加光圈）。
- **宠物头像/缩略图/地图小像**：全部 Derived（§9）。

### 6.2 Maps

- **4 套 Tileset（32px 格，Source，P0）**：
  - `tileset_grassland_base`：晨曦村+青草原+翠树林共用（草地/道路/花草/树木/围栏/村庄建筑基础）；
  - `tileset_waters`：静水湖域（水面 autotile 岸边/内外角/河道、湖岸、湿地）；
  - `tileset_thunder`：雷鸣高地（岩地、电弧地貌、雷晶）；
  - `tileset_ruins`：远古遗迹（石板、残垣、符文、遗迹门）。
  - 地块类型清单：ground/road/water/cliff/tree/rock/fence/flower/bridge/building/decoration（按 UI 文档地图节）。
- **7 个地图对象 Sprite（Source，P0）**：player、camp（篝火）、chest、gather、exit、boss_door、hidden_spot，32px 级、透明背景、与地图透视/光源一致。
- **野怪 4 行为形象**：Derived —— 由该野怪宠物图标缩放 + 行为徽章（程序绘制：游荡无徽章/胆小浅蓝点/主动红点/稀有金星）替代 4 个占位圆，**不 AI 生成**；精英个体叠加 SVG 光环徽章。
- **地图 NPC 形象**：Derived —— 1 个通用人形模板（随 `prop_npc` 一并生成）+ 9 种程序色变。
- **宝箱两态**：Source 1 张（未开），已开态由源图程序派生（开盖+变暗）或直接 AI 同批出 2 帧（二选一，QC 定）。
- **6 战斗背景**（Source，P1，1920×1080）：每区域 1 张，与 Tileset 主题同色系。
- **区域缩略图**：Derived —— 战斗背景中心裁切+缩放（P2）。
- `entry.png`：废弃清理项。

### 6.3 Battle FX（31 个 VFX 模板，Source，P1；捕捉/留生为 P0）

> 不要求 1 技能 = 1 特效。属性 VFX 按「属性 × 强度档」，强度档由技能实际形态决定（只有配置里真实存在的群体/蓄力/强力单体才配高档位）。

| 分组 | 模板 | 数量 |
|---|---|---:|
| 属性攻击 | fire(small/medium/large/ultimate)、thunder(small/medium/large)、water(small/medium)、earth(small/large)、wind(small/medium)、light(small/medium)、metal(small/medium)、dark(small)、wood(small) | 19 |
| 治疗 | heal_small（单体）、heal_aoe（全体铃音） | 2 |
| 护盾 | shield（岩盾/金御/ADD_SHIELD 阶段共用） | 1 |
| 增益 | buff_up（战吼/集气/铁壁/疾风步/健美/冒想/嘲讽/援护/反击态势） | 1 |
| 减益 | debuff_down（破甲/诅咒/致盲/浸湿/禁疗附加表现） | 1 |
| 控制 | control_bind（缠绕/气流禁锢/麻痹/沉默之雾共用） | 1 |
| 中毒 | poison（毒刺/荆棘/剧毒） | 1 |
| 吸血 | life_drain（吸血之牙） | 1 |
| 驱散/破盾 | dispel（驱散之光/破盾击） | 1 |
| 留生一击 | leave_one_hp（保护触发+震慑附加） | 1 |
| 捕捉 | capture（投球轨迹+收束，成功/失败反馈用 CSS 抖动/光效） | 1 |
| Boss 阶段 | boss_phase（转阶段警示环） | 1 |
| **合计** | | **31** |

换宠、行动顺序变化、暴击、伤害数字、受击闪烁：**NO_ASSET_REQUIRED**（CSS/引擎 tween）。

### 6.4 UI

- **9 属性图标**（Source，P0，128×128）：全局共享，配色锁定 UI 文档 §1.2 官方色。
- **24 状态图标**（Source，P1，128×128）：按 `statuses.yml` 24 条逐一设计；ATK_UP/DEF_UP/SPD_UP/SPDEF_UP 用「↑+属性符号」模板族，其余专属图形（混乱/震慑/隐匿/狂暴必须显著可辨）。
- **20 技能类型底图**（Source，P1，256×256）：physical_strike / claw_slash / projectile / explosion / falling / wave / heal / shield / buff_up / debuff_down / bind / mist / poison / drain / dispel / counter / taunt / book_common / passive_badge / capture_assist。技能图标 = 底图 + 属性色描边/角标（程序组合派生），覆盖全部 99 技能。
- **Logo**（Source，P1，SVG 或 AI 转 SVG）。
- **面板/按钮/边框/Tab/血条/经验条/稀有度框/选中态/Loading/Toast**：NO_ASSET_REQUIRED（CSS 已实现或 SVG）。
- **功能小图标集**（约 12 个：关闭/继续/勾选/警告/锁/播放/停止/收藏/锁定/精英/任务标记/书）：NO_ASSET_REQUIRED（内联 SVG 程序绘制）。
- **空状态插图** 1~2 张（P2）。

### 6.5 Items（7 底图 Source + 33 变体 Derived）

| 底图 | 变体 | 变体数 |
|---|---|---:|
| `item_potion_base` | 小/中/大（尺寸+色） | 3 |
| `item_revive` | — | 0 |
| `item_purify_potion` | — | 0 |
| `item_ball_base` | 普通/高级/特级（色+纹） | 3 |
| `item_crystal_base` | 9 属性色（复用官方属性色） | 9 |
| `item_boss_core_base` | 5 区域色 | 5 |
| `item_skillbook_base` | 13 本（书底图+技能属性徽章组合） | 13 |
| **合计** | | **33** |

### 6.6 NPC / 角色

- 9 NPC 对话立绘（Source，P1，512×512 透明）：村长/草原巡逻员/森林隐士/湖域守护者/雷鸣贤者/遗迹向导/迷路旅人/神秘影子/旅行商人（与 `quests.yml` 一致，不扩编）。
- 对话头像 Derived（立绘裁切）；地图形象 Derived（通用模板+色变）。
- 玩家角色：地图 Sprite（P0）；新游戏预设头像为待确认项（§17-1）。

### 6.7 Other

- 6 随机事件插画（Source，P2，512×512，对应 `random-events.yml` 6 事件）。
- 首页背景 1 张（Source，P2）。
- 成就徽章/头像：当前配置未实装 → 不生成（§16-4）。

---

## 7. 资源需求矩阵

> 列说明：现状（有/占/无 = 已有正式资源/占位/缺失）；方式 = §11 生成方式；S/D/P = Source/Derived/Programmatic。
> 宠物 27 只逐条列出（原则 7：不可合并），其余按组列出。

| Asset ID | 分类 | 资源名称 | 使用位置 | 来源 | 现状 | 独立生成 | 方式 | S/D/P | 优先级 |
|---|---|---|---|---|---|---|---|---|---|
| PET-001 | Pets | pet_PET_METAL_001_portrait 铄锋 | 图鉴/详情/战斗/获取 | 需求§59+pets.yml | 无 | 是 | A | S | P0 |
| PET-002 | Pets | pet_PET_METAL_002_portrait 玄铠 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-003 | Pets | pet_PET_METAL_003_portrait 鎏刃灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-004 | Pets | pet_PET_WOOD_001_portrait 藤梦鹿 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-005 | Pets | pet_PET_WOOD_002_portrait 青芽灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-006 | Pets | pet_PET_WOOD_003_portrait 森蚀灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-007 | Pets | pet_PET_WATER_001_portrait 汐月灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-008 | Pets | pet_PET_WATER_002_portrait 涟歌灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-009 | Pets | pet_PET_WATER_003_portrait 雾澜兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-010 | Pets | pet_PET_FIRE_001_portrait 烬牙兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-011 | Pets | pet_PET_FIRE_002_portrait 绯焰灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-012 | Pets | pet_PET_FIRE_003_portrait 赤曜兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-013 | Pets | pet_PET_EARTH_001_portrait 岩魁 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-014 | Pets | pet_PET_EARTH_002_portrait 岳灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-015 | Pets | pet_PET_EARTH_003_portrait 磐震兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-016 | Pets | pet_PET_WIND_001_portrait 岚羽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-017 | Pets | pet_PET_WIND_002_portrait 逐风灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-018 | Pets | pet_PET_WIND_003_portrait 空澜兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-019 | Pets | pet_PET_THUNDER_001_portrait 霆跃兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-020 | Pets | pet_PET_THUNDER_002_portrait 鸣霄灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-021 | Pets | pet_PET_THUNDER_003_portrait 紫霄灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-022 | Pets | pet_PET_LIGHT_001_portrait 曜羽灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-023 | Pets | pet_PET_LIGHT_002_portrait 曦鹿 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-024 | Pets | pet_PET_LIGHT_003_portrait 辉星兽（传说） | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-025 | Pets | pet_PET_DARK_001_portrait 幽蚀灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-026 | Pets | pet_PET_DARK_002_portrait 夜缚兽 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| PET-027 | Pets | pet_PET_DARK_003_portrait 冥刃灵 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-001 | Pets | boss_BOSS_MEADOW_GUARDIAN_portrait 岩甲犀 | Boss页/战斗 | bosses.yml | 无 | 是 | A | S | P0 |
| BOSS-002 | Pets | boss_BOSS_FOREST_KING_portrait 翠牙狼 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-003 | Pets | boss_BOSS_WATERS_GUARDIAN_portrait 潮灵蛇 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-004 | Pets | boss_BOSS_THUNDER_GUARDIAN_portrait 雷翼鹰 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-005 | Pets | boss_BOSS_RUINS_GUARDIAN_portrait 暗影巨像 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-006 | Pets | boss_BOSS_HIDDEN_SHADOW_portrait 暗纱狐 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-007 | Pets | boss_BOSS_ELITE_FOREST_portrait 翠鳞蟒 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| BOSS-008 | Pets | boss_BOSS_HIDDEN_RUINS_portrait 星辰鹿 | 同上 | 同上 | 无 | 是 | A | S | P0 |
| CHR-001 | NPC | sprite_player 玩家地图角色 | MapScene | 需求§70/§123 | 占 | 是 | A | S | P0 |
| NPC-001~009 | NPC | npc_<id>_portrait ×9（村长等） | 对话框/任务 | quests.yml npcs | 无 | 是 | B | S | P1 |
| MAP-TS-01 | Maps | tileset_grassland_base | 村庄/草原/森林 | maps.yml | 占 | 是 | A | S | P0 |
| MAP-TS-02 | Maps | tileset_waters | 静水湖域 | 同上 | 占 | 是 | A | S | P0 |
| MAP-TS-03 | Maps | tileset_thunder | 雷鸣高地 | 同上 | 占 | 是 | A | S | P0 |
| MAP-TS-04 | Maps | tileset_ruins | 远古遗迹 | 同上 | 占 | 是 | A | S | P0 |
| MAP-PR-01 | Maps | prop_camp 篝火营地 | MapScene | 需求§75 | 占 | 是 | A | S | P0 |
| MAP-PR-02 | Maps | prop_chest 宝箱（开态派生） | MapScene | 需求§73 | 占 | 是 | A+C | S | P0 |
| MAP-PR-03 | Maps | prop_gather 采集点 | MapScene | maps.yml gathers | 占 | 是 | A | S | P0 |
| MAP-PR-04 | Maps | prop_exit 出口通道 | MapScene | maps.yml exits | 占 | 是 | A | S | P0 |
| MAP-PR-05 | Maps | prop_boss_door Boss 入口 | MapScene | 需求§77 | 占 | 是 | A | S | P0 |
| MAP-PR-06 | Maps | prop_hidden_spot 隐藏点 | MapScene | 需求§72 | 占 | 是 | A | S | P0 |
| MAP-PR-07 | Maps | prop_npc 通用 NPC 人形模板 | MapScene | quests.yml | 占 | 是 | A+C | S | P1 |
| BG-01~06 | Maps | battle_bg_<biome> ×6 | BattleView | UI文档§7 | 无 | 是 | A | S | P1 |
| BG-07 | Maps | bg_home 首页背景 | HomeView | UI文档§3 | 无 | 是 | A | S | P2 |
| FX-EL-01~19 | FX | vfx_<element>_<tier> ×19（见§6.3） | BattleView 技能演出 | skills.yml | 无 | 是 | B | S | P1 |
| FX-CB-01~12 | FX | vfx_combat_* ×12（治疗2/盾/Buff/Debuff/控制/毒/吸血/驱散/留生/捕捉/Boss阶段） | BattleView | skills/statuses/bosses.yml | 无 | 是（捕捉/留生 P0） | B | S | P1 |
| UI-EL-01~09 | UI | icon_element_<id> ×9 | 全局属性标识 | elements.yml | 无 | 是 | B | S | P0 |
| UI-ST-01~24 | UI | icon_status_<id> ×24 | BattleView/PetView | statuses.yml | 无 | 是 | B | S | P1 |
| UI-SK-01~20 | UI | icon_skilltype_<name> ×20 底图 | PetView/BattleView | skills.yml | 无 | 是 | B | S | P1 |
| UI-LOGO | UI | logo_game | MainLayout/NewGame | UI文档 | 无 | 是 | A | S | P1 |
| ITM-01~07 | Items | 7 道具底图（药瓶/复苏/净化/球/结晶/核心/书） | Inventory/Shop/捕捉 | items.yml | 无 | 是 | B+C | S | P1 |
| EVT-01~06 | Other | event_<id>_cg ×6 | 事件对话框 | random-events.yml | 无 | 是 | A | S | P2 |
| DER-01 | Pets | 宠物头像/缩略图/地图小像 ×27 | 队伍/仓库/图鉴/地图 | 多文档 | 无 | 否 | C | D | P0 |
| DER-02 | Maps | 野怪 4 行为形象（图标+行为徽章） | MapScene | 需求§71 | 占 | 否 | C+D | D | P0 |
| DER-03 | NPC | NPC 对话头像 ×9 + 地图色变 ×9 | 对话框/MapScene | quests.yml | 无 | 否 | C | D | P1 |
| DER-04 | Items | 道具变体 ×33（药3/球3/结晶9/核心5/书13） | Inventory/Shop | items.yml | 无 | 否 | C | D | P1 |
| DER-05 | Maps | 区域缩略图 ×6（战斗背景裁切） | WorldMapView | 需求§116 | 无 | 否 | C | D | P2 |
| PRG-01~20 | 多类 | CSS/SVG/引擎程序资源 ×约 20 项（见 §11-E） | 全局 | 多文档 | 部分已有 | 否 | D/E | P | P0~P2 |

---

## 8. 资源复用关系

### 8.1 VFX → 技能映射（85 主动技能全部被 31 模板覆盖）

```text
vfx_fire_small        ← 烈焰爪、火牙
vfx_fire_medium       ← 烈焰爆发、熔岩冲撞
vfx_fire_large        ← 炎灵、炼狱火雨
vfx_fire_ultimate     ← 陨星坠（蓄力）
vfx_thunder_small     ← 雷击、电光一闪
vfx_thunder_medium    ← 雷霆万钧
vfx_thunder_large     ← 雷暴、连锁闪电
vfx_water_small       ← 汐涌、水跃击、水鞭
vfx_water_medium      ← 潮涌、洪流
vfx_earth_small       ← 落岩
vfx_earth_large       ← 地震、岩爆
vfx_wind_small        ← 风刃
vfx_wind_medium       ← 疾风骤起、风切
vfx_light_small       ← 光耀
vfx_light_medium      ← 光爆、棱镜之光
vfx_metal_small       ← 金属爪、破甲击、钢刃
vfx_metal_medium      ← 刃暴
vfx_dark_small        ← 暗影球、暗袭、影分身（隐匿）
vfx_wood_small        ← 藤鞭、荆棘缠绕、枝条抽打、飞叶(敌方)、荆棘反刺(被动)
vfx_heal_small        ← 治愈之光、治愈孢子、净化之水、圣光、祝福
vfx_heal_aoe          ← 治愈铃音、绽放、群体治愈
vfx_shield            ← 岩盾、金御、石壁、Boss ADD_SHIELD 阶段
vfx_buff_up           ← 战吼、集气、铁壁、疾风步、健美、冒想、嘲讽、援护、反击、硬化(敌方)、熔铸、风行、引雷、群体守备、蓄能、顺风(被动)
vfx_debuff_down       ← 破甲击(破甲)、诅咒、沉默之雾(沉默)、雷波(麻痹)、暗幕、猎杀标记、禁疗诅咒、致盲/浸湿/禁疗附加
vfx_control_bind      ← 缠绕、气流禁锢
vfx_poison            ← 毒刺、剧毒
vfx_life_drain        ← 吸血之牙
vfx_dispel            ← 驱散之光、破盾击、净化
vfx_leave_one_hp      ← 留生一击（含震慑附加表现）
vfx_capture           ← 捕捉行动（三种球仅色彩差异，程序 tint）
vfx_boss_phase        ← 8 Boss 转阶段警示（护盾/Buff 复用上面模板）
```

被动技能 14 个（不屈/顺风/余烬/再生/厚皮 + 战意/铁躯/迅足/凝神/狂暴本能/复苏/猎获/荆棘反刺/士气昂扬）不配独立 VFX：不屈/厚皮/战意/铁躯/迅足/凝神/狂暴本能/士气昂扬为数值修正或状态附加，顺风/复苏/猎获/荆棘反刺/余烬分别复用 buff_up / heal_small / heal_small / wood_small / fire_small。

### 8.2 其他复用关系

```text
tileset_grassland_base ← start_village / meadow / forest（3 地图）
tileset_waters ← waters；tileset_thunder ← thunder；tileset_ruins ← ruins
pet_<id>_portrait      ← 图鉴/详情/战斗/获取弹窗/头像/缩略图/地图小像/野怪形象（1 源 ≥6 用途）
npc_<id>_portrait      ← 对话框立绘/对话头像/任务页
icon_element_<id>      ← 属性标签/技能图标角标/结晶 recolor/宠物卡描边（全局 9 个）
item_crystal_base      ← 9 属性结晶；item_boss_core_base ← 5 Boss 核心
item_skillbook_base    ← 13 技能书；icon_skilltype_* ← 99 技能图标底图
battle_bg_<biome>      ← 该区域战斗背景 + 区域缩略图（大地图）
prop_npc               ← 9 NPC 地图形象（程序色变）
```

---

## 9. Source Asset 与 Derived Asset

### 9.1 Source Asset（仅下列进入 AI 生成任务，共 161 个）

§7 矩阵中全部 `S` 标记项。**头像、缩略图、变体、组合图标一律不交 AI 重复生成。**

### 9.2 Derived Asset（自动后处理任务，脚本输出，共约 80 个输出文件）

| 派生组 | 派生规则 | 输出 |
|---|---|---|
| DER-01 宠物头像族 | portrait → 居中裁切（头部为主）→ 256/128/64 三档 | 27 组 |
| DER-02 地图野怪 | pet icon 64px + 行为徽章（程序绘制）+ 精英光环 SVG | 4 类行为 × 按遭遇组动态组合 |
| DER-03 NPC 头像/地图色变 | portrait 裁切 128；npc 模板 hue 色变 | 9 + 9 |
| DER-04 道具变体 | 底图 recolor/缩放/徽章组合（属性色取自 UI 文档官方色） | 33 |
| DER-05 区域缩略图 | battle_bg 中心裁切 256×256 | 6 |
| DER-06 宝箱开态 | chest 源图开盖变体（或 AI 同批 2 帧，QC 决定） | 1 |
| DER-07 技能图标组合 | 底图 + 属性色环/角标（前端合成或脚本预生成） | 99（按需） |
| DER-08 特殊外观 | 异色：hue-rotate 滤镜；辉光：Phaser glow/光圈叠加 | 程序运行时，不落文件 |

派生脚本建议放入 `frontend/scripts/`（与现有 `gen-placeholder-assets.mjs` 同级），在对应 Source 批次 QC 通过后执行。

### 9.3 NO_ASSET_REQUIRED（程序实现，约 20 项）

面板/卡片/弹窗背景、按钮、边框/分割线/Tab、血条/经验条、稀有度边框、属性标签底色、选中/悬停态、Loading、Toast、伤害数字、暴击反馈、受击闪烁（Phaser tween/CSS）、行动顺序条、换宠/加速过渡、精英 ✨ 徽章（SVG）、功能小图标集（关闭/继续/勾选/警告/锁/播放/停止/收藏/锁定/任务标记等约 12 个内联 SVG）、捕捉成功/失败抖动光效（CSS）、成就/统计/设置页装饰、教学浮层边框。

---

## 10. 资源命名与目录规范

命名统一：`<category>_<entity>_<purpose>[_<variant>]`，全小写 + 下划线，禁止 `final/new/test` 等命名。

在现有 `frontend/public/assets/`（maps/ + sprites/）基础上扩展，不搬迁已有文件：

```text
frontend/public/assets/
├── maps/                     # 已有 6 JSON + tileset.png（占位，将被替换）
│   ├── tilesets/             # tileset_grassland_base.png 等 4 套
│   └── backgrounds/          # battle_bg_meadow.png 等 6 + bg_home.png
├── sprites/                  # 已有目录：player/camp/chest/... 原地替换
├── pets/portraits/           # pet_PET_FIRE_001_portrait.png（27）
├── pets/icons/               # 派生：pet_PET_FIRE_001_icon_256/128/64.png
├── bosses/portraits/         # boss_BOSS_FOREST_KING_portrait.png（8）
├── npc/portraits/            # npc_NPC_VILLAGE_ELDER_portrait.png（9）
├── fx/{elemental,combat}/    # vfx_fire_small.png（或 sheet）等 31
├── ui/icons/{elements,status,skill,misc}/   # icon_element_fire.png 等 9+24+20
├── ui/backgrounds/           # logo 与空状态插图
├── items/icons/              # item_potion_base.png + 派生变体
└── events/                   # event_EVENT_INJURED_PET_cg.png（6）
```

QC 产物（contact sheet、预览地图、报告）放 `docs/art/`（不进入游戏打包路径，构建时排除）。接入层建议建立 `AssetRegistry` 统一映射（inventory §14），避免组件内散落 URL——接入代码工作不在本计划范围。

---

## 11. AI 生成方式

| 方式 | 定义 | 适用资源 |
|---|---|---|
| A — 单张 AI 生成 | 独立构图的大图 | 27 宠物、8 Boss、9 NPC、1 玩家、6 地图对象、4 Tileset、7 背景、Logo、6 事件 |
| B — AI 批量一致性生成 | 同风格基线批量产出 | 31 VFX、9 属性图标、24 状态图标、20 技能底图、7 道具底图 |
| C — AI + 程序处理 | AI 出底图，脚本出变体 | 道具 33 变体、宝箱开态、NPC 色变、技能图标组合 |
| D — 程序生成 | 代码直接产出 | 行为徽章、精英光环 SVG、功能小图标 SVG、派生裁切缩放 |
| E — NO_ASSET_REQUIRED | CSS/Canvas/引擎能力 | §9.3 全部约 20 项 |

生成能力约束（来自提示词文档）：透明背景直接生成；同类资源受控 Seed/Style Reference；不支持的能力记 `BLOCKED` 不伪造。

---

## 12. 批量生成批次规划

| 批次 | 内容 | 数量 | 依赖 | 通过条件 |
|---|---|---:|---|---|
| Batch 0 风格基线 | Art Bible（画风/比例/线条/光源/属性色/稀有度视觉差/地图透视/VFX 发光/UI 兼容）+ A0 锚点 3 宠（覆盖不同属性/体型/稀有度，每只 ≤2 候选） | 3+ | 无 | A0 全过 QC 才继续 |
| Batch 1 宠物立绘 | B01~B08 每批 3 只，按体型/属性/稀有度平衡分组，不按 ID 顺序 | 24 | Batch 0 | M1/M2/M3 阶段 QC + 27 宠 contact sheet 总检 |
| Batch 2 宠物派生 | 头像 256/128/64、地图小像、异色/辉光滤镜参数 | 派生 | Batch 1 | 小尺寸可辨识 |
| Batch 3 地图 | 4 Tileset（无缝 3×3 测试）→ Props/地图对象 7 → tileset-preview-map 拼接 QC | 11 | Batch 0 | 接缝/透视/光源/比例测试通过 |
| Batch 4 角色 | 8 Boss 立绘 → 9 NPC 立绘 → 玩家 Sprite + NPC 地图模板 | 19 | Batch 1 风格 | 与宠物风格一致；NPC 头像裁切可用 |
| Batch 5 战斗 FX | 先 P0（vfx_capture + vfx_leave_one_hp），再 Elemental 19，再功能类 10；统一画布/锚点/帧数/透明背景 | 31 | Batch 0；载体格式待确认（§17-4） | 无白/黑背景，loop 项首尾可接 |
| Batch 6 UI 图标 | 9 属性 → 24 状态 → 20 技能底图 → Logo；统一 128/256 规格与描边 | 54 | Batch 0 | 缩小至 24px 仍可辨 |
| Batch 7 道具/事件/背景 | 7 道具底图 + 33 派生变体 → 6 战斗背景 → 6 事件 CG → 首页背景 | 7+派生 | Batch 6（属性色） | 变体可区分；背景与 Tileset 同色系 |
| Batch 8 QA 与返工 | 全量风格漂移/尺寸/透明/裁切/重复/缺失检查；游戏内实机截图核对；只返工问题资源 | — | 全部 | §14 验收清单 |

---

## 13. 优先级与依赖关系

| 优先级 | 数量 | 内容 |
|---|---:|---|
| P0 核心视觉 | **57** | 27 宠物立绘、8 Boss 立绘、9 属性图标、4 Tileset、玩家+6 地图对象、vfx_capture、vfx_leave_one_hp |
| P1 完整体验 | **97** | 9 NPC、NPC 地图模板、24 状态图标、20 技能底图、7 道具底图、6 战斗背景、29 其余 VFX、Logo |
| P2 视觉增强 | **7** | 首页背景、6 事件插画（区域缩略图/空状态插图/出场特效按需，不预生成） |

依赖链：Batch 0 →（1→2）→（3、4 并行）→ 5 → 6 → 7 → 8。VFX（Batch 5）依赖 §17-4 载体格式裁决；区域缩略图依赖战斗背景（P1 完成后再派生）。57+97+7 = 161，与 §15 总数一致。

---

## 14. QA / 验收标准

**单资源级**（`game-asset-qc`）：

- 宠物：完整身体无裁切、轮廓辨识度、与 pets.yml description 匹配、透明背景、统一构图/光源、与 A0 锚点一致；
- Tileset：3×3 无缝（左右/上下/四角）、道路全套连接件、水体岸边/内外角、透视与光源一致、32px 对齐；
- VFX：帧尺寸一致、中心不漂移、透明背景、loop 首尾可接、发光强度统一；
- 图标：24px 缩小可辨、同族风格一致、属性色与 UI 文档官方色一致。

**批次级**：B01~B02 后 M1、B03~B05 后 M2、B06~B08 后 M3、27 宠完成后 `pets-contact-sheet.png` 总检；地图完成后 `tileset-preview-map` 拼接图。

**接入级**：每批 accepted 资源替换对应占位后，前端实机截图核对（地图 6 张、战斗页、图鉴、背包、商店、Boss 页）。

**禁止**：QC 失败重生成全部资源；未过 QC 覆盖正式资源；图内出现文字/水印/Logo。

---

## 15. 资源数量统计

```text
原始资源需求数（§4.1 点位）            约 500
        ↓ 去重（§5 八原则）
去重后逻辑资源数（独立最终资源）        161（AI Source）
        ↓
真正需要 AI 生成的 Source Asset         161
可程序派生资源数（Derived 输出文件）    约 80（头像族 27 组/道具变体 33/NPC 派生 18/缩略图 6/其他）
无需美术文件（CSS/SVG/引擎程序实现）    约 20 项
```

**按分类统计（Source 161）**：

| 分类 | 数量 | 明细 |
|---|---:|---|
| Pets（含 Boss） | 35 | 27 宠物 + 8 Boss |
| Maps | 17 | 4 Tileset + 6 地图对象 + 7 背景（战斗 6 + 首页 1） |
| FX | 31 | 19 属性档 + 12 功能类 |
| UI | 54 | 9 属性 + 24 状态 + 20 技能底图 + 1 Logo |
| Items | 7 | 7 底图（33 变体为派生） |
| NPC / 角色 | 11 | 9 NPC + 1 玩家 + 1 NPC 地图模板 |
| Other | 6 | 6 事件插画 |
| **合计** | **161** | |

**按优先级**：P0 = 57，P1 = 97，P2 = 7（合计 161）。

与 inventory 对比：inventory 建议制作约 120~140，本计划为 161，差异主要来自：状态图标按 24 实配全额计入（+）、技能图标从「约 24」精算为 20 底图（−）、道具从 30 降为 7 底图（−）、VFX 精算为 31（持平）、NPC 地图模板与事件/背景归类调整。**凡数字不确定处均已按配置实数核实，无编造。**

---

## 16. 已发现的需求冲突

| # | 冲突 | 处理 |
|---|---|---|
| 1 | **NPC 对话文本与 Boss 配置名称不一致**：`quests.yml` 中湖域守护者称「潮汐蟒」（配置为 潮灵蛇）、雷鸣贤者称「紫电龙」（配置为 雷翼鹰）、遗迹向导称「冥渊龙」（配置为 暗影巨像）。影响立绘生成提示词取材 | 资源一律以 `bosses.yml` 正式名称/形象为准；对话文本修正属内容问题，已上报待确认，不擅自改文案 |
| 2 | inventory 记道具 34 / 技能书 12，实际 `items.yml` 为 **35 / 13** | 以配置为准，本文档已修正 |
| 3 | UI 文档 §15.2 旧估算（技能图标 80~100、道具图标 40~60、状态图标约 10）与实际配置（技能 99、道具 35、状态 24）不符 | 以实际配置 + 语义去重结果为准（20 底图 / 7 底图 / 24 图标） |
| 4 | 需求 §110 成就奖励含「称号/头像/徽章」，但 `achievements.yml`（阶段 11 未验收）奖励仅 GOLD/ITEM | 不生成成就徽章/头像资源，记入待确认（§17-5） |
| 5 | 提示词文档要求按 body_type/palette/silhouette/combat_role 分批，但 `pets.yml` 无这些字段 | 按 description 文本 + element + rarity 人工推断分组（Batch 1 分组表在生成前输出），不改配置 |
| 6 | inventory 记商店 19 商品，阶段 10 说明为 25 商品（统计口径差异） | 不影响美术资源（商品图标全部复用道具图标），记录备查 |

---

## 17. 尚待确认的问题

| # | 问题 | 当前处理 |
|---|---|---|
| 1 | 新游戏「预设头像/形象」（需求 §123）是否需要 4~6 张玩家预设头像 | 暂按 0（CSS 色块）不入基线；确认后追加 P1 小批量 |
| 2 | 战斗特效载体格式：战斗页为 Vue DOM（无 Phaser 战斗场景），VFX 用 APNG / sprite sheet / Lottie / CSS 序列帧 | Batch 5 启动前裁决；不影响模板数量与命名 |
| 3 | 地图野怪形象用「宠物图标缩放」还是独立像素小 Sprite | 暂按图标派生（DER-02）；若验收认为辨识度不足再补 4 个通用 Sprite |
| 4 | 战斗背景 6 张是否可降为「1 通用 + 局部变体」 | 暂按 6 张（区域主题明确）；预算紧张时降级不影响功能 |
| 5 | 成就系统（阶段 11）验收后是否需要徽章/分类图标 | 待阶段 11 验收结论；需要时按 7 分类 SVG 小图标补充（程序绘制优先） |
| 6 | 宝箱两态是 1 源图派生还是 AI 直接出 2 帧 | Batch 3 执行时 QC 决定，不增加计划外数量 |
| 7 | 净化药当前为配置预留（待战斗内道具开放），图标是否随本轮生成 | 暂随本轮底图批次一并生成（成本极低），避免二次开工 |

---

## 18. 最终执行顺序

```text
① Batch 0：Art Bible + A0 锚点 3 宠 → QC 通过
② Batch 1：24 宠立绘（B01~B08，含 M1/M2/M3 + contact sheet 总检）
③ Batch 2：宠物派生脚本（头像/缩略图/地图小像/异色滤镜参数）
④ Batch 3：4 Tileset + 7 地图对象（无缝与拼接 QC）
⑤ Batch 4：8 Boss + 9 NPC + 玩家（角色风格对齐宠物）
⑥ Batch 5：31 VFX（先捕捉/留生 P0 两件；载体格式先裁决 §17-2）
⑦ Batch 6：UI 图标 54（属性 → 状态 → 技能 → Logo）
⑧ Batch 7：道具底图 7 + 变体 33 + 背景 7 + 事件 6
⑨ Batch 8：全量 QA、实机核对、问题资源定点返工
⑩ 每批 accepted 后：派生脚本输出 → 替换占位 → 更新 inventory 状态 → （全部完成后）同步 README/AGENTS 进度
```

**推荐首先执行**：Batch 0（风格基线 + A0 锚点）。它是全部后续批次的硬前置；A0 建议选取：烬牙兽（火/普通/四足物理型）、汐月灵（水/稀有/元素灵体）、岩魁（土/普通/重型巨灵）——覆盖属性、体型、稀有度差异。

---

## 附：验收自查（对应任务书第十七节）

- [x] 已读取三个核心资料（需求文档 V1.0 / AI 美术提示词 / art-resource-inventory）
- [x] 已根据最新需求与实际配置判断资源，而非单独依赖 inventory
- [x] 所有占位资源均完成核对（15 个 Phaser 占位 + emoji 清单，§3/§7）
- [x] 27 只宠物均被覆盖（PET-001~027 逐条列出）
- [x] 地图资源已组件化去重（6 地图 → 4 Tileset + 通用 Props）
- [x] 技能特效已按视觉语义去重（85 主动技能 → 31 模板，§8.1 全映射）
- [x] 状态效果已按状态语义去重（24 状态 → 24 图标 + Buff/Debuff 模板族）
- [x] UI 资源已组件复用（无整页 UI 图；面板/按钮 CSS；图标按族设计）
- [x] Source / Derived 已分离（§9）
- [x] 未把可自动裁切的头像列入 AI 生成（头像/缩略图/对话头像全部派生）
- [x] 每个最终资源均可追溯到使用场景与来源文档（§7 矩阵）
- [x] 每个资源有唯一 Asset ID（PET/BOSS/CHR/NPC/MAP/BG/FX/UI/ITM/EVT/DER/PRG）
- [x] 每个资源指定生成方式（A/B/C/D/E）与优先级（P0/P1/P2）
- [x] 已建立复用关系（§8）
- [x] 已给出批量生成顺序（§12/§18）与数量统计（§15）
- [x] 未因「可能以后有用」增加资源（进化/成就徽章/出场特效等均未入基线）
- [x] 已完成 Batch 0～7 的资源生成、派生与页面接入，并完成 Batch 8 全量静态 QA
