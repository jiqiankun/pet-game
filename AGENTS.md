# AGENTS.md — 宠物精灵游戏项目开发规范

本文件是本项目 AI 辅助开发的项目规范与约束说明。任何参与本项目开发的 AI 或开发者，**开始任何开发任务前必须先阅读本文件与「权威文档清单」中的全部文档**。

---

## 1. 项目概览

宠物精灵游戏是一款**个人开发、个人部署、个人游玩**的轻量单机 Web 宠物收集养成游戏，核心玩法为：宠物收集 + 自由培养 + 队伍构筑 + 回合制战斗 + 区域探索 + Boss 挑战。

核心设计原则：

> 系统可以有深度，但操作和实现不能过重。

第一阶段完整内容已经完成；当前开发目标为**桌面版世界与 UI 重构**，以常驻世界、WorldGraph、地图联动、Battle Context 和桌面体验为主，保留既有宠物、战斗与养成规则。

## 2. 权威文档清单（开发前必读）

| 文档 | 职责 |
|---|---|
| `docs/requirements/宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0.md` | 当前桌面版世界、地图、导航、页面联动和战斗上下文需求的最高项目依据 |
| `docs/requirements/宠物精灵游戏第一阶段需求设计文档 V1.0.md` | 未被桌面重构覆盖的玩法规则、数值规则与内容规模依据 |
| `docs/technical/宠物精灵游戏第一阶段技术方案说明 V1.0.md` | 技术栈、架构边界、部署方式与基础数据方案依据 |
| `docs/planning/宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md` | 当前桌面重构的开发顺序、阶段范围、依赖和验收标准 |
| `docs/development/PHASE0_BASELINE.md` | 当前需求裁决、构建/内容/存档基线与 R-001～R-205 追踪 |
| `docs/planning/宠物精灵游戏分阶段开发规划 V1.0.md` | 第一阶段阶段 0～14 的历史开发记录与仍有效约束 |
| `AGENTS.md`（本文件） | 项目级开发规范与 AI 协作约束 |
| `docs/development/FRONTEND_STANDARDS.md` | 前端编码规范、组件约定、Phaser 边界 |
| `docs/development/BACKEND_STANDARDS.md` | 后端编码规范、模块结构、配置与事务约定 |
| `docs/development/TESTING_STANDARDS.md` | 测试规约、测试重点、阶段验收规则 |
| `docs/architecture/ARCHITECTURE.md` | 当前系统实际技术结构（了解代码如何工作） |
| `docs/architecture/PROJECT_STRUCTURE.md` | 实际代码目录与模块职责 |
| `docs/development/DEVELOPMENT_STATUS.md` | 当前开发状态、历史阶段记录、遗留问题、临时技术债务 |

> 安装运行与部署见 `docs/guide/QUICK_START.md`（快速开始）与 `docs/deployment/DEPLOYMENT.md`（完整部署）；玩家玩法说明见 `docs/guide/GAMEPLAY.md`。

**文档优先级：** 用户当前明确要求 > 桌面重构需求 > 第一阶段需求/技术文档中未被覆盖的规则 > 当前桌面重构规划/阶段 0 裁决 > 第一阶段历史规划 > 本文件其余内容 > 历史结论。文档之间出现未裁决冲突时，**必须上报并等待确认，禁止自行选择一方并隐式修改需求**。

## 3. 技术栈与架构（已确定，禁止重新设计）

- 前端：Vue 3 + TypeScript + Vite + Pinia + Vue Router（**Hash 模式**）+ Phaser（2D 表现）+ Tiled（JSON 地图）。
- 后端：Java 21 + Spring Boot 3.5.x，单体模块化（不拆微服务、不多 Maven 模块）。
- 数据：MyBatis-Plus + MySQL 8.4 LTS，Flyway 管理表结构变更。
- 接口：REST JSON，统一前缀 `/api/**`，统一响应结构 `{ success, data, message, code }`，业务错误使用稳定 errorCode。
- 部署：前端构建产物打入 Spring Boot 静态资源，输出单个可执行 `pet-game.jar`，`java -jar` 运行，默认监听 `127.0.0.1:8080`。
- 仓库：单 Git 仓库，目录为 frontend / backend / docs（下分 requirements / technical / architecture / planning / development / guide / prompts / art）/ scripts / config-example。

**架构边界（必须遵守）：**

> Vue 负责页面，Phaser 负责游戏表现，Spring Boot 负责业务规则，BattleEngine 负责全部战斗计算，MySQL 只存玩家存档，YAML 只存游戏内容配置。

- 内容配置与玩家存档严格分离；玩家数据只保存引用（如 species_id、skill_id），不复制配置内容。
- Phaser 不是业务系统：宠物数据、图鉴、背包、任务、存档、战斗公式一律不得写进 Phaser 场景。
- 战斗临时数据只存服务器内存；战斗结束统一结算落库；服务重启后未完成战斗直接丢弃。
- 手动/自动/普通/精英/Boss 战斗**全部使用同一个 BattleEngine**，唯一差异是「谁决定行动」；禁止出现多套战斗逻辑。
- 前端只提交行动意图，不提交计算结果；伤害、金币、经验、捕捉结果一律由后端计算。

**明确禁止引入：** 微服务、Spring Cloud、Redis、Kafka/RabbitMQ/RocketMQ、Elasticsearch、WebSocket、Kubernetes、Docker、Nginx、分布式事务、分库分表、复杂 DDD 框架、事件总线基础设施、CQRS、Event Sourcing、配置中心、登录鉴权/多用户体系、任何文档未要求的新基础设施。

## 4. 构建与运行

- 开发模式：前端 `npm run dev`（Vite 代理 `/api` → `localhost:8080`），后端以 Spring Boot 本地启动，MySQL 本地实例。
- 正式构建：统一构建脚本执行「前端 build → 清理并复制 dist 到后端静态资源 → Maven package → 输出 release 目录」。
- 正式运行只需 Java 21 + MySQL 8.4 + `pet-game.jar`；玩家环境不需要 Node。
- 数据库结构变更只允许通过 Flyway 迁移文件，**禁止手工改表**。

> 详细安装与运行步骤见 `docs/guide/QUICK_START.md`；完整部署细节见 `docs/deployment/DEPLOYMENT.md`。

## 5. 分阶段开发规则

第一阶段历史规划按阶段 0～14 执行并已完成；当前桌面重构按《宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md》的阶段 0～11 执行。每次开发任务必须：

1. **先读文档**：阅读需求文档、技术方案、分阶段开发规划与本文件及前后端编码规范与测试规约。
2. **明确当前阶段**：确认任务对应的阶段编号，以该阶段「实现范围」为唯一工作清单。
3. **检查前置依赖**：前置阶段的完成标准必须已满足；不满足时先报告，不自行补建。
4. **只实现当前阶段**：对照该阶段「本阶段不包含」逐项自查；不得因「顺便实现比较方便」提前实现后续系统。
5. **不修改无关模块**：不重构未涉及代码；确需变更表结构时随 Flyway 新增迁移并说明理由。
6. **不扩展需求**：发现设计缺失时执行「标记问题 → 给出建议 → 等待确认」，不直接实现。
7. **测试验证**：实现过程中按《docs/development/TESTING_STANDARDS.md》编写测试；完成后逐条对照该阶段「完成标准」与「核心业务规则」验证。
8. **验收自查**：汇报已完成项、验证结果、遗留问题、风险。
9. **更新进度**：阶段验收通过后，更新 `docs/development/DEVELOPMENT_STATUS.md` 与本文件「当前阶段状态」一节。
10. **同步更新文档**：每个开发阶段验收通过后，必须同步更新以下文档（**文档更新是阶段验收的必要条件，未更新文档视为验收未通过**）：
    - `README.md`：更新项目进度、已完成阶段、当前状态等信息。
    - `AGENTS.md`：更新「当前开发阶段状态」（§6）及相关约束。
    - `docs/development/DEVELOPMENT_STATUS.md`：更新当前阶段、已完成阶段、遗留问题、临时技术债务。
    - `docs/development/FRONTEND_STANDARDS.md`：如本阶段涉及前端新增约定或技术变更，同步更新。
    - `docs/development/BACKEND_STANDARDS.md`：如本阶段涉及后端新增约定、模块结构或数据库规范变更，同步更新。
    - `docs/development/TESTING_STANDARDS.md`：如本阶段新增测试重点或验收规则，同步更新。

**阶段验收通过前，不开启下一阶段开发。**

## 6. 当前开发阶段状态

- 当前开发阶段：**桌面版世界/UI 重构阶段 2（WorldGraph、玩家知识与世界状态基础）— 实现完成，后端全量测试/构建与桌面运行态补验通过**
  - 已实现最小 WorldGraph（World/WorldMapNode/WorldConnection）与 `WorldGraphBuilder`、`LocationRef` 兼容位置引用、`WorldTruthService` 知识过滤与位置/安全点/状态读写、`WorldController` `/api/world/**` 接口集、Flyway `V14__world_graph.sql`（`player_world_state` + `player_known_location`）。
  - 校验器增强并纳入后端测试/构建：实测区域必填 `spawnObjectId`、出口必填 `entryObjectId`、普通双向连接成对校验、单向连接显式标注 `oneWay:true`、`initialMapId` 不可指向结构预留区域；新增 `MapTiledConsistencyValidator` 供阶段 3 内容修复使用（Tiled 对象 ID 契约校验）。
  - 因遗迹（`MAP_AREA_RUINS`）为无出口尾区，将 `EXIT_WATERS_TO_RUINS` 与 `EXIT_THUNDER_TO_RUINS` 显式标注 `oneWay:true`。
  - 已验证：后端全量测试全绿；前端 `vue-tsc -b` 与 Vite 生产构建通过；新增 `WorldGraphBuilderTest`/`LocationRefTest`/`MapTiledConsistencyValidatorTest` 并在 `GameConfigMapValidateTest` 增补阶段 2 用例。
  - 桌面运行态补验通过：本地 JDK21+MySQL 启动后端，Flyway V14 生效（schema=14），`/api/world` 全接口链（旧档迁移落到出生锚点、知识过滤视图、位置保存/恢复、越界跨图拒绝 `POSITION_CROSS_MAP`、伪造节点拒绝 `KNOWLEDGE_NODE_MISSING`、捷径列表）均符合预期；补验中修复 `PlayerWorldStateEntity` 缺 `@TableId` 导致的 `selectById/updateById` 坏绑定 500。
  - 仅桌面端属于本轮范围；手机、平板、触控与移动端专项回归不实施、不验收。
- 历史状态：桌面重构阶段 1（常驻世界根、Context Stack 与输入基础）运行态补验基本完成；第一阶段阶段 0～14 已完成，作为当前增量改造的稳定业务基线。
- 下一阶段：阶段 2 运行态补验通过后，才可进入**桌面重构阶段 3（世界图谱前端投影与纵切内容修复）**。
- 各阶段实现详情、遗留问题、已知限制与临时技术债务：见 [docs/development/DEVELOPMENT_STATUS.md](docs/development/DEVELOPMENT_STATUS.md)

> 本文件仅记录当前阶段与进展指针；历史阶段实现记录与遗留事项统一维护在 `docs/development/DEVELOPMENT_STATUS.md`，避免本文件演变为历史日志。

## 7. 固定规则速查（不得擅自修改）

- 9 属性、每宠单属性；克制 ×1.50 / 被克 ×0.75；本属性技能 ×1.20。
- 27 种基础宠物（稀有度 12/9/5/1），Lv.50 上限，六维属性，资质 0～100。
- 6 宠携带、3 宠上场、5 套预设；每回合每宠一次行动，速度决定顺序且每回合重算。
- 技能携带：自身主动最多装备 4 个 + 技能书主动最多携带 2 个（技能书主动最多学习 10 个，超限需遗忘）；被动分**固有被动**与**技能书被动**两大来源，技能来源（INNATE/BOOK）与类型（ACTIVE/PASSIVE）必须分字段表达。
- 被动：**固有被动**（species.passives，来源 INNATE/LEVEL_UP/EVOLUTION）全部自动生效、不占槽位、不可卸下，每宠约 3~5 个且至少 1 个核心特色被动；**技能书被动**（sourceType=SKILL_BOOK）遵循「**已学习 ≠ 当前生效**」，每宠最多同时启用 2 个（独立被动槽 slot 7~8），可在战斗外启用/停用/替换；同名/同效果被动不重复生效，依 effectGroup + stackRule（UNIQUE/HIGHEST_ONLY/ADDITIVE/LIMITED）归一化去重。
- 宠物自身技能：标准 6 主动 + 3 被动（主动范围 5～7、被动内容建议 2～5），Lv.1～40 升级自动解锁（不消耗金币/技能点）；不同宠物允许共享 skillId，实际 SkillDefinition 控制约 80～100 个、禁止复制技能定义。
- 暴击率 5%、倍率 1.4～2.0 均匀随机；治疗不暴击；正常命中最低 1 点伤害。
- 无 MP，技能以冷却限制；技能冷却/Buff/异常不跨战斗保留，HP 跨战斗保留。
- 【留生一击】致死时将可捕捉目标 HP 保留为 1（暴击不可绕过）并附加震慑（仅提供安全捕捉窗口，不提高捕获率）；触发保护时才清除持续伤害状态；Boss 正常受伤但不附加震慑。
- 吸血按实际 HP 损失计算（护盾吸收/过量伤害不计），DOT/反击/反射默认不吸血，受禁疗影响。
- 【命运天平】HP 百分比交换不是伤害；Boss 采用交换幅度上限（默认单次最多 20 个百分点）而非免疫。
- 战败零惩罚；营地恢复免费；地图刷新不依赖现实时间。
- 放生培养加成上限 50%；幸运值每 100 兑换一次不封顶；Boss 掉落情报 3/6/10 次解锁。
- 捕捉球仅 3 档；自动战斗仅限已击败过的敌人（Boss 分难度）；自动挑战不是扫荡。
- 捕捉公式 = 基础捕获率 × HP 系数 × 异常加成 × 球倍率 × 精英系数，不添加文档之外修正项；被捕捉宠物不结算击败掉落、不参与野生奖励；锁定宠物禁止放生，放生礼物价值点数底线规则（决策七）。
- 洗点第一阶段免费；Boss 无门票/体力/次数/冷却限制。
- 探索背包与战斗背包共享玩家库存；战斗内只允许服务端配置 `usableInBattle=true` 的 HEAL_HP/REVIVE，捕捉球继续走 CAPTURE 行动；自动使用恢复/复苏默认关闭，结算时统一扣库。
- 内容规模上限：5 区域 + 1 据点、5 主 Boss + 2～3 隐藏、主线 10～15、支线 8～12、成就 30～50、主动技能约 80～100（当前 85；按技术方案被动技能无池上限，当前被动体系 51：41 固有/升级被动（含 27 宠核心特色被动）+ 10 技能书被动；技能书种类 ≥20 且被动技能书 ≥10）、道具约 40～60，达到后不扩充。

## 8. 明确不做清单（第一阶段）

进化/突破实际玩法、双属性、繁殖、遗传、性格、复杂装备、公会、好友、PVP、交易、拍卖行、多人联机、排行榜、每日/周常任务、签到、体力、门票、限时活动、商店刷新、季节、真实时间刷新、复杂天气/生态模拟、3D、玩家职业/战斗等级、多结局、复杂 GM 后台、活动中心/公告/商城弹窗、扫荡、自动探索、复杂元素反应网络、独立 App、手机/平板/触控专项适配。游戏内轻量昼夜、每 Region 少量天气和低频事件属于桌面重构阶段 9 范围。

## 9. 汇报格式（每个开发任务结束时）

- 当前阶段与任务范围；
- 已完成内容（对照阶段实现范围）；
- 验收结果（逐条对照完成标准：通过/未通过/待确认）；
- 测试验证结果（已编写/已通过的测试情况）；
- 遗留问题与待确认需求；
- 是否建议进入下一阶段。
