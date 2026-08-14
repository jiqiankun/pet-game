# 系统架构（当前实现）

本文档描述《宠物精灵》**当前代码实际是怎么工作的**，面向开发者。

> 技术栈选型、架构边界与部署方式的**理由**与权威决策以《宠物精灵游戏第一阶段技术方案说明 V1.0.md》为准；本文档描述现状，不重复论证。

---

## 1. 总体架构

```text
浏览器
  │
  └── Vue 3 SPA（Hash 路由）
       │
       ├── Vue 业务页面 ──┐
       │                 │ GameBridge 事件桥
       ├── Phaser 游戏场景┘
       │
       │ REST JSON /api/**
       ▼
  Spring Boot（单体模块化）
       │
       ├── Controller / Service（业务规则）
       ├── BattleEngine（统一战斗计算）
       ├── GameConfig（YAML 配置驱动）
       └── MyBatis-Plus → MySQL 8.4（玩家存档）
```

核心一句话：

> Vue 负责页面，Phaser 负责游戏表现，Spring Boot 负责业务规则，BattleEngine 负责全部战斗计算，MySQL 只存玩家存档，YAML 只存游戏内容配置。

## 2. 浏览器 / Vue / Spring Boot / MySQL 关系

- **浏览器**加载单个 SPA（Hash 路由，便于单 JAR 部署）。
- **Vue** 渲染页面并调用后端 REST 接口；前端只提交**行动意图**，不提交计算结果。
- **Spring Boot** 承载全部业务规则，向前端返回统一响应结构 `{ success, data, message, code }`。
- **MySQL** 只存储玩家存档数据，不存储游戏内容配置。

## 3. Vue 与 Phaser 职责划分

- **Vue**：页面、状态管理（Pinia）、路由、API 调用、业务交互。
- **Phaser**：2D 地图渲染、玩家移动、野怪简单行为、地图对象交互。
- 两者通过 `GameBridge` 事件桥单向通信：Phaser 发送事件 → Vue 处理 → 回传结果。
- **Phaser 不是业务系统**：宠物数据、图鉴、背包、任务、存档、战斗公式一律不得写入 Phaser 场景。

## 4. Spring Boot 模块关系

后端为单体模块化，按业务域分包（`com.petgame.*`）：common、config、player、pet、team、battle、capture、map、boss、inventory、pokedex、quest、achievement、statistics、completion、shop、storage、save、developer。

典型分层：`Controller（HTTP 适配）→ Service（业务编排）→ Domain（领域规则）/ Mapper（数据访问）→ Entity（数据库实体）`。

## 5. BattleEngine 核心定位

- **全部战斗共用同一个 BattleEngine**，手动 / 自动 / 普通 / 精英 / Boss 唯一差异是「谁决定行动」（DecisionProvider）。
- 战斗临时数据只存服务器内存；战斗结束统一结算落库；服务重启后未完成战斗直接丢弃。
- 伤害、命中、暴击、捕捉、状态等计算由 `battle/calculator/` 下的纯函数计算器完成。

## 6. REST 调用关系

- 统一前缀 `/api/**`，开发者接口 `/api/dev/**`。
- 前端经 `src/api/` 封装调用，统一处理响应结构。
- 关键接口示例：Bootstrap（`/api/game/bootstrap`）、战斗（`/api/battles/**`）、野生（`/api/wild/battles`）、地图（`/api/map/**`）、Boss（`/api/bosses/**`）、商店（`/api/shop`）、图鉴（`/api/pokedex/**`）、任务（`/api/quests/**`）。

## 7. YAML 游戏配置体系

- 内容配置位于 `backend/src/main/resources/game-config/`（宠物、技能、状态、Boss、道具、地图、遭遇、任务、成就、商店、随机事件、推荐 Build、敌方胜利互动等）。
- 系统规则配置 `system.yml`（克制倍率、暴击、等级上限、加点、图鉴、精英、Boss AI 等）。
- 加载：`GameConfigLoader` 加载内部默认 + 外部 `config-dir` 同 ID 覆盖 → `GameConfigValidator` 启动校验（ID 重复 / 引用缺失 / 非法数值则启动失败）→ `GameConfigRegistry` 提供运行时查询。
- 配置不做热更新，修改需重启。

## 8. MySQL 玩家存档体系

- 玩家数据只存**引用**（species_id、skill_id、map_id、boss_id 等），不复制配置内容。
- 表结构由 Flyway 迁移管理（`V1`~`V10`），禁止手工改表。
- 主要数据域：玩家、宠物、技能学习、队伍、背包、地图进度、Boss 进度、图鉴、任务、成就、统计、挑战等。

## 9. 战斗生命周期

1. 开战（`/api/battles` / 野生 / Boss）→ 创建 `BattleContext` 存内存。
2. 回合循环：前端提交行动 → 引擎按速度排序 → 计算事件序列 → 返回快照。
3. 战斗结束（胜利 / 战败 / 逃跑 / 捕捉）。
4. 结算（`settle`）同事务落库：HP 回写、经验、金币、掉落、图鉴、统计、任务、成就等。
5. 战败零惩罚，返回最近营地并恢复全队。

## 10. 地图与 Phaser 集成方式

- 地图配置（区域 / 出口 / 营地 / 采集 / 宝箱）由 `maps.yml` 承载；表现层由前端 Tiled JSON 承载。
- 后端 `MapExplorationService` 权威处理区域解锁、移动、营地、采集、宝箱、遭遇与战败。
- Phaser 通过 `GameBridge` 上报交互事件（encounter / exit / camp / gather / chest），Vue 再调用后端。

## 11. 配置加载流程

```text
启动
  → GameConfigLoader 读取内部 game-config + 外部 config-dir 覆盖
  → GameConfigValidator 启动校验（失败则启动失败）
  → GameConfigRegistry 建立索引
  → 业务 Service 通过 Registry 读取配置
```

## 12. 数据持久化流程

- 战斗等临时数据存内存，不落库。
- 结算、捕捉、放生、兑换、任务完成等关键流程在**单事务**内完成，防止部分成功脏数据。
- 统计、成就、图鉴等旁路记录使用 `REQUIRES_NEW` 传播，失败仅告警，不阻断主流程。

## 13. 前后端边界

- **前端**提交意图（如「使用技能 X 攻击目标 Y」、捕捉、逃跑），展示后端返回的快照与事件。
- **后端**计算一切结果（伤害、金币、经验、捕捉结果）。
- 前端不得提交计算结果；后端校验所有参数，不信任前端传入数值。

---

> 更详细的模块职责见 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)；前后端编码约束见 docs/development/FRONTEND_STANDARDS.md / docs/development/BACKEND_STANDARDS.md。