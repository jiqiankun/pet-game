# 宠物精灵游戏

个人开发、个人部署、个人游玩的轻量单机 Web 宠物收集养成游戏。

**核心玩法：** 宠物收集 + 自由培养 + 队伍构筑 + 回合制战斗 + 区域探索 + Boss 挑战。

> 系统可以有深度，但操作和实现不能过重。

---

## 第一阶段内容规模

| 内容 | 规模 |
|---|---|
| 基础宠物 | 27 种（9 属性 × 3） |
| 主要区域 | 5 + 1 据点 |
| 主要 Boss | 5 + 2～3 隐藏/精英 |
| 主线节点 | 10～15 |
| 支线 | 8～12 |
| 成就 | 30～50 |
| 技能配置 | 约 80～100 |
| 道具 | 约 40～60 |

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Vue Router（Hash 模式） |
| 2D 表现 | Phaser + Tiled（JSON 地图） |
| 后端 | Java 21 + Spring Boot 3.5.x |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.4 LTS |
| 数据迁移 | Flyway |
| 接口 | REST JSON（`/api/**`） |
| 部署 | 单个可执行 `pet-game.jar` |

---

## 快速开始

### 环境要求

- Java 21
- MySQL 8.4 LTS
- Node.js（仅开发构建时需要）
- Maven（仅开发构建时需要）

### 开发模式

1. 启动 MySQL 本地实例。
2. 启动后端（Spring Boot，端口 8080）。
3. 进入 `frontend/` 目录，执行 `npm run dev`（Vite 开发服务器，代理 `/api` → `localhost:8080`）。

### 正式构建与运行

执行统一构建脚本（`build.bat` / `build.sh`），自动完成：

```text
前端 build → 清理并复制静态资源 → Maven package → 输出 release 目录
```

运行：

```bash
java -jar pet-game.jar
```

访问 `http://localhost:8080`。

正式运行只需 **Java 21 + MySQL 8.4 + pet-game.jar**，玩家环境不需要 Node。

---

## 项目结构

```text
pet-game/
├── frontend/           # Vue 3 前端工程
├── backend/            # Spring Boot 后端工程
├── docs/               # 编码规范与测试规约
├── scripts/            # 构建脚本
├── config-example/     # 外部配置示例
├── AGENTS.md           # 项目开发规范（AI 协作约束）
└── README.md           # 本文件
```

---

## 项目文档

### 核心文档

| 文档 | 说明 |
|---|---|
| `宠物精灵游戏第一阶段需求设计文档 V1.0.md` | 玩法规则、数值规则、内容规模的唯一依据 |
| `宠物精灵游戏第一阶段技术方案说明 V1.0.md` | 技术栈、架构边界、部署方式、数据方案的唯一依据 |
| `宠物精灵游戏分阶段开发规划 V1.0.md` | 开发顺序、阶段范围、验收标准、阶段约束的唯一依据 |
| `宠物精灵游戏第一阶段 UI 设计文档 V1.0.md` | 视觉风格、色彩体系、页面布局、响应式规范 |

### 开发规范（`docs/` 目录）

| 文档 | 说明 |
|---|---|
| [`docs/FRONTEND_STANDARDS.md`](docs/FRONTEND_STANDARDS.md) | 前端编码规范：Vue 组件、TypeScript、Pinia、Phaser 边界、路由、样式 |
| [`docs/BACKEND_STANDARDS.md`](docs/BACKEND_STANDARDS.md) | 后端编码规范：模块结构、分层职责、API、配置驱动、事务、安全边界 |
| [`docs/TESTING_STANDARDS.md`](docs/TESTING_STANDARDS.md) | 测试规约：单元测试重点、集成测试、回归规则、阶段验收规则 |

### 项目约束

| 文档 | 说明 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | 项目级开发规范与 AI 协作约束（**开发前必读**） |

---

## 开发进度

当前阶段：**阶段 5（捕捉与宠物仓库管理）— 进行中**

已完成阶段：阶段 0、阶段 1、阶段 2、阶段 3、阶段 4

详细的阶段划分与进度跟踪见 [`AGENTS.md`](AGENTS.md) §6「当前阶段状态」。

### 阶段 0 完成内容

- 前端 Vue 3 + TypeScript + Vite 骨架（11 个一级页面占位路由、API 封装层、全局样式、Vite 开发代理）
- 后端 Java 21 + Spring Boot 3.5.x 骨架（统一响应结构、全局异常处理、健康检查接口、Flyway 初始迁移、15 个业务模块占位）
- 统一构建脚本 `build.bat` / `build.sh`（前端 build → 静态资源复制 → Maven package → release 输出）
- 启动脚本 `start.bat` / `start.sh`
- 外部配置示例 `config-example/game/system.yml`
- 单元测试（ApiResponse 结构验证、HealthController 接口验证）

### 阶段 1 完成内容

- 游戏配置体系：9 种属性定义与克制关系（五行环 + 副链 + 光暗互克）、系统规则配置（克制倍率、暴击参数、等级上限、队伍数量、资质范围等）
- 配置加载机制：JAR 内部默认配置 + 外部配置目录同 ID 覆盖（`GameConfigLoader`）
- 启动校验：ID 重复、引用不存在、非法概率/倍率/等级等严重错误时启动失败（`GameConfigValidator`）
- 统一配置注册中心：`GameConfigRegistry` 提供克制倍率查询、属性索引等运行时能力
- 配置查询 API：`/api/game/config/elements`、`/api/game/config/advantage`、`/api/game/config/system`
- 统一随机工具 `GameRandom`（支持固定种子，可复现完整随机流程）
- 配置目录骨架：`resources/game-config/`（system.yml、elements.yml + pets/skills/bosses/items/drops/shops/quests 子目录）
- 单元测试（GameRandom 种子可复现/范围/边界、配置校验 8 种错误场景、克制倍率查询 10 种关系）

### 阶段 2 完成内容

- 玩家存档数据模型：player / player_pet / player_pet_skill / player_team / player_team_member / player_inventory / game_setting 七张表（Flyway V2 迁移）
- 新游戏流程：名称 + 预设形象 + 初始宠物三选一（烬牙兽/汐月灵/藤梦鹿），配置驱动，初始宠物资质全 A
- Bootstrap 聚合接口 `GET /api/game/bootstrap`：一次返回首页所需核心状态
- 存档状态检查 `GET /api/game/save-status`、新游戏创建 `POST /api/game/new-game`、手动保存 `POST /api/game/save`
- 初始宠物配置 `initial-pets.yml`（含初始金币、经验池、地图 ID）
- 前端新游戏页面（名称输入 + 形象选择 + 宠物三选一 + 创建流程）
- 前端首页骨架（玩家状态卡片 + 当前队伍 + 快捷操作）
- 前端游戏 Store（存档状态、Bootstrap 加载、新游戏创建、手动保存）
- 自动跳转：无存档时自动进入新游戏流程
- 单元测试（初始宠物配置校验 7 种场景）

### 阶段 3 完成内容

- 战斗引擎领域模块 `com.petgame.battle`：BattleEngine 统一入口，手动/自动战斗共用同一引擎，差异仅在 DecisionProvider（敌方由 WildEnemyDecisionProvider 决策）
- 伤害结算链路：基础值+属性成长 → 防御/抗性减伤（200/(200+def)）→ 属性克制 ×1.50/×0.75 → 本属性技能 ×1.20 → 状态/Buff 修正 → 暴击 ×1.4~2.0 → 最低 1 点伤害保底
- 技能配置模型与 `skills.yml`（18 个技能：伤害/治疗/蓄力/AOE/施加状态，冷却驱动、无 MP）
- 状态体系 `statuses.yml`（14 个状态：DOT/控制/减益/增益四类 + 状态联动规则配置化）
- 被动技能框架（9 种触发时机 + 5 种效果类型，含不屈/入场增益等，配置驱动不写死 ID）
- 战斗行动与事件模型：SKILL/DEFEND/SWITCH 行动意图 + 23 种事件类型的完整回合事件流
- 回合流程：速度决定行动顺序（每回合重算）、蓄力/控制/防御处理、倒下补位、胜负判定、回合结束 DOT/持续递减/冷却递减
- 战斗服务与 REST 接口：POST `/api/battles`（测试战斗，支持固定种子）、GET `/api/battles/{id}`、POST `/api/battles/{id}/actions`；战斗临时数据只存服务器内存，零落库
- 前端基础战斗页面 `/battle`：单位卡片/HP 条/状态标签/技能按钮（含冷却）/目标选择/换宠/中文事件日志/种子输入
- 配置校验扩展：技能/状态/被动/测试战斗配置启动全量校验（引用完整性、枚举合法性）
- 修复阶段 2 遗留缺陷：新增 MyBatis-Plus MetaObjectHandler 自动填充 createdAt/updatedAt
- 单元测试 57 个（含 BattleEngine 18 场景：克制/暴击/治疗/行动顺序/冷却/蓄力/AOE/换宠/补位/防御/沉默/DOT/嘲讽/不屈/胜负判定/种子复现）+ E2E 验收脚本 `scripts/e2e-battle-test.ps1`

### 阶段 4 完成内容

- **战斗结算接入持久化**：`POST /api/battles/{id}/settle` 同事务落库 HP 回写、经验/金币/掉落发放、战斗统计累加；防重复结算（`BATTLE_ALREADY_SETTLED`）
- **公共经验池**：经验统一进玩家公共池（无上限），玩家自主分配到宠物；升级不直接发经验给宠物
- **五种升级方式**：升 1 级 / 升 5 级 / 升到指定等级 / 升到上限（Lv.50）/ 自定义投入经验；升级预览接口返回属性变化与解锁技能
- **等级成长公式**：最终属性 = 种族基础（含个体浮动） + 等级固定成长 + 资质成长修正 + 自由属性点；资质影响升级成长（非即时加属性）
- **自由属性点**：每级 3 点 + 稀有度每 10 级额外（RARE +2/EPIC +4/LEGENDARY +6）；加点转换表（HP +5/次、力量等 +1/次、速度 +1/次但消耗 2 点）；洗点第一阶段免费、全量返还
- **技能等级解锁与装配**：升级自动学习解锁技能（默认不装备）；最多 4 个主动技能槽位（1~4），装备/卸下接口
- **宠物详情页**：基础/属性/技能三标签；属性分解表（基础/成长/资质/加点/合计）；升级预览、加点、洗点、技能装配 UI
- **队伍系统**：6 宠携带（3 首发 + 3 候补），位置 1~6 唯一；整体替换在单事务内完成；`PUT /api/team/members`
- **背包系统**：不限容量、按分类组织（捕捉/恢复/材料/技能书/重要物品）；恢复道具（HEAL_HP/REVIVE）仅在战斗外使用（战斗内不使用，用户裁决，见规划文档 §9.3 决策八）
- **前端养成闭环**：宠物详情页、队伍编辑页、背包页、战斗结算结果展示（经验/金币/掉落/HP 回写）
- **Flyway V3 迁移**：`player_pet` 表新增 `captured_level` + 六维 `base_*_offset`（个体基础浮动固化值）
- **道具配置** `items.yml`：3 个恢复道具（小型恢复药/中型恢复药/复苏药剂），含 category、usableOutsideBattle、usableInBattle 字段
- **系统规则扩展**：经验公式（expBase=100、expGrowthFactor=1.15）、加点成本转换表、稀有度额外点数、baseStatVariance=0.05
- 单元测试 155 个（含 PetGrowthService 17 场景、PetService 28 场景、TeamService 16 场景、InventoryService 20 场景、BattleServiceSettlement 15 场景）
- 验收修复 4 处：队伍首发标记改用位置 1~3 判定；倒下宠物 0HP 参战不再强制 1HP（含开局补位/判负）；自由点数按转换表消耗计算（速度加点扣 2 点）；升级预览非法参数返回业务错误而非 500

---

## 架构概要

```text
Vue 3 SPA ─────────────┐
│                       │
├─ Vue 业务页面        ├─ Phaser 游戏场景
│  宠物/队伍/图鉴       │  地图/战斗/动画
│  Boss/任务/背包       │  角色移动/特效
└───────────┬───────────┘
            │ REST API
            ▼
     Spring Boot
     ├─ Controller / Service
     ├─ BattleEngine（统一战斗计算）
     ├─ Game Config（YAML 配置驱动）
     └─ MyBatis-Plus → MySQL 8.4
```

> Vue 负责页面，Phaser 负责游戏表现，Spring Boot 负责业务规则，BattleEngine 负责全部战斗计算，MySQL 只存玩家存档，YAML 只存游戏内容配置。
