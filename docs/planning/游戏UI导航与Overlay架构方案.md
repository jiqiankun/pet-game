# 游戏 UI 导航与 Overlay 架构方案

> **历史方案说明（2026-08-19）：** 本文保留为现有 Overlay 演进背景。当前实施以《宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md》和阶段 0 裁决为准；本轮仅桌面端，本文移动端/BottomSheet 内容不实施、不验收。

**适用项目：** 宠物精灵游戏第一阶段
**依据：** 《宠物精灵游戏第一阶段需求设计文档 V1.0》《宠物精灵游戏第一阶段技术方案说明 V1.0》《宠物精灵游戏分阶段开发规划 V1.0》、`docs/architecture/ARCHITECTURE.md`、`docs/development/FRONTEND_STANDARDS.md`
**目标版本：** 阶段 14 完成之后的架构优化（非新增阶段，属第一阶段收尾工程化改进）

---

## 0. 文档定位

本文档描述**游戏内页面/场景/弹层/战斗/地图/菜单之间联动方式**的重新梳理与规范化方案。

> 核心目标：将游戏从传统 Web「页面跳转式交互」调整为「主场景常驻 + 功能层叠加 + 少量真实场景切换」的交互架构。

本文档先给出现状与问题、目标层级模型、分阶段改造计划与验收标准；**代码修改需在本文档审核通过后按阶段执行**。

---

## 1. 当前页面联动现状

### 1.1 技术栈

- Vue 3 + TypeScript（严格模式）+ Vite + Pinia + Vue Router（**Hash 模式**）+ Phaser 4.2.1。
- 无第三方 UI 库；样式为全局 CSS 变量 + 各组件 `scoped` 样式。
- 响应式：全局 `@media (max-width: 768px)` + 各页底部导航。

### 1.2 路由体系（`src/router/index.ts`，共 18 个顶级路由）

| 路由 | 页面 | 是否游戏内交互 | 当前联动方式 |
|---|---|---|---|
| `/` | HomeView | 是（首页/大厅） | 路由 |
| `/new-game` | NewGameView | 是（开新档） | 路由 |
| `/explore` | ExploreView（Phaser 地图） | 是（主场景） | 路由 |
| `/world-map` | WorldMapView | 是（大地图） | 路由 |
| `/battle` | BattleView | 是（战斗） | 路由 |
| `/pets` | PetView | 是（宠物养成） | 路由 |
| `/team` | TeamView | 是（队伍） | 路由 |
| `/storage` | StorageView | 是（仓库） | 路由 |
| `/pokedex` | PokedexView | 是（图鉴） | 路由 |
| `/boss` | BossView | 是（Boss） | 路由 |
| `/inventory` | InventoryView | 是（背包） | 路由 |
| `/shop` | ShopView | 是（商店） | 路由 |
| `/quest` | QuestView | 是（任务） | 路由 |
| `/achievement` | AchievementView | 是（成就） | 路由 |
| `/statistics` | StatisticsView | 是（统计） | 路由 |
| `/settings` | SettingsView | 是（设置） | 路由 |
| `/save-backup` | SaveBackupView | 是（存档备份） | 路由 |
| `/dev-tools` | DevToolsView | 否（开发者工具） | 路由 |

### 1.3 地图探索实现

- **ExploreView**：`onMounted` 加载 bootstrap + 当前地图 → `createPhaserGame()` 创建 Phaser；`onBeforeUnmount` 调 `game.destroy(true)`。
- **Phaser 场景**：`BootScene`（资源加载）→ `MapScene`（地图渲染/玩家移动/野怪 AI/对象交互）。
- **通信**：`GameBridge` 单例事件桥。Phaser → Vue 用业务事件（`encounter:touch` 等），Vue → Phaser 用 `cmd:*` 命令（`cmd:restart-map` / `cmd:set-input-lock` / `cmd:remove-wild` / `cmd:remove-object`）。
- **地图状态**：`useMapStore` 保存 `currentMap`（区域访问视图）、`defeatedWildIds`、`activeEncounterSpawnId`；**玩家坐标/方向/相机位置保存在 MapScene 本地，不入 store**。

### 1.4 战斗进入与退出

```text
遇敌（MapScene 触发 encounter:touch）
  → ExploreView 弹「遭遇野生宠物」确认框
  → battleStore.startMapEncounter(groupId)
  → router.push('/battle')
  → BattleView 结算
  → handleLeave() 只 battleStore.leaveBattle()（清空战斗 store）
  → 用户需手动导航回其它页面
```

Boss 战：`BossView.startBattle()` → `bossStore.startBossBattle()` → `router.push('/battle')`。

### 1.5 背包/宠物/任务/图鉴/商店/设置等

全部为独立路由，通过顶部导航 `RouterLink` 或 `router.push` 进入；进入即离开当前地图页面。

### 1.6 已有全局 Overlay（好范例）

- **DialogueBox**（NPC 对话）：全局挂在 `MainLayout`，由 `questStore.currentDialogue` 驱动，`position: fixed` 覆盖顶层。
- **TutorialOverlay**（新手教学）：全局挂在 `MainLayout`，由 `questStore.tutorialState` 驱动。

### 1.7 状态管理

Pinia stores 按业务域划分：`game`（player/pets/team/bootstrap）、`map`、`battle`、`quest`、`boss`、`pokedex`、`shop`、`storage`、`achievement`、`statistics`、`app`（连接状态）。

---

## 2. 当前存在的问题

1. **战斗整页跳转**：遇敌 → `/battle` 会卸载 ExploreView → `game.destroy(true)` → 地图销毁；战斗结束不自动回地图，玩家需重新进入 `/explore` → 重新初始化 → **从出生点重生**。
2. **玩家坐标/方向/相机位置丢失**：这些状态只存在于 MapScene 本地，切页即丢。
3. **背包/宠物/图鉴/任务/商店/设置等核心菜单走路由**：打开它们意味着离开地图，不符合「主场景常驻」目标。
4. **无统一 Overlay 体系**：Explore 内部散落多个居中 modal（遭遇/营地/出口/奖励/随机事件），各功能页各自为战。
5. **无统一返回键处理**：ESC / 浏览器 Back / Android Back / UI 返回无统一策略；战斗中 Back 可能直接退出地图导致战斗状态丢失。
6. **Game State 与 UI State 混用**：例如对话框打开状态、选中项等 UI 状态散落在组件内，部分与业务状态耦合。
7. **地图暂停/恢复无统一机制**：目前仅靠 `cmd:set-input-lock` 锁输入，无统一 OverlayPolicy。
8. **BGM/音效**：当前无音频系统，暂不涉及（见 §16）。
9. **开发者页面/存档备份/统计等非探索上下文页面**：混在游戏主循环路由中，导航栏项过多。

---

## 3. 页面分类结果

### 3.1 判断标准

> 玩家打开该功能后，游戏角色在世界中的位置是否发生变化？

- **位置变化** → Scene/Route 切换。
- **位置未变化** → Overlay。
- **非游戏内交互**（开发者工具、错误页）→ 普通 Route。

### 3.2 分类表

| 页面 | 分类 | 理由 |
|---|---|---|
| `/explore` | **Scene（主场景，常驻）** | 地图探索即主场景 |
| `/world-map` | **Scene** | 大地图是空间层级的切换（区域选择） |
| `/battle` | **BattleOverlay（FullScreen）** | 战斗时玩家位置未变，地图应保留 |
| `/pets` | **PetOverlay（Panel）** | 位置未变 |
| `/team` | **TeamOverlay（Panel）** | 位置未变 |
| `/storage` | **StorageOverlay（Panel）** | 位置未变 |
| `/pokedex` | **PokedexOverlay（Panel）** | 位置未变 |
| `/boss` | **BossOverlay（Panel）** | 位置未变（Boss 战本身复用 BattleOverlay） |
| `/inventory` | **InventoryOverlay（Panel）** | 位置未变 |
| `/shop` | **ShopOverlay（Panel）** | 位置未变 |
| `/quest` | **QuestOverlay（Panel）** | 位置未变 |
| `/achievement` | **AchievementOverlay（Panel）** | 位置未变 |
| `/statistics` | **StatisticsOverlay（Panel）** | 位置未变 |
| `/settings` | **SettingsOverlay（Panel）** | 位置未变 |
| `/save-backup` | **SaveBackupOverlay（Panel）** | 位置未变，但属低频系统功能 |
| `/new-game` | **Scene（保留 Route）** | 开新档属主场景切换 |
| `/dev-tools` | **普通 Route（保留）** | 开发者工具，非游戏内交互 |

### 3.3 保留的 Route

- `/`（首页/大厅）— 游戏主入口与存档门面。
- `/new-game` — 开新档。
- `/dev-tools` — 开发者工具（非游戏内交互）。
- 新增 `/error`（可选）— 错误/异常页。

> 阶段 13 之后，`/explore` 作为主场景路由保留；`/world-map` 作为空间层级 Scene 保留。

### 3.4 改为 Overlay 的功能

`/battle`、`/pets`、`/team`、`/storage`、`/pokedex`、`/boss`、`/inventory`、`/shop`、`/quest`、`/achievement`、`/statistics`、`/settings`、`/save-backup`。

> **注意**：改为 Overlay 不意味着立刻删除这些路由。为兼容刷新与异常情况，**这些路由保留为「独立打开模式」**（例如直接访问 `/pets` 时以独立页展示），同时新增 Overlay 模式。二者共用同一业务组件（见 §22、§26）。

---

## 4. Scene 定义

| Scene | 说明 | 常驻 |
|---|---|---|
| TitleScene | 首页/大厅（`/`） | 是 |
| ExplorationScene | 地图探索（`/explore`，Phaser 主场景） | **是（目标）** |
| WorldMapScene | 大地图（区域选择） | 是 |
| IndoorScene | 室内/据点（如宠物中心，当前未实现，预留） | 预留 |
| SpecialScene | 特殊章节/结局（预留） | 预留 |

---

## 5. Overlay 分类

| 类型 | 说明 | 适配 |
|---|---|---|
| **FullScreenOverlay** | 战斗、捕捉演出、Boss 演出、重要剧情、大型详情 | 全屏覆盖 |
| **PanelOverlay** | 背包/宠物/队伍/图鉴/任务/角色信息/商店/Boss/成就/统计/设置/存档备份 | PC 端面板、移动端 BottomSheet/FullScreen |
| **DialogOverlay** | NPC 对话、确认框、技能学习/替换确认、进出区域确认 | 居中卡片 |
| **Toast/Notification** | 获得金币/道具/宠物升级/任务进度 | 轻提示 |

---

## 6. OverlayManager 设计

### 6.1 原则

- **复用现有 Pinia，不新引入重型框架**。
- 以「**Overlay 栈**」统一维护，避免几十个 `showXxx` boolean。
- 业务模块内部自维护 View Stack，不全部注册为全局 Overlay。

### 6.2 结构

新增 `stores/overlay.ts`（`useOverlayStore`），维护：

```ts
interface OverlayEntry {
  type: OverlayType        // 'BATTLE' | 'INVENTORY' | 'PET' | 'TEAM' | 'POKEDEX' | 'QUEST' | 'SHOP' | 'BOSS' | 'ACHIEVEMENT' | 'STATISTICS' | 'SETTINGS' | 'STORAGE' | 'SAVE_BACKUP' | 'REWARD' | 'DIALOG'
  id: number               // 唯一 id（自增）
  policy: OverlayPolicy    // 运行策略（§7）
  data?: unknown           // 打开参数（如 { encounterId, enemyTeam }）
  level: number            // 层级：primary/secondary/system
}
```

对外 API（结合现有 store 命名风格）：

```ts
open(type, data?)   // push 到栈顶，按 policy 下发 cmd:set-input-lock 等
close()             // 关闭栈顶
close(type)         // 关闭指定类型的最上层
closeAll()
isOpen(type)
top                 // 当前最上层
```

### 6.3 Overlay 栈规则（§十 限定逻辑层级）

```text
Scene
  └─ PrimaryOverlay（背包/宠物/图鉴/任务/商店/Boss 等，同一时刻通常只有 1 个）
       └─ SecondaryOverlay（宠物详情/技能详情等，模块内部 View Stack）
            └─ SystemDialog（确认框/消息框）
                 └─ Toast
```

- **同一时刻最多 1 个 Primary Panel Overlay**（打开新 Panel 前先关闭旧 Panel，或明确替换）。
- **SystemDialog/Toast 可叠加在任意 Overlay 之上**。
- **战斗（FullScreen）专属**：可叠加在 Panel Overlay 之上（例如从 Boss 面板发起 Boss 战），此时关闭战斗后回到 Boss 面板。

### 6.4 业务模块内部 View Navigation

- `PetOverlay` 内部：`PetList → PetDetail → SkillDetail`，用一个本地 `currentView` 状态管理，不注册为多个全局 Overlay、不使用路由。
- `PokedexOverlay` 内部：`PokedexList → PokedexDetail`。
- `QuestOverlay` 内部：`QuestList → QuestDetail`。
- 返回：内部 View 逐层 pop，最后回到 Panel 顶层，再关闭 Overlay 回到地图。

---

## 7. OverlayPolicy（运行策略）

每个 Overlay 定义运行策略；`OverlayManager` 依此统一下发地图暂停/恢复命令。

| Overlay | 地图渲染 | 玩家移动 | 时间/野怪 AI | 再次触发 Encounter | BGM |
|---|---|---|---|---|---|
| 背包 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| 宠物 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| 图鉴 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| 任务 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| 商店 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| Boss 面板 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| NPC 对话 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |
| 战斗 | 保留或隐藏 | 暂停 | 暂停 | 暂停 | 战斗 BGM |
| Toast | 保留 | 保留 | 继续 | 保留 | 保留 |
| 设置 | 保留 | 暂停 | 暂停 | 暂停 | 保留 |

> 当前使用 `cmd:set-input-lock` 控制输入；OverlayManager 打开 Panel 时下发 `locked=true`，关闭时根据栈顶自动恢复 `false`。战斗在锁定输入外，还应暂停野怪 AI 与接触检测（见 §8）。

---

## 8. Exploration suspend / resume

### 8.1 目标

地图实例（Phaser Game）原则上**常驻不销毁**；打开 Overlay 时暂停，关闭后恢复。

### 8.2 暂停维度

战斗或其他 FullScreen Overlay 打开时，至少暂停：

- 玩家移动
- 野怪 AI（updateWilds）
- 再次触发 Encounter（接触检测）
- 地图快捷键
- 地图时间推进
- 可交互对象触发

### 8.3 实现方式

在 **MapScene.update()** 增加「运行状态」判断，而非仅靠输入锁：

```ts
private explorationPaused = false  // 由 OverlayManager 经 cmd 统一控制

update(time, delta) {
  if (this.explorationPaused) return   // 暂停全部逻辑（含野怪 AI / 接触检测）
  if (!this.inputLocked) {
    this.movePlayer(delta)
    this.updateWilds(time, delta)
    this.checkContacts()
    this.checkInteractions()
  }
}
```

新增 bridge 命令：`cmd:set-exploration-paused`（`{ paused: boolean }`），由 OverlayManager 统一下发。

### 8.4 恢复维度

关闭战斗/Overlay 后必须恢复：

- 当前 mapId
- 玩家位置
- 玩家方向
- 相机位置
- 当前地图事件
- NPC 状态
- 已击败敌人状态
- 宝箱状态
- 当前环境
- 地图 BGM
- 临时事件状态

> 由于地图实例不销毁，以上状态天然保留（MapScene 本地 + useMapStore + 后端会话）。**战斗结束不得重新加载地图、不得重生玩家**，除非属于战败/传送/剧情等特殊情况。

---

## 9. 战斗生命周期改造（P0）

### 9.1 目标流程

```text
Exploration
  → Encounter Trigger（MapScene 触发 encounter:touch）
  → Suspend Exploration（OverlayManager 下发暂停）
  → Open BattleOverlay（FullScreen）
  → Battle
  → BattleResult（结算）
  → Apply Result（回写 GameState / Exploration）
  → Close BattleOverlay
  → Resume Exploration
```

### 9.2 ExploreView 改造

- 遇敌确认后，不再 `router.push('/battle')`，改为 `overlayStore.open('BATTLE', {...})`。
- 地图组件**不销毁**，仅暂停。

### 9.3 BattleView 改造

- 增加「是否作为 Overlay 挂载」模式：由 OverlayManager 渲染时，地图在底层保留。
- 战斗结束（`settlement` 触发）后，走统一 `BattleResult` 回写（§10），再 `overlayStore.close('BATTLE')`。
- 独立模式（直接访问 `/battle`）仍可用，用于测试战斗/开发者入口。

### 9.4 战斗结束回地图

- 胜利：`markWildDefeated(activeEncounterSpawnId)` → 下发 `cmd:remove-wild` → 关闭 Overlay → 恢复探索。
- 逃跑：同战败结算，关闭 Overlay 恢复。
- 战败：见 §17。

### 9.5 避免重复触发 Encounter

- `MapScene` 中 `wild.engaged` 已防止同一野怪重复上报。
- 战斗关闭恢复后，若玩家仍与敌人碰撞，需额外保护：使用 `encounterLock`（恢复后短暂禁止再次触发，如 500ms）或复用 `engaged` 机制。

---

## 10. BattleResult 统一回写机制

### 10.1 目标

BattleOverlay 不直接任意修改地图；统一通过 GameState 回写。

### 10.2 结构

新增统一 `BattleResult`（在前端 `stores/battle.ts` 的 `settlement` 基础上收敛，字段与后端 `BattleSettlement` 对齐）：

```ts
interface BattleResult {
  resultType: 'WIN' | 'DEFEAT' | 'FLEE' | 'CAPTURE'
  experienceChanges
  levelChanges
  hpChanges
  statusChanges
  capturedPets
  itemChanges
  currencyChanges
  enemyResult
  questProgress
  eventFlags
}
```

### 10.3 流程

```text
Battle
  → settlement（后端落库）
  → 前端 BattleResult（映射/包装）
  → GameState（更新 game/map/boss/quest 等 store）
  → Exploration（下发 remove-wild / 恢复）
```

> 当前后端 `settle` 已统一落库并返回 `BattleSettlement`，前端只需把现有「多个 watch 事后联动」收敛为 BattleResult 统一处理，**不改变后端战斗规则与数值**。

---

## 11. 返回键统一处理

### 11.1 优先级

```text
存在 SystemDialog
  → 关闭 SystemDialog
否则存在 SecondaryOverlay（Overlay 内部 View）
  → 返回上一层
否则存在 PrimaryOverlay
  → 关闭 Overlay
否则处于探索中
  → 打开暂停菜单
否则（首页/独立页）
  → 默认浏览器行为
```

### 11.2 实现

- 新增 `stores/overlay.ts` 维护栈，`handleBack()` 统一处理。
- 监听 `keydown`（ESC）与浏览器 `popstate`（Hash Back）。
- **战斗保护**：战斗中 Back 不退出战斗（除非确认放弃，且后端战斗本就丢弃）。

---

## 12. PC / 移动端适配策略

- Panel Overlay 业务组件**只写一套**（`*Overlay.vue`），通过 CSS + 响应式类切换形态：
  - PC：右侧/中央面板。
  - 移动端（`@media max-width 768px`）：全屏 / BottomSheet。
- 不创建 `MobileInventory` / `DesktopInventory` 两套组件。
- 复用现有 `@media (max-width: 768px)` 全局方案。

---

## 13. BGM / 音效策略

- 当前项目**无音频系统**（未引入音频资源）。本节为预留约束。
- 引入音频时不因切页重新加载；Overlay 打开/关闭基于 Scent 常驻，BGM 状态天然保留。
- 战斗 BGM：战斗 Overlay 打开时淡入，关闭时恢复地图 BGM。**不因重新创建地图而重新播放**。

---

## 14. 动画与过渡

- 战斗出现：`100~300ms` 遇敌特效后浮现 BattleOverlay。
- 战斗结束：`BattleResult → 退出动画 → 恢复 Exploration`。
- 过渡保持轻量，注意移动端性能；可通过 `transition`/`v-if` 实现，不引入动画库。

---

## 15. 性能策略

- 地图常驻但不持续满载：Overlay 打开时暂停 update loop / 野怪 AI / 接触检测 / 输入。
- 明确「组件仍存在」≠「组件仍持续执行所有逻辑」。
- 移动端避免地图后台持续运行导致耗电/掉帧。

---

## 16. 状态管理边界

- **Game State**（存入/引用后端）：`playerPosition`（会话内）、`petHp`、`inventory`、`quests`、`enemyState`、`defeatedWildIds`、`activeEncounterSpawnId`。
- **UI State**（仅前端）：`currentOverlay`、`selectedPetId`、`currentPetTab`、`inventoryTab`、`dialogPage`。
- 关闭 Overlay 不丢失 Game State；纯 UI 状态不入存档。
- 玩家坐标/方向/相机等会话级探索状态，纳入 `useMapStore` 或独立 `ExplorationContext`（§18），确保切 Overlay 后恢复。

---

## 17. 战败流程

- 与现有系统保持一致：战败零惩罚，返回最近恢复点并恢复全队（后端随 settle 完成）。
- 战败后若需传送回营地/恢复点，属于真正的 Scene 状态变化 → 由后端返回的 `defeat` 信息驱动前端关闭战斗 Overlay 并按需触发 Scene 切换（重载地图到恢复点）。
- 不默认「关闭 Overlay 继续原地探索」。

---

## 18. ExplorationContext（复用现有 store）

- 不新建重型框架；在 `useMapStore` 基础上补充会话级探索字段：
  - `playerPosition` / `playerDirection` / `cameraPosition`
  - `npcStates` / `enemyStates`
  - `defeatedEnemies`（已有 defeatedWildIds）
  - `collectedObjects`（已有 consumedChestIds 等）
  - `activeEvents` / `eventFlags`
  - `weather` / `mapBgm` / `temporaryEffects` / `encounterState`
- 当前多数已有后端会话（`MapEnterView`）支撑，前端仅需补足坐标/方向/相机等表现态。

---

## 19. 兼容性风险与说明

1. **刷新/异常**：根据现有存档策略，刷新后恢复到最近安全探索状态（`/explore` 重新初始化），**不强制恢复战斗中每一回合**；不构建复杂战斗快照系统。
2. 保留原路由作为「独立打开模式」，直接访问 `/pets` 等仍可用，避免因 Overlay 化破坏刷新/bookmark。
3. **不破坏现有存档**：本文档只改前端联动力式，不涉及表结构、不改变后端战斗/数值/技能/地图玩法。
4. **不引入大型状态管理框架**：复用 Pinia。
5. **不大量重复组件**：业务组件复用同一套，仅布局由 CSS 切换。

---

## 20. 分阶段改造计划

### P0 — 地图 ↔ 战斗（最高优先级）

- 新增 `stores/overlay.ts`（OverlayManager + OverlayPolicy 基础）。
- MapScene 增加 `explorationPaused` 与 `cmd:set-exploration-paused`。
- Explore 遇敌改为打开 BattleOverlay，战斗结束关闭并恢复探索，不销毁地图、不重生玩家。
- 实现避免重复触发 Encounter 的保护。
- BattleResult 回写收敛。

### P1 — 地图 ↔ NPC / 商店 / 背包 / 宠物

- NPC 对话已有 Overlay（保留）。
- 商店、背包、宠物改为 Overlay（复用现有业务组件，加 Panel 外壳）。
- 地图触发这些功能改为打开 Overlay。

### P2 — 图鉴 / 任务 / 设置 / 奖励 / 技能详情 / 队伍 / 仓库 / Boss / 成就 / 统计

- 图鉴、任务、设置、队伍、仓库、Boss、成就、统计、存档备份改为 Overlay。
- Overlay 内部 View Stack 建设（列表→详情→技能详情）。

### P3 — 统一收尾

- OverlayManager / OverlayPolicy 完善。
- 返回键统一（ESC / Back / Android Back）。
- BGM / 动画 / 移动端适配打磨。
- 顶部导航栏精简（改为地图内 HUD 菜单入口，不再罗列全部路由）。

---

## 21. 验收标准

- 战斗不再依赖整页 Route 跳转。
- 战斗结束后地图无需重新初始化，玩家坐标不丢失、相机不异常。
- NPC/野怪状态不会因 Overlay 关闭错误重置。
- 不会因 Overlay 关闭再次触发同一 Encounter。
- 背包/宠物等核心菜单优先使用 Overlay。
- Overlay 内部支持自己的 View Navigation。
- Overlay 数量与层级可控，返回键逻辑统一。
- 地图暂停与恢复逻辑统一。
- PC 与移动端使用同一套核心业务逻辑。
- 未破坏现有存档、未改变战斗规则/数值/技能/地图玩法。
- 未引入大型状态管理框架、未大量重复组件、未遗留大量无意义路由。
- 刷新/异常关闭至少能安全恢复到合理状态。
- 新架构后续可自然支持新的 Overlay 功能。

---

## 22. 关键实现约束（避免过度设计）

- **优先复用现有 store / 组件 / 机制**（GameBridge、useMapStore、useBattleStore、settlement、useQuestStore 的 Overlay 范例）。
- **不删除现有路由**，改为「Overlay 模式 + 独立模式」双模式，业务组件共用。
- **不改变任何游戏规则/数值**，本次只做 UI 联动、生命周期与状态管理。
- **不做与本任务无关的大规模重构**。
