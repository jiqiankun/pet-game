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
- 战斗外使用校验 `usableOutsideBattle`；战斗内不使用恢复道具、不提供道具行动（用户裁决，见规划文档 §9.3 决策八）。
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
├── controller/MapController           # REST 接口：/api/map/**
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

`BossDecisionProvider` 为评分式规则 AI（候选行动生成 → 过滤 → 评分 → 选最高分，接近分用 `ctx.getRandom()` 小幅随机），详细设计见 `docs/BOSS_AI_REWORK.md`：

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
