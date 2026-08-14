# 项目结构（代码目录）

本文档介绍《宠物精灵》实际代码目录与各模块职责，面向开发者。

> 目录以当前仓库实际结构为准，仅说明主要模块，不罗列全部文件。

---

## 1. 顶层目录

```text
pet-game/
├── frontend/          # Vue 前端 + Phaser 游戏表现
├── backend/           # Spring Boot 后端业务
├── docs/              # 项目文档（玩法 / 架构 / 规范 / 美术）
├── plans/             # 开发计划与设计草稿
├── scripts/           # 构建辅助与 E2E 验收脚本
├── config-example/    # 外部配置示例
├── AGENTS.md          # AI / 开发规范
├── README.md          # 项目首页
├── build.bat          # Windows 统一构建
└── build.sh           # Linux / macOS 统一构建
```

## 2. frontend/（Vue 前端）

```text
frontend/
└── src/
    ├── api/            # 后端 API 封装（按业务域）
    ├── assets/         # 静态资源与全局样式
    ├── game/           # Phaser 相关
    │   ├── PhaserGame.ts   # Phaser 游戏实例
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
- `Explore` 地图探索、`WorldMap` 大地图
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
        ├── db/migration/      # Flyway 迁移（V1~V10）
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
├── QUICK_START.md          # 安装与运行
├── GAMEPLAY.md             # 玩家玩法说明
├── ARCHITECTURE.md         # 当前系统架构
├── PROJECT_STRUCTURE.md    # 本文件
├── DEVELOPMENT_STATUS.md   # 开发状态
├── FRONTEND_STANDARDS.md   # 前端编码规范
├── BACKEND_STANDARDS.md    # 后端编码规范
├── TESTING_STANDARDS.md    # 测试规约
├── AUTO_BATTLE_DESIGN.md   # 自动战斗策略设计
├── BOSS_AI_REWORK.md       # Boss AI 设计
└── art/                    # 美术生产规范、资源清单、批次 QC 与候选图
```

## 5. plans/（开发计划）

- `前五阶段开发修订计划.md`
- `第十阶段：智能自动战斗策略开发提示词.md`
- `《宠物精灵》AI 美术资源批量生成提示词.md`
- `项目文档体系重构方案.md`

## 6. scripts/（构建辅助与验收）

- `dev-hint.bat` 开发模式启动提示
- `e2e-battle-test.ps1` / `e2e-capture-test.ps1` / `e2e-map-test.ps1` / `e2e-boss-test.ps1` / `e2e-quest-test.ps1` / `e2e-phase10-test.ps1` 各阶段 E2E 验收脚本

## 7. config-example/（外部配置示例）

- 提供系统配置外部覆盖示例（`game/system.yml`、`game/system-rules.yml`），正式运行时复制到 `./config/game/` 使用。

---

> 各模块内部实现约定见 [BACKEND_STANDARDS.md](BACKEND_STANDARDS.md) 与 [FRONTEND_STANDARDS.md](FRONTEND_STANDARDS.md)。
