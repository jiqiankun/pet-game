# 《宠物精灵》整体页面布局与 Overlay 交互架构改造实施方案

> **历史方案说明（2026-08-19）：** 当前桌面重构已由新版详细任务规划接管；本文中的手机/平板、Bottom Sheet 与响应式验收均不在本轮范围，已实现程度必须以源码和 `PHASE0_BASELINE.md` 为准。

**适用项目：** 宠物精灵游戏第一阶段
**依据：**
- 《宠物精灵游戏第一阶段需求设计文档 V1.0》
- 《宠物精灵游戏第一阶段技术方案说明 V1.0》
- 《宠物精灵游戏第一阶段UI设计文档 V1.0》
- 《宠物精灵游戏分阶段开发规划 V1.0》
- `docs/architecture/ARCHITECTURE.md`、`docs/architecture/PROJECT_STRUCTURE.md`
- `docs/development/FRONTEND_STANDARDS.md`
- `docs/planning/游戏UI导航与Overlay架构方案.md`（既有 Overlay 架构方案，P0 已落地）
- `AGENTS.md`（项目级规范与固定规则）

**目标阶段：** 第一阶段收尾工程化改进（非新增阶段）
**状态：** 待审核（本文件为一次性实施方案，审核通过后按 P0→P1→P2→P3 顺序执行）

---

## 0. 文档定位与目标

> 将游戏从传统 Web「页面跳转式交互」统一改造为「**地图常驻 + HUD + 功能浮层 + 战斗浮层**」的真实游戏客户端式交互体系。

核心体验目标：

- **探索连续**：打开/关闭功能不产生「离开世界」的感觉。
- **操作快速**：常见操作控制在 1~3 次有效操作内。
- **上下文稳定**：任何浮层关闭后回到原位置、镜头与游戏状态。
- **层级清晰**：玩家永远知道当前在哪一层。
- **返回统一**：返回 = 只关闭最上层浮层。
- **手机友好**：不因屏幕小而变成大量页面跳转。
- **PC 高效**：充分利用 Hover / Tooltip / 快捷键 / 侧边面板。
- **开发可维护**：所有功能遵守统一 Overlay 规则，而非每系统一套联动。

> 本文档结论与既有的 `docs/planning/游戏UI导航与Overlay架构方案.md` 一致，并在此基础上结合**当前真实代码已落地的 P0**，给出更细化的完整改造实施方案。**不改变任何游戏规则、数值、表结构。**

---

## 1. 当前页面架构分析（现状）

### 1.1 技术栈

- Vue 3 + TypeScript（严格） + Vite + Pinia + Vue Router（Hash）+ Phaser 4.2.1。
- 无第三方 UI 库；全局 CSS 变量（`src/assets/main.css`）+ 各组件 `scoped` 样式。
- 响应式断点：`768px`（平板）、`480px`（手机），PC 优先。
- 入口：`main.ts` → `App.vue` → `MainLayout`（顶栏导航 + `<RouterView/>` + 全局 `DialogueBox` + `TutorialOverlay`）。
- 枚举中文映射统一在 `src/utils/labels.ts`。

### 1.2 当前路由列表（`src/router/index.ts`，18 个顶级路由）

| 路由 | 页面 | 当前联动方式 | 浮层化现状 |
|---|---|---|---|
| `/` | HomeView | 路由 | 保留（大厅/存档门面） |
| `/new-game` | NewGameView | 路由 | 保留（开新档） |
| `/explore` | ExploreView（Phaser 地图） | 路由 | **主场景（常驻）** |
| `/world-map` | WorldMapView | 路由 | 待改 Overlay |
| `/battle` | BattleView | 路由 | 已有 BattleOverlay（浮层模式），独立路由保留作测试 |
| `/pets` | PetView | 路由 | **已浮层化**（FeatureOverlay） |
| `/team` | TeamView | 路由 | 待改 Overlay |
| `/storage` | StorageView | 路由 | 待改 Overlay |
| `/pokedex` | PokedexView | 路由 | **已浮层化**（FeatureOverlay） |
| `/boss` | BossView | 路由 | 待改 Overlay（战内复用 BattleOverlay 已实现） |
| `/inventory` | InventoryView | 路由 | **已浮层化**（FeatureOverlay） |
| `/shop` | ShopView | 路由 | 待改 Overlay |
| `/quest` | QuestView | 路由 | **已浮层化**（FeatureOverlay） |
| `/achievement` | AchievementView | 路由 | 待改 Overlay |
| `/statistics` | StatisticsView | 路由 | 待改 Overlay |
| `/settings` | SettingsView | 路由 | **已浮层化**（FeatureOverlay） |
| `/save-backup` | SaveBackupView | 路由 | 待改 Overlay（低频系统功能） |
| `/dev-tools` | DevToolsView | 路由 | 保留（非游戏内交互） |

### 1.3 地图探索实现（阶段 6 起，已在工作）

- **ExploreView**：`onMounted` 加载 bootstrap + 当前地图 → `createPhaserGame()`；`onBeforeUnmount` → `game.destroy(true)`。
- **Phaser 场景**：`BootScene`（资源加载）→ `MapScene`（地图渲染/玩家移动/野怪 AI/对象交互上报）。
- **桥接**：`GameBridge` 单例；Phaser→Vue 业务事件（`encounter:touch`/`gather:request`/`chest:request`/`camp:touch`/`exit:touch`/`boss:touch`/`npc:touch`/`hidden:touch`），Vue→Phaser `cmd:*`（`cmd:restart-map`/`cmd:set-input-lock`/`cmd:set-exploration-paused`/`cmd:remove-wild`/`cmd:remove-object`）。
- **地图状态**：`useMapStore` 保存 `currentMap`(MapEnterView)、`defeatedWildIds`、`activeEncounterSpawnId`、`worldMap`。**玩家坐标/方向/相机保存在 MapScene 本地，不入 store**。

### 1.4 哪些已采用 Overlay / Modal / Drawer / BottomSheet

- **已实现浮层体系（P0 落地）**：
  - `stores/overlay.ts`（`useOverlayStore`）：`BATTLE` + `FEATURE` 两类栈，打开下发 `cmd:set-exploration-paused`。
  - `BattleOverlay.vue`：全屏战斗浮层，地图遇敌 / Boss 战复用。
  - `FeatureOverlay.vue`：通用功能浮层壳（遮罩+标题栏+关闭+滚动内容），slot 承载 `InventoryView/PetView/PokedexView/QuestView/SettingsView`（懒加载）。
  - `MapScene.explorationPaused`：暂停玩家移动/野怪 AI/接触检测，地图实例保留。
- **全局浮层组件**：`DialogueBox`（NPC 对话，挂 `MainLayout`）、`TutorialOverlay`（新手教学，挂 `MainLayout`）。
- **页面内 Modal**：ExploreView 内部（遭遇确认/营地/出口/奖励/随机事件）；TeamView 详情浮层；PetView 技能浮层/升级确认；PokedexView 详情面板；StorageView 放生预览；BossView 自动结果。

### 1.5 地图切换 / 状态保留现状

- **切换是否销毁**：路由跳转离开 `/explore` 会 `game.destroy(true)` → 地图销毁；重新进入需重新初始化。
- **玩家位置**：保存在 MapScene 本地，路由跳走即丢。
- **镜头**：MapScene 未显式设置相机，默认跟随画布；无 scroll 时等价于不重置，但切页即重建。
- **NPC/野怪/宝箱/采集状态**：主要由后端会话（`MapEnterView.consumedChestIds/usedGatherIds/activatedCampIds`）+ 前端 `defeatedWildIds` 支撑；野怪/玩家表现态在 MapScene 本地。
- **战斗进出**：遇敌确认 → `battleStore.startMapEncounter` → 打开 `BattleOverlay`（已浮层化，地图保留）；Boss 战从 `BossView` 打开 `BattleOverlay`。

### 1.6 队伍 / 宠物 / 背包 / 仓库 当前结构

- **TeamView**（`/team`）：5 套预设标签 + 拖拽排序（HTML5 DnD，非唯一方式）+ 首发/候补下拉 + 详情浮层 + 战斗守卫（`battleStore.inBattle` 禁用）。独立路由，无统一浮层壳。
- **PetView**（`/pets`）：三/四标签（基础/属性/技能/推荐方案）+ 升级/加点/洗点/技能书/被动技能书槽位 + 详情报价。**已作为 FeatureOverlay 内容复用**。
- **InventoryView**（`/inventory`）：分类 + 恢复道具「选道具→选宠物→使用」流程。**已作为 FeatureOverlay 内容复用**。
- **StorageView**（`/storage`）：筛选/排序 + 批量 + 放生预览。独立路由，未浮层化。
- **ShopView**（`/shop`）：金币栏 + 商品卡片 + 数量加减 + 购买。独立路由，未浮层化，**当前无 NPC→商店入口**。

### 1.7 响应式实现

- 全局 `@media (max-width: 768px)`，多数页面有 `@media` 适配；移动端底部导航（`MainLayout` 顶栏在 768px 转底部）。
- 各页面 `@media (max-width: 768px)` 自行适配（网格 1 列、面板全屏等）。

### 1.8 统一 UI 状态管理

- 已存在 `useOverlayStore`（BATTLE/FEATURE 栈）。
- 各业务 UI 状态（对话框、选中项、Tab、筛选）分散在组件内或各自 store（如 `pokedex.filterLevel`）。

### 1.9 重复/可复用组件

- 重复 Modal 样式在多个页面内各自实现（`modal-mask/modal-card`、`detail-overlay/detail-panel`、`.btn-primary/.btn-secondary` 语义重复）。
- **可直接复用**：`FeatureOverlay`（浮层壳）、`BattleOverlay`、`DialogueBox`、`TutorialOverlay`、`labels.ts`、`game-assets.ts`（`petIconUrl` 等）、现有业务 View 组件（作为浮层内容复用）。

---

## 2. 当前页面联动存在的问题

1. **核心菜单仍走路由**：`/team`、`/storage`、`/world-map`、`/boss`、`/shop`、`/achievement`、`/statistics`、`/save-backup` 仍是独立路由，打开即离开地图。
2. **遭遇确认里「调整首发」跳 `/team`**（ExploreView L413）：`RouterLink to="/team"` 会卸载地图，返回后从出生点重生。
3. **Boss 从地图入口 `router.push('/boss?bossId=')`**：离开地图，Boss 战结束后不自动回地图。
4. **顶栏导航罗列全部路由**（MainLayout）：PC 顶部一排、移动端底部一排工具按钮，遮挡地图、不符合「地图即世界」。
5. **无统一返回键策略**：ESC / Android Back / UI 返回各自为战；多浮层返回语义不统一。
6. **无统一 PauseLevel**：目前仅 `cmd:set-input-lock`（锁输入）与 `cmd:set-exploration-paused`（全停）两档，缺少「暂停探索但环境动画延续」的分级。
7. **播放器坐标/镜头/会话表现态不入 store**：切页即丢（浮层化后不再切页可缓解，但为稳妥应入 `useMapStore`）。
8. **浮层类型贫乏**：`useOverlayStore` 只有 `BATTLE/FEATURE` 两类，`FEATURE` 用字符串 `feature` 区分，无法表达「NPC→商店」嵌套、QuickTeam、PetDetail 二级等；栈去重逻辑（同类型先删再加）限制了部分嵌套场景。
9. **浮层视觉形态单一**：`FeatureOverlay` 只有居中大面板 + 移动端全屏，缺少 BottomSheet / 右侧 Drawer / 近全屏等形态。
10. **无全局 Toast / 反馈层统一组件**：ExploreView 内自带 toast，各页面各自处理，风格不一。
11. **无 Overlay 上下文记忆**：关闭浮层后 Tab/筛选/滚动位置不保留（部分 store 保留 filter，但组件内状态不保留）。
12. **无快捷操作**：缺 QuickTeam、快速恢复、快捷菜单、键盘快捷键。
13. **NPC 对话无「对话→商店」上下文**：NPC 只能纯对话，商店需要单独进 `/shop`。

---

## 3. 页面分类结果（复用既有方案 §3）

判断标准：**完成该操作后玩家是否仍应继续当前位置的探索？**

- **是 → Overlay / Drawer / BottomSheet / Modal。**
- **否（位置变化或非游戏内交互）→ Router Page。**

| 页面 | 分类 |
|---|---|
| `/explore` | **Scene（主场景，常驻）** |
| `/world-map` | **WorldMapOverlay**（查看不动地图；仅传送/进入区域时切换） |
| `/battle` | **BattleOverlay**（已实现） |
| `/pets` / `/team` / `/inventory` / `/storage` / `/pokedex` / `/quest` / `/shop` / `/boss` / `/achievement` / `/statistics` / `/settings` / `/save-backup` | **对应 Overlay** |
| `/new-game`、`/dev-tools` | 保留 Route |
| `/`（首页/大厅/存档门面） | 保留 Route |

> **兼容策略**：不删除现有路由。为兼容刷新/bookmark/独立测试，各 Overlay 页面保留「独立打开模式」（直接访问 `/team` 等仍以独立页展示），与 Overlay 模式共用同一业务组件（见 §25）。

---

## 4. 可复用组件清单

| 组件/文件 | 用途 | 动作 |
|---|---|---|
| `stores/overlay.ts` | Overlay 栈 | **扩展**（类型/策略/嵌套） |
| `FeatureOverlay.vue` | 功能浮层壳 | **复用 + 增强**（形态/Back/动画） |
| `BattleOverlay.vue` | 战斗浮层 | **复用**（保持不变） |
| `DialogueBox.vue` | NPC 对话 | **复用**，接入 Overlay 栈语义 |
| `TutorialOverlay.vue` | 新手教学 | 复用 |
| `labels.ts` / `game-assets.ts` | 枚举映射 / 资源 URL | 复用 |
| `InventoryView/PetView/PokedexView/QuestView/SettingsView` | 业务内容 | **复用**为浮层内容（已做） |
| `TeamView/StorageView/WorldMapView/BossView/ShopView/AchievementView/StatisticsView/SaveBackupView` | 业务内容 | **包装为浮层内容**（去路由依赖） |
| `useMapStore` | 地图状态 | **扩展**（坐标/镜头/表现态） |
| `MapScene` | 地图表现 | **扩展**（PauseLevel、交互提示按钮所需数据上报） |

---

## 5. 需要淘汰或迁移的实现

- **淘汰**：ExploreView 遭遇确认框里的 `RouterLink to="/team"`（改为打开 TeamOverlay）。
- **迁移**：`boss:touch` 从 `router.push('/boss')` 改为打开 `BossOverlay`。
- **迁移**：`openWorldMap()` 从 `router.push('/world-map')` 改为打开 `WorldMapOverlay`。
- **改造**：`MainLayout` 顶栏导航精简为「首页 + 进入探索」入口，其余移入地图 HUD 游戏菜单。
- **收敛**：`FeatureOverlay` 演进为通用 Overlay 壳，替换各页面内自定义 `modal-mask/modal-card` 重复样式。
- **保留**：`/battle` 独立路由作为战斗测试页（游戏内不经此路由）。

---

## 6. GameShell 方案

在现有 `App.vue → MainLayout` 基础上演进，不另起名而是**把 `MainLayout` 提升为 GameShell 职责**，承载：

```text
MainLayout（GameShell 职责）
│
├── RouterView（非探索上下文页面：/、/new-game、/dev-tools、独立模式页面）
│
├── PersistentGameScene（/explore 常驻，见 §7）
│   ├── MapScene（Phaser）
│   ├── ExplorationHUD（§12）
│   └── ContextInteractionLayer（情境交互按钮，§13）
│
├── OverlayLayer（§8 OverlayManager 渲染全部浮层）
│
├── BattleOverlay（最高层，§20）
│
└── GlobalFeedbackLayer（Toast/Reward/Achievement/Tutorial/Error，§14）
```

要点：
- **不把 Phaser 提升到 App 级**（破坏现有 `/explore` 生命周期与存续逻辑），而是让 `/explore` 成为唯一常驻主场景；其它页面（`/、/new-game、/dev-tools`）作为进出游戏的「世界之外」页面。
- Overlay 渲染统一由 `OverlayLayer` 组件根据 `useOverlayStore` 栈渲染，业务内容组件复用各 View。

---

## 7. MapScene 常驻方案

- `/explore` 是唯一主场景，正常游戏过程中不卸载。
- 打开 Overlay 仅暂停（`explorationPaused`），**不销毁 Phaser Game**。
- 会话级探索表现态（玩家坐标/方向/相机）纳入 `useMapStore`（见 §23），Overlay 关闭后恢复。
- **区域切换（传送/出口/营地刷新）**：仍需 `cmd:restart-map` 重建地图实例（属于真实场景切换），但投标不经过 Overlay 层。

---

## 8. OverlayManager 设计（扩展 `useOverlayStore`）

### 8.1 类型

```ts
type OverlayType =
  | 'BATTLE'                              // 战斗（最高层，全屏）
  | 'REWARD'                              // 奖励/结算
  | 'QUICK_TEAM'                          // 快捷队伍（BottomSheet）
  | 'TEAM'
  | 'PET'                                 // 宠物详情（PetDetailOverlay）
  | 'INVENTORY'
  | 'WAREHOUSE'                           // 道具仓库
  | 'PET_STORAGE'                         // 宠物仓库
  | 'POKEDEX'
  | 'QUEST'
  | 'WORLD_MAP'
  | 'NPC_DIALOG'
  | 'SHOP'
  | 'SETTINGS'
  | 'ACHIEVEMENT'
  | 'STATISTICS'
  | 'SAVE_BACKUP';

interface OverlayEntry {
  type: OverlayType
  id: number
  pauseLevel: GamePauseLevel          // §10
  data?: unknown                       // 打开参数（如 { petId }、{ npcId }）
  // 由 manager 维护，业务组件不直接操作
}
```

### 8.2 对外 API（保持既有命名风格，扩展）

```ts
open(type, opts?: { data?, pauseLevel? })   // push 到栈顶，按 ratio 下发暂停命令
closeTop()                                   // 关闭最上层（返回键）
close(type)                                  // 关闭指定类型的最上层
closeAll()
isOpen(type)
top
stack
handleBack()                                 // 统一返回（§22）
```

### 8.3 栈规则

```text
Scene
  └─ PrimaryOverlay（TEAM/PET/INVENTORY/POKEDEX/QUEST/SHOP/BOSS... 同一时刻通常 1 个）
       └─ SecondaryOverlay（PetDetail、NPC→Shop 等，模块内或栈内二级）
            └─ SystemDialog（确认框/消息框）
                 └─ Toast
```

- **返回永远只关闭最上层**。
- REWARD/Achievement/Tutorial 等系统级反馈可叠加在任意层之上。
- BATTLE 属独立最高层，可从任意 Overlay 之上打开（如 Boss 面板 → 战斗），关闭后回到原层。

---

## 9. Overlay 层级深度控制

- 允许 **地图 → 一级 Overlay → 最多一个必要的二级 Overlay**。
- 超过两级优先改用模块内状态切换 / Tab / Detail Panel / Accordion，不继续压栈。
- 例：`对 TEAM → PetDetail`（二级）；`NPC_DIALOG → SHOP`（二级）；`PET` 内部用 Tab 承载「基础/属性/技能/被动/培养」，不逐级开新 Overlay。

---

## 10. GamePauseLevel（地图暂停分级）

```ts
type GamePauseLevel = 0 | 1 | 2 | 3;
```

| Level | 名称 | 适用 | 玩家移动 | 野怪 AI/遇敌 | 环境动画/粒子/BGM |
|---|---|---|---|---|---|
| 0 | 不暂停 | Toast、获得道具、任务更新、小提示 | 继续 | 继续 | 继续 |
| 1 | 锁玩家输入 | NPC 对话、宝箱选择、简单互动 | 暂停 | 继续 | 继续 |
| 2 | 暂停探索逻辑 | 队伍/宠物/背包/仓库/图鉴/任务/商店/设置 | 暂停 | 暂停 | 继续（世界保留生命感） |
| 3 | 战斗锁定 | BattleOverlay | 暂停 | 暂停 | 战斗表现 |

实现：`MapScene` 增加 `pauseLevel` 字段，由 `cmd:set-pause-level` 下发；`update()` 按分级决定是否执行 `movePlayer` / `updateWilds` / `checkContacts`。现有 `cmd:set-exploration-paused` 可保留为 Level 2/3 的便捷等价命令，或直接迁移为 `cmd:set-pause-level`。

---

## 11. ExplorationHUD（探索 HUD）

地图上层新增常驻 HUD，只放高价值信息：

```text
┌──────────────────────────────────────────┐
│ 当前区域                  [小地图] [⚙]      │
│                                          │
│           游戏地图（Phaser）               │
│                                          │
│ 当前追踪任务：调查森林深处                   │
│ 首发 🔥烈焰狐 Lv.27  HP ███████░           │
│ 队伍 ● ● ●          [快捷菜单] [交互]      │
└──────────────────────────────────────────┘
```

- **当前区域**：`mapStore.currentMap?.name`。
- **首发宠物 + HP**：由 `gameStore.teamMembers`（或后端提供 HUD 摘要接口）计算。
- **队伍整体状态**：正常 `● ● ●`；有濒危时转 `⚠ 队伍中有宠物濒危`（动态提醒，见 §14）。
- **当前追踪任务**：`questStore.getActiveSummary()`，只显示一条。
- **小地图**：轻量（可后续接入，第一阶段可先做「区域名 + 返回大地图按钮」）。
- **快捷菜单 / 交互按钮**：见 §13。

避免把背包/图鉴/仓库/宠物/设置/任务/商店全部做成长期固定按钮；手机端尤其精简。

---

## 12. 统一快捷菜单（游戏菜单）

- 手机/平板：HUD 上一个「游戏菜单」按钮，点击展开：

```text
队伍   宠物
背包   图鉴
任务   地图
仓库   设置
```

- PC：键盘快捷键（T/B/I/M/Q/P/G/S）触发同一套 Overlay 系统。
- 底层统一调用 `overlayStore.open(...)`。

---

## 13. 情境交互层（ContextInteractionLayer）

- 根据玩家附近对象动态显示动作按钮：「对话」「打开」「采集」「观察」。
- 数据来源：`MapScene` 在 `update()` 中上报附近对象到 `useMapStore`（新增 `nearbyObject` 状态），或通过 bridge `cmd:*` 由 Vue 侧维护。
- 点击按钮通过既有 bridge 事件回传（复用 `camp:touch`/`gather:request`/`chest:request`/`npc:touch` 等）。
- 不把每个动作都做成长期 HUD 按钮。

---

## 14. GlobalFeedbackLayer（统一反馈层）

- 新增全局 `Toast`（统一 `showToast`，可入 `useAppStore` 或独立 `ui` store）。
- 新增 `RewardPopup`（奖励/结算展示，替换 ExploreView 内 rewardPopup）。
- `Achievement` / `TutorialOverlay` 复用/接入。
- `ErrorFeedback`（统一错误提示条）。
- 信息按重要程度动态出现，平时保持简洁。

---

## 15. QuickTeamOverlay（快捷队伍）

- 点击 HUD 队伍状态展开 BottomSheet。
- 展示每只宠物：名称 / HP / 异常状态。
- 高频操作：查看 HP、查看异常、快速恢复（§16）、换首发、进入完整 TeamOverlay。
- 不进入大型 TeamOverlay 即可完成日常查看/恢复。

---

## 16. 快速使用道具

- 在 QuickTeam 选择受伤宠物 → 「快速恢复」。
- 系统从背包推荐合适恢复道具（如 `<100HP → 恢复药`、`100~200 → 高级恢复药`），展示推荐项 → 选择 → 即时使用 → HP 变化反馈。
- 整个过程不离开 QuickTeamOverlay。

---

## 17. 队伍 / 宠物联动

```text
Map → TEAM Overlay → PetDetail Overlay → 返回 TEAM → 关闭 → 原地图
```

- **TeamOverlay**：PC 右侧 Drawer / Side Panel；手机 BottomSheet（可拉至近全屏）。功能：当前队伍、调整顺序（拖拽+点击）、设置首发、查看 HP/异常、快速治疗、点击进宠物详情。
- **PetOverlay（PetDetail）**：Large Overlay（近全屏），内部 Tab：基础/属性/技能/被动/培养/其它。复用现有 `PetView` 内容。
- 关闭 PetDetail 返回 Team，再关闭返回地图。

---

## 18. 背包 / 仓库联动

- **InventoryOverlay**：分类（恢复/捕捉/战斗道具/技能书/素材/关键）。
- 使用流程：选恢复药 → 选宠物 → 使用 → 即时 HP 变化，全程不离开浮层。复用 `InventoryView`。
- **WarehouseOverlay（道具仓库）**：Large Overlay；PC 左分类+中列表+右详情；手机全屏。支持分类/搜索/数量/排序/稀有度/最近获得/批量。
- **PetStorageOverlay（宠物仓库）**：与队伍联动（选宠物→加入队伍→选替换位置→完成）；筛选（属性/等级/稀有度/名称/最近获得/是否在队伍）；PC 可拖拽但保留点击。

---

## 19. 图鉴 / 任务 / 大地图联动

- **CodexOverlay**：复用 `PokedexView`；详情展示是否发现/捕获/属性/已知技能/弱点/抗性/栖息地。
- 「查看栖息地」→ 打开 `WorldMapOverlay` 并高亮目标区域。
- **QuestOverlay**：复用 `QuestView`；支持「追踪任务」与「地图查看」。「地图查看」→ `WorldMapOverlay` 自动定位目标；返回保持任务上下文。
- **WorldMapOverlay**：复用 `WorldMapView`；查看区域/已探索地点/任务目标/宠物栖息地/传送点；**仅点传送/进入区域才切换地图状态**，普通查看不破坏 MapScene。

---

## 20. NPC 对话 / 商店联动

- **NPCDialogOverlay**：复用 `DialogueBox`，地图作为背景，对话暂停玩家输入（PauseLevel 1）。
- **NPC → 商店**：对话中触发商店时打开 `SHOP Overlay`（二级）；关闭商店返回对话；再关闭返回地图。不要 `NPC → /shop → /map`。
- 对话数据结构需扩展触发动作（如「打开商店」「接受任务」），由后端 `DialogueView` 或前端按 NPC 类型映射。

---

## 21. BattleOverlay（战斗最高层）

- 保持现有 `BattleOverlay` 全屏浮层。
- 流程：MapScene → 触发战斗 → 暂停地图（PauseLevel 3）→ BattleOverlay → 战斗 → ResultOverlay（结算）→ 关闭 BattleOverlay → 原地图继续。
- 战斗中**禁止**打开普通仓库/宠物管理/图鉴/普通背包/任务；仅允许 `BattleSkillPanel / BattlePetPanel / BattleBagPanel / BattleStatusPanel / BattleSettingsPanel`（战斗内面板）。
- 战斗结束恢复地图，不重生、不重置镜头（现有已完成）。

---

## 22. 统一返回行为

- 优先级：
  ```text
  存在 SystemDialog → 关闭 Dialog
  否则存在二级 Overlay → 返回上一层
  否则存在一级 Overlay → 关闭 Overlay
  否则处于探索 → 打开暂停菜单（或关闭游戏菜单）
  否则（首页/独立页）→ 默认浏览器行为
  ```
- 实现：`overlayStore.handleBack()`；监听 `keydown`（ESC）与浏览器 `popstate`（Hash Back）。
- 移动端 BottomSheet 支持下滑关闭，但关闭结果与其它入口一致。
- **战斗保护**：战斗中 Back 不退出战斗。

---

## 23. 状态保存（上下文记忆）

- **地图上下文（最高优先级，必须稳定保存）**：玩家位置、方向、镜头、当前地图、NPC 状态、野怪状态、采集状态。纳入 `useMapStore`（`playerPosition/playerDirection/cameraPosition` 等会话级表现态），浮层关闭后恢复。
- **Overlay 上下文（适度记忆）**：当前 Tab、排序、筛选、滚动位置。在 Open 时快照、Close 时释放，或存入各 store 的 UI 字段（如 `pokedex.filterLevel` 已保留）。
- 纯 UI 状态不入存档；Game State 由后端持有一份。

---

## 24. PC / 平板 / 手机方案

| Overlay | PC | 手机/平板 |
|---|---|---|
| QuickTeam | 小面板 | BottomSheet |
| Team | 右侧 Drawer | BottomSheet / 近全屏 |
| Inventory | 中央面板 | BottomSheet / 近全屏 |
| Pet | Large Overlay | 全屏 |
| Warehouse/PetStorage/Codex/Quest/WorldMap | 中央/右侧面板 | 全屏 |
| NPCDialog | 底部对话框 | 底部对话框 |
| Battle | 全屏 | 全屏 |

- 同一套业务组件，通过 CSS + 响应式类切换形态（PC 面板 / 移动 BottomSheet / 全屏）。
- 核心按钮位于拇指易触区域。

---

## 25. 需要修改的文件（范围清单）

**新增：**
- `stores/overlay-types.ts`（或并入 overlay store）：OverlayType / GamePauseLevel 常量。
- `components/overlay/OverlayLayer.vue`：统一浮层渲染层。
- `components/overlay/OverlayShell.vue`：通用浮层壳（替代/增强 FeatureOverlay，支持 Panel/BottomSheet/Drawer/FullScreen 形态）。
- `components/feedback/GlobalToast.vue`、`RewardPopup.vue`、`ErrorFeedback.vue`。
- `components/hud/ExplorationHUD.vue`、`GameMenu.vue`、`ContextInteractionPanel.vue`。
- `components/overlay/QuickTeamOverlay.vue`。
- `composables/useKeyboardShortcuts.ts`（可选）。

**修改（关键）：**
- `stores/overlay.ts`：扩展 OverlayType/二级/返回/层级/形态。
- `stores/map.ts`：增加 `playerPosition/playerDirection/cameraPosition/bookkeeping`、`nearbyObject`。
- `game/scenes/MapScene.ts`：增加 `pauseLevel` 与 `cmd:set-pause-level`；按需上报附近对象；玩家坐标写回 store。
- `game/bridge/GameBridge.ts`：新增/调整命令类型。
- `views/Explore/ExploreView.vue`：接入 HUD/快捷菜单/OverlayLayer；移除「调整首发」路由跳转、`openWorldMap` 路由跳转；改造 `boss:touch` 为打开 BossOverlay。
- `views/Explore/components/FeatureOverlay.vue`：演进为通用 OverlayShell 或迁移到 `components/overlay/`。
- `layouts/MainLayout.vue`：精简顶栏导航；挂载 OverlayLayer/GlobalFeedback/Reward。
- 各业务 View（Team/Storage/WorldMap/Boss/Shop/Achievement/Statistics/SaveBackup）：去路由依赖，改为可作为浮层内容嵌入（通过 props 或 store 数据注入，不强制 `useRoute`/`useRouter`）。
- `router/index.ts`：保留独立路由（独立模式），不删除。
- `views/Quest/components/DialogueBox.vue`：支持对话内触发商店/任务动作。

**后端（如需要）：**
- 若 HUD 需要首发 HP/队伍状态接口，可新增轻量 `GET /api/game/hud`（可选，不强制）。
- NPC 对话触发商店：如 `DialogueView` 需携带动作类型与 shopId，由后端扩展（若现有无该字段）。

---

## 26. 开发任务拆分

### P0 — 基础架构（对标需求 §41 P0）
1. 扩展 `stores/overlay.ts`（OverlayType 全集、二级、`handleBack`、pauseLevel）。
2. `MapScene` 增加 `pauseLevel` 与 `cmd:set-pause-level`；玩家坐标/镜头写回 `useMapStore`。
3. 新增 `OverlayLayer` + 通用 `OverlayShell`（Panel/BottomSheet/Drawer/FullScreen），替换 `FeatureOverlay` 的使用。
4. 统一返回：ESC / Hash Back / Android Back；战斗保护。
5. 新增 `GlobalToast` / `RewardPopup` / `ErrorFeedback`，接入 ExploreView。

### P1 — 高频功能（需求 §41 P1）
6. `ExplorationHUD` + `GameMenu` + `ContextInteractionPanel`。
7. `QuickTeamOverlay` + 快速恢复。
8. `TeamOverlay`（包装 TeamView）+ `PetDetailOverlay`（包装 PetView）。
9. `InventoryOverlay`（已有 Feature）+ 背包内使用优化。
10. NPC 对话接入 Overlay 栈；`NPCDialog → Shop` 二级联动。

### P2 — 管理功能（需求 §41 P2）
11. `PetStorageOverlay`（包装 StorageView 宠物部分）。
12. `WarehouseOverlay`（道具仓库）。
13. `CodexOverlay`（已有 Feature）+ 栖息地 → WorldMapOverlay 高亮。
14. `QuestOverlay`（已有 Feature）+ 地图查看定位。
15. `WorldMapOverlay`（包装 WorldMapView）。
16. `BossOverlay`（包装 BossView，进入即开战复用 BattleOverlay）。
17. `SettingsOverlay`（已有 Feature）、`Achievement/Statistics/SaveBackup` Overlay。

### P3 — 体验增强（需求 §41 P3）
18. 键盘快捷键（T/B/I/M/Q/P/G/S）。
19. 长按/Hover 详情、Tooltip。
20. 动态 HUD 提醒（濒危/背包上限/图鉴解锁/任务更新）。
21. 统一转场动画（150~300ms）。
22. 性能优化（按 §28）；Overlay 上下文记忆打磨。

---

## 27. 风险与对策

| 风险 | 对策 |
|---|---|
| 独立路由刷新/bookmark 失效 | 保留现有路由为「独立打开模式」，业务组件共用 |
| 返回键误退出战斗 | 战斗保护（Back 不退出） |
| 玩家坐标/镜头丢失 | 纳入 `useMapStore`，浮层关闭后恢复 |
| 重复触发 Encounter | 复用 `wild.engaged` + 关闭战斗后短暂 encounterLock |
| 浮层无限堆叠 | 深度上限两级，超限改用模块内状态 |
| 移动端性能 | Overlay 打开暂停地图逻辑（PauseLevel），不无谓重绘 |
| 大量路由跳转遗留 | 逐步迁移，验收时统计无必要路由跳转 |
| 文档与代码不一致 | 实施同步更新需求/技术/规划/README/AGENTS（见 §29） |

---

## 28. 性能影响与优化

- MapScene 常驻但 Overlay 打开时暂停 update loop / 野怪 AI / 接触检测 / 输入。
- 「组件存在」≠「持续满载」；必要时 `pauseRendering` 或降低后台帧率，但不过度优化。
- 大型列表（宠物/图鉴）如需虚拟滚动仅当数据量大时引入；当前 27 宠规模无需虚拟滚动。
- 宠物立绘按需加载；图鉴资源按需请求。
- Overlay 关闭后清理事件监听、定时器，防止状态泄漏。

---

## 29. 文档同步维护（实施完成必要条件）

- `README.md`：更新导航模型、交互方式、当前收尾状态。
- `AGENTS.md`：更新「当前开发阶段状态」相关约束（若改变交互约定）。
- `docs/requirements/宠物精灵游戏第一阶段需求设计文档 V1.0.md`：若涉及「进入队伍页面/跳转战斗页面」等描述，改为 Overlay 交互模型。
- `docs/technical/宠物精灵游戏第一阶段技术方案说明 V1.0.md`：同步 Overlay 架构与前端交互。
- `docs/planning/游戏UI导航与Overlay架构方案.md`：标注 P0 已落地与本方案落地记录。
- `docs/development/FRONTEND_STANDARDS.md`：新增 Overlay / HUD / 反馈层 / 快捷键约定。
- `docs/development/DEVELOPMENT_STATUS.md`：更新阶段记录与遗留问题。

---

## 30. 回归测试重点

- [ ] 地图打开队伍后位置不变
- [ ] 关闭队伍后继续原地探索
- [ ] 打开宠物详情不重新加载地图
- [ ] 打开背包不触发遇敌
- [ ] NPC 对话期间玩家不能移动
- [ ] NPC → 商店 → NPC 返回正确
- [ ] 图鉴 → 地图 → 图鉴上下文正确
- [ ] 多层 Overlay 返回正确
- [ ] ESC 永远只关闭最高层
- [ ] 手机返回键行为正确
- [ ] 战斗开始时普通 Overlay 不冲突
- [ ] 战斗结束后恢复地图
- [ ] 玩家位置不丢失
- [ ] 镜头不重置
- [ ] NPC/野怪状态正确
- [ ] 手机布局不溢出
- [ ] PC Hover / 快捷键正确
- [ ] 重复开关 Overlay 无状态泄漏
- [ ] 无必要路由跳转

> 前端 `vue-tsc` + `vite build` 通过；后端测试不受影响（本方案不改后端逻辑，除非新增 HUD 接口）。

---

## 31. 最终验收目标

- 探索连续：打开/关闭功能不产生离开世界感。
- 操作快速：常见操作 1~3 次有效操作内。
- 上下文稳定：任何 Overlay 关闭后回到原位置/镜头/状态。
- 层级清晰：玩家知道当前在哪一层。
- 返回统一：不用学习每个功能不同关闭方式。
- 手机友好：不因小屏变成大量跳转。
- PC 高效：Hover / Drawer / 快捷键。
- 开发可维护：统一 Overlay 规则，后续新系统默认遵循。

## 32. 最终核心设计原则

> **地图是世界，HUD 是信息，Overlay 是工具，BattleOverlay 是临时战斗场景。**

新增系统时首先判断「完成操作后用户是否仍继续当前位置探索」，若为是，默认使用 Overlay / Drawer / BottomSheet / Modal，而非 Router Page。
