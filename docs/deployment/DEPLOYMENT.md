# 宠物精灵游戏 — 部署指南

本指南是《宠物精灵》项目的**唯一主要部署文档**，负责从零到一的环境准备、启动、访问验证、构建发布与故障排查。

> 目标读者：**第一次接触本项目**的开发者或玩家。仅阅读本文档即可完成本机的安装、启动、访问与验证。
>
> - 安装运行相关的权威架构决策以《宠物精灵游戏第一阶段技术方案说明 V1.0.md》为准。
> - 已具备环境的用户可直接跳转 [快速启动](#快速启动)。
> - 本文档内容均来自项目当前真实配置（`pom.xml`、`package.json`、`vite.config.ts`、`application.yml`、`scripts/`、`config-example/` 等），如与实现有出入，以代码为准并请同步修正本文档。

---

## 目录

- [1. 部署说明](#1-部署说明)
- [2. 快速启动](#2-快速启动)
- [3. 环境要求](#3-环境要求)
- [4. 项目获取与目录说明](#4-项目获取与目录说明)
- [5. 配置文件说明](#5-配置文件说明)
- [6. 数据库准备](#6-数据库准备)
- [7. 依赖安装](#7-依赖安装)
- [8. 开发环境启动（完整流程）](#8-开发环境启动完整流程)
- [9. 启动成功判断](#9-启动成功判断)
- [10. 系统访问地址](#10-系统访问地址)
- [11. 正式构建与 JAR 运行](#11-正式构建与-jar-运行)
- [12. 停止服务](#12-停止服务)
- [13. Docker 部署说明](#13-docker-部署说明)
- [14. Windows 部署注意事项](#14-windows-部署注意事项)
- [15. 常见问题与故障排查](#15-常见问题与故障排查)
- [16. 数据与资源持久化](#16-数据与资源持久化)
- [17. 更新部署](#17-更新部署)
- [18. 部署前检查清单](#18-部署前检查清单)

---

## 1. 部署说明

### 1.1 部署方式

项目采用 **Spring Boot 单可执行 JAR** 部署：前端构建产物（`frontend/dist`）在构建时复制进后端静态资源目录（`backend/src/main/resources/static/`）并打入 JAR，最终只运行一个进程：

```text
浏览器
   ↓
pet-game.jar（Spring Boot）
   ├── 前端静态资源（Vue SPA + Phaser 游戏资源）
   ├── REST API（/api/**）
   ├── 游戏业务与战斗计算
   └── 配置加载（YAML）
   ↓
MySQL（玩家存档）
```

正式运行**不需要** Node.js / npm / Vite / Maven，只需 **Java 21 + MySQL 8.4 + pet-game.jar**。

### 1.2 主要运行组件

| 组件 | 说明 |
|---|---|
| Spring Boot 后端 | Java 21，监听 `127.0.0.1:8080`，提供 API 与静态资源 |
| MySQL 数据库 | 8.4 LTS，玩家存档的唯一存储（库名 `pet_game`） |
| Flyway | 后端启动时自动执行数据库迁移，**无需手工建表** |
| 前端（开发模式） | Vite dev server `http://localhost:5173`，代理 `/api` → `:8080` |

### 1.3 支持情况速查

| 事项 | 支持情况 |
|---|---|
| 本地部署（单机） | ✅ 支持（默认方案） |
| 前后端分别启动（开发模式） | ✅ 支持 |
| 单 JAR 一体运行（正式） | ✅ 支持（推荐） |
| Docker / Docker Compose | ❌ 暂不支持（见 [§13](#13-docker-部署说明)） |
| 外部数据库 | ✅ MySQL 8.4（必需） |
| Redis 等缓存中间件 | ❌ 不使用 |

---

## 2. 快速启动

> 面向**已准备好环境**（Java 21、MySQL 8.4、Node.js 18+）的开发者。首次接触请从 [§3 环境要求](#3-环境要求) 开始。

```bash
# 1. 准备数据库（在 MySQL 中执行一次）
#    CREATE DATABASE pet_game DEFAULT CHARACTER SET utf8mb4;

# 2. 启动后端（Flyway 自动建表，默认连接 root/root@localhost:3306/pet_game）
cd backend
mvn spring-boot:run

# 3. 另开终端，启动前端（开发模式）
cd frontend
npm install
npm run dev

# 4. 浏览器访问
#    前端开发服务器: http://localhost:5173
#    后端健康检查:    http://127.0.0.1:8080/api/health
```

正式运行（单 JAR）：

```bash
# 一次构建（自动完成 前端 build → 复制静态资源 → Maven package → release 目录）
scripts/build.bat    # Windows
scripts/build.sh     # Linux / macOS

# 启动
java -jar release/pet-game.jar
# 浏览器访问 http://localhost:8080
```

---

## 3. 环境要求

### 3.1 软件清单

| 软件 | 必需 | 版本 | 用途 |
|---|---|---|---|
| JDK | **是**（硬性要求） | **Java 21** | 后端运行与构建（`pom.xml` 指定 `<java.version>21</java.version>`） |
| MySQL | **是**（硬性要求） | **8.4 LTS**（技术方案指定；Flyway SQL 与连接参数按 8.x 编写） | 玩家存档数据库 |
| Node.js | 构建/开发必需，正式运行不需要 | **18+**（Vite 6 要求 `^18.0.0 || ^20.0.0 || >=22.0.0`；推荐 20 LTS / 22 LTS） | 前端依赖安装与构建（`npm run build` 执行 `vue-tsc -b && vite build`） |
| npm | 构建/开发必需 | 随 Node.js 自带 | 前端包管理（本项目使用 npm，`frontend/package-lock.json`） |
| Maven | 后端构建必需 | **3.9.x**（推荐 3.9.9） | 后端构建。项目仅提交了 `.mvn/wrapper/maven-wrapper.properties`（指向 3.9.9），**未提交 `mvnw`/`mvnw.cmd` 可执行文件**，因此需系统安装 Maven |
| Docker | 否（可选） | — | 项目未使用，安装与否不影响部署 |

> 说明：项目当前**未显式锁定** Node 与 Maven 的精确版本（`package.json` 无 `engines`），上表为按构建脚本（`scripts/build.sh` 检查 “Node.js 18+”、“Java 21”）与 Vite 6 实际要求整理的**推荐环境**。Java 21 与 MySQL 8.4 为项目硬性要求。

### 3.2 检查环境

```bash
java -version        # 需输出 21.x
mvn -version         # 需输出 3.9.x（后端构建时）
node -v              # 需 ≥ 18
npm -v               # 随 Node 自带
mysql --version      # 可选，确认数据库客户端
```

> Docker 相关命令（`docker --version`、`docker compose version`）本项目**不需要**，可跳过。

---

## 4. 项目获取与目录说明

### 4.1 获取项目

项目为单 Git 仓库。仓库当前为本地仓库，未配置公开远程地址，**不要杜撰 clone 地址**：

```bash
# 若已有远程地址：
git clone <仓库地址>
cd pet-game

# 或直接拷贝项目目录到目标机器
```

### 4.2 部署相关目录

```text
pet-game/
├── frontend/                 # Vue 3 + Phaser 前端
│   ├── public/assets/        #   游戏美术资源（宠物/地图/Boss/VFX/道具/背景等）
│   ├── package.json
│   └── vite.config.ts        #   dev 端口 5173，代理 /api → :8080
├── backend/                  # Spring Boot 后端
│   ├── pom.xml               #   Java 21 / Spring Boot 3.5.3
│   └── src/main/resources/
│       ├── application.yml   #   应用配置（端口/数据源/Flyway/日志/游戏配置目录）
│       ├── db/migration/     #   Flyway 迁移脚本（V1~V13）
│       ├── game-config/      #   游戏内容配置（YAML，打包进 JAR 的默认值）
│       └── static/           #   前端构建产物（由构建脚本生成，已 gitignore）
├── scripts/                  # 构建/启动脚本（build / start / dev-hint）+ e2e/
├── config-example/           # 外部配置覆盖示例（system.yml / system-rules.yml）
├── docs/                     # 项目文档（本文档位于 docs/deployment/）
├── README.md                 # 项目入口
└── AGENTS.md                 # AI/开发规范
```

> `backend/src/main/resources/static/` 由构建脚本生成，属于构建产物，源码仓库中不保留（见 [.gitignore](../../.gitignore)）。

---

## 5. 配置文件说明

> 本项目**不使用 `.env` 文件**（前端不读取环境变量，后端配置在 YAML）。因此没有 `.env.example`，也不需要 `cp .env.example .env`。

### 5.1 配置文件清单

| 配置 | 路径 | 说明 |
|---|---|---|
| 应用配置（默认） | `backend/src/main/resources/application.yml` | 端口、数据源、Flyway、日志、`game.*` 游戏配置项 |
| 游戏内容配置（默认） | `backend/src/main/resources/game-config/**` | 宠物、技能、地图、Boss、道具、任务等（打包进 JAR） |
| 外部覆盖配置 | 运行目录下 `./config/game/`（`game.config-dir` 指定） | 同名配置项覆盖 JAR 内默认值 |
| 外部配置示例 | `config-example/game/` | 复制到运行目录 `config/game/` 使用 |

### 5.2 需要检查/修改的配置项

主要位于 `backend/src/main/resources/application.yml`：

```yaml
server:
  address: 127.0.0.1   # 监听地址
  port: 8080            # 端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pet_game?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: root
  flyway:
    enabled: true       # 启动时自动建表/迁移
    locations: classpath:db/migration

logging:
  file:
    name: ./data/logs/pet-game.log   # 日志文件（相对启动目录）

game:
  config-dir: ./config/game          # 外部游戏配置目录
  developer-mode: false              # 开发者模式（false 关闭，true 开启开发者工具与调试接口）
  backup-dir: ./data/backups         # 存档自动备份目录
```

> ⚠️ 文档不展示真实密码。默认 `root`/`root` 是项目开发默认值，**正式使用请务必修改** `spring.datasource.username/password`（修改后需重新构建，或以外部 `application.yml` 覆盖，见下）。

### 5.3 修改配置的三种方式

1. **开发模式**：直接修改 `backend/src/main/resources/application.yml`，重启后端生效。
2. **不重新构建的正式部署**：在 `pet-game.jar` 同级或 `./config/` 目录放置一份 `application.yml`（Spring Boot 标准外部配置加载机制，会覆盖 JAR 内默认值）。
3. **游戏内容覆盖**：将 `config-example/game/` 下的 YAML 复制到运行目录 `./config/game/`，修改后重启生效。

> 所有配置**不做热更新**，修改后必须重启应用。

---

## 6. 数据库准备

### 6.1 建库

启动 MySQL 后，创建数据库（只需执行一次）：

```sql
CREATE DATABASE pet_game DEFAULT CHARACTER SET utf8mb4;
```

> 字符集使用 `utf8mb4`（连接参数中 `characterEncoding=utf-8`）。

### 6.2 建表与初始化

- 项目采用 **Flyway 自动迁移**（`spring.flyway.enabled=true`，`locations=classpath:db/migration`）。
- 后端启动时自动执行 `V1__init.sql` ~ `V13__tutorial_reset.sql`，**无需手工建表**，也**禁止手工改表**（数据库结构变更一律通过新增 Flyway 迁移）。
- 游戏内容（宠物、技能、初始宠物等）由 `game-config/**` YAML 在启动时加载，不属于数据库初始化内容。

### 6.3 验证连接

后端启动日志出现 Flyway 迁移成功信息、且不报数据库连接错误即视为连接成功；也可启动后用健康检查接口确认（见 [§9](#9-启动成功判断)）。

### 6.4 连接失败排查入口

检查 `application.yml` 的 `spring.datasource`：

- 数据库是否已启动、端口是否为 `3306`；
- 库名是否为 `pet_game`；
- 用户名 / 密码是否正确；
- 若 MySQL 不是本机，需修改 `url` 中的主机地址，并将 `server.address` 等按需调整。

---

## 7. 依赖安装

### 7.1 前端依赖

```bash
cd frontend
npm install
```

- 使用 `package-lock.json` 锁定版本；安装失败排查见 [§15](#15-常见问题与故障排查)。

### 7.2 后端依赖

后端无 `mvnw` 可执行文件，使用系统 Maven：

```bash
cd backend
mvn clean install -DskipTests   # 或 mvn clean package
```

> 首次构建会从 Maven 中央仓库下载依赖，耗时取决于网络。

---

## 8. 开发环境启动（完整流程）

> 开发模式下**前端与后端是两个独立进程**，端口分别 `5173` / `8080`。

### 第一步：启动 MySQL

启动本地 MySQL 8.4 服务（Windows 可通过“服务”或 `net start mysql`；macOS/Linux 用 `brew services start mysql` / `systemctl start mysqld` 等），并确认 [§6](#6-数据库准备) 的 `pet_game` 库已创建。

### 第二步：启动后端

```bash
cd backend
mvn spring-boot:run
# 或在 IDE 中直接运行 PetGameApplication（backend/src/main/java/com/petgame/PetGameApplication.java）
```

后端监听 `127.0.0.1:8080`。启动时自动执行 Flyway 迁移并加载游戏配置。

### 第三步：启动前端（另开一个终端）

```bash
cd frontend
npm install     # 首次或依赖有变动时执行
npm run dev
```

Vite 开发服务器启动于 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`（见 `vite.config.ts` 的 `server.proxy`）。

### 第四步：访问系统

- 游戏前端（开发）：`http://localhost:5173`
- 后端 API（开发）：`http://127.0.0.1:8080/api/**`

> 开发模式下也可运行 `scripts/dev-hint.bat` 查看启动提示（该脚本本身只是提示，仍需要分别在两个终端启动前后端）。

---

## 9. 启动成功判断

### 9.1 后端

- 日志出现 Flyway 迁移执行记录（`Migrating schema ... to version 13` 等）。
- 日志出现：

```text
Tomcat started on port 8080
Started PetGameApplication in ... seconds
```

- 健康检查接口返回正常（返回 `success: true`，`data.status = ok`，`data.version = 1.0.0`）：

```bash
# 浏览器或命令行访问
curl http://127.0.0.1:8080/api/health
```

### 9.2 前端（开发模式）

- 终端提示：

```text
Local: http://localhost:5173/
```

- 浏览器打开 `http://localhost:5173` 能看到游戏首页，且能正常调用 `/api/**`（说明代理到后端成功）。

### 9.3 正式单 JAR

- `java -jar pet-game.jar` 启动后，浏览器访问 `http://localhost:8080` 出现游戏首页。
- 项目**未引入** Swagger / API 文档组件；也没有独立的登录页（无登录鉴权，本地单机直接进入游戏）。

---

## 10. 系统访问地址

| 服务 | 地址 |
|---|---|
| 游戏前端（正式单 JAR） | `http://localhost:8080` |
| 游戏前端（开发模式） | `http://localhost:5173` |
| 后端 API | `http://127.0.0.1:8080/api/**` |
| 健康检查 | `http://127.0.0.1:8080/api/health` |
| API 文档 | 无（未引入 Swagger 等组件） |
| 管理页面 | 无独立管理页（开发者工具在游戏内 “DevTools” 视图，需 `game.developer-mode: true` 开启） |

> 前端使用 Hash 路由（`/#/...`），请以根路径 `http://localhost:8080`（或 `:5173`）访问，避免直接打开深层路径出现 404 / 白屏。

---

## 11. 正式构建与 JAR 运行

### 11.1 统一构建

使用统一构建脚本，自动完成「前端 build → 清理并复制 `dist` 到后端静态资源 → Maven package → 输出 release 目录」：

```bash
scripts/build.bat    # Windows（cmd）
scripts/build.sh     # Linux / macOS（需 bash）
```

构建产物：

```text
release/
├── pet-game.jar     # 可执行 JAR（含前端静态资源与后端业务）
└── config/          # 外部配置示例（由 config-example 复制而来）
```

> 脚本要求 Node.js 18+ 与 Java 21 已加入 PATH；`mvn` 需可用（脚本优先使用 `mvnw`，本项目无 `mvnw`，将回退到系统 `mvn`）。

### 11.2 运行 JAR

```bash
java -jar pet-game.jar
# 或
java -jar release/pet-game.jar
# 或使用启动脚本
scripts/start.bat    # Windows
scripts/start.sh     # Linux / macOS
```

启动脚本会 `cd` 到 `release/` 目录后运行，因此相对路径（`./config/game`、`./data/...`）均相对于 `release/` 目录。

访问 `http://localhost:8080`。

> 可选的 JVM 内存参数示例（按机器情况调整）：`java -Xms256m -Xmx1024m -jar pet-game.jar`。

---

## 12. 停止服务

| 场景 | 操作 |
|---|---|
| 前端 dev server（终端前台） | `Ctrl + C` |
| 后端 dev（`mvn spring-boot:run`） | `Ctrl + C` |
| JAR 前台运行（终端） | `Ctrl + C` |
| JAR 后台运行 | 结束对应 `java` 进程（Windows 任务管理器 / `taskkill`，Linux `kill <pid>`） |
| MySQL | 通常保持运行；如需停止，按 MySQL 服务方式停止（不影响项目进程本身） |

---

## 13. Docker 部署说明

**当前版本暂未提供 Docker 一键部署。**

项目仓库中**不存在** `Dockerfile`、`docker-compose.yml` 等容器化文件；《宠物精灵游戏第一阶段技术方案说明 V1.0.md》与《分阶段开发规划》均明确**不使用 Docker、不提前引入容器化组件**。

因此本部署文档不包含 Docker 构建 / Compose 命令，也不会为了写文档而额外开发 Docker 支持。部署请直接按 [§11](#11-正式构建与-jar-运行) 使用单 JAR 方案。

---

## 14. Windows 部署注意事项

本项目主要在 Windows 开发，同时提供了 Windows 与 Unix 两套脚本，需要注意以下差异：

### 14.1 脚本运行方式

| 脚本 | 平台 | 说明 |
|---|---|---|
| `scripts/build.bat`、`scripts/start.bat`、`scripts/dev-hint.bat` | Windows（cmd） | 在 cmd / PowerShell 中运行 |
| `scripts/build.sh`、`scripts/start.sh` | Linux / macOS / Git Bash / WSL | 需 bash；在 Git Bash / WSL 中运行 |

- PowerShell 中运行 `.bat`：直接输入 `.\build.bat` 或 `cmd /c build.bat`。
- `.sh` 脚本在 Git Bash / WSL 下如需执行，先 `chmod +x scripts/*.sh`；Windows 原生命令行**不能**直接运行 `.sh`。

### 14.2 换行符（CRLF / LF）

- `.sh` 脚本需保持 **LF** 换行。若用 Windows 检出后被转为 CRLF，可能报 `bad interpreter` 之类的错误，可通过 Git 配置 `core.autocrlf` 或在编辑器中改回 LF 解决。
- `.bat` 脚本使用 Windows 换行即可。

### 14.3 路径与命令差异

- 项目脚本内部均使用相对路径定位（`%~dp0` / `$(dirname ...)`），**无需修改路径**；运行脚本时请使用仓库根目录下的 `scripts/` 路径。
- `application.yml` 中相对路径（`./config/game`、`./data/logs`、`./data/backups`）相对于 **JAR / 后端启动目录**，不同机器、不同启动方式下路径基准可能不同，需注意。

### 14.4 软件安装（Windows）

- **JDK 21**：安装后需配置 `JAVA_HOME` 并把 `bin` 加入 `PATH`；`java -version` 确认。
- **Node.js**：安装 LTS 版后 `node -v` / `npm -v` 确认（npm 自带）。
- **Maven**：解压后配置 `M2_HOME` / `MAVEN_HOME` 与 `PATH`；`mvn -version` 确认。
- **MySQL 8.4**：推荐 MySQL Installer 安装；或 zip 解压后 `mysqld --initialize` + 注册服务启动。

### 14.5 端口占用排查（Windows）

```powershell
netstat -ano | findstr :8080
# 若被占用，记下最后一列 PID，再
taskkill /PID <PID> /F
```

---

## 15. 常见问题与故障排查

### 15.1 端口被占用

症状：

```text
Port 8080 already in use
Port 5173 already in use
```

处理：

- Windows：`netstat -ano | findstr :8080` 找到 PID 后 `taskkill /PID <PID> /F`，或改用其他端口。
- Linux/macOS：`lsof -i :8080` 找到进程后 `kill <pid>`。
- 若需更换端口：修改 `application.yml` 的 `server.port`；前端 `vite.config.ts` 的 `server.port`。

### 15.2 数据库连接失败

症状：启动日志出现 `Communications link failure`、`Access denied for user`、`Unknown database` 等。

排查顺序：

1. MySQL 是否启动、端口 `3306` 是否监听；
2. `application.yml` 的 `spring.datasource.url` 中库名是否为 `pet_game`、主机端口是否正确；
3. 用户名 / 密码是否正确（默认 `root` / `root`）；
4. 字符集 / 时区参数是否正常（`characterEncoding=utf-8`、`serverTimezone=Asia/Shanghai`）；
5. 网络可达性（本机部署通常无此问题）。

### 15.3 前端无法访问后端（开发模式）

排查：

1. 后端是否已启动（`mvn spring-boot:run` 或 IDE 运行），端口 8080；
2. 前端是否通过 `/api` 路径调用（`frontend/src/api/client.ts` 中 `baseURL: ''`，接口地址形如 `/api/**`）；
3. `vite.config.ts` 的代理是否生效：`/api` → `http://localhost:8080`；
4. 浏览器 Network 面板确认请求状态码（404/500/代理错误）。

> 正式单 JAR 模式前端与后端同源，不存在 CORS / 代理问题；项目亦未启用 CORS 跨域（单机同源访问不需要）。

### 15.4 Node 依赖安装失败

排查：

1. Node 版本是否满足 Vite 6 要求（`^18 || ^20 || >=22`）；
2. 是否使用 npm（本项目用 `package-lock.json`，勿混用 pnpm/yarn）；
3. 网络 / 镜像问题：可临时更换 npm 源（`npm config set registry https://registry.npmmirror.com`）；
4. 若依赖损坏，可删除 `node_modules` 与 `package-lock.json` 后重新 `npm install`（不影响游戏存档）。

### 15.5 后端无法启动（Java）

排查：

1. `java -version` 是否为 **21**（低于 21 会编译/运行失败）；
2. Maven 是否可用、`mvn -version` 是否为 3.9.x；
3. 数据库是否就绪（见 15.2）；
4. 配置校验是否通过（见 15.6）；
5. 端口 8080 是否被占用（见 15.1）。

### 15.6 启动报配置校验失败

症状：启动日志出现 `GameConfigValidator` 相关错误，提示 ID 重复 / 引用缺失 / 非法数值。

处理：按日志定位到具体 YAML 配置项并修正。

> 注意（本项目历史教训）：**YAML 字段名必须使用 camelCase**（与 Java 模型属性一致），不要写成 `kebab-case`（如 `condition-type` 应为 `conditionType`），否则启动校验失败。

### 15.7 游戏资源加载失败

对《宠物精灵》项目，重点检查资源路径：

- 图片 / 地图 / tileset / 战斗特效路径是否正确（`frontend/public/assets/**`，构建后进入 `static/assets/**`）；
- 资源文件名**大小写**问题：
  - Windows 文件系统通常**大小写不敏感**，而 Linux 部署环境通常**大小写敏感**；
  - 例如 `Pet.png` 与 `pet.png`、`tileset.png` 与 `Tileset.png` 在 Windows 能加载、在 Linux 可能 404；
  - 排查：核对 `frontend/public/assets/` 中的实际文件名与代码引用是否完全一致（含目录名）。
- 是否重新构建过前端：修改资源后未执行 `npm run build` 并重新打包 JAR，旧静态资源不会更新；
- 是否以根路径访问（Hash 路由下直接打开深层路径可能导致资源相对路径错乱）。

### 15.8 访问 / 出现 404 或白屏

排查：

1. 正式模式是否先执行了构建脚本（`backend/src/main/resources/static/` 是否为空）——**未构建前端就 `java -jar` 会拿到空壳**；
2. 是否以根路径 `http://localhost:8080` 访问（Hash 路由）；
3. 浏览器控制台是否有 JS 报错 / 资源 404。

---

## 16. 数据与资源持久化

### 16.1 运行数据 / 持久化数据清单

| 目录 / 数据 | 内容 | 位置（相对启动目录） |
|---|---|---|
| **MySQL 数据库 `pet_game`** | 玩家存档、宠物、图鉴、任务、成就、统计等**全部玩家数据** | 数据库（非文件目录） |
| `./data/backups` | 存档自动备份（`game.backup-dir`，导入前 / 重置前自动写入） | `data/backups` |
| `./config/game` | 外部覆盖配置（由 `config-example` 复制，`game.config-dir`） | `config/game` |
| `./data/logs` | 运行日志（`pet-game.log`，按 10MB 滚动、保留 7 份） | `data/logs` |

> 以上相对路径均相对于 **JAR / 后端启动目录**：开发模式（`cd backend && mvn spring-boot:run`）下为 `backend/data/...`；正式运行（`cd release && java -jar`）下为 `release/data/...`。

### 16.2 重新部署 / 升级时不能删除

- **MySQL `pet_game` 库**：含全部玩家存档，删除即丢档；
- **`./data/backups`**：存档备份，建议保留；
- **`./config/game`**：若做过自定义配置，需保留（或提前备份后合并）。

> 前端静态资源与游戏内容配置（JAR 内 `game-config`）随构建产物重建，**不属于**运行数据；`backend/src/main/resources/static/`、`release/`、`frontend/dist`、`node_modules` 等均可安全重建。

### 16.3 项目不存在

- 无独立上传文件目录（`uploads/`）；
- 无独立存档文件目录（`saves/`）——存档在 MySQL；
- AI 生成美术资源源文件位于 `docs/art/`，属于项目资料而非运行数据。

---

## 17. 更新部署

假设已有环境按本文档部署完成，更新到新版本：

```bash
git pull            # 或按实际方式更新源码
```

前端依赖与资源（如有前端改动）：

```bash
cd frontend
npm install         # 依赖有变动时
npm run build
```

后端构建与数据库迁移：

```bash
cd backend
mvn clean package -DskipTests
```

或直接重跑统一构建脚本（一步完成前端构建 + 复制静态资源 + 后端打包 + release 输出）：

```bash
scripts/build.bat   # 或 scripts/build.sh
```

重新启动：`java -jar release/pet-game.jar`（或 `scripts/start.bat` / `start.sh`）。

### 更新注意事项

- **数据库迁移**：Flyway 会在启动时自动执行新增迁移，**无需手工执行 SQL**；同时**禁止手工改表**。
- **影响存档的操作**：升级过程中**不要删除 MySQL `pet_game` 库与 `./data/backups`**；如需保险，可先通过游戏内「存档备份」功能手动备份。
- **游戏内容**：若运行目录 `./config/game/` 有自定义覆盖配置，升级后需核对与新版本 `config-example` 的字段是否兼容（配置结构有版本号 `game.config-version` 校验）。
- **前端资源**：前端有改动时必须重新构建并重新打包 JAR，否则浏览器加载的是旧静态资源。

---

## 18. 部署前检查清单

- [ ] JDK 版本为 21（`java -version`）
- [ ] 后端构建工具 Maven 3.9.x 可用（`mvn -version`）
- [ ] （如需构建）Node.js ≥ 18 且 npm 可用（`node -v` / `npm -v`）
- [ ] MySQL 8.4 已启动
- [ ] 数据库 `pet_game` 已创建（`CREATE DATABASE pet_game DEFAULT CHARACTER SET utf8mb4;`）
- [ ] `application.yml` 数据源用户名 / 密码 / 库名已核对（正式使用请改默认密码）
- [ ] 需要覆盖的游戏配置已放入 `./config/game/`
- [ ] 前端依赖安装成功（`npm install`）
- [ ] 后端依赖 / 打包成功（`mvn ... package`）
- [ ] 后端启动成功（日志出现 `Started PetGameApplication`，Flyway 迁移无报错）
- [ ] 健康检查通过（`http://127.0.0.1:8080/api/health` 返回 `success:true`）
- [ ] 前端（开发模式）启动成功（`Local: http://localhost:5173/`）或单 JAR 构建完成
- [ ] 浏览器能访问游戏（`http://localhost:8080` 或 `http://localhost:5173`）
- [ ] 前端能正常调用 API（`/api/**`，无 404 / 500）
- [ ] 游戏资源正常加载（宠物 / 地图 / 战斗特效，无控制台 404）
- [ ] 存档 / 数据库可正常写入（新建游戏并正常游玩、重启后存档仍在）
