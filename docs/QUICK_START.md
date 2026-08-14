# 快速开始（安装与运行）

本文档说明如何安装、配置、启动与构建《宠物精灵》。面向玩家与开发者。

> 精确的部署与架构决策以《宠物精灵游戏第一阶段技术方案说明 V1.0.md》为准。

---

## 1. 环境要求

| 环境 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 运行后端（必需） |
| MySQL | 8.4 LTS | 玩家存档数据库（必需） |
| Node.js | 18+ | 仅开发与正式构建时需要（玩家运行不需要） |
| Maven | 有 `mvnw` 时无需单独安装 | 仅正式构建时需要 |

**正式运行**只需 Java 21 + MySQL 8.4 + `pet-game.jar`，玩家环境不需要 Node。

---

## 2. MySQL 准备

1. 安装并启动 MySQL 8.4。
2. 创建数据库 `pet_game`：

```sql
CREATE DATABASE pet_game DEFAULT CHARACTER SET utf8mb4;
```

3. 后端默认连接配置（见 `backend/src/main/resources/application.yml`）：

- 地址：`localhost:3306`
- 库名：`pet_game`
- 用户名 / 密码：`root` / `root`

如需修改，调整 `application.yml` 的 `spring.datasource` 或外部配置。

> 表结构由 Flyway 在应用启动时自动创建与迁移，**无需手工建表**。

---

## 3. 配置方式

- **内部默认配置**：随 JAR 打包在 `backend/src/main/resources/game-config/`（宠物、技能、地图等内容）。
- **外部覆盖配置**：启动目录下 `./config/game/`（由 `game.config-dir` 指定）。同名配置项会覆盖内部默认值。
- 示例配置见仓库根目录 `config-example/`，可按需复制到运行目录：

```text
config/
└── game/
    ├── system.yml
    └── system-rules.yml
```

> 配置不做热更新，修改后需重启生效。

---

## 4. 开发环境启动

### 4.1 启动后端

```bash
cd backend
mvn spring-boot:run
# 或使用 IDE 启动 PetGameApplication
```

后端默认监听 `127.0.0.1:8080`。

### 4.2 启动前端（开发模式）

```bash
cd frontend
npm install
npm run dev
```

Vite 开发服务器默认 `http://localhost:5173`，并将 `/api` 代理到后端 `http://localhost:8080`。

> 开发模式下也可使用 `scripts/dev-hint.bat` 查看启动提示。

---

## 5. 正式构建

执行统一构建脚本（自动完成「前端 build → 复制静态资源 → Maven package → 输出 release 目录」）：

```bash
build.bat     # Windows
./build.sh    # Linux / macOS
```

构建产物输出到 `release/pet-game.jar`，并附带 `release/config/` 外部配置示例。

---

## 6. JAR 运行

```bash
java -jar pet-game.jar
# 或
java -jar release/pet-game.jar
```

访问 `http://localhost:8080`。

---

## 7. 常见问题

| 现象 | 可能原因与处理 |
|---|---|
| 启动报数据库连接失败 | MySQL 未启动、库名/账号/密码不匹配；检查 `application.yml` 的 `spring.datasource` |
| 启动报配置校验失败 | 修改了 `game-config` 内容导致 ID 重复 / 引用缺失 / 非法数值；按启动日志修正配置 |
| 前端打不开 / 接口 404 | 后端未启动，或前端 `/api` 未代理到 `:8080` |
| 页面白屏 | 确认以根路径访问（Hash 路由），刷新后仍异常请查看浏览器控制台 |
| 修改配置不生效 | 配置不做热更新，需重启应用 |