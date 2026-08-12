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

当前阶段：**阶段 0（工程脚手架与构建部署流水线）— 已完成**

已完成阶段：阶段 0

详细的阶段划分与进度跟踪见 [`AGENTS.md`](AGENTS.md) §6「当前阶段状态」。

### 阶段 0 完成内容

- 前端 Vue 3 + TypeScript + Vite 骨架（11 个一级页面占位路由、API 封装层、全局样式、Vite 开发代理）
- 后端 Java 21 + Spring Boot 3.5.x 骨架（统一响应结构、全局异常处理、健康检查接口、Flyway 初始迁移、15 个业务模块占位）
- 统一构建脚本 `build.bat` / `build.sh`（前端 build → 静态资源复制 → Maven package → release 输出）
- 启动脚本 `start.bat` / `start.sh`
- 外部配置示例 `config-example/game/system.yml`
- 单元测试（ApiResponse 结构验证、HealthController 接口验证）

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
