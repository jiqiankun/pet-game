# 宠物精灵

一款个人开发、个人部署、个人游玩的**轻量单机 Web 宠物收集养成游戏**。

> 宠物收集 + 自由培养 + 队伍构筑 + 回合制战斗 + 区域探索 + Boss 挑战。

核心设计原则：

> 系统可以有深度，但操作和实现不能过重。

---

## 项目简介

- **个人开发 / 个人部署 / 个人游玩**：单机取向，本机部署，无需账号与多用户体系。
- **轻量单机 Web 游戏**：浏览器打开即玩，正式运行只需 Java + MySQL。
- **宠物收集养成**：捕捉、培养、进化前的完整养成闭环。
- **回合制战斗**：统一战斗引擎，支持手动 / 自动 / 普通 / 精英 / Boss 多种战斗。
- **地图探索**：基于 Phaser + Tiled 的 2D 地图区域探索与随机遭遇。
- **Boss 挑战**：多难度 Boss、控制抗性、阶段机制、自动挑战。

## 游戏特色

- **27 种基础宠物**：9 属性 × 3，稀有度横跨普通到传说。
- **自由属性培养**：公共经验池 + 五种升级方式 + 自由加点 / 洗点。
- **技能体系**：自身主动 / 被动 + 技能书主动 / 被动，冷却驱动、无 MP。
- **队伍构筑**：6 宠携带、3 宠上场、5 套预设。
- **地图探索**：区域解锁、营地传送、采集、宝箱、随机事件、隐藏遭遇。
- **Boss 多难度挑战**：普通 / 困难 / 噩梦，阶段机制与掉落情报。
- **动态挑战强度**：四档全局难度、地图边界内的野外有限缩放，以及可锁定重试的 Boss 遭遇。
- **图鉴研究**：逐级解锁信息，历史记录，野外识别。
- **成就与完成度**：成就、玩家统计、Boss 挑战目标、游戏完成度、宠物履历。

## 核心玩法

```text
区域探索
  ↓
野生遭遇
  ↓
战斗 / 捕捉
  ↓
宠物培养
  ↓
队伍构筑
  ↓
Boss 挑战
  ↓
解锁新区域
  ↓
图鉴 / 任务 / 成就 / 完成度
```

## 游戏内容

> 以下为**当前实际配置规模**（以 `backend/src/main/resources/game-config/` 为准）。

| 内容 | 当前规模 |
|---|---|
| 基础宠物 | 27 种（9 属性 × 3） |
| 区域 | 5 区域 + 1 据点 |
| 主 Boss | 5 |
| 隐藏 / 精英 Boss | 3 |
| 任务 | 25（主线 12 + 支线 10 + 隐藏 3） |
| 技能 | 136（主动 85 + 被动 51，其中 41 固有/升级被动（含 27 宠核心特色被动）+ 10 技能书被动） |
| 状态 | 24 |
| 道具 | 45 |
| 成就 | 33 |

> 完整玩法与数值规则以《宠物精灵游戏第一阶段需求设计文档 V1.0.md》为准。

## 游戏截图

> 正式游戏美术资源已接入；后续在阶段 14 总验收中补充游戏内实机截图。

## 技术栈

### 前端

- Vue 3 + TypeScript + Vite
- Pinia（状态管理）+ Vue Router（Hash 模式）
- Phaser 4.2.1（2D 表现）+ Tiled（JSON 地图）

### 后端

- Java 21 + Spring Boot 3.5.x
- MyBatis-Plus（ORM）
- Flyway（数据库迁移）

### 数据

- MySQL 8.4 LTS
- YAML 游戏内容配置（JAR 内默认 + 外部可覆盖）

### 部署

```text
frontend build
      ↓
Spring Boot static
      ↓
pet-game.jar
```

正式运行只需 **Java 21 + MySQL 8.4 + pet-game.jar**。

## 项目结构

```text
pet-game/
├── frontend/          # Vue 前端与 Phaser 游戏表现
├── backend/           # Spring Boot 后端业务
├── docs/              # 项目文档
│   ├── requirements/  #   需求设计（唯一依据）
│   ├── technical/     #   技术方案与子系统设计
│   ├── architecture/  #   架构与代码目录说明
│   ├── planning/      #   开发阶段与规划
│   ├── development/   #   开发规范与状态
│   ├── deployment/    #   部署指南（唯一主要部署文档）
│   ├── guide/         #   快速开始与玩法指南
│   ├── prompts/       #   AI 开发提示词
│   └── art/           #   美术生产规范与资源
├── scripts/           # 构建/启动脚本 + E2E 验收脚本
│   ├── build.sh / build.bat / start.sh / start.bat / dev-hint.bat
│   └── e2e/           #   E2E 验收脚本
├── config-example/    # 外部配置示例
├── AGENTS.md          # AI / 开发规范（开发前必读）
└── README.md
```

完整目录职责与模块说明见 [docs/architecture/PROJECT_STRUCTURE.md](docs/architecture/PROJECT_STRUCTURE.md)，文档总索引见 [docs/README.md](docs/README.md)。

## 部署与运行

> 完整的环境准备、数据库初始化、构建发布、Windows 注意事项、故障排查与部署前检查清单，统一见 [部署指南](docs/deployment/DEPLOYMENT.md)（唯一主要部署文档）；最简启动见 [快速开始](docs/guide/QUICK_START.md)。

**开发模式**（需要 Java 21 + MySQL 8.4 + Node 18+）：

```bash
# 1. 准备 MySQL 8.4，创建数据库 pet_game（utf8mb4）
# 2. 启动后端（默认 127.0.0.1:8080，Flyway 自动建表）
cd backend && mvn spring-boot:run
# 3. 另开终端，前端开发模式
cd frontend && npm install && npm run dev   # http://localhost:5173，代理 /api → :8080
```

**正式运行**（只需 Java 21 + MySQL 8.4 + pet-game.jar）：

```bash
scripts/build.bat    # Windows（构建：前端 build → 复制静态资源 → Maven package → release）
scripts/build.sh     # Linux / macOS

java -jar release/pet-game.jar              # 访问 http://localhost:8080
# 或 scripts/start.bat / scripts/start.sh
```

## 开发进度

当前阶段：**阶段 14（存档备份、开发者工具、兼容性与总验收）— 已完成** ✅

第一阶段全部 15 个阶段（阶段 0 ~ 阶段 14）已完成。

已完成内容：存档备份 / 恢复 + 开发者工具 + 战斗调试 / 随机数调试 + 新手教学完善 + 内容补齐（85 主动 + 51 被动 + 27 宠技能映射）+ 被动技能体系结构性整合 + 27 宠核心特色被动 + 数值平衡初调 + 美术资源 Batch 0～8 + **美术资源接入与战斗表现回归**（战斗快照展示标识、战斗立绘与特效修复、补齐 10 张技能书图标、首页 / 队伍 / 新游戏 / 宠物详情复用宠物资源）+ 响应式兼容适配（全局 CSS + 13 页面 @media 768px）+ 前端战斗调试信息展示面板 + E2E 九大核心场景验收脚本。后端测试全绿，前端类型检查与生产构建通过。

已完成：阶段 0 ~ 阶段 13。各阶段进度、遗留问题与已完成内容详见 [docs/development/DEVELOPMENT_STATUS.md](docs/development/DEVELOPMENT_STATUS.md)。

## 文档导航

> 完整文档索引见 [docs/README.md](docs/README.md)。

### 项目设计（权威来源）

- [《宠物精灵游戏第一阶段需求设计文档 V1.0》](docs/requirements/宠物精灵游戏第一阶段需求设计文档 V1.0.md) — 玩法规则、数值规则、内容规模的唯一依据
- [《宠物精灵游戏第一阶段技术方案说明 V1.0》](docs/technical/宠物精灵游戏第一阶段技术方案说明 V1.0.md) — 技术栈、架构边界、部署方式、数据方案
- [《宠物精灵游戏分阶段开发规划 V1.0》](docs/planning/宠物精灵游戏分阶段开发规划 V1.0.md) — 开发顺序、阶段范围、验收标准
- [AI / 开发规范](AGENTS.md) — 开发前必读

### 玩家指南

- [部署指南](docs/deployment/DEPLOYMENT.md) — 唯一主要部署文档：环境准备、数据库初始化、构建发布、故障排查
- [快速开始](docs/guide/QUICK_START.md) — 最简启动路径
- [游戏玩法说明](docs/guide/GAMEPLAY.md) — 怎么玩

### 架构与结构

- [系统架构](docs/architecture/ARCHITECTURE.md) — 当前系统实际结构
- [项目结构](docs/architecture/PROJECT_STRUCTURE.md) — 代码目录与模块职责

### 开发与测试

- [前端开发规范](docs/development/FRONTEND_STANDARDS.md)
- [后端开发规范](docs/development/BACKEND_STANDARDS.md)
- [测试规约](docs/development/TESTING_STANDARDS.md)
- [开发状态](docs/development/DEVELOPMENT_STATUS.md)
- [动态难度、野外缩放、Boss 自适应与等级压制方案](docs/technical/动态难度、野外缩放、Boss自适应与等级压制系统方案.md)

## 开发说明

- 开发前必须阅读 [AGENTS.md](AGENTS.md) 与上述权威文档。
- 前端代码怎么写见 [docs/development/FRONTEND_STANDARDS.md](docs/development/FRONTEND_STANDARDS.md)。
- 后端代码怎么写见 [docs/development/BACKEND_STANDARDS.md](docs/development/BACKEND_STANDARDS.md)。
- 怎么测试与验收见 [docs/development/TESTING_STANDARDS.md](docs/development/TESTING_STANDARDS.md)。
