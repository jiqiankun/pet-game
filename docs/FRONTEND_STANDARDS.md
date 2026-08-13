# 前端编码规范

**适用项目：** 宠物精灵游戏第一阶段  
**依据：** 《宠物精灵游戏第一阶段技术方案说明 V1.0》《宠物精灵游戏第一阶段 UI 设计文档 V1.0》

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
- **路由命名**：与一级功能对应（`/`首页、`/explore`探索、`/pets`宠物、`/team`队伍、`/pokedex`图鉴、`/inventory`背包、`/quest`任务、`/boss`Boss、`/achievement`成就、`/statistics`统计、`/settings`设置）。
- **路由守卫**：无存档时自动进入新游戏流程；有存档时恢复上次状态。

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

## 9. 样式与响应式规范

- **响应式优先级**：PC 网页优先，兼容平板与手机。
- **移动端适配**：底部导航、弹层、折叠信息；核心玩法全部可用。
- **色彩体系**：严格遵循《宠物精灵游戏第一阶段 UI 设计文档》定义的色彩体系（品牌色、功能色、属性色、稀有度色、背景色、文字色）。
- **资源命名**：统一使用 ID，禁止中文文件名。示例：`pets/fire/PET_FIRE_001.png`、`skills/fire/FIRE_BLAZE_CLAW.png`。
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
- **前端只提交行动意图**（SKILL/DEFEND/SWITCH + 目标），伤害/暴击/克制等结果一律以快照中的事件为准；未选行动的宠物由后端默认防御。
- 事件展示：后端返回事件序列（`BattleEventType`），Store 负责把事件翻译为中文日志，后续接入 Phaser 表现时仍沿用「后端事件 → 前端播放」模型。
- 当前 `/battle` 为 Vue 基础战斗页面（阶段 3 范围）；Phaser BattleScene 表现层在后续阶段接入，不在本页面内实现动画计算。
- 技能名称等展示内容通过配置查询接口（`/api/game/config/skills`）获取，不在前端硬编码内容配置。
