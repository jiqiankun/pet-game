# 后端编码规范

**适用项目：** 宠物精灵游戏第一阶段 + 桌面版世界/UI 重构
**依据：** 《宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0》《宠物精灵游戏第一阶段技术方案说明 V1.0》《宠物精灵游戏第一阶段需求设计文档 V1.0》

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
- 世界/地图接口同样只接受移动或交互意图；Connection、Gateway、LocationRef、知识状态和返回位置必须由服务端配置/状态验证，客户端不得提交任意跨图坐标。

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
- 实体的 `createdAt` / `updatedAt` 统一使用 MyBatis-Plus `@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)` 标注，由统一的 `MetaObjectHandler`（`MybatisMetaConfig`）自动填充；**禁止在业务代码中手工赋值时间戳字段**。

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

### 12.1 战斗模块实现约定（阶段 3 起）

`com.petgame.battle` 包结构：

```text
battle/
├── engine/       # BattleEngine 入口、BattleContext、TurnResult
├── calculator/   # 伤害/治疗/命中/暴击/状态修正计算器（纯函数，无状态）
├── model/        # BattleUnit、BattleAction、StatusInstance、BattleSide
├── event/        # BattleEvent、BattleEventType
├── passive/      # PassiveManager（被动触发时机/效果配置驱动）
├── ai/           # DecisionProvider 接口与敌方决策实现
├── victory/      # 敌方胜利互动（阶段 12）：BattleDefeatContext、VictoryInteractionService、VictoryInteractionView
├── service/      # BattleService（战斗内存池）与快照 DTO
└── controller/   # BattleController
```

- **引擎不针对具体配置 ID 写分支**：技能/状态/被动全部配置驱动，新增内容只加 YAML。
- 技能/状态/被动/测试战斗配置（`skills.yml`、`statuses.yml`、`test-battle.yml`）必须接入启动校验（引用完整性、枚举合法性）。
- 战斗内存池：`BattleService` 以 `ConcurrentHashMap<battleId, BattleContext>` 持有进行中战斗，**战斗过程中零数据库写入**；战斗结束后的结算落库在后续阶段接入。
- 战斗接口支持固定种子（`seed`）以保证随机流程可复现，便于测试与验收。
- 业务错误码约定：`BATTLE_NOT_FOUND`、`BATTLE_FINISHED`、`INVALID_ACTION` 等稳定 errorCode。

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

---

## 15. 养成系统实现约定（阶段 4 起）

### 15.1 模块结构与职责

```text
pet/
├── domain/PetGrowthService      # 面板属性公式、升级经验、自由点数、技能解锁（无状态、仅读配置）
├── domain/PetPanelStats         # 面板属性 + 六维分解（base/growth/aptBonus/freeBonus/total）
├── service/PetService           # 升级/加点/洗点/技能装配编排（事务化、调用 PetGrowthService）
├── service/PetDetail            # 宠物详情聚合 DTO（基础/属性/技能三标签一次返回）
└── controller/PetController     # REST 接口

team/
├── service/TeamService          # 队伍成员整体替换（单事务）
└── controller/TeamController    # PUT /api/team/members

inventory/
├── service/InventoryService     # 背包查询 + 恢复道具使用（HEAL_HP/REVIVE）
└── controller/InventoryController

battle/service/BattleService      # 新增 settleBattle：战斗结算落库（HP回写/经验/金币/掉落/统计）
```

### 15.2 面板属性公式（核心规则）

> 最终属性 = 种族基础（含个体浮动） + 等级固定成长 + 资质成长修正 + 自由属性点。

- **种族基础**：`species.baseXxx + pet.baseXxxOffset`（个体浮动固化值，捕捉时由 `baseStatVariance` 随机生成）。
- **等级成长**：`levelStatGrowth * (level - 1)`（HP 维度独立用 `levelHpGrowth`）。
- **资质修正**：`growth * (aptitude - 50) / 100`（资质影响升级成长而非基础值；Lv.1 时资质无影响）。
- **自由点数**：HP 维度 `freePointHp * freePointHpValue`，其余 `freePointXxx * freePointStatValue`。
- 所有数值参数从 `SystemRuleConfig` 读取，**禁止硬编码**。

### 15.3 升级经验与自由点数

- 升级经验：`expBase * expGrowthFactor^(level-1)`，达到 `levelCap` 返回 0。
- 经验统一进玩家**公共经验池**（无上限），升级时由玩家自主分配扣减；**不直接发经验给宠物**。
- 五种升级方式（`LevelUpMode`）：`ONE` / `FIVE` / `TO_LEVEL` / `TO_CAP` / `CUSTOM_EXP`，全部走 `PetService.levelUp`。
- 自由点数：每级 `freePointsPerLevel`（默认 3）+ 稀有度每 10 级额外（COMMON 0 / RARE 2 / EPIC 4 / LEGENDARY 6）。
- 加点消耗：HP `hpPointCost`（默认 1）、速度 `speedPointCost`（默认 2）、其余 `statPointCost`（默认 1）。
- 洗点第一阶段免费，按需求 §20 转换表全量返还已消耗自由点数（速度每点次按 2 点折算）。

### 15.4 技能解锁与装配

- 升级自动**学习**解锁的种族技能（`unlockLevel` 命中区间），默认**不装备**。
- 最多 4 个主动技能槽位（slot 1~4），装备/卸下通过 `PetService.equipSkill` / `unequipSkill`。
- 已学习但未装备的技能在技能标签展示，供玩家手动装配。

### 15.5 战斗结算（事务化）

- 接口：`POST /api/battles/{id}/settle`，仅在 `battle.finished=true` 时允许。
- 单事务内完成：HP 回写（玩家宠物）、经验进公共池、金币发放、掉落入背包、战斗统计累加。
- **防重复结算**：已结算战斗返回 `BATTLE_ALREADY_SETTLED` errorCode。
- 战败零惩罚：经验/金币/掉落均为 0，仅回写 HP。

### 15.6 队伍成员替换

- 接口：`PUT /api/team/members`，整体替换 6 槽位（位置 1~6）。
- 单事务内完成：删除旧成员、插入新成员；位置 1~6 唯一、宠物归属校验。
- 候补可为空，但位置必须连续（1~N，N ≤ 6）。

### 15.7 背包恢复道具

- 接口：`POST /api/inventory/items/{itemId}/use`，请求体 `{ petId }`。
- `HEAL_HP`：`currentHp = min(currentHp + value, maxHp)`；`REVIVE`：仅 HP=0 可用，`currentHp = maxHp * value / 100`。
- 战斗外使用校验 `usableOutsideBattle`。桌面重构 R-043 覆盖旧“战斗内无道具”裁决：战斗内只允许 `usableInBattle=true` 且类型为 `HEAL_HP`/`REVIVE` 的道具，探索/战斗共享玩家库存；开战时生成资源快照，战斗中仅记内存消耗，结算统一扣库。前端过滤不能替代服务端的 `usableInBattle`、类型、数量和目标校验。
- 数量不足返回 `ITEM_NOT_ENOUGH`，宠物不存在返回 `PET_NOT_FOUND`。

### 15.8 数据库迁移（V3）

- `player_pet` 表新增 `captured_level`（捕捉时等级，用于放生礼物计算）、六维 `base_*_offset`（个体基础浮动固化值）。
- 迁移文件 `V3__pet_growth_fields.sql`，**禁止手工改表**。

### 15.9 道具配置扩展

`items.yml` 新增字段：
- `category`：分类（CAPTURE / RECOVERY / MATERIAL / SKILL_BOOK / KEY_ITEM）。
- `usableOutsideBattle`：战斗外是否可用。
- `usableInBattle`：战斗内是否可用（第一阶段不实现战斗内使用，字段仅用于配置完备性）。
- `value`：恢复量（HP 点数或百分比）；捕捉球（itemType=CAPTURE_BALL）时为捕获倍率（1.0/1.5/2.5）。

---

## 16. 捕捉与仓库系统实现约定（阶段 5 起）

### 16.1 模块结构与职责

```text
capture/
├── WildEncounterService             # 野生遭遇生成（刷新组权重/等级/资质/个体浮动/稀有技能/特殊外观）
└── controller/WildBattleController  # POST /api/wild/battles、GET capture-rates、开发者补球入口

storage/
├── PetStorageService                # 仓库筛选排序/昵称/锁定/收藏/放生预览与执行
├── ReleaseGiftCalculator            # 放生礼物价值点数与抽取（纯函数）
└── controller/PetStorageController  # /api/storage/**

battle/calculator/CaptureCalculator  # 捕捉率公式（纯函数，需求 §46）
battle/service/BattleService         # 扩展 startWildBattle/getCaptureRates/settleBattle(WILD)
```

### 16.2 种族配置唯一来源

- 种族数据唯一来源为 `game-config/pets/pets.yml`（`registry.getSpecies()`）；`initial-pets.yml` 仅声明「speciesId + 资质覆盖 + 初始道具」，不复制种族数值。
- 野生单位与玩家宠物共用同一面板公式（`PetGrowthService.computePanelStats`），保证捕捉前后属性一致。

### 16.3 捕捉规则

- 捕捉率 = 基础捕获率（种族配置）× (1 − captureHpFactor × HP比例) × (1 + statusCaptureBonus × min(异常数, captureStatusMaxCount)) × 球倍率 × 精英系数，clamp [0,1]；**不添加文档之外修正项**。
- 捕捉/逃跑是 BattleEngine 的普通行动类型（CAPTURE/FLEE，仅 WILD 战斗可用），禁止另建捕捉专用战斗逻辑。
- 捕捉球无论成败均消耗：战斗内只记入 BattleContext（consumedCaptureBalls），结算时同事务从背包扣除；开战时快照背包存量供战斗内数量校验。
- 捕捉成功目标立即退出敌方队伍（不触发倒下/击败被动），候补补位；被捕捉宠物不结算击败掉落、不参与野生奖励。
- 逃跑成功率 `fleeSuccessRate` 配置化（当前裁决为必定成功），同战败结算（HP 回写、无奖励、无胜方）。

### 16.4 捕捉结算与去向

- 野生战斗结算（`POST /api/battles/{id}/settle`，可选 body `{ joinTeam }`）同事务完成：HP 回写、捕捉落库、捕捉球扣除、野生奖励。
- 捕捉落库：等级=捕获等级、六维资质/个体浮动/特殊外观=遭遇生成时固化数据、HP 保留捕捉时余量、已解锁种族技能按配置槽位装备、稀有技能 sourceType=CAPTURE 仅学习不装备、capturedMapId/capturedAt 记录。
- 去向选择：joinTeam=true 且队伍未满 6 只时直接入队（TeamService.addPetToActiveTeam），否则留在仓库；队伍已满时静默留仓库不阻断结算。
- 野生奖励 = 遭遇组每级基础值 × 敌等级 × 稀有度系数（wildRewardRarityMultiplier）。

### 16.5 仓库与放生

- 仓库不限容量，直接查询 player_pet 全量（筛选/排序在服务层内存完成）。
- 稀有技能定义：`player_pet_skill.source_type = 'CAPTURE'` 的技能记录。
- 放生保护：锁定/收藏/在队宠物禁止放生；单只受保护明确报错，批量自动排除并返回 skipped 明细。
- 放生礼物（决策七）：应得点数 = 稀有度基础值（releaseGiftBaseValue）× 捕获等级系数（1+等级×releaseLevelFactorPerLevel，上限 releaseLevelFactorCap）× 培养系数（1.0～releaseCultivationFactorMax，以已分配自由点数/releaseCultivationPointsCap 度量）；从 release-gifts.yml 池按权重抽取至累计单位价值 ≥ 应得点数。
- 放生同事务：删除宠物与技能记录 + 发放礼物（GOLD/EXP/ITEM）；无任何放生记录写回（第一阶段无统计需求）。

### 16.6 数据库

- 阶段 5 无新增 Flyway 迁移：仓库字段（locked/favorite/specialAppearance/captured_map_id/captured_at）已在 V2 迁移建立，captured_level/base_*_offset 已在 V3 建立。

---

## 17. 地图探索与区域系统实现约定（阶段 6 起）

### 17.1 模块结构与职责

```text
map/
├── service/MapExplorationService      # 核心服务：区域解锁/移动/营地/采集/宝箱/遭遇/战败
├── controller/MapController           # REST 接口：/api/maps/**
├── entity/                            # 5 实体（复合主键无 @TableId）
└── mapper/                            # 5 mapper

config/model/MapsConfig                # 地图配置模型（region/exit/camp/gather/chest/reward）
```

### 17.2 地图配置约定

- 配置唯一来源：`game-config/maps/maps.yml`（`MapsConfig`），包含区域/出口/营地/采集/宝箱/奖励定义。
- 区域解锁类型：`AUTO`（自动）/`BOSS`（Boss 击败，阶段 7）/`QUEST`（任务完成，阶段 9）；本阶段仅 AUTO。
- 出口解析：客户端传 `exitId`，后端从当前区域配置解析对应 `entryObjectId`，避免前端伪造。
- `planned: true` 的区域仅结构预留，不参与运行时解锁/加载。
- 采集/宝箱奖励通过 `reward` 字段配置（type: GOLD/EXP/ITEM，value 数量），奖励发放由 InventoryService 完成。

### 17.3 地图刷新与会话

- 每次进入区域生成新 `sessionId`（UUID），存入 `player_map_session`。
- 采集记录（`player_gather_used`）与会话绑定：新会话可重新采集。
- 宝箱记录（`player_chest_loot`）永久绑定：不受会话刷新影响。
- 离开区域、营地休息、营地传送均触发新会话。

### 17.4 营地系统

- 营地免费恢复全队 HP（含倒下宠物复苏至 maxHp）。
- 首次进入区域自动激活该区域营地（`autoActivate`）。
- 已激活营地间可免费快速传送；传送触发地图刷新（视为重新进入区域）。

### 17.5 战败流程

- `MapExplorationService.handleDefeat(player)`：退出战斗 → 返回最近营地（当前区域已激活营地）→ 全队恢复至 maxHp → 生成轻度嘲讽提示（从 `defeatMessages` 配置抽取）。
- 在 BattleService 结算同一事务内调用，避免部分成功脏数据。
- 战败零惩罚：不扣金币、不扣经验、不掉物品、不丢宠物。

### 17.6 区域解锁懒写入

- `ensureAutoUnlocks(saveId)`：查询玩家已解锁区域，补齐所有 AUTO 类型区域的解锁记录。
- 新游戏创建后首次访问地图时懒写入，避免新游戏流程耦合地图逻辑。

### 17.7 与战斗系统集成

- `BattleService` 注入 `MapExplorationService`（构造函数参数）。
- `startTestBattle`/`startWildBattle` 添加 `requireFightablePet`：队伍中所有宠物 currentHp=0 → `NO_FIGHTABLE_PETS` 业务错误。
- 战斗结算 `settleBattle` 失败时（!playerWon && !fled）调用 `mapExplorationService.handleDefeat`。
- `hasActiveBattle()` 供 TeamService 检测战斗状态。

### 17.8 与队伍系统集成

- `TeamService` 新增 5 套预设支持：`getTeamPresets()`（懒创建）、`activatePreset(teamId)`。
- `@Lazy` 注入 `BattleService` 避免循环依赖（TeamService ↔ BattleService）。
- `requireNotInBattle()` 守卫：战斗中拒绝预设切换/成员编辑。
- `updateTeamMembers` 支持 `teamId` 参数（null = 当前激活预设）。

### 17.9 数据库迁移（V4）

- 5 张玩家状态表：
  - `player_region_unlock`（save_id + map_id，区域解锁记录）
  - `player_camp_activation`（save_id + camp_id，营地激活记录）
  - `player_chest_loot`（save_id + chest_id，宝箱一次性消耗）
  - `player_map_session`（save_id + map_id，地图会话 UUID）
  - `player_gather_used`（save_id + gather_id + session_id，采集会话绑定）
- 复合主键无 @TableId，insert/select/delete via wrapper。
- 迁移文件 `V4__map_exploration_tables.sql`，**禁止手工改表**。

---

## 18. Boss 系统与重复挑战实现约定（阶段 7 起）

### 18.1 模块结构与职责

```text
boss/
├── service/BossService              # 核心服务：开战/结算/自动挑战/幸运兑换/情报
├── controller/BossController        # REST 接口：/api/bosses/**
├── entity/                          # 5 实体（复合主键无 @TableId）
└── mapper/                          # 5 mapper

config/model/BossesConfig            # Boss 配置模型（BossConfig > DifficultyConfig > PhaseTrigger / DropEntry）
```

### 18.2 Boss 配置约定

- 配置唯一来源：`game-config/bosses/bosses.yml`（`BossesConfig`），包含 Boss 基础信息 + 3 难度配置。
- 每个 Boss 包含：id、name、mapId、element、recommendedLevel、difficulties（NORMAL/HARD/NIGHTMARE）。
- 每个难度包含：stats、skills、passives、phases、drops、luckGain。
- 掉落分 4 档稀有度（COMMON/RARE/EPIC/LEGENDARY），对应情报解锁阈值（1/3/6/10 次）。
- 阶段触发器（PhaseTrigger）：hpPercent + effects（ADD_SKILL/ADD_SHIELD/BUFF_SELF），每触发器仅激活一次。
- 配置校验：`GameConfigValidator.validateBosses()` 检查 element/skills/passives/items/maps 引用完整性、HP 阈值合法性、概率范围。

### 18.3 控制抗性与连续衰减

- 系统规则配置（`system.yml`）：`controlResistance`（精英 0.8/Boss 0.6）、`consecutiveControlDecay` [1.0, 0.7, 0.4]、`controlDecayResetRounds`（默认 2）。
- `BattleUnit` 新增字段：`controlResistance`、`consecutiveControlCount`、`roundsWithoutControl`、`phaseTriggers`。
- 施加控制类状态（SPECIAL_CONTROL）时：先乘目标 controlResistance，再乘连续衰减系数，判定命中。
- 每回合结束时：未处于控制状态则递增 roundsWithoutControl，达到阈值时归零 consecutiveControlCount。

### 18.4 BattleService Boss 集成

- `startBossBattle(bossId, difficulty, seed)`：校验 Boss 存在 + 难度已解锁 + 队伍有可战斗宠物；构建 Boss 敌方（controlResistance + phaseTriggers）；使用 bossEngine 开战。
- 双引擎实例（同一 BattleEngine 类，仅 DecisionProvider 不同）：`engine`（WildEnemyDecisionProvider，TEST/WILD）与 `bossEngine`（BossDecisionProvider，BOSS）；`submitActions` 按 `battleType` 路由（`engineFor`）。
- `createBossBattle(...)`：创建 Boss 战斗上下文但不 startBattle（自动挑战专用，`runFullBattle` 内部统一开战，避免登场被动重复触发）。
- `BattleContext` 新增字段：`bossId`、`bossDifficulty`、`uncapturable`（Boss 战斗禁止捕捉/逃跑）。
- 结算扩展：BOSS 类型胜利时发放掉落/经验/金币/击败次数/幸运值/难度解锁；战后自动恢复全队 HP。
- `runFullBattle(playerAI)`：AI vs AI 跑完整个战斗（自动挑战使用）。

### 18.5 自动挑战与幸运兑换

- 自动挑战 5 种模式：`ONCE`/`FIVE`/`TEN`/`UNTIL_FAIL`/`UNTIL_LUCKY`；需已手动击败过对应难度（`player_boss_manual_clear`）。
- 循环：自动恢复 → runFullBattle → settleBattle → 检查停止条件。
- 幸运兑换：幸运值 >= 100 时可兑换已公开掉落池物品；扣幸运值 + 发放物品同事务。
- 幸运值每 Boss 独立，不同难度共享；按难度 +4/+7/+10。

### 18.6 Boss AI

`BossDecisionProvider` 为评分式规则 AI（候选行动生成 → 过滤 → 评分 → 选最高分，接近分用 `ctx.getRandom()` 小幅随机），详细设计见 `docs/technical/BOSS_AI_REWORK.md`：

- **候选行动**：按「技能 × 目标」组合生成；攻击估算复用 `DamageCalculator`（基础值/减伤）+ 现有克制表 + 本属性加成；治疗/护盾估算复用 `HealCalculator`。
- **属性克制与斩杀**：克制倍率直接进入估算伤害；预计伤害 ≥ 目标 HP+护盾时追加斩杀奖励；低 HP 目标加权。
- **阶段策略**：阶段索引 = 已激活 phaseTrigger 数量（引擎维护，AI 只读）；一阶段均衡 / 二阶段进攻（攻击 ×1.3）/ 三阶段爆发（攻击 ×1.6、关键控制回升、治疗降权）；倍率配置化（`system.yml bossAi`）。
- **控制策略**：估算成功率 = chance × 目标 controlResistance × consecutiveControlDecay（与引擎同公式，仅估算不改结算）；目标已受控时大幅降权，避免机械连续控制。
- **治疗策略**：友方 HP% < `healTriggerHpPercent`（0.40）时紧迫度提高；过量治疗不计分；HP% > `healNoNeedHpPercent`（0.90）不治疗。
- **边界 fallback**：沉默/无就绪技能/无候选 → DEFEND；死亡单位不进入任何候选。
- **禁止玩家动态缩放**（需求 §80）：不读取玩家等级/战力/属性总和，难度仅来源于 Boss 配置、技能配置、阶段与战场状态。
- 普通野生敌人仍使用 `WildEnemyDecisionProvider`，不受 Boss AI 影响。

### 18.7 数据库迁移（V5）

- 5 张 Boss 进度表：
  - `player_boss_defeat_count`（save_id + boss_id + difficulty，击败次数）
  - `player_boss_difficulty_unlock`（save_id + boss_id + difficulty，难度解锁记录）
  - `player_boss_luck`（save_id + boss_id，幸运值）
  - `player_boss_drop_unlock`（save_id + boss_id + rarity，掉落情报解锁）
  - `player_boss_manual_clear`（save_id + boss_id + difficulty，手动击败记录，自动挑战解锁校验）
- 复合主键无 @TableId，insert/select/delete via wrapper。
- 迁移文件 `V5__boss_tables.sql`，**禁止手工改表**。

---

## 19. 图鉴系统实现约定（阶段 8 起）

### 19.1 模块结构与职责

```text
pokedex/
├── service/PokedexService           # 核心服务：研究值累积/等级计算/信息解锁/历史记录/野外识别
├── controller/PokedexController     # REST 接口：/api/pokedex/**
├── entity/                          # 2 实体（复合主键无 @TableId）
├── mapper/                          # 2 mapper
└── vo/                              # PokedexEntryVo / PokedexDetailVo / PokedexHistoryVo / WildIdentificationVo

config/model/SystemRuleConfig.PokedexRuleConfig  # 图鉴配置内部类（嵌入 system.yml）
```

### 19.2 图鉴配置约定

- 配置嵌入 `system.yml` 的 `pokedex` 段，通过 `SystemRuleConfig.PokedexRuleConfig` 反序列化。
- 研究等级门槛 `levelThresholds`：Map<Integer, Integer>，键为等级（1~5），值为所需累计研究值，必须严格递增。
- 研究值来源分值（11 种）：`firstDiscoveryPoints`、`firstCapturePoints`、`subsequentCapturePoints`、`battleParticipationPoints`、`battleWinPoints`、`skillUnlockPoints`、`highAptitude80Points`、`highAptitude90Points`、`rareSkillDiscoveryPoints`、`specialAppearancePoints`、`eliteCapturePoints`。
- 资质预估等级标签 `aptitudeGrades`：Map<String, Integer>，键为等级标签（S/A/B/C），值为最低资质阈值。
- 配置校验：`GameConfigValidator.validatePokedexRules()` 检查门槛严格递增、分值非负、资质等级合法。

### 19.3 研究等级与信息解锁

- 研究等级 Lv.0~5，由累计研究值与门槛配置决定。
- 保底规则：`seen=true` 至少 Lv.1，`caught=true` 至少 Lv.2（即使研究值未达到门槛）。
- 信息解锁层级：Lv.0 仅「???」、Lv.1 名称/属性/描述、Lv.2 +稀有度/捕获率、Lv.3 +技能/被动/六维基础、Lv.4 +稀有技能池/出现区域、Lv.5 +历史记录/特殊外观/进化占位。
- PokedexService 根据等级过滤返回信息，未解锁字段为 null。

### 19.4 既有行为接入

- **BattleService**：`startWildBattle` 遍历敌方 speciesId 调用 `recordDiscovery`；`settleCaptures` 捕捉成功后调用 `recordCapture`；`settleBattle` 收集参战/获胜种族 ID 调用 `recordBattleParticipation`/`recordBattleWins`。
- **PetService**：`levelUp` 解锁新种族技能时调用 `recordSkillUnlock`。
- **GameService**：`createNewGame` 初始宠物创建后调用 `recordCapture` 记录发现与捕获。
- 所有记录方法使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，记录失败不阻断主流程。

### 19.5 数据库迁移（V6）

- 2 张图鉴进度表：
  - `player_pokedex`（save_id + species_id，研究进度：research_points/seen/caught/first_seen_at/first_caught_at）
  - `player_pokedex_history`（save_id + species_id，历史记录：total_captures/total_defeats/elite_encounters/special_appearances/best_*/discovered_rare_skills）
- 复合主键无 @TableId，insert/select via LambdaQueryWrapper。
- 历史记录放生不清除。
- 迁移文件 `V6__pokedex_tables.sql`，**禁止手工改表**。

---

## 20. 任务系统实现约定（阶段 9 起）

### 20.1 配置驱动

- 任务配置位于 `game-config/quests/quests.yml`，含主线 12 节点 + 支线 10 + 隐藏 3、NPC 对话约 10、教学 8 步。
- `QuestsConfig` 模型：`QuestConfig`（id/name/type/description/prerequisiteQuestId/hidden/trigger/objectives/rewards/mapChanges）、`ObjectiveConfig`（type: DIALOGUE/GATHER/CAPTURE/DEFEAT/DEFEAT_BOSS/ARRIVE）、`RewardConfig`（fixed/choices/giftPet）、`HiddenTriggerConfig`（triggerType: LOCATION/PET/ITEM/DIALOGUE_COUNT）、`MapChangeConfig`（changeType: OPEN_SHORTCUT/ADD_MERCHANT/REPAIR_ROAD/OPEN_RESTORE_POINT）、`NpcConfig`（npcId/dialogues）、`TutorialStepConfig`（stepId/skippable/rewards）。
- `GameConfigRegistry` 新增 `getQuest()`、`getNpc()`、`getQuestsByRegion()`、`getMainQuests()`、`getSideQuests()`、`getTutorials()` 等索引查询。
- `GameConfigValidator.validateQuests()` 校验：ID 唯一性、prerequisite 引用完整性、目标类型枚举合法性、targetId 引用完整性、奖励道具引用、赠送宠物种族引用、NPC regionId 引用。

### 20.2 任务状态机

- 状态流转：`AVAILABLE` → `ACTIVE` → `COMPLETED`。
- 前置任务支持逗号分隔多前置（如 `QUEST_MAIN_09` 需要 `QUEST_MAIN_07,QUEST_MAIN_08`）。
- 并行分支：森林 Boss 后水域+雷域同时解锁（`unlockRegionId: MAP_AREA_WATERS,MAP_AREA_THUNDER`），两者都完成后解锁遗迹。
- 隐藏任务通过 `HiddenTriggerConfig` 触发（LOCATION/PET/ITEM/DIALOGUE_COUNT 类型），触发后才可见于任务列表。

### 20.3 事件驱动推进

- `QuestService.checkObjectiveProgress(saveId, eventType, targetId, count)` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 传播。
- 内部 try-catch 不阻断主流程。
- 事件钩子接入点：
  - `BattleService.settleBattle()` → CAPTURE/DEFEAT/DEFEAT_BOSS
  - `MapExplorationService.enterRegion()` → ARRIVE + LOCATION 隐藏触发器
  - `MapExplorationService.gather()` → GATHER
  - `NpcDialogueService.talk()` → DIALOGUE + DIALOGUE_COUNT 隐藏触发

### 20.4 任务完成事务

- `completeQuest()` 在单事务内完成：状态更新 + 固定奖励发放 + 区域解锁 + 地图变更激活 + 赠送宠物 + 通关标记。
- 三选一奖励通过 `chooseReward()` 单独调用，锁定后不可更改。
- 赠送宠物完整流程：创建 `PlayerPetEntity` → 种族技能装备 → 额外技能 → 图鉴补录 → HP 计算。
- 通关条件：最终遗迹区域 NORMAL Boss 首通，标记 `PlayerEntity.storyCompleted = true`。

### 20.5 循环依赖处理

- QuestService 注入 PokedexService 使用 `@Lazy`。
- BattleService / MapExplorationService 注入 QuestService 使用 `@Lazy`。

### 20.6 数据库迁移（V7）

- 6 张任务系统表（复合主键无 @TableId）：
  - `player_quest`（save_id + quest_id）
  - `player_quest_objective`（save_id + quest_id + objective_id）
  - `player_dialogue`（save_id + npc_id）
  - `player_tutorial`（save_id + step_id）
  - `player_map_change`（save_id + change_id）
  - `player_hidden_trigger`（save_id + trigger_key）
- ALTER `player` 表新增 `story_completed` 字段。
- 迁移文件 `V7__quest_tables.sql`，**禁止手工改表**。

---

## 21. 阶段 10 系统实现约定（效率、经济与随机内容）

### 21.1 道具体系补齐

- `ItemConfig` 新增字段：`skillId`（SKILL_BOOK 类引用 skills.yml）、`price`（商店售价，0=不可购买）。
- 新增道具：9 属性材料（MATERIAL）、5 Boss 核心（MATERIAL）、净化药（RECOVERY）、10 技能书道具。

### 21.2 商店系统

- 配置：`game-config/shop/shop.yml`，商品含 `itemId` / `price` / `unlockQuestId`（主线任务解锁）。
- `ShopService`：查询商品列表（含解锁状态，金币/商品从 `player` + `shop.yml` 读取）；购买在**单事务**内校验金币充足 → 扣金币 → 入背包。
- `ShopController`：`GET /api/shop`、`POST /api/shop/buy`。解锁校验直接查 `player_quest` 任务 COMPLETED 状态（无独立解锁表）。
- 商店无每日刷新、无限购（明确不做）。

### 21.3 技能书系统

- `SkillConfig` 新增 `learnRestriction`（可选：elements/rarities/speciesIds/excludeSpeciesIds）与 `exclusive`（专属技能不可学习）。
- `PetService` 新增四接口：
  - `learnSkillBook(petId, itemId, forgetSkillId)`：校验道具为 SKILL_BOOK + 背包持有 + 学习限制 + 非专属 + 上限 10（超限需 forgetSkillId）→ 扣道具 → 写 `player_pet_skill`（source_type=SKILL_BOOK）。
  - `forgetBookSkill(petId, skillId)`：仅 source_type=SKILL_BOOK 可遗忘，如已装备则卸下。
  - `equipBookSkill(petId, skillId, bookSlot)` / `unequipBookSkill(petId, bookSlot)`：书槽 5~6（与自身槽位 1~4 独立）。
- `PetDetail` 新增 `bookSkillSlots` / `learnedBookSkills` / `bookSkillLearnCount`。
- 已学技能排序使用 `Comparator.nullsLast` 避免未装备技能（slot=null）引发 NPE。

### 21.4 精英个体与特殊外观

- `SystemRuleConfig` 新增 elite 配置段（spawnChance/levelBonusMin/levelBonusMax/minAptitudeFloor/rareSkillChanceBonus）与 `specialAppearanceVariants` 列表。
- `WildEncounterService` 按概率生成精英（等级加成 + 资质下限 + 稀有技能概率），标记 `isElite=true`；特殊外观从多变体按概率抽取。
- `BattleUnit.WildUnitData` 与 `UnitSnapshot` 新增 `elite` 字段供前端展示。

### 21.5 随机事件

- 配置：`game-config/events/random-events.yml`，事件含 regionIds 限定与选项权重结果（GIFT_GOLD/GIFT_ITEM/GIFT_MATERIAL/TRIGGER_BATTLE/TRIGGER_CAPTURE/NOTHING）。
- `RandomEventService`：`rollRandomEvent(mapId, random)`（15% 概率触发，区域筛选）；`resolveEventOption(eventId, optionId, random)`（按权重抽取结果）。
- `MapController`：`GET /api/maps/events/roll`、`POST /api/maps/events/resolve`。

### 21.6 隐藏遭遇与埋伏

- `encounters.yml` 新增隐藏遭遇组（hidden）；`maps.yml` 区域新增 `ambushSpots`（ambushId/encounterGroupId/chance/oneTime）。
- 一次性埋伏触发后记录到 `player_ambush_triggered`，不再重复。

### 21.7 推荐 Build

- 配置：`game-config/builds/build-recommendations.yml`（按种族分组，statPriority/skillPriority）。
- `GET /api/pets/{petId}/build-recommendations` 纯展示，不修改玩家数据。

### 21.8 配置校验

- `GameConfigValidator` 新增：`validateShop()`（商品引用/price 非负/unlockQuestId 引用）、`validateRandomEvents()`（事件 ID 唯一/区域/道具/遭遇组引用）、`validateBuildRecommendations()`（种族/技能引用）。

### 21.9 数据库迁移（V8）

- 2 张表（复合主键无 @TableId）：
  - `player_ambush_triggered`（save_id + ambush_id）一次性埋伏记录
  - `player_random_event_used`（save_id + event_id + session_id）会话事件去重
- 迁移文件 `V8__phase10_tables.sql`，**禁止手工改表**。

---

## 22. 动态难度与 Boss 遭遇快照约定（阶段 13）

- 全局难度仅由 `system.yml.gameDifficulty` 的 `NORMAL/ELITE/NIGHTMARE/HELL` 档位驱动；业务代码不得散落按难度 ID 的分支。
- 地图正式野外遭遇必须调用 `WildEncounterService.generateEncounter(groupId, region, teamPets, difficulty, random)`：最终等级同时受地图边界、难度边界和游戏等级上限约束；队伍数量同时受刷新组、难度和标准上场位约束。
- `BattleService` 是野外和 Boss 的唯一战斗装配入口；难度只决定临时 `BattleUnit`，不得改变宠物存档等级、经验、技能学习记录或自由点。
- Boss 首次入场前由 `BossEncounterSnapshotService` 写入 `player_boss_encounter_snapshot`；唯一键为 `save_id + boss_id + boss_difficulty`。后续手动重试、自动挑战和服务重启后的挑战都只读取该快照。
- 切换全局难度不改写既有快照；仅当当前全局难度与快照难度不同时，才允许显式重置，且同难度不得重掷。
- `BattleLevelResolver` 必须复用 `PetGrowthService.freePointsEarned()` 与 `computePanelStatsAtLevel()`，以稳定最大余数法投影超出有效等级预算的自由点；战斗通用 `BattleUnit.level` 保存有效等级，`actualLevel` 仅作展示与结算回写参考。
- V11 迁移新增 `player.game_difficulty` 与 Boss 快照表；所有存档结构变更仍只能经 Flyway 迁移完成。

## 23. 存档备份与开发者工具约定（阶段 14）

### 23.1 存档备份（`save` 模块）

- 存档文件为自定义 `.pet-save.zip`，内部含 `manifest.json`（gameVersion / saveVersion / exportedAt / playerName）与 `save.json`（全量玩家逻辑数据），**不导出数据库物理文件、不落配置内容**，玩家数据只保存引用（species_id / skill_id 等）。
- 版本职责分离：gameVersion 发布版本、saveVersion 存档结构版本、configVersion 配置结构版本；导入时仅校验 saveVersion（高于当前拒绝，等于 / 低于可导入）。
- 导入流程固定为：校验文件 → 检查 saveVersion → **导入前自动备份当前存档** → 清理旧单主存档 → 事务内导入 → 失败回滚；备份或清理步骤不得省略。
- 快照以 `save_id` 为键读取 / 写入；仅 `player_pet`、`player_team` 两张自增主键被他人引用的表在导入时做 id 重映射（petId / teamId / petId 引用一并重映射），其余表无需重映射。
- 自动备份触发点：导入前（`import-before`）、重置游戏前（`reset-before`）、开发者高风险操作前（`dev-before`）；**不做定时后台备份**。
- 备份目录由 `game.backup-dir` 配置（默认 `./data/backups`）。

### 23.2 开发者工具（`developer` 模块）

- 开发者模式由 `game.developer-mode` 开关控制（默认关闭），`DevController(/api/dev/*)` 统一校验，未开启一律拒绝（`DEV_MODE_DISABLED`）。
- 数据操作类工具（资源 / 宠物 / 地图 / Boss）由 `DevService` 实现；**高风险数据修改前必须调用 `SaveBackupService.createBackup("dev-before")` 自动备份**（备份失败不阻断开发者操作，仅记录日志）。
- 全部开发者操作写入 `dev_operation_log`（action 形如 `dev.grantGold`，detail 为可读文本）。
- 跨请求一次性标志（强制精英 / 强制随机事件）放入 `DevContext`（内存原子布尔），被 `WildEncounterService` / `RandomEventService` 消费后清除，写入端与消费端均不得在多处重复实现。
- 战斗调试类（无敌 / 一击必杀 / 固定暴击 / 固定随机种子 / 伤害明细）与随机数调试本次未实现，留待阶段 14 后续子批次，禁止提前以临时实现补位。
- V12 迁移新增 `dev_operation_log` 表。

## 24. 前端存档备份与开发者工具约定（阶段 14）

- 存档备份页 `/save-backup`：导出走浏览器直接下载（`/api/save/export` 返回二进制流，不走 JSON 拦截器）；导入走 `multipart/form-data` 上传。
- 开发者工具页 `/dev-tools`：仅当 `gameStore.developerMode` 为真时在导航显示；未开启时页面提示需在服务端开启后重启。

---

## 25. 桌面版世界/UI 重构后端基线（阶段 0 起）

- 世界静态事实、玩家知识、动态世界状态和临时 UI/战斗 Context 必须分层；数据库只保存玩家引用与动态状态，不复制 YAML 内容。
- WorldGraph 是 Region、Map、Connection、Gateway、Anchor 和 Landmark 的唯一拓扑事实源；Tiled 只承载画面、碰撞和对象放置，不能独立定义业务连接。
- 现有 Region=Map 模型先通过兼容映射读取，随后按森林纵切逐步迁移；禁止一次性改写全部旧 ID。
- 所有存档表结构变更只新增 Flyway；当前 `saveVersion=1` 的新游戏、主线中段、内容完成三个样本位于 `backend/src/test/resources/save-fixtures`，每次调整存档结构必须做兼容导入回归。
- 动态世界使用游戏内有限状态，不绑定现实时间；不新增后台定时基础设施、消息队列或缓存系统。
- 需求裁决、版本边界和迁移顺序以 `docs/development/PHASE0_BASELINE.md` 为准。
