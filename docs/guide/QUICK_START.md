# 快速开始（安装与运行）

本文档提供《宠物精灵》**最简启动路径**，面向玩家与开发者。

> 完整的环境准备、数据库初始化、构建发布、Windows 注意事项、故障排查与部署前检查清单，统一见 [部署指南](../deployment/DEPLOYMENT.md)（**唯一主要部署文档**）。
>
> 精确的部署与架构决策以《宠物精灵游戏第一阶段技术方案说明 V1.0.md》为准。

---

## 环境要求（速览）

| 环境 | 版本 | 用途 |
|---|---|---|
| Java | **21** | 运行后端（必需） |
| MySQL | **8.4 LTS** | 玩家存档数据库（必需） |
| Node.js | 18+ | 仅开发与正式构建时需要（玩家运行不需要） |
| Maven | 3.9.x | 仅后端构建时需要 |

**正式运行**只需 Java 21 + MySQL 8.4 + `pet-game.jar`，玩家环境不需要 Node。

---

## 开发环境启动

```bash
# 1. 准备数据库（MySQL 中执行一次）
#    CREATE DATABASE pet_game DEFAULT CHARACTER SET utf8mb4;

# 2. 启动后端（Flyway 自动建表；默认 root/root@localhost:3306/pet_game）
cd backend
mvn spring-boot:run        # 默认监听 127.0.0.1:8080

# 3. 另开终端启动前端（开发模式）
cd frontend
npm install
npm run dev                # http://localhost:5173，代理 /api → :8080
```

---

## 正式构建与运行

```bash
# 一次构建：前端 build → 复制静态资源 → Maven package → release 目录
scripts/build.bat    # Windows
scripts/build.sh     # Linux / macOS

# 启动
java -jar release/pet-game.jar
# 或 scripts/start.bat / scripts/start.sh
```

访问 `http://localhost:8080`。

---

## 常见问题（摘要）

| 现象 | 可能原因与处理 |
|---|---|
| 启动报数据库连接失败 | MySQL 未启动、库名/账号/密码不匹配；检查 `application.yml` 的 `spring.datasource` |
| 启动报配置校验失败 | 修改了 `game-config` 内容导致 ID 重复 / 引用缺失 / 非法数值；按启动日志修正（YAML 字段须 camelCase） |
| 前端打不开 / 接口 404 | 后端未启动，或前端 `/api` 未代理到 `:8080` |
| 页面白屏 | 确认以根路径访问（Hash 路由），刷新后仍异常请查看浏览器控制台 |
| 修改配置不生效 | 配置不做热更新，需重启应用 |

> 更多排查（端口占用、资源加载大小写、Node 依赖等）见 [部署指南：常见问题与故障排查](../deployment/DEPLOYMENT.md#15-常见问题与故障排查)。
