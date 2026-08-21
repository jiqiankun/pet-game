# 项目结构（代码目录）

本文档介绍《宠物精灵》实际代码目录与各模块职责，面向开发者。

> 目录以当前仓库实际结构为准，仅说明主要模块，不罗列全部文件。

---

## 1. 顶层目录

```text
pet-game/
├── frontend/          # Vue 前端 + Phaser 游戏表现
├── backend/           # Spring Boot 后端业务
├── docs/              # 项目文档（需求 / 技术 / 架构 / 规划 / 规范 / 美术）
│   ├── requirements/  #   需求设计（唯一依据）
│   ├── technical/     #   技术方案与子系统设计
│   ├── architecture/  #   架构与代码目录说明
│   ├── planning/      #   开发阶段与规划
│   ├── development/   #   开发规范与状态
│   ├── guide/         #   安装运行与玩法指南
│   ├── prompts/       #   AI 开发提示词
│   └── art/           #   美术生产规范与资源
├── scripts/           # 构建/启动脚本 + E2E 验收脚本
├── config-example/    # 外部配置示例
├── AGENTS.md          # AI / 开发规范
└── README.md          # 项目首页
```

## 2. frontend/（Vue 前端）

```text
frontend/
└── src/
    ├── api/            # 后端 API 封装（按业务域）
    ├── assets/         # 静态资源与全局样式
    ├── game/           # Phaser 相关
    │   ├── PhaserGame.ts   # Phaser 游戏实例
    │   ├── mapSceneData.ts # MapEnterView → Phaser 场景载荷转换
    │   ├── bridge/         # Vue ↔ Phaser 事件桥（GameBridge）
    │   └── scenes/         # BootScene / MapScene
    ├── layouts/        # 页面布局（MainLayout）
    ├── router/         # 路由配置（Hash 模式）
    ├── stores/         # Pinia store（按业务域）
    ├── types/          # 公共 TypeScript 类型
    ├── views/          # 页面组件（按一级功能分目录）
    └── App.vue / main.ts
```

前端主要页面（`views/`）：

- `Home` 首页、`NewGame` 新游戏
- `Explore` 地图探索（含缓存的 `WorldRoot`）、`WorldMap` 大地图
- `Battle` 战斗、`Pet` 宠物详情、`Team` 队伍、`Storage` 仓库
- `Pokedex` 图鉴、`Boss` Boss、`Inventory` 背包、`Shop` 商店
- `Quest` 任务、`Achievement` 成就、`Statistics` 统计、`Settings` 设置

## 3. backend/（Spring Boot 后端）

```text
backend/
└── src/main/
    ├── java/com/petgame/      # 后端业务代码
    └── resources/
        ├── application.yml    # 应用配置（数据源 / Flyway / 端口）
        ├── db/migration/      # Flyway 迁移（V1~V13）
        ├── game-config/       # 游戏内容配置（YAML）
        └── static/            # 前端构建产物（构建时生成）
```

后端业务模块（`com.petgame.*`）：

- `common` 统一响应、异常处理、随机工具
- `config` Spring 配置、游戏配置加载 / 校验 / 注册中心
- `player` 玩家存档、新游戏、Bootstrap
- `pet` 宠物培养（成长 / 加点 / 技能 / 技能书 / 履历）
- `team` 队伍与预设
- `battle` 战斗引擎、计算器、敌方胜利互动
- `capture` 野生遭遇生成
- `map` 地图探索、随机事件
- `boss` Boss 系统、Boss 挑战目标
- `inventory` 背包
- `pokedex` 图鉴
- `quest` 任务、NPC 对话、新手教学
- `achievement` 成就
- `statistics` 玩家统计
- `completion` 游戏完成度
- `shop` 商店
- `storage` 宠物仓库、放生
- `save` 存档导入导出
- `developer` 开发者模式

## 4. docs/（项目文档）

```text
docs/
├── README.md                   # 文档总索引
├── requirements/               # 需求设计（权威来源）
│   ├── 宠物精灵游戏第一阶段需求设计文档 V1.0.md
│   ├── 宠物精灵游戏第一阶段UI设计文档 V1.0.md
│   └── 宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0.md
├── technical/                  # 技术方案与子系统设计
│   ├── 宠物精灵游戏第一阶段技术方案说明 V1.0.md
│   ├── AUTO_BATTLE_DESIGN.md
│   ├── BOSS_AI_REWORK.md
│   └── 动态难度、野外缩放、Boss自适应与等级压制系统方案.md
├── architecture/               # 架构与代码结构
│   ├── ARCHITECTURE.md
│   └── PROJECT_STRUCTURE.md    # 本文件
├── planning/                   # 开发阶段与规划
│   ├── 宠物精灵游戏分阶段开发规划 V1.0.md
│   ├── 宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md
│   ├── 前五阶段开发修订计划.md
│   └── passive-skill-expansion-plan.md
├── development/                # 开发规范与状态
│   ├── DEVELOPMENT_STATUS.md
│   ├── PHASE0_BASELINE.md
│   ├── PHASE1_WORLD_CONTEXT.md
│   ├── FRONTEND_STANDARDS.md
│   ├── BACKEND_STANDARDS.md
│   └── TESTING_STANDARDS.md
├── deployment/                 # 部署指南（唯一主要部署文档）
│   └── DEPLOYMENT.md
├── guide/                      # 快速开始与玩法指南
│   ├── QUICK_START.md
│   └── GAMEPLAY.md
├── prompts/                    # AI 开发提示词
│   └── 第十阶段：智能自动战斗策略开发提示词.md
└── art/                        # 美术生产规范、资源清单、批次 QC 与候选图
```

## 5. scripts/（构建/启动与 E2E 验收）

- `build.sh` / `build.bat`：Linux / Windows 统一构建
- `start.sh` / `start.bat`：Linux / Windows 启动
- `dev-hint.bat` 开发模式启动提示
- `phase0-baseline.ps1`：桌面重构阶段 0 的需求编号、内容数量、前端构建与后端测试基线复验
- `e2e/` 子目录：`e2e-battle-test.ps1` / `e2e-capture-test.ps1` / `e2e-map-test.ps1` / `e2e-boss-test.ps1` / `e2e-quest-test.ps1` / `e2e-phase10-test.ps1` / `e2e-phase14-test.ps1` 各阶段 E2E 验收脚本

阶段 0 新增的存档兼容测试样本位于 `backend/src/test/resources/save-fixtures/`，覆盖新游戏、中期进度和第一阶段完成三种 `saveVersion=1` 存档；它们只用于测试，不是运行时默认存档。阶段 1 的世界根、Context Stack 与运行态补验记录位于 `docs/development/PHASE1_WORLD_CONTEXT.md`。

## 6. config-example/（外部配置示例）

- 提供系统配置外部覆盖示例（`game/system.yml`、`game/system-rules.yml`），正式运行时复制到 `./config/game/` 使用。

---

> 各模块内部实现约定见 [BACKEND_STANDARDS.md](../development/BACKEND_STANDARDS.md) 与 [FRONTEND_STANDARDS.md](../development/FRONTEND_STANDARDS.md)。
