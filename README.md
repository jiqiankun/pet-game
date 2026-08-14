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

当前阶段：**阶段 10（效率、经济与随机内容系统）— 已完成**

已完成阶段：阶段 0、阶段 1、阶段 2、阶段 3、阶段 4、阶段 5、阶段 6、阶段 7、阶段 8、阶段 9、阶段 10

下一阶段：阶段 11（以规划文档为准）

详细的阶段划分与进度跟踪见 [`AGENTS.md`](AGENTS.md) §6「当前阶段状态」。

### 阶段 0 完成内容

- 前端 Vue 3 + TypeScript + Vite 骨架（11 个一级页面占位路由、API 封装层、全局样式、Vite 开发代理）
- 后端 Java 21 + Spring Boot 3.5.x 骨架（统一响应结构、全局异常处理、健康检查接口、Flyway 初始迁移、15 个业务模块占位）
- 统一构建脚本 `build.bat` / `build.sh`（前端 build → 静态资源复制 → Maven package → release 输出）
- 启动脚本 `start.bat` / `start.sh`
- 外部配置示例 `config-example/game/system.yml`
- 单元测试（ApiResponse 结构验证、HealthController 接口验证）

### 阶段 1 完成内容

- 游戏配置体系：9 种属性定义与克制关系（五行环 + 副链 + 光暗互克）、系统规则配置（克制倍率、暴击参数、等级上限、队伍数量、资质范围等）
- 配置加载机制：JAR 内部默认配置 + 外部配置目录同 ID 覆盖（`GameConfigLoader`）
- 启动校验：ID 重复、引用不存在、非法概率/倍率/等级等严重错误时启动失败（`GameConfigValidator`）
- 统一配置注册中心：`GameConfigRegistry` 提供克制倍率查询、属性索引等运行时能力
- 配置查询 API：`/api/game/config/elements`、`/api/game/config/advantage`、`/api/game/config/system`
- 统一随机工具 `GameRandom`（支持固定种子，可复现完整随机流程）
- 配置目录骨架：`resources/game-config/`（system.yml、elements.yml + pets/skills/bosses/items/drops/shops/quests 子目录）
- 单元测试（GameRandom 种子可复现/范围/边界、配置校验 8 种错误场景、克制倍率查询 10 种关系）

### 阶段 2 完成内容

- 玩家存档数据模型：player / player_pet / player_pet_skill / player_team / player_team_member / player_inventory / game_setting 七张表（Flyway V2 迁移）
- 新游戏流程：名称 + 预设形象 + 初始宠物三选一（烬牙兽/汐月灵/藤梦鹿），配置驱动，初始宠物资质全 A
- Bootstrap 聚合接口 `GET /api/game/bootstrap`：一次返回首页所需核心状态
- 存档状态检查 `GET /api/game/save-status`、新游戏创建 `POST /api/game/new-game`、手动保存 `POST /api/game/save`
- 初始宠物配置 `initial-pets.yml`（含初始金币、经验池、地图 ID）
- 前端新游戏页面（名称输入 + 形象选择 + 宠物三选一 + 创建流程）
- 前端首页骨架（玩家状态卡片 + 当前队伍 + 快捷操作）
- 前端游戏 Store（存档状态、Bootstrap 加载、新游戏创建、手动保存）
- 自动跳转：无存档时自动进入新游戏流程
- 单元测试（初始宠物配置校验 7 种场景）

### 阶段 3 完成内容

- 战斗引擎领域模块 `com.petgame.battle`：BattleEngine 统一入口，手动/自动战斗共用同一引擎，差异仅在 DecisionProvider（敌方由 WildEnemyDecisionProvider 决策）
- 伤害结算链路：基础值+属性成长 → 防御/抗性减伤（200/(200+def)）→ 属性克制 ×1.50/×0.75 → 本属性技能 ×1.20 → 状态/Buff 修正 → 暴击 ×1.4~2.0 → 最低 1 点伤害保底
- 技能配置模型与 `skills.yml`（18 个技能：伤害/治疗/蓄力/AOE/施加状态，冷却驱动、无 MP）
- 状态体系 `statuses.yml`（14 个状态：DOT/控制/减益/增益四类 + 状态联动规则配置化）
- 被动技能框架（9 种触发时机 + 5 种效果类型，含不屈/入场增益等，配置驱动不写死 ID）
- 战斗行动与事件模型：SKILL/DEFEND/SWITCH 行动意图 + 23 种事件类型的完整回合事件流
- 回合流程：速度决定行动顺序（每回合重算）、蓄力/控制/防御处理、倒下补位、胜负判定、回合结束 DOT/持续递减/冷却递减
- 战斗服务与 REST 接口：POST `/api/battles`（测试战斗，支持固定种子）、GET `/api/battles/{id}`、POST `/api/battles/{id}/actions`；战斗临时数据只存服务器内存，零落库
- 前端基础战斗页面 `/battle`：单位卡片/HP 条/状态标签/技能按钮（含冷却）/目标选择/换宠/中文事件日志/种子输入
- 配置校验扩展：技能/状态/被动/测试战斗配置启动全量校验（引用完整性、枚举合法性）
- 修复阶段 2 遗留缺陷：新增 MyBatis-Plus MetaObjectHandler 自动填充 createdAt/updatedAt
- 单元测试 57 个（含 BattleEngine 18 场景：克制/暴击/治疗/行动顺序/冷却/蓄力/AOE/换宠/补位/防御/沉默/DOT/嘲讽/不屈/胜负判定/种子复现）+ E2E 验收脚本 `scripts/e2e-battle-test.ps1`

### 阶段 4 完成内容

- **战斗结算接入持久化**：`POST /api/battles/{id}/settle` 同事务落库 HP 回写、经验/金币/掉落发放、战斗统计累加；防重复结算（`BATTLE_ALREADY_SETTLED`）
- **公共经验池**：经验统一进玩家公共池（无上限），玩家自主分配到宠物；升级不直接发经验给宠物
- **五种升级方式**：升 1 级 / 升 5 级 / 升到指定等级 / 升到上限（Lv.50）/ 自定义投入经验；升级预览接口返回属性变化与解锁技能
- **等级成长公式**：最终属性 = 种族基础（含个体浮动） + 等级固定成长 + 资质成长修正 + 自由属性点；资质影响升级成长（非即时加属性）
- **自由属性点**：每级 3 点 + 稀有度每 10 级额外（RARE +2/EPIC +4/LEGENDARY +6）；加点转换表（HP +5/次、力量等 +1/次、速度 +1/次但消耗 2 点）；洗点第一阶段免费、全量返还
- **技能等级解锁与装配**：升级自动学习解锁技能（默认不装备）；最多 4 个主动技能槽位（1~4），装备/卸下接口
- **宠物详情页**：基础/属性/技能三标签；属性分解表（基础/成长/资质/加点/合计）；升级预览、加点、洗点、技能装配 UI
- **队伍系统**：6 宠携带（3 首发 + 3 候补），位置 1~6 唯一；整体替换在单事务内完成；`PUT /api/team/members`
- **背包系统**：不限容量、按分类组织（捕捉/恢复/材料/技能书/重要物品）；恢复道具（HEAL_HP/REVIVE）仅在战斗外使用（战斗内不使用，用户裁决，见规划文档 §9.3 决策八）
- **前端养成闭环**：宠物详情页、队伍编辑页、背包页、战斗结算结果展示（经验/金币/掉落/HP 回写）
- **Flyway V3 迁移**：`player_pet` 表新增 `captured_level` + 六维 `base_*_offset`（个体基础浮动固化值）
- **道具配置** `items.yml`：3 个恢复道具（小型恢复药/中型恢复药/复苏药剂），含 category、usableOutsideBattle、usableInBattle 字段
- **系统规则扩展**：经验公式（expBase=100、expGrowthFactor=1.15）、加点成本转换表、稀有度额外点数、baseStatVariance=0.05
- 单元测试 155 个（含 PetGrowthService 17 场景、PetService 28 场景、TeamService 16 场景、InventoryService 20 场景、BattleServiceSettlement 15 场景）
- 验收修复 4 处：队伍首发标记改用位置 1~3 判定；倒下宠物 0HP 参战不再强制 1HP（含开局补位/判负）；自由点数按转换表消耗计算（速度加点扣 2 点）；升级预览非法参数返回业务错误而非 500

### 阶段 5 完成内容

- **宠物种族配置**：`game-config/pets/pets.yml` 补齐 27 种基础宠物（9 属性 × 3，稀有度分布 12/9/5/1，基础捕获率 COMMON 0.55/RARE 0.40/EPIC 0.25/LEGENDARY 0.10，每宠 4 技能 + 稀有技能池 + 被动）；`initial-pets.yml` 瘦身为「speciesId + 资质覆盖 + 初始道具」，种族数据唯一来源为 pets 配置
- **野生遭遇生成**：`WildEncounterService` 按刷新组配置（`encounters.yml`）权重抽种族、等级范围、六维资质随机、个体浮动 ±5%、稀有技能 5%、特殊外观 1%；与玩家宠物共用面板公式
- **简化遭遇入口**：`POST /api/wild/battles`（阶段 6 由地图承接，不耦合地图逻辑）+ 探索页临时入口按钮
- **捕捉计算**：`CaptureCalculator` 纯函数实现需求 §46 公式（基础捕获率 × HP 系数 × 异常加成（计 2 个封顶）× 球倍率 × 精英系数，clamp [0,1]）；不添加文档之外修正项
- **捕获率实时显示**：`GET /api/wild/battles/{id}/capture-rates`，回合变化后前端自动刷新
- **三档捕捉球**：普通/高级/特级（倍率 1.0/1.5/2.5 配置化）；新游戏赠送 10/5/2（用户裁决）；捕捉失败消耗球；开发者模式临时补充入口（正式获取途径属阶段 10）
- **战斗捕捉/逃跑行动**：复用同一 BattleEngine，新增 CAPTURE/FLEE 行动与 5 种事件；捕捉成功目标立即退出敌方队伍（不触发倒下/击败被动）、候补补位、新捕捉宠物不加入当前战斗；逃跑必定成功（配置化）同战败结算（HP 回写、无奖励、无胜方）
- **捕捉结算**：野生战斗结算同事务完成捕捉落库（等级/资质/个体浮动/HP 余量/技能学习/稀有技能/特殊外观/捕获地图与时间）、捕捉球扣除、野生奖励（遭遇组每级基础值 × 敌等级 × 稀有度系数 1.0/1.2/1.5/2.0，被捕捉宠物不参与）；捕捉去向选择：队伍未满 6 只可直接入队（settle 传 joinTeam），否则进仓库
- **宠物仓库**：不限容量；按名称/属性/稀有度/等级/综合资质/稀有技能/特殊外观/收藏/锁定/是否在队伍筛选，按等级/稀有度/综合资质/捕获时间排序（`GET /api/storage/pets`）
- **锁定/收藏/昵称**：锁定后禁止放生；昵称 ≤12 字符、清除后恢复显示种族名称（种族名称始终保留）
- **放生与临别礼物**：单只/批量；自动排除锁定/收藏/在队宠物；珍稀/传说/高资质/稀有技能/特殊外观额外警告；礼物价值点数 = 稀有度基础值（20/60/150/400）× 捕获等级系数（1+等级×1%，上限 ×1.5）× 培养系数（1.0～1.5，上限 50%），从礼物池按权重抽取至累计价值 ≥ 应得点数（决策七）
- **前端**：战斗页野生遭遇入口/捕捉球选择条/捕捉率实时标签/逃跑按钮/捕捉去向选择/结算捕捉结果展示；新增仓库页 `/storage`（筛选排序/昵称/锁定/收藏/放生预览二次确认/礼物汇总）
- **无 Flyway 迁移**：仓库所需字段（locked/favorite/specialAppearance/capturedMapId/capturedAt）已在 V2 迁移中建立
- **初始宠物等级 5 级（用户裁决）**：避免初始状态打不过野生宠物；HP 按 Lv.5 面板公式计算，学习 unlockLevel ≤ 5 的种族技能，属性自然强于 1 级；新游戏赠送道具补充小型恢复药 ×10 + 复苏药 ×2（HP 跨战斗保留的前期续航保障）
- 单元测试 181 个全量通过：新增 CaptureCalculator 6 场景、WildEncounterService 6 场景、ReleaseGiftCalculator 6 场景、PetStorageService 7 场景；配置全量加载测试扩展至阶段 5（27 种族/稀有度分布/捕捉球/遭遇组/礼物池）；验收修复 3 处：野生临时实体自由点数字段初始化（防 NPE）、道具校验 null category/itemType 容错、player_pet_skill.learned_at 自动填充（MetaObjectHandler）
- E2E 验收脚本 `scripts/e2e-capture-test.ps1` 全部验收项通过：新游戏赠送道具 → 捕捉率查询 → 压血投球捕捉 → 结算入队/留仓库 → 捕捉球消耗 → 仓库筛选排序/昵称/锁定/收藏/放生保护/礼物底线

### 阶段 6 完成内容

- **Phaser 集成**：Phaser 3.90.0 + 3 核心 Scene（BootScene/MapScene/BattleScene）+ GameBridge 类型化事件桥接（Phaser ↔ Vue 单向通信）；占位 PNG 资源生成脚本（tileset + 13 个 sprite）
- **Tiled JSON 地图管线**：25×19 格 × 32px；图层约定（ground/obstacle/objects）；对象层类型（wild_spawn/camp/chest/gather/exit/boss_entrance/npc/hidden_spot）
- **地图配置体系**：`game-config/maps/maps.yml`（6 区域：3 实装 + 3 结构预留 planned:true）；`encounters.yml` 扩展 ENCOUNTER_MEADOW/FOREST；`MapsConfig` 模型（region/exit/camp/gather/chest/reward）
- **后端 map 模块**：`MapExplorationService`（核心服务，区域解锁/移动/营地/采集/宝箱/遭遇/战败）+ `MapController`（REST 接口）；5 实体 + 5 mapper（player_region_unlock/player_camp_activation/player_chest_loot/player_map_session/player_gather_used）
- **Flyway V4 迁移**：5 张玩家状态表（区域解锁、营地激活、宝箱消耗、地图会话、采集消耗）；复合主键无 @TableId
- **区域解锁与移动**：AUTO/BOSS/QUEST 三种解锁类型（本阶段仅 AUTO）；懒写入解锁记录；出口解析由后端权威完成（传 exitId，后端解析对应 entry）
- **营地系统**：免费恢复全队 HP（含倒下宠物复苏）；激活后可在已激活营地间免费传送；传送触发地图刷新
- **地图刷新**：离开区域重新进入、营地休息/传送生成新 session（UUID）；采集记录与会话绑定，新会话可重新采集
- **采集点与宝箱**：普通采集点重进区域可刷新（会话绑定），隐藏宝箱一次性（永久消耗）；奖励入背包
- **野怪简单行为**：WANDER/TIMID/AGGRESSIVE/RARE_STAY 四种 AI 模式；接触触发遭遇；玩家可绕开
- **战败流程**：BattleService 结算同事务调用 handleDefeat；退出战斗 → 返回最近营地 → 全队恢复 → 轻度嘲讽提示；零惩罚
- **5 套队伍预设**：getTeamPresets（懒创建）/activatePreset；战斗中禁止切换/编辑（@Lazy 注入 BattleService 避免循环依赖）
- **大地图雏形**：`/world-map` 页面显示已解锁区域卡片、推荐等级、营地传送
- **前端探索页**：`ExploreView.vue`（Phaser 容器 + 模态对话框 + toast）；方向键/WASD 移动 + E 键交互；GameBridge 事件驱动
- **前端队伍页重写**：`TeamView.vue`（5 预设标签页 + HTML5 拖拽 + 技能查看 + 详情链接）
- **战斗页扩展**：BattleView 添加 defeat 面板；battle store 新增 startMapEncounter + adoptSnapshot
- **后端集成**：BattleService 注入 MapExplorationService（NO_FIGHTABLE_PETS 检查 + defeat 钩子 + hasActiveBattle）；TeamService 扩展 5 预设 + @Lazy 注入
- 单元测试 236 个全量通过：新增 MapExplorationServiceTest 16 场景、GameConfigMapValidateTest、TeamServiceTest 扩展（5 预设 + 战斗守卫）、BattleServiceSettlementTest 扩展（defeat/NO_FIGHTABLE_PETS）
- E2E 验收脚本 `scripts/e2e-map-test.ps1` 全部通过：3 区域解锁 → 出口移动 → 采集/宝箱一次性 → 跨区刷新组拒绝 → 地图遭遇战斗+结算 → 营地休息/传送 → 5 套预设切换

### 阶段 7 完成内容

- **Boss 配置体系**：`bosses.yml` + `BossesConfig` 模型（2 Boss × 3 难度：NORMAL/HARD/NIGHTMARE），每难度含 stats/skills/passives/phases/drops/luckGain；掉落分 4 档稀有度（COMMON/RARE/EPIC/LEGENDARY）
- **控制抗性与连续衰减**：`system.yml` 新增 controlResistance（精英 0.8/Boss 0.6）、consecutiveControlDecay [1.0, 0.7, 0.4]、controlDecayResetRounds=2；BattleEngine 对 SPECIAL_CONTROL 状态应用抗性×衰减判定
- **阶段机制**：Boss HP 低于阈值触发 ADD_SKILL/ADD_SHIELD/BUFF_SELF；每触发器仅激活一次；PHASE_TRANSITION 事件
- **Boss AI**：`BossDecisionProvider` 考虑属性克制、低血目标优先、技能冷却、阶段策略
- **Flyway V5 迁移**：5 张表（player_boss_defeat_count/difficulty_unlock/luck/drop_unlock/manual_clear）
- **Boss 模块**：5 实体 + 5 mapper + `BossService`（开战/结算/自动挑战/幸运兑换/情报）+ `BossController`（REST）
- **BattleService 集成**：`startBossBattle` 构建 Boss 敌方（控制抗性 0.6 + 阶段触发器）；BOSS 结算（掉落/经验/金币/击败次数/幸运值/难度解锁/全队恢复）
- **BattleEngine `runFullBattle`**：AI vs AI 跑完整个战斗（自动挑战使用）；`uncapturable` 标记禁止 Boss 捕捉
- **幸运值系统**：每 Boss 独立，按难度 +4/+7/+10；每 100 点兑换一次；情报解锁阈值 1/3/6/10 次
- **自动挑战**：5 种模式（ONCE/FIVE/TEN/UNTIL_FAIL/UNTIL_LUCKY），需已手动击败过
- **前端**：`types/boss.ts` + `stores/boss.ts` + `BossView.vue`（列表/难度/情报/挑战/自动/兑换）；ExploreView boss 入口导航；BattleView Boss 战禁止捕捉/逃跑
- 单元测试 262 个全量通过：新增 BattleEngineControlResistanceTest 8 场景、BattleEnginePhaseTest 4 场景、BossDecisionProviderTest 4 场景、BossConfigValidateTest 10 场景
- E2E 验收脚本 `scripts/e2e-boss-test.ps1`：Boss 列表/详情/开战/战斗/结算/击败次数/自动挑战/幸运兑换

### 阶段 8 完成内容

- **Flyway V6 迁移**：`player_pokedex`（种族研究进度，复合主键 save_id + species_id）+ `player_pokedex_history`（种族历史记录，放生不清除）
- **图鉴研究值配置**：`system.yml` 追加 `pokedex` 配置段（研究等级门槛 Lv.1~5、11 种研究值来源分值、资质预估等级标签 S/A/B/C/D）；`SystemRuleConfig.PokedexRuleConfig` 内部类；`GameConfigValidator` 追加门槛严格递增 + 分值非负 + 资质等级合法校验
- **PokedexService 核心服务**：研究值累积（11 种来源）、研究等级计算（配置门槛 + seen 保底 Lv.1 + caught 保底 Lv.2）、逐级信息解锁（Lv.0→???、Lv.1→名称/属性/描述、Lv.2→稀有度/捕获率、Lv.3→技能/被动/六维基础、Lv.4→稀有技能池/出现区域、Lv.5→历史记录/特殊外观/进化占位）、Lv.5 野外识别（资质预估等级 S/A/B/C/D）
- **PokedexController REST API**：`GET /api/pokedex`（全量列表）、`GET /api/pokedex/{speciesId}`（详情）、`POST /api/pokedex/{speciesId}/identify`（Lv.5 野外识别）
- **既有行为接入**：BattleService（遭遇发现 + 捕捉记录 + 战斗参与/获胜）、PetService（技能解锁记录）、GameService（新游戏初始宠物补录）；所有记录方法使用 `REQUIRES_NEW` 传播策略，记录失败不阻断主流程
- **前端图鉴页面**：`types/pokedex.ts`（PokedexEntry/PokedexDetail/PokedexHistory/WildIdentification）+ `stores/pokedex.ts`（Pinia Store，筛选/统计）+ `PokedexView.vue`（统计栏 + 筛选栏 + 卡片网格 + 详情面板，按等级逐级展示已解锁信息）
- 单元测试：PokedexServiceTest 20+ 场景（研究等级计算/首次后续发现/捕获/高资质/稀有技能/特殊外观/精英捕获/战斗参与获胜/技能解锁/历史记录累加/野外识别）；GameConfigValidatorTest 追加 3 个图鉴配置校验测试

### 阶段 9 完成内容

- **Flyway V7 迁移**：6 张任务系统表（player_quest/player_quest_objective/player_dialogue/player_tutorial/player_map_change/player_hidden_trigger，复合主键无 @TableId）+ ALTER player 表新增 story_completed 字段
- **任务配置 quests.yml**：主线 12 节点（含水域/雷域并行分支设计，森林 Boss 后同时解锁，两者都完成后解锁遗迹）+ 支线 10（含地图永久变更）+ 隐藏 3（LOCATION/PET/ITEM/DIALOGUE_COUNT 触发器）；NPC 对话约 10 个；新手教学 8 步
- **QuestsConfig 配置模型**：QuestConfig/ObjectiveConfig/RewardConfig/HiddenTriggerConfig/MapChangeConfig/NpcConfig/TutorialStepConfig；GameConfigRegistry 新增 quest/npc/tutorial 索引；GameConfigValidator.validateQuests() 校验 ID 唯一性/前置引用/目标类型/targetId 引用/奖励引用/NPC 引用
- **QuestService 核心服务**：任务列表/详情/接受/事件推进/完成/三选一奖励/区域解锁/地图变更/隐藏任务触发/赠送宠物/通关标记；事件钩子 REQUIRES_NEW 传播不阻断主流程
- **NpcDialogueService + TutorialService**：NPC 线性对话树推进+对话次数累计+隐藏触发；教学步骤查询/完成/跳过+捕捉教学发放技能书
- **QuestController REST API**：11 个端点（任务列表/详情/接受/完成/三选一/主线摘要/NPC 对话/教学状态/完成步骤/跳过/地图变更）
- **已有系统集成**：BattleService（CAPTURE/DEFEAT/DEFEAT_BOSS）、MapExplorationService（ARRIVE/GATHER/LOCATION 触发）、GameService（主线摘要+教学状态 Bootstrap）；@Lazy 防循环依赖
- **Boss 配置补齐**：6 个 Boss（水域/雷域/遗迹主 Boss + 初始区域隐藏/森林精英/遗迹隐藏）× 3 难度
- **新区域实装**：3 区域从 planned 改为实装（静水湖域/雷鸣高地/远古遗迹，QUEST 解锁）+ 3 Tiled 占位地图（waters.json/thunder.json/ruins.json）+ 遭遇组配置
- **前端实现**：QuestView（三标签布局）+ QuestDetail（目标进度+奖励+操作）+ DialogueBox（逐字打字对话框）+ TutorialOverlay（新手教学浮层）+ HomeView（主线摘要卡片）+ ExploreView（npc:touch 对话）+ MainLayout（全局对话框+教学浮层）
- 单元测试：QuestServiceTest/NpcDialogueServiceTest/TutorialServiceTest/QuestConfigValidateTest；E2E 脚本 `e2e-quest-test.ps1`

### 阶段 10 完成内容

- **Flyway V8 迁移**：2 张表（player_ambush_triggered 一次性埋伏记录 / player_random_event_used 会话事件去重）
- **道具体系补齐**：9 属性材料 + 5 Boss 核心 + 净化药 + 10 技能书道具；ItemConfig 新增 skillId/price 字段
- **商店系统**：shop.yml 25 商品（主线任务解锁）；ShopConfig/ShopService/ShopController（`GET /api/shop`、`POST /api/shop/buy`，单事务扣金币+入背包）；前端商店页 ShopView + shop store
- **技能书系统**：PetService 新增学习/遗忘/装备/卸下 4 接口（学习上限 10、携带槽位 5~6、专属技能保护、学习限制 elements/rarities/speciesIds）；SkillConfig 新增 learnRestriction/exclusive；PetDetail 新增 bookSkillSlots/learnedBookSkills/bookSkillLearnCount；前端宠物页技能书管理区
- **精英个体**：system.yml elite 配置段（5% 概率、等级 +2~5、资质下限 60、稀有技能概率加成）；WildEncounterService 精英生成；UnitSnapshot 新增 elite 字段；前端遭遇/战斗精英标识
- **特殊外观多变体**：system.yml specialAppearanceVariants（APPEARANCE_SHINY 0.5% / APPEARANCE_GLOW 0.3%）替代单一 SPECIAL 标记
- **随机事件**：random-events.yml 6 事件（区域限定 + 选项权重结果：GIFT_GOLD/GIFT_ITEM/GIFT_MATERIAL/TRIGGER_BATTLE/TRIGGER_CAPTURE/NOTHING）；RandomEventService + `GET /api/maps/events/roll`、`POST /api/maps/events/resolve`；前端探索页事件对话框
- **隐藏遭遇与埋伏**：encounters.yml hidden 遭遇组；MapsConfig 新增 ambushSpots 配置结构；一次性埋伏实体/mapper
- **推荐 Build**：build-recommendations.yml（6 种族 × 2 方案）；`GET /api/pets/{petId}/build-recommendations` 纯展示；前端宠物页推荐方案标签页
- **战斗加速**：BattleView 速度控制 1x/2x/3x + 自动播放（Boss 战可用）
- **配置校验**：GameConfigValidator 新增 validateShop/validateRandomEvents/validateBuildRecommendations
- 单元测试：ShopServiceTest/RandomEventServiceTest/Phase10ConfigValidateTest + PetServiceTest 技能书排序回归；E2E 脚本 `e2e-phase10-test.ps1`；未实现自动战斗策略预设（用户明确排除）

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
