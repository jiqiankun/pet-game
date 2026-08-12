# 后端编码规范

**适用项目：** 宠物精灵游戏第一阶段  
**依据：** 《宠物精灵游戏第一阶段技术方案说明 V1.0》《宠物精灵游戏第一阶段需求设计文档 V1.0》

---

## 1. 技术栈约束

| 项 | 方案 |
|---|---|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5.x |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.4 LTS |
| 数据迁移 | Flyway |
| 构建 | Maven |
| 部署 | Executable JAR |

**明确禁止引入：** 微服务、Spring Cloud、Redis、Kafka/RabbitMQ/RocketMQ、Elasticsearch、WebSocket、Kubernetes、Docker、Nginx、分布式事务、分库分表、复杂 DDD 框架、事件总线基础设施、CQRS、Event Sourcing、配置中心、登录鉴权/多用户体系、任何文档未要求的新基础设施。

---

## 2. 项目结构

单体模块化，不拆微服务、不多 Maven 模块：

```text
backend/
└── src/main/java/com/petgame/
    ├── common/         # 公共工具、统一响应、异常处理
    ├── config/         # Spring 配置、游戏配置加载
    ├── player/         # 玩家存档
    ├── pet/            # 宠物管理
    ├── skill/          # 技能
    ├── team/           # 队伍
    ├── battle/         # 战斗引擎
    ├── capture/        # 捕捉
    ├── map/            # 地图
    ├── boss/           # Boss 系统
    ├── inventory/      # 背包
    ├── pokedex/        # 图鉴
    ├── quest/          # 任务
    ├── achievement/    # 成就
    ├── statistics/     # 统计
    ├── save/           # 存档导入导出
    └── developer/      # 开发者模式
```

---

## 3. 模块内部结构

每个业务模块建议分层：

```text
pet/
├── controller/    # HTTP 接口适配
├── service/       # 业务流程编排
├── domain/        # 核心领域规则
├── repository/    # 数据访问编排
├── mapper/        # MyBatis-Plus 数据访问
├── entity/        # 数据库实体
├── dto/           # 请求 DTO
└── vo/            # 响应 VO
```

**分层职责：**
- **Controller**：只做 HTTP 适配与参数校验，不写业务逻辑。
- **Service**：编排业务流程，调用 Domain 与 Repository。
- **Domain**：承载核心领域规则（如 BattleEngine、CaptureCalculator）。
- **Mapper**：只做数据访问，不包含业务逻辑。

---

## 4. API 规范

### 4.1 统一前缀

所有接口使用 `/api/**` 前缀。开发者接口使用 `/api/dev/**`。

### 4.2 统一响应结构

```json
{
  "success": true,
  "data": {},
  "message": null,
  "code": null
}
```

错误响应：

```json
{
  "success": false,
  "data": null,
  "message": "经验池经验不足",
  "code": "EXP_NOT_ENOUGH"
}
```

### 4.3 规则

- 业务错误使用**稳定 errorCode**，前端不依赖异常字符串。
- Bootstrap 接口一次返回首页所需核心状态，避免首页连续请求十几个接口。
- 战斗接口只接受行动意图（技能 + 目标），不返回表现层指令。
- 所有参数后端校验；不信任前端传入的伤害、金币、经验、捕捉结果。

---

## 5. 配置驱动规范

### 5.1 配置分类

- **系统规则配置**：属性克制倍率、暴击区间、等级上限、携带/上场数量、自由属性点规则、捕获规则、Boss 幸运值规则等。
- **内容配置**：宠物种族、技能、Boss、道具、掉落、商店、任务、地图刷新组等。

### 5.2 规则

- 核心数值一律配置化，**禁止硬编码**。
- 新增宠物/技能/Boss/地图正常情况下只加配置与资源、不改业务代码。
- 所有配置接入**启动校验**：ID 重复、引用不存在、非法概率/倍率/等级等严重错误时**启动失败**并给出明确提示。
- 支持外部配置目录（`game.config-dir`）同 ID 覆盖内部配置。
- **不做热更新**；修改配置需重启。
- 配置错误必须在启动时暴露，不允许延迟到运行时。

---

## 6. 统一随机规范

- 所有随机场景（暴击、命中、捕获、掉落、资质、稀有技能、特殊外观、放生礼物）统一使用 `GameRandom`。
- 支持固定种子（`randomSeed`），可复现完整随机流程。
- **禁止业务代码直接使用** `Math.random()` 或 `new Random()`。
- `BattleContext` 记录当前随机数生成器状态。

---

## 7. 事务规范

以下关键流程必须事务化，不允许部分成功：

| 流程 | 事务内容 |
|---|---|
| 战斗结算 | 更新 HP、发经验、发金币、发掉落、更新图鉴、更新 Boss、更新统计 |
| 捕获成功 | 创建宠物、扣捕捉球、更新图鉴、更新统计 |
| 放生 | 计算礼物、发礼物、删除/标记宠物、更新统计 |
| 幸运兑换 | 校验幸运值、扣幸运值、发放奖励 |
| 存档导入 | 校验 → 自动备份 → 导入 → 校验 → 提交（失败回滚） |

---

## 8. 数据分离规范

> 内容配置与玩家存档严格分离。

- **YAML 配置**：定义宠物种族、技能、Boss、道具等内容（如 `PetSpeciesConfig`）。
- **MySQL 存档**：保存玩家数据，只存引用（如 `species_id`、`skill_id`），不复制配置内容。
- 修改宠物基础配置（如烬牙兽基础力量成长），所有玩家宠物自动使用新配置。
- 战斗临时数据（`BattleContext`）只存服务器内存，战斗结束统一结算落库；服务重启后未完成战斗直接丢弃。

---

## 9. 数据库规范

- 数据库结构变更只允许通过 **Flyway 迁移文件**，禁止手工改表。
- 迁移文件命名：`V{版本号}__{描述}.sql`（如 `V1__init.sql`）。
- 索引保持简单，按需创建（如 `player_pet(species_id)`、`player_team_member(team_id)`）。
- 不为假设需求加冗余字段；仅保留文档明确要求的预留项。

---

## 10. 日志规范

**必须记录：**
- 应用启动与配置加载
- 配置校验错误
- 数据库异常
- 战斗异常
- 存档导入导出
- 开发者操作

**禁止记录：**
- 高频战斗数值（如每次技能伤害）不写 INFO 日志，避免日志膨胀。

日志目录：`./data/logs/pet-game.log`。

---

## 11. 版本号规范

| 版本号 | 职责 |
|---|---|
| `gameVersion` | 游戏发布版本 |
| `saveVersion` | 存档数据结构版本 |
| `configVersion` | 游戏配置结构版本 |

三者职责分离，随变更独立维护。

---

## 12. 战斗引擎规范（核心约束）

> 手动/自动/普通/精英/Boss 战斗**全部使用同一个 BattleEngine**。

- **唯一差异**是「谁决定行动」：`PlayerDecisionProvider` vs `AutoDecisionProvider`。
- **禁止**出现 `ManualBattleService`、`AutoBattleService`、`BossBattleService` 等多套战斗逻辑。
- 战斗结果必须由后端计算，前端只提交行动意图。
- 后端输出标准战斗事件（`DAMAGE`、`HEAL`、`BUFF_APPLIED` 等），前端按事件播放表现。
- 战斗相关改动必须保持单一引擎原则，并补充对应规则验证。

---

## 13. 安全边界

- 本机可信模型（`127.0.0.1`），但所有参数后端校验。
- 前端只能提交意图（如「使用技能 X 攻击目标 Y」），不能提交计算结果（如「造成 9999 伤害」）。
- 伤害、金币、经验、捕捉结果一律由后端计算。

---

## 14. 部署规范

- 正式运行只需 Java 21 + MySQL 8.4 + `pet-game.jar`。
- `java -jar pet-game.jar` 运行，默认监听 `127.0.0.1:8080`。
- 前端构建产物打入 Spring Boot 静态资源，输出单个可执行 JAR。
- 统一构建脚本执行完整流水线，不允许手工复制文件完成发布。
