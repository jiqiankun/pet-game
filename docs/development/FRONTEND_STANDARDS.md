# 前端编码规范

**适用项目：** 宠物精灵游戏第一阶段 + 桌面版世界/UI 重构
**依据：** 《宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0》《宠物精灵游戏第一阶段技术方案说明 V1.0》

---

## 1. 技术栈约束

| 项 | 方案 |
|---|---|
| 框架 | Vue 3 |
| 语言 | TypeScript（严格模式） |
| 构建 | Vite |
| 状态管理 | Pinia |
| 路由 | Vue Router（Hash 模式） |
| 2D 表现 | Phaser |
| 地图 | Tiled + JSON |

**禁止引入：** 未经项目文档确认的前端框架、UI 库、状态管理方案或其他重型依赖。

---

## 2. 目录结构约定

```text
frontend/
├── src/
│   ├── api/            # 后端 API 封装，按业务域组织
│   ├── assets/         # 静态资源（图片、字体、图标）
│   ├── components/     # 公共可复用组件
│   ├── composables/    # 可复用的组合式函数
│   ├── game/           # Phaser 相关代码
│   │   ├── PhaserGame.ts
│   │   ├── bridge/     # Vue ↔ Phaser 事件桥接
│   │   ├── scenes/     # Scene 类（BootScene、MapScene、BattleScene）
│   │   ├── objects/    # 游戏对象
│   │   └── effects/    # 视觉特效
│   ├── layouts/        # 页面布局组件
│   ├── router/         # 路由配置
│   ├── stores/         # Pinia store，按业务域划分
│   ├── types/          # 公共 TypeScript 类型定义
│   ├── utils/          # 工具函数
│   └── views/          # 页面组件，按一级功能划分
├── package.json
└── vite.config.ts
```

**规则：**
- 每个一级页面在 `views/` 下对应一个目录（Home、Explore、Pet、Team、Pokedex、Boss、Inventory、Quest、Achievement、Statistics、Settings）。
- 公共组件放 `components/`，仅在某页面内使用的子组件放该页面目录下。
- 类型定义统一放 `types/`，避免类型散落。

---

## 3. Vue 组件规范

- **Composition API**：所有组件使用 `<script setup lang="ts">` 语法。
- **组件命名**：PascalCase，如 `PetCard.vue`、`BattlePanel.vue`。
- **Props 声明**：必须使用 TypeScript 类型注解，通过 `defineProps<T>()` 声明。
- **Emits 声明**：必须通过 `defineEmits<T>()` 声明事件类型。
- **单文件组件结构**：`<script setup>` → `<template>` → `<style scoped>`。
- **组件职责**：单个组件只关注一个功能域；超过 300 行时考虑拆分。

---

## 4. 状态管理规范（Pinia）

- Store 按**业务域**划分（如 `usePlayerStore`、`usePetStore`、`useTeamStore`、`useBattleStore`、`useInventoryStore`）。
- Store 中只保存**前端状态**；核心游戏数据以后端为准，Store 不替代后端做业务计算。
- **Phaser 场景中禁止直接操作 Pinia Store**；必须通过 bridge 事件机制与 Vue 层通信。
- 跨组件共享的临时 UI 状态（弹层、选中项等）允许在 Store 中管理。

---

## 5. 路由规范

- **Hash 模式**：所有路由使用 `/#/path` 格式，简化单 JAR SPA 部署。
- **桌面主流程**：有存档后以常驻 World Root 为游戏根；宠物、队伍、图鉴、背包、任务、Boss、商店、设置和战斗通过 Context/Overlay 打开，不得为了普通功能切换卸载探索地图。
- **兼容路由**：现有 `/pets`、`/team`、`/pokedex`、`/inventory`、`/quest`、`/boss`、`/battle` 等路由在阶段 1～6 只作为直接访问、异常恢复和开发调试入口；同一业务组件必须与 Overlay 复用，不维护两套逻辑。
- **路由守卫**：无存档时自动进入新游戏流程；有存档时恢复上次状态。
- **返回顺序**：浏览器返回和 Esc 优先按 Context Stack 关闭最上层可关闭上下文；世界页只保留一个稳定历史保护位，禁止每次打开浮层都写入历史；Context 为空后才进行真正路由返回。

### 5.1 阶段 1 常驻世界与 Context Stack 约定

- `/explore` 必须由 `views/Explore/WorldRoot.vue` 承载；`App.vue` 只缓存该世界根。打开普通功能 Context 时不得卸载 `ExploreView` 或重建 Phaser。
- `stores/overlay.ts` 是唯一的游戏内 Context 生命周期入口。每个条目至少保留 `id/key/type/parentId/source/blockLevel/inputContext/closePolicy/returnData/triggerElement`；禁止页面另建第二套全局返回或暂停状态。
- Context 的阻塞语义固定为：`NONE` 不阻塞、`INPUT` 只清空世界输入、`WORLD` 停止玩家/野怪/接触更新、`BATTLE` 锁定最高战斗上下文。NPC 对话与所有阻塞确认使用 `WORLD`，奖励使用 `NONE`。
- Context 输入类型固定为 `WORLD/PANEL/DIALOG/BATTLE/TEXT_INPUT`；只有 `WORLD` 可打开游戏快捷键并向 Phaser 透传移动。文本输入聚焦、窗口失焦、页面隐藏、阻塞 Context 打开时必须发送 `cmd:clear-input`。
- 按类型关闭必须从栈顶反向查找；需要精确关闭时传 Context `id`，不得再使用数组第一个同类型条目。需要替换栈顶时使用 `replaceTop`，不得先关闭再打开而触发中间焦点恢复。`EXPLICIT` Context（战斗、不可取消事件）必须消费 Esc 与浏览器返回，不得被关闭。
- `OverlayShell`、NPC 对话、探索确认框和战斗层必须具备初始焦点、Tab/Shift+Tab 边界、`role="dialog"`、栈顶 `aria-modal` 与关闭后的焦点恢复。子 Context 关闭后优先聚焦父 Context；栈清空后恢复原触发元素，若该元素已卸载则回退 `[data-world-focus-root]`。
- 世界地图作为 Overlay 关闭时不得重启 MapScene；仅真正传送/进入区域时发送 `cmd:restart-map`。`REGION_MAP` 作为 `WORLD_MAP` 的子 Context，阶段 2 前仅复用现有兼容地图数据，禁止提前创建 WorldGraph 或新持久化模型。MapScene 创建后发送 `map:ready`，由 Vue 再同步当前 Context 的暂停等级。
- 桌面默认快捷键为 W/A/S/D（移动）、E（世界交互）、Q（快捷队伍）、B（背包）、J（任务）、M（世界/区域地图）、Esc（栈顶返回）。旧 P/G/T/I/S 功能快捷键不再注册，避免与移动或上下文冲突。

---

## 6. API 调用规范

- 所有后端调用统一封装在 `api/` 目录，按业务域分文件（如 `pet.ts`、`battle.ts`、`boss.ts`）。
- 统一处理响应结构 `{ success, data, message, code }`。
- **错误处理使用稳定 errorCode**，不依赖后端异常字符串判断业务逻辑。
- 禁止在组件内直接使用 `fetch` 或 `axios`，必须通过 `api/` 层封装。
- Vite 开发代理配置 `/api` → `http://localhost:8080`。

---

## 7. TypeScript 规范

- **严格模式**：`tsconfig.json` 开启 `strict: true`。
- **禁止 `any`**：除非有明确注释说明为何必须使用 `any`，且经过审查确认无替代方案。
- **类型复用**：公共类型定义在 `types/` 目录；API 响应类型与请求类型分别定义。
- **枚举与常量**：业务枚举（如属性、稀有度、状态）定义为 TypeScript enum 或 const 对象，与后端保持一致。
- **非空断言**：避免滥用 `!`，优先使用可选链 `?.` 和空值合并 `??`。

---

## 8. Phaser 边界规范（核心约束）

> **Phaser 不是业务系统。**

**Phaser 只负责：**
- 地图场景渲染与玩家移动
- 战斗表现（站位、技能动画、伤害数字、Buff/Debuff 效果）
- 野生宠物显示与简单行为
- 地图对象交互（采集点、宝箱、营地、Boss 入口）

**禁止在 Phaser Scene 中：**
- 存储或计算宠物数据、图鉴、背包、任务、存档
- 实现战斗公式或伤害计算
- 直接调用后端 API
- 直接操作 Pinia Store

**通信方式：** Phaser 通过 bridge 向 Vue 层发送事件，Vue 层处理后回传结果。例如：

```text
玩家点击野生宠物 → Phaser 发送事件 → Vue/GameService 调用后端 → 创建战斗
```

**Scene 数量限制：** 第一阶段最多 3 个核心 Scene（BootScene、MapScene、BattleScene）。不为每张地图创建独立 Scene 类，地图差异通过 Tiled 配置解决。

---

## 9. 桌面样式规范

- **当前交付范围**：只开发和验收桌面端；覆盖常见 16:9 桌面/笔记本分辨率和超宽桌面窗口。
- **范围外**：手机、平板、触控导航、虚拟摇杆、Bottom Sheet 和移动端专项回归不在本轮范围；现有小屏样式保留但不作为桌面重构验收依据。
- **色彩体系**：严格遵循《宠物精灵游戏第一阶段 UI 设计文档》定义的色彩体系（品牌色、功能色、属性色、稀有度色、背景色、文字色）。
- **资源命名**：统一使用 ID，禁止中文文件名。示例：`pets/fire/PET_FIRE_001.png`、`skills/fire/FIRE_BLAZE_CLAW.png`。
- **宠物资源路径约定（阶段 14 美术验收）**：图标 `public/assets/pets/icons/pet_{speciesId}_icon_{64|128|256}.png`，立绘 `public/assets/pets/portraits/pet_{speciesId}_portrait.png`；Boss 立绘 `public/assets/bosses/portraits/boss_{bossId}_portrait.png`；道具图标 `public/assets/items/item_{itemId}.png`。前端统一通过 `src/game-assets.ts` 的 `petIconUrl` / `petPortraitUrl` / `itemIconUrl` 等辅助函数构造路径，禁止在组件内硬编码资源路径。
- **战斗展示标识**：后端 `UnitSnapshot` 显式下发 `artType` / `artId`（PET / BOSS / null），前端仅据此构造立绘 URL，不从名称或 unitId 猜测；无资源单位（null）不请求路径，保留文字卡片。
- **不开发独立 App**。

---

## 10. UI/交互优先级

> 正确性 > 可用性 > 一致性 > 美观程度。

- 前期不为视觉效果阻塞核心功能开发。
- 美术资源可用占位物先行，验收前必须列出待补清单。
- 战斗表现遵循「后端事件 → 前端播放」模型，前端不自行计算结果。

---

## 11. 开发模式

- 前端开发服务器：`npm run dev`（Vite，默认端口 5173）。
- 后端 Spring Boot 本地启动（端口 8080）。
- Vite 代理 `/api` 请求到后端 `http://localhost:8080`。
- 正式构建产物打入 Spring Boot 静态资源，输出单个 `pet-game.jar`。

---

## 12. 战斗页面约定（阶段 3 起）

- 战斗类型定义集中在 `src/types/battle.ts`（`UnitSnapshot` / `BattleEvent` / `BattleSnapshot` / `BattleAction`），与后端 DTO 对齐。
- 战斗状态集中在 `stores/battle.ts`（`useBattleStore`）：快照、待提交行动、事件日志；页面组件不自行持有战斗数据。
- **前端只提交行动意图**（SKILL/DEFEND/SWITCH/CAPTURE/FLEE + 目标），伤害/暴击/克制/捕捉结果一律以快照中的事件为准；未选行动的宠物由后端默认防御。
- 事件展示：后端返回事件序列（`BattleEventType`），Store 负责把事件翻译为中文日志，后续接入 Phaser 表现时仍沿用「后端事件 → 前端播放」模型。
- 当前 `/battle` 为 Vue 基础战斗页面（阶段 3 范围）；Phaser BattleScene 表现层在后续阶段接入，不在本页面内实现动画计算。
- 技能名称等展示内容通过配置查询接口（`/api/game/config/skills`）获取，不在前端硬编码内容配置。

---

## 13. 养成与队伍页面约定（阶段 4 起）

- 阶段 4 类型定义集中在 `src/types/pet.ts`（`PetDetail` / `LevelUpPreview` / `TeamView` / `InventoryView` / `BattleSettlement`），与后端 DTO 对齐。
- API 客户端 `src/api/client.ts` 提供 `apiGet` / `apiPost` / `apiPut` 三种通用方法，各页面按需导入使用，不在页面内自行创建 axios 实例。
- **宠物详情页**（`/pets`）采用三标签布局（基础/属性/技能）：属性标签展示六维分解表（基础/成长/资质/加点/合计），升级区域支持五种模式与预览，加点区域按转换表交互，技能标签展示 4 槽位装备/卸下。
- **队伍编辑页**（`/team`）采用 6 槽位布局（位置 1~3 首发、4~6 候补），下拉选择宠物、重复选择自动禁用，保存时整体提交 `PUT /api/team/members`。
- **背包页**（`/inventory`）按分类分组展示道具，恢复道具使用区支持选择道具+宠物+使用，展示 HP 变化结果。
- **战斗结算**：`stores/battle.ts` 新增 `settlement` 状态与 `settleBattle()` 方法；战斗结束时 `watch` 自动触发结算，BattleView 展示经验/金币/掉落/HP 回写结果面板。
- **战斗中禁用培养操作**：PetView 与 TeamView 通过 `battleStore.inBattle` 计算属性检测战斗状态，战斗中禁用所有培养与编辑按钮。

---

## 14. 捕捉与仓库页面约定（阶段 5 起）

- 阶段 5 类型定义：`src/types/battle.ts` 扩展（`battleType` / `fled` / `captured` / CAPTURE、FLEE 行动 / `CaptureRateView`），`src/types/pet.ts` 扩展（`CapturedPetView`），仓库类型集中在新增 `src/types/storage.ts`，均与后端 DTO 对齐。
- **捕捉率只展示后端计算结果**：BattleView 通过 `battleStore.captureRates` 展示，回合变化后自动重新拉取（`loadCaptureRates`）；前端不做任何捕捉率计算。
- **捕捉交互**：野生战斗中行动面板新增「捕捉/逃跑」按钮；捕捉流程为「选球 → 点目标」，捕捉球列表来自背包接口（itemType=CAPTURE_BALL）；捕捉去向（入队/仓库）在战斗结束后的去向选择面板确认，随 settle 提交 `joinTeam`。
- **简化遭遇入口**：战斗页「野生遭遇」按钮 + 探索页临时入口（仅跳转，不承载遭遇逻辑）；阶段 6 由地图承接后移除或改造，不在其中沉淀地图耦合逻辑。
- **仓库页**（`/storage`）：状态集中在新增 `stores/storage.ts`（`useStorageStore`）；筛选/排序条件以查询参数提交后端；放生必须先走预览接口（保护原因/警告/礼物点数）二次确认，放生结果礼物汇总展示后手动关闭。
- **昵称展示约定**：种族名称始终保留，有昵称时展示为「昵称（种族名）」。

---

## 15. 地图探索与 Phaser 约定（阶段 6 起）

### 15.1 Phaser 集成约定

- **Phaser 版本**：4.2.1（与 `frontend/package.json` 的 `phaser` 依赖一致），通过 `src/game/PhaserGame.ts` 创建实例，ExploreView 在 `onMounted` 启动、`onBeforeUnmount` 销毁。
- **3 核心 Scene**：`BootScene`（资源加载，含未数据保护）、`MapScene`（地图渲染 + 野怪 AI + 交互）、`BattleScene`（预留，后续阶段接入）。不为每张地图创建独立 Scene，地图差异通过 Tiled JSON 解决。
- **GameBridge 事件桥**：`src/game/bridge/GameBridge.ts`，类型化事件总线（`emit`/`on`/`off`）；Phaser 只通过 bridge 向 Vue 层发送事件，Vue 层处理后回传结果；Phaser 禁止直接调用后端 API、禁止操作 Pinia Store。
- **bridge 命令约定**：Vue → Phaser 使用前缀 `cmd:`（如 `cmd:restart-map`、`cmd:remove-wild`、`cmd:set-input-lock`）；Phaser → Vue 使用业务事件名（如 `encounter`、`exit`、`camp`、`gather`、`chest`）。

### 15.2 Tiled 地图约定

- **地图尺寸**：25×19 格 × 32px，统一使用占位 PNG 资源（`public/assets/`）。
- **图层约定**：`ground`（底层地块）、`obstacle`（阻挡层，gid 3=水、4=树）、`objects`（对象层）。
- **对象类型**：`wild_spawn`（野生刷新点，props: encounterGroupId/spawnId/aiMode）、`camp`（营地）、`chest`（宝箱）、`gather`（采集点）、`exit`（出口）、`boss_entrance`（Boss 入口占位）、`npc`（NPC 占位）、`hidden_spot`（隐藏点占位）。
- **资源命名**：地图 `public/assets/maps/{mapId}.json`，tileset `public/assets/maps/tileset.png`，精灵 `public/assets/sprites/{id}.png`，统一使用 ID，禁止中文文件名。

### 15.3 地图探索页约定

- **ExploreView**（`/explore`）：Phaser 容器 + 模态对话框（遭遇/营地/出口/奖励）+ toast；生命周期：`onMounted` 加载 bootstrap + currentMap → 创建 Phaser 游戏。
- **地图 store**：`stores/map.ts`（`useMapStore`），维护当前地图状态、已击败野怪、交互结果；Phaser 通过 bridge 事件同步状态。
- **移动操作**：方向键/WASD 控制玩家移动，E 键与附近对象交互；碰撞检测分轴支持沿墙滑动。
- **野怪 AI**：WANDER（随机游荡）、TIMID（玩家靠近时远离）、AGGRESSIVE（主动靠近）、RARE_STAY（稀有短暂停留）。

### 15.4 大地图页约定

- **WorldMapView 当前实现**（历史）：`/world-map` 显示已解锁区域卡片和营地传送入口。
- **桌面重构目标**：World/Region Map 作为常驻世界上的大型 Overlay，由 WorldGraph + PlayerKnowledge 驱动；打开/关闭不得重建 World Layer。旧 `/world-map` 仅保留兼容入口。

### 15.5 队伍页约定（阶段 6 重写）

- **TeamView**（`/team`）：5 套预设标签页 + HTML5 drag & drop + 技能查看（来自 petSummaries）+ 详情链接。
- **预设 API**：`GET /api/team/presets`（查询 5 套预设）、`PUT /api/team/presets/{teamId}/activate`（切换激活预设）。
- **战斗守卫**：战斗中禁止预设切换/编辑，通过 `battleStore.inBattle` 检测并禁用操作按钮。

---

## 16. Boss 页面约定（阶段 7 起）

### 16.1 Boss Store 与类型

- **类型定义**：`types/boss.ts`（`BossInfo`、`DifficultyInfo`、`DropTierInfo`、`BossBattleResult`、`AutoSummary`）。
- **Store**：`stores/boss.ts`（`useBossStore`），管理 Boss 列表、当前选中 Boss、战斗状态、自动挑战进度。
- **API 封装**：`api/boss.ts`，统一调用 `/api/bosses/**` 接口。

### 16.2 BossView 页面约定

- **BossView**（`/boss`）：Boss 列表卡片（名称/属性/推荐等级/击败次数/幸运值）+ 难度选择（普通/困难/噩梦，未解锁灰显 + 锁定图标）+ 掉落情报（分 4 档稀有度，未解锁显示 ???）+ 操作栏（挑战/自动挑战下拉/幸运兑换）+ 战斗结算汇总面板。
- **兼容路由参数**：旧 `/boss?bossId=xxx` 可用于直接访问/调试；正常地图入口通过 Boss Context 传递 ID。
- **战斗导航**：正常 Boss 战不得跳转 `/battle`；统一打开 Battle Context，结算后返回原 Boss 场或安全点。

### 16.3 BattleView Boss 战扩展

- **`uncapturable` 标记**：Boss 战斗时 BattleSnapshot 返回 `uncapturable: true`，前端禁用捕捉/逃跑按钮。
- **Boss 战 badge**：战斗页面显示 Boss 战标识（名称 + 难度）。
- **阶段转换提示**：显示 PHASE_TRANSITION 事件消息。

### 16.4 地图 Boss 入口

- **ExploreView** `boss:touch` 事件：从 GameBridge 接收 `boss:touch` 事件（携带 `id: bossId`），导航至 `/boss?bossId=xxx`（而非占位 toast）。

---

## 17. 图鉴页面约定（阶段 8 起）

### 17.1 类型与 Store

- **类型定义**：`types/pokedex.ts`（`PokedexEntry`、`PokedexDetail`、`PokedexHistory`、`WildIdentification`、`SkillInfo`、`PassiveInfo`），与后端 VO 对齐。
- **Store**：`stores/pokedex.ts`（`usePokedexStore`），管理 entries 全量列表、currentDetail 详情、filterLevel/filterElement 筛选条件。
- **计算属性**：`filteredEntries`（按研究等级 + 属性筛选）、`discoveredCount`、`caughtCount`、`fullyResearchedCount`、`totalCount`。
- **方法**：`loadPokedex()`、`loadDetail(speciesId)`、`identifyWild(speciesId, aptitudes)`。

### 17.2 PokedexView 页面约定

- **PokedexView**（`/pokedex`）：统计栏（发现 X/27、捕获 Y/27、完全研究 Z/27）+ 筛选栏（按研究等级 Lv.0~5 + 按属性）+ 宠物卡片网格 + 详情模态面板。
- **卡片样式**：未发现灰暗 + 问号（`card-unknown`）、已发现正常 + 边框（`card-seen`）、已捕获全彩 + 徽章（`card-caught`）。
- **详情面板**：研究等级进度条 + 按等级逐级展示已解锁信息（未解锁部分显示 `???`）；Lv.5 显示历史记录面板。
- **Lv.5 野外识别**：遭遇时调用 identify 接口，返回资质预估等级标签（S/A/B/C/D），在遭遇界面展示提示。

---

## 18. 任务页面与教学约定（阶段 9 起）

### 18.1 类型与 Store

- **类型定义**：`types/quest.ts`（`QuestSummary`、`QuestListView`、`ObjectiveInfo`、`RewardPreview`、`QuestDetail`、`QuestCompleteResult`、`DialogueView`、`TutorialStateView`、`ActiveQuestSummary`、`MapChangeView`）。
- **Store**：`stores/quest.ts`（`useQuestStore`），管理 `questList`、`currentDialogue`、`tutorialState`。
- **方法**：`loadQuests()`、`loadQuestDetail(questId)`、`acceptQuest()`、`completeQuest()`、`chooseReward()`、`talkNpc()`、`continueDialogue()`、`closeDialogue()`、`loadTutorial()`、`completeTutorialStep()`、`skipTutorial()`、`getActiveSummary()`、`getMapChanges()`。

### 18.2 任务页面布局

- **QuestView**（`/quest`）：三标签布局（主线/支线/已完成），左列表+右详情双栏。
- **QuestDetail** 组件：目标进度列表 + 奖励预览 + 三选一选择 + 赠送宠物预览 + 地图变更 + 操作按钮（接受/完成）。
- 任务状态标签：可接受（蓝）、进行中（橙）、已完成（绿）、未解锁（灰）。
- 隐藏任务未触发时不显示，已触发显示 `???` 名称。

### 18.3 NPC 对话框

- **DialogueBox** 组件：全局挂载在 `MainLayout`，由 `questStore.currentDialogue` 驱动显示。
- 逐字打字效果（30ms/字），点击跳过打字或继续下一段对话，对话结束自动关闭。
- ExploreView 通过 `npc:touch` bridge 事件调用 `questStore.talkNpc(npcId)` 触发对话。

### 18.4 新手教学浮层

- **TutorialOverlay** 组件：全局挂载在 `MainLayout`，由 `questStore.tutorialState` 驱动显示。
- 未全部完成时显示当前未完成步骤（名称 + 描述 + 完成/跳过按钮）。
- 捕捉教学完成时后端自动发放留生一击技能书。

### 18.5 首页主线摘要

- **HomeView** 新增「当前主线」卡片：任务名称 + 描述 + 当前目标进度条 + 「查看全部」链接跳转 `/quest`。

---

## 19. 商店/技能书/推荐Build/战斗加速约定（阶段 10 起）

### 19.1 商店页（Shop）

- **类型定义**：`types/shop.ts`（`ShopItemView`、`ShopView`、`BuyResult`）。
- **Store**：`stores/shop.ts`（`useShopStore`），方法 `loadShop()` / `buyItem(itemId, quantity)`，购买成功后同步本地金币。
- **ShopView**（`/shop`）：金币栏 + 商品卡片网格（名称/分类/描述/价格/数量加减/购买按钮）；未解锁商品置灰并显示解锁提示；购买成功/失败提示条。

### 19.2 宠物页技能书与推荐 Build

- `types/pet.ts` 的 `PetDetail` 新增 `bookSkillSlots`（书槽 5~6）/ `learnedBookSkills` / `bookSkillLearnCount`（/10）。
- PetView 技能标签新增「技能书主动技能」区：书槽装备/卸下、学习输入（道具 ID，满 10 时需选遗忘目标）、已学列表（装备到书槽/遗忘）。
- PetView 新增「推荐方案」标签页：`GET /api/pets/{petId}/build-recommendations` 懒加载（切入标签时才请求），展示加点优先级 + 推荐技能，纯展示不可操作。

### 19.3 探索页随机事件与精英标识

- ExploreView 进入区域（onMounted / 出口移动后）调用 `GET /api/maps/events/roll`，返回事件时弹出事件对话框（描述 + 选项按钮），选择后 `POST /api/maps/events/resolve` 展示结果；TRIGGER_BATTLE 结果自动跳转战斗页。
- 遭遇对话框支持精英标识（`WildTouchPayload.elite` 可选字段，金色提示条）。

### 19.4 战斗加速（BattleView）

- 战斗头部新增速度控制：1x / 2x / 3x 按钮 + 「自动」开关；自动模式按速度档位（1500/600/200ms）自动提交回合（未选行动宠物自动防御）。
- 战斗结束/离开页面时自动停止自动播放（`onBeforeUnmount` 清理定时器）。
- `UnitSnapshot` 新增 `elite` 字段，敌方卡片展示「✨精英」徽章。

---

## 20. 动态难度与等级压制展示约定（阶段 13）

- 设置页通过 `GET/PUT /api/game/difficulty` 读取和保存全局难度；前端只提交难度 ID，不计算野外等级、Boss 数值或等级上限。
- Boss 页通过 `GET /api/bosses/{bossId}/encounter-snapshot?difficulty=...` 展示已锁定遭遇；只有后端返回 `canReset=true` 时显示重置按钮，并在点击前要求用户明确确认。
- `UnitSnapshot.actualLevel/effectiveLevel` 仅用于展示。有效等级低于真实等级时统一展示为 `Lv.真实 → 有效`，不得在前端自行按比例重算 HP、属性、技能或自由点。
- Boss 快照为空时仅提示「首次挑战后固定」；全局难度与快照难度不一致时必须明确告知「本次仍按旧快照挑战」。

## 21. 枚举中文展示规范（阶段 15 起，全站强制）

- 所有从后端返回的枚举值（属性、稀有度、伤害类型、效果类型、技能类型、技能来源、六维属性等）在页面展示与下拉选项中**一律转换为中文**，禁止直接展示枚举原文（如 `FIRE`、`COMMON`、`PHYSICAL`）。
- 枚举 → 中文映射统一集中定义在 `frontend/src/utils/labels.ts`，提供 `elementLabel / rarityLabel / rarityColor / damageTypeLabel / effectTypeLabel / skillTypeLabel / sourceLabel / STAT_LABELS` 等函数与常量。
- **禁止在各页面内重复定义映射表**；页面仅负责从 `utils/labels.ts` 导入使用。已废弃的本地映射（各页面的 `elementLabels`、`rarityLabels`、`rarityColors`、`RARITY_NAMES` 等）一律删除，统一改用 `utils/labels.ts`。
- 下拉选项（如仓库筛选、加点维度）同样使用映射函数展示中文，但提交给后端的 value 仍为原始枚举值。
- 六维属性统一使用 `STAT_LABELS`（HP=生命、STRENGTH=力量、SPIRIT=灵力、DEFENSE=防御、RESISTANCE=抗性、SPEED=速度）。
- 新增枚举类型时，先在 `utils/labels.ts` 补充映射与函数，再在各页面使用；不得绕过该文件直接硬编码中文。
