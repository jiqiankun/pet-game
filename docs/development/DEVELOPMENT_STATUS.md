# 开发状态

本文档记录《宠物精灵》的开发进度、历史阶段结果、遗留问题与已知限制。

> 当前桌面重构阶段范围与验收标准以《宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md》为准；第一阶段历史记录仍以《宠物精灵游戏分阶段开发规划 V1.0.md》为准。本文档只记载**当前实现状态**，不定义规则。

---

## 1. 当前状态

- **当前阶段**：桌面版世界/UI 重构阶段 2（WorldGraph、玩家知识与世界状态基础）— **实现完成，后端全量测试/前后端构建通过与桌面运行态补验通过**
- **当前交付范围**：仅桌面端；手机、平板、触控交互与移动端专项回归不在本轮范围。
- **历史基线**：第一阶段阶段 0～14 已全部完成，稳定业务能力增量复用。
- **下一阶段**：阶段 2 运行态补验通过后，进入桌面重构阶段 3（世界图谱前端投影与纵切内容修复）。

### 桌面重构阶段 0 完成摘要

- 五项裁决 D0-01～D0-05 已冻结：终端范围、战斗道具、轻量动态世界、旧路由兼容期限、Region→Map 渐进迁移。
- R-001～R-205 已全部映射到实施阶段与验收前缀：MUST 199、SHOULD 5、MAY 1。
- 冻结内容/资源基线：27 宠、85 主动、51 被动、45 道具、8 Boss、25 任务、33 成就、6 个兼容地图配置、6 张 Tiled、292 张 PNG、0 音频、Flyway V1～V13、saveVersion 1。
- 新增新游戏/主线中段/内容完成三类脱敏存档样本；当前版本均可通过正式导入流程解析并写入。
- 修复 `SaveBackupService.importSave` 导入前未清理旧存档的问题，保持“自动备份 → 清理旧存档 → 导入”处于同一事务。
- 建立 `scripts/phase0-baseline.ps1` 统一复验入口；2026-08-19 后端 512 项测试全绿，前端类型检查和生产构建通过，ExploreView 构建块 1,709,979 bytes。
- 当前视觉截图受验收机 MySQL84 服务控制权限和浏览器连接异常限制，已保留桌面采集口径与完整操作路径；不影响构建、存档兼容和需求裁决基线。
- 完整证据、追踪矩阵和 W0-01～W0-08 阻断缺陷见 `docs/development/PHASE0_BASELINE.md`。

### 桌面重构阶段 1 实施摘要（运行态验收进行中）

- `/explore` 已改由缓存的 `WorldRoot.vue` 承载；常规 HUD/快捷键入口打开 Context，不再将宠物、队伍、背包、任务和世界地图作为主流程路由。
- `useOverlayStore` 已升级为最小 Context Stack：条目含唯一实例键、父层、来源、阻塞语义、输入上下文、关闭策略、可选返回数据与触发焦点；按类型关闭时命中最靠上的实例，按实例 ID 可精确关闭。
- 世界层接入 WORLD / PANEL / DIALOG / BATTLE / TEXT_INPUT 输入语义；阻塞面板、NPC 对话、确认框与战斗均冻结探索，奖励不阻塞世界输入；MapScene 通过 `cmd:clear-input`、`map:ready` 避免失焦或重启后持续移动/错误恢复。
- M/Q/B/J/Esc 规则、窗口焦点/Tab 边界、输入框过滤、单一历史保护位的浏览器返回与世界地图/区域图 Overlay 返回逻辑已落地；旧功能路由仍可直达兼容。
- 验证已通过：前端类型检查、Vite 生产构建、后端 512 项全绿、阶段 0 基线脚本（跳过重建）、M/Q/B/J 不改 Hash、世界实例保持、`WORLD → WORLD_MAP → REGION_MAP` 的 LIFO、随机事件冻结、焦点/文本输入、旧路由和嵌套浏览器返回。
- 用户已授权直接覆盖测试数据，并已以新建存档完成 NPC、出口、奖励、任务三 Overlay 链和地图遭遇/Battle Context 补验；Overlay 自动结算与后端事务自锁均已修复，`BattleServiceSettlementTest` 20 项通过。重启后端后已验证逃跑结算卡显示、返回关闭与同一世界实例恢复；仅剩真实页面隐藏后的持续移动手工验证。详情见 `docs/development/PHASE1_WORLD_CONTEXT.md`。在补验通过前禁止开启阶段 2。

### 桌面重构阶段 2 实施摘要

- 新增最小 WorldGraph 领域模型（`World/WorldMapNode/WorldConnection`）与 `WorldGraphBuilder`，以 `maps.yml` 为世界拓扑单一事实源派生 World → Region → Map → Connection/Gateway/Anchor；`LocationRef` 提供统一位置引用并兼容旧 raw ID。
- `WorldTruthService` 承担世界事实与玩家知识/世界状态的读写：`getWorldView` 依据 PlayerKnowledge 过滤隐藏连接（未发现不下发）、捷径（未解锁不下发）；`updatePosition` 校验并保存精确坐标/朝向/安全点、拒绝非法跨图位置；`discoverLocation` 受控写入知识并拒绝伪造节点；新档/旧档兼容初始化按 `player.current_map_id` 推导并落到配置出生锚点。
- 新增 Flyway `V14__world_graph.sql`：`player_world_state`（当前地图/区域、坐标、朝向、相机锚点、最近安全点、世界状态版本）与 `player_known_location`（REGION/MAP/CONNECTION/LANDMARK/SHORTCUT 已发现/已解锁知识）。
- 新增 `WorldController` `/api/world/**` 接口集：世界图谱、当前精确位置、位置保存、已解锁捷径、知识发现。
- 校验器增强并纳入后端测试/构建：实测区域必填 `spawnObjectId`（出生锚点）、出口必填 `entryObjectId`（到达锚点）、普通双向连接须成对校验、单向连接须显式标注 `oneWay:true`、`initialMapId` 不可指向结构预留区域；新增 `MapTiledConsistencyValidator`，为阶段 3 内容修复提供 Tiled 对象 ID 契约校验输入。
- 因遗迹（`MAP_AREA_RUINS`）为无出口尾区，将 `EXIT_WATERS_TO_RUINS` 与 `EXIT_THUNDER_TO_RUINS` 显式标注 `oneWay:true`（单向/危险连接须显式标注，阶段 2 兼容规则）。
- 新增后端测试：`WorldGraphBuilderTest`、`LocationRefTest`、`MapTiledConsistencyValidatorTest`，并在 `GameConfigMapValidateTest` 增补双向成对/oneWay 豁免/锚点必填用例；后端全量测试全绿，前端 `vue-tsc` 类型检查与 Vite 生产构建通过。
- 前端 `types/map.ts` 已对齐 `WorldView`/`MapNodeView`/`ConnectionView`/`CurrentLocationView` 等阶段 2 DTO；图谱前端投影与目录/快捷按钮等交互在阶段 3 落地。
- **运行态补验（完成）**：本地 JDK21 + MySQL 8.4 启动后端，Flyway V14 生效（schema=14）；`/api/world` 全接口链验证通过：旧档迁移落到出生锚点、`getWorldView` 返回经过滤的 World→Region→Map 层级与 `oneWay` 连接、`updatePosition` 保存/恢复坐标朝向且越界跨图返回 `POSITION_CROSS_MAP`、`discoverLocation` 合法写入并使 MAP 知识翻转 `discovered`、伪造节点返回 `KNOWLEDGE_NODE_MISSING`、捷径接口返回空集。补验中修复一处真实缺陷：`PlayerWorldStateEntity` 缺 `@TableId`，导致 `PlayerWorldStateMapper.selectById/updateById` 无效绑定即 500，已补 `@TableId(IdType.INPUT)` 修复。隐藏连接/捷径过滤逻辑因当前无该类配置未在运行态触发（由校验器与单元覆盖）。

## 2. 阶段总览

| 阶段 | 核心内容 | 状态 |
|---|---|---|
| 0 | 工程脚手架（前端 / 后端骨架、构建脚本、外部配置示例） | ✅ |
| 1 | 配置体系（属性克制、系统规则、加载 / 校验 / 注册中心） | ✅ |
| 2 | 存档 + 新游戏 + Bootstrap + 前端首页 | ✅ |
| 3 | BattleEngine 统一引擎、伤害 / 技能 / 状态 / 被动、战斗接口、前端战斗页 | ✅ |
| 4 | 战斗结算落库、公共经验池、升级公式、加点 / 洗点、技能装配、宠物详情、队伍、背包 | ✅ |
| 5 | 野生遭遇、捕捉计算与捕捉 / 逃跑、三档捕捉球、仓库、放生、27 宠物配置 | ✅ |
| 6 | Phaser + Tiled 地图管线、探索 / 营地 / 采集 / 宝箱、5 套预设、战败流程 | ✅ |
| 7 | Boss 配置（2 Boss × 3 难度）、控制抗性、阶段机制、Boss AI、自动挑战、幸运值 | ✅ |
| 8 | 图鉴研究进度 + 历史记录、研究等级、野外识别 | ✅ |
| 9 | 任务系统（主线 12 + 支线 10 + 隐藏 3）、NPC 对话、教学、6 Boss、3 新区域 | ✅ |
| 10 | 道具体系、商店、技能书、精英个体、特殊外观、随机事件、隐藏遭遇、推荐 Build、战斗加速 | ✅ |
| 11 | 成就、玩家统计、Boss 挑战目标、游戏完成度、宠物履历 | ✅ |
| 12 | 敌方胜利互动系统（VictoryInteraction） | ✅ |
| 13 | 动态难度、野外缩放、Boss 自适应与等级压制 | ✅ |
| 14 | 存档备份、开发者工具、兼容性与总验收 | ✅ |

## 3. 各阶段完成内容

### 阶段 0：工程脚手架
前端 Vue 3 + TS + Vite 骨架（页面占位路由、API 封装、全局样式、Vite 代理）；后端 Spring Boot 骨架（统一响应、全局异常、健康检查、Flyway 初始迁移、业务模块占位）；统一构建脚本与启动脚本；外部配置示例。

### 阶段 1：配置体系
9 属性与克制关系（五行环 + 副链 + 光暗互克）、系统规则配置；`GameConfigLoader`（内部默认 + 外部覆盖）、`GameConfigValidator`（启动校验）、`GameConfigRegistry`（运行时查询）；统一随机工具 `GameRandom`（可复现）。

### 阶段 2：存档 + 新游戏
七张存档表（Flyway V2）；新游戏流程（名称 + 形象 + 初始宠物三选一）；Bootstrap 聚合接口；存档状态 / 创建 / 保存；前端新游戏页与首页骨架。

### 阶段 3：BattleEngine
统一战斗引擎（手动 / 自动共用，差异在 DecisionProvider）；伤害结算链路；技能 / 状态 / 被动配置体系；战斗行动与事件模型；回合流程；战斗 REST 接口（内存态，零落库）；前端基础战斗页。

### 阶段 4：战斗结算与养成
结算同事务落库（HP / 经验 / 金币 / 掉落 / 统计）；公共经验池与五种升级；等级成长公式；自由加点 / 洗点；技能解锁装配；宠物详情页；6 宠队伍；背包恢复道具；Flyway V3。

### 阶段 5：捕捉与仓库
27 宠物种族配置补齐；野生遭遇生成；捕捉公式与捕捉 / 逃跑行动；三档捕捉球；捕获率实时显示；仓库筛选排序 / 昵称 / 锁定 / 收藏；放生与临别礼物；初始宠物 Lv.5。

### 阶段 6：地图探索
Phaser + Tiled 地图管线；3 区域实装 + 3 结构预留；营地恢复 / 传送；区域解锁与移动；采集 / 宝箱；5 套队伍预设；战败流程；Flyway V4。

### 阶段 7：Boss 系统
Boss 配置（2 Boss × 3 难度）；控制抗性与连续衰减；阶段机制；Boss AI；7 张 Boss 进度表（Flyway V5）；BossService / Controller；自动挑战；幸运值兑换。

### 阶段 8：图鉴
图鉴研究进度 + 历史记录（Flyway V6）；研究等级 Lv.0~5 配置化；逐级信息解锁；11 种研究值来源；Lv.5 野外识别；既有行为接入。

### 阶段 9：任务系统
任务配置（主线 12 + 支线 10 + 隐藏 3）；NPC 对话；新手教学 8 步；6 Boss 补齐；3 新区域实装；Flyway V7（6 张表 + story_completed）。

### 阶段 10：效率、经济与随机内容
道具体系补齐；商店系统；技能书系统；精英个体；特殊外观多变体；随机事件；隐藏遭遇与埋伏；推荐 Build；战斗加速 1x/2x/3x；Flyway V8。

### 阶段 11：成就、统计与完成度
成就系统（事件驱动、条件类型、奖励）；玩家统计（23 统计键）；Boss 挑战目标（5 主 Boss × 4 目标）；游戏完成度（8 项加权）；宠物履历；Flyway V10。

### 阶段 12：敌方胜利互动系统
敌方胜利互动（训练师台词 / 野外宠物动作 + 叫声 / 旁白）；`victory-interactions.yml` 数据驱动（TRAINER/WILD_PET/BOSS 三类 + 8 战况标签 + 稀有度）；三级选择策略（专属 → 风格匹配公共池 → 通用回退）；权重随机 + 防重复 + 彩蛋；`BattleDefeatContext` 轻量聚合；Boss 挑战 / 连续战败统计；战败分支接入结算返回互动；前端结算面板互动展示。

### 阶段 13：动态难度、野外缩放、Boss 自适应与等级压制
全局难度 `NORMAL/ELITE/NIGHTMARE/HELL` 与设置页持久化；地图、难度、队伍三重边界内的野外有限缩放；Boss 首次遭遇快照（重试、自动挑战和重启复用，仅跨全局难度可显式重置）；有限可选支援位与既有评分式 AI；高难 Boss 战临时有效等级、自由点稳定最大余数投影及 HP 比例回写；Flyway V11（难度字段与遭遇快照表）。前端展示旧快照警告、重置入口与真实等级 → 有效等级。后端完整测试 440 项通过，前端类型检查与生产构建通过。

## 阶段 14（已完成）：存档备份、开发者工具、兼容性与总验收
**存档备份 / 恢复**：自定义 `.pet-save.zip` 存档文件（`manifest.json` 含 gameVersion/saveVersion + `save.json` 保存全量玩家逻辑数据，不导出数据库物理文件）；导出 / 导入 / 手动备份 / 重置游戏 / 备份列表；导入流程＝校验文件 → 检查 saveVersion（高于当前拒绝）→ 导入前自动备份当前存档 → 事务内导入（pet/team 主键重映射）→ 失败回滚；自动备份发生在导入前、重置前，不做定时后台备份。Flyway V12 新增 `dev_operation_log` 开发者操作日志表。急难接口 `SaveController(/api/save/*)`、`SaveBackupService`、`SaveSnapshot`、`SaveManifest`、`GameProperties.backupDir`。

**开发者模式与数据操作类工具**：`GameProperties.developerMode` 开关（默认关闭），`DevController(/api/dev/*)` 统一校验模式；`DevService` 覆盖资源（加金币 / 经验池 / 道具）、宠物（添加指定等级资质 / 重置战绩并回满 HP）、地图（解锁区域 / 强制刷新 / 强制精英 / 强制随机事件）、Boss（解锁难度 / 直达难度 / 设置击败次数 / 设置幸运值 / 强制掉落）；高风险操作前自动备份（`dev-before`）；`DevContext` 内存一次性标志驱动强制精英 / 强制随机事件（被 `WildEncounterService` / `RandomEventService` 消费后清除）；`DevOperationLog` 记录全部操作。前端新增「存档备份」页（导出 / 导入 / 手动备份 / 备份列表 / 重置）与「开发者工具」页（开发者模式开启时导航显示），路由 `/save-backup` / `/dev-tools`。

**战斗调试 / 随机数调试**：`GameRandom` 支持可选随机序列录制（`recordDraws` / `drawLog`）；`BattleContext` 增加 `playerInvincible / playerOneHitKill / playerFixedCrit / debugDamage` 调试标志；`DevContext` 提供持久战斗调试开关 + 一次性固定随机种子；`DamageCalculator` 支持 `forceCrit` 重载；`BattleEngine.applyDamage` 接入无敌（伤害归零）/ 一击必杀（直接击杀）/ 固定暴击；`BattleService` 开战时快照调试标志并消费固定种子，`BattleSnapshot` 回填 `debugDamage / debugRandomDraws`。DevController 新增 `/api/dev/battle/*`（invincible / one-hit-kill / fixed-crit / debug-damage / fixed-seed / state）。前端 DevToolsView 新增「战斗调试」卡片。

**新手教学完善**：补齐需求 §125 第 9 项 Boss 引导步骤（`TUT_BOSS`，triggerType=BOSS）；新增「重置教学提示」能力（后端 `TutorialService.resetTutorial` + `POST /api/tutorial/reset`，前端 TutorialOverlay 重置入口）；`player_tutorial` 表新增 `reward_granted` 列（Flyway V13）实现奖励防重——捕捉教学技能书仅首次完成发放，重置后再次完成不重复发放（符合需求「第一次捕捉教学免费赠送」）。

**内容补齐（技能池 / 27 宠技能映射）**：技能池按「属性共享 × 机制共享」两维度扩充至 85 主动 + 51 被动（需求 §149，其中含 27 宠核心特色被动）；`skills.yml` 的 `passives` 段包含 41 个固有/升级被动和 10 个 `sourceType: SKILL_BOOK` 被动技能书。新增主动技能覆盖九属性（火 `SKILL_MAGMA_CLASH/INFERNO`、水 `SKILL_WATER_WHIP/TORRENT`、木 `SKILL_BRANCH_STRIKE/BLOOM`、金 `SKILL_IRON_EDGE/REFORGE`、土 `SKILL_STONE_BARRIER/ROCK_BLAST`、风 `SKILL_AIR_CUTTER/WIND_GRACE`、雷 `SKILL_CHAIN_LIGHTNING/LIGHTNING_ROD`、光 `SKILL_PRISM/BLESSING`、暗 `SKILL_NIGHT_SHROUD/SHADOW_CLONE`）与跨属性机制技能（群体增益 `SKILL_GROUP_GUARD`、群体治疗 `SKILL_GROUP_HEAL`、猎杀标记 `SKILL_MARK_TARGET`、禁疗 `SKILL_ANTI_HEAL`、再生 `SKILL_ENERGIZE`、驱散 `SKILL_ELIMINATE`）；新增 9 个固有被动（战意 / 铁躯 / 迅足 / 凝神 / 狂暴本能 / 复苏 / 猎获 / 荆棘反刺 / 士气昂扬），全部仅复用引擎已实现效果类型与触发时机。27 宠技能映射全部升级到「5~6 主动 + 2~3 被动」，按宠物定位（DAMAGE / TANK / SUPPORT / CONTROL）差异化拼装，同属性宠物玩法区分度显著提升。`GameConfigPhase3LoadTest` 全量配置校验通过；2026-08-15 后端 507 测试全绿。

**技能书扩充（主动 + 被动）**：技能书种类扩充至 23 种（13 主动 + 10 被动），被动技能书 ≥10 满足需求「技能书技能种类 ≥20、被动 ≥10」。新增 10 个技能书被动（`skills.yml`：`PASSIVE_BOOK_VANGUARD/FORTIFY/ENTRY_BOOST/STURDY/RECUPERATE/ON_KILL_ATK/DEATH_FIRE/THORN_AURA/LAST_STAND/AVENGE`），仅复用引擎已实现效果类型（`APPLY_STATUS_ALLY_ALL` / `APPLY_STATUS_SELF` / `REDUCE_PHYSICAL_DAMAGE` / `HEAL_SELF` / `DAMAGE_ENEMY_RANDOM` / `SURVIVE_LETHAL`）与触发时机（`BATTLE_START` / `ON_ENTER` / `BEFORE_TAKE_DAMAGE` / `TURN_END` / `ON_KILL` / `ON_DEFEAT` / `AFTER_TAKE_DAMAGE` / `ON_ALLY_DEFEAT`），不新增美术资源；`items.yml` 新增 10 本被动技能书道具（`ITEM_SKILL_BOOK_*`），`shop.yml` 投放（价格 380~500，随主线 `QUEST_MAIN_04~08` 解锁）。后端：`PetService.learnSkillBook` 支持被动技能解析（`registry.getPassive`），被动技能无学习上限、不占装备槽；`loadBookSkillInfo` 将被动技能书以 `source=BOOK` 展示到被动列表；`BattleService.buildPlayerUnit` 加载技能书习得被动并自动生效。2026-08-15 后端 507 测试全绿。

**被动技能体系结构性整合（固有被动 vs 技能书被动）**：把被动明确分为两大来源——**固有被动**（`species.passives`，来源 INNATE/LEVEL_UP/EVOLUTION，全部自动生效、不占槽位、不可卸下）与**技能书被动**（`player_pet_skill`，sourceType=SKILL_BOOK，后天培养 / Build 调整）。技能书被动引入**独立启用槽（2 个，slot 7~8）**，实现「**已学习 ≠ 当前生效**」：学习数量与生效数量分离，玩家在战斗外启用 / 停用 / 替换，最多同时启用 2 个。被动配置补充 `sourceType / effectGroup / stackRule / maxStack / priority` 字段（`PassiveSkillConfig`），启动校验覆盖合法值；新增 `PassiveEffectResolver` 在装配后按 effectGroup + stackRule 归一化 `unit.passives`（UNIQUE/HIGHEST_ONLY 取最高一个、ADDITIVE/LIMITED 允许叠加、同名去重），落实「同名 / 同效果被动不重复生效」。对现有 10 种被动技能书逐一盘点并调整（弱化全队光环「先锋 / 铁壁光环」、降级「殊死 / 烬爆」为受限技能书并收敛数值、保留其余通用 Build 向技能书）；`pets.yml` 27 宠固有被动补足至 3~5 个并标注来源。PetService 新增 `equipBookPassive / unequipBookPassive`，前端 `PetView.vue` 区分「固有被动（自动生效）」与「技能书被动（已启用 X/2）」，`AutoBattleDecisionProvider` 通过 effectGroup 语义感知生效被动。新增 `PassiveEffectResolverTest`、`PetServiceTest` 被动槽用例、`AutoBattleDecisionProviderTest` 被动语义用例，后端全部测试通过。

**27 宠核心特色被动逐一落地**：针对遗留的「27 宠仅复用少量固有被动导致特色雷同」问题，为全部 27 宠各设计 1 个**准专属核心特色被动**（`skills.yml` 新增 27 个 `PASSIVE_SIG_*` 固有被动定义），每宠作为首个固有被动（unlockLevel: 1）。全部复用引擎既有效果类型（`APPLY_STATUS_SELF` / `APPLY_STATUS_ALLY_ALL` / `DAMAGE_ENEMY_RANDOM` / `HEAL_SELF`）与触发时机（`ON_KILL` / `BATTLE_START` / `ON_ENTER` / `AFTER_TAKE_DAMAGE` / `ON_DEFEAT` / `ON_ALLY_DEFEAT` / `TURN_END`），不新增被动机制类型与美术资源。每个核心被动使用独立 `effectGroup`（`SIG_*`）与 `stackRule`（HIGHEST_ONLY / UNIQUE），与通用被动、技能书被动互不冲突，保障宠物独特性：按元素属性与战斗定位（DAMAGE / TANK / SUPPORT / CONTROL）差异化设计，如金属系「锋锐 / 破甲威慑 / 鎏金反刃」、木系「藤蔓庇护 / 青芽治愈 / 蚀木孢子」、水系「汐月涌动 / 涟漪共鸣 / 雾海潮汐」、火系「烬牙撕咬 / 绯焰焚身 / 赤曜疾驰」、土系「岩壁庇护 / 岳核护持 / 磐震反震」、风系「岚羽急袭 / 逐风疾行 / 空澜回旋」、雷系「霆跃疾雷 / 鸣霄蓄势 / 紫霄落雷」、光系「曜光加护 / 晨曦再生 / 辉星陨落」、暗系「幽蚀回噬 / 夜幕反噬 / 冥刃反斩」。`pets.yml` 27 宠固有被动统一补足至 4 个（1 核心特色 + 3 通用，解锁等级错开）。`GameConfigPhase3LoadTest` / `GameConfigMapValidateTest` 全量校验通过，后端 43 个测试类全部通过（Failures / Errors 均为 0）。

**数值平衡（成长 / 捕捉 / Boss 难度初调）**：经验曲线 `expGrowthFactor 1.15→1.13`（放缓后期指数，Lv1→50 累计约 30 万经验），各区域 `expPerLevel` 上调（MEADOW 12→15 / FOREST 14→18 / WATERS、THUNDER 16→20 / RUINS 20→26）平滑前中期推进；主 Boss NORMAL/HARD 加厚血量（草原 800→1200 / 1550、森林 1000→1500 / 2000、水域 1500→1800 / 2350、雷域 1400→1650 / 2200、遗迹 HARD 3000→3400），NORMAL 补 `PASSIVE_REGENERATE` 延长战斗并完整展示阶段机制，遗迹 NORMAL 保持 3000 的同时 HARD 上调至 3400 维持难度坡度。捕捉曲线沿用既有公式（未调整系统规则），由 `BalanceVerificationTest` 锁定低血 / 异常收益与 clamp。以上为配置初调结论，仍需结合战斗调试（无敌 / 一击必杀 / 固定随机种子 / 伤害明细）在总验收阶段实测校准。

**响应式兼容适配**：全局 `main.css` 添加响应式基础断点（768px 平板 / 480px 手机，PC 优先）；为 13 个核心页面补充 `@media (max-width: 768px)` 移动端样式——BattleView（技能网格化、HP 条缩小、事件日志折叠）、PetView（标签页堆叠）、BossView（flex 列布局）、PokedexView（单列图鉴）、StorageView（筛选栏折叠）、InventoryView（列表适配）、HomeView（卡片堆叠）、SaveBackupView（存档管理适配）、DevToolsView（面板适配）、ShopView（单列商品）、WorldMapView（列表适配）、ExploreView（探索面板适配）、AchievementView / StatisticsView（列表紧凑化）。

**前端战斗调试信息展示**：BattleView 新增可折叠「调试信息」面板（仅 `gameStore.developerMode && snapshot.debugDamage` 时显示），展示每回合伤害明细与随机数序列（数据来源 `BattleSnapshot.debugDamage / debugRandomDraws`）；`battle.ts` 类型定义新增 `debugDamage` / `debugRandomDraws` 字段。

**E2E 测试脚本增强**：重写 `scripts/e2e/e2e-phase14-test.ps1`，覆盖九大核心场景 API 级验收——场景一（新游戏：重置→创建→Bootstrap 验证）、场景二（野外捕捉：遭遇→行动→捕捉→结算）、场景三（培养：升级→加点→洗点→技能装备）、场景四（3V3 战斗：完整测试战斗流程）、场景五（探索持续性：多场战斗 HP 消耗→营地恢复）、场景六（Boss：挑战→自动挑战）、场景七（自动战斗：配置自动→自动回合→结算）、场景八（重复捕捉：仓库筛选→放生预览→批量放生）、场景九（存档：导出→手动备份→备份列表→导入→数据恢复验证）。脚本含 PASS/FAIL 计数与汇总输出。

**美术资源接入（阶段 14 美术验收回归）**：按《美术资源接入与战斗表现修复计划.md》完成总验收回归修复。① 战斗快照展示标识——`BattleUnit` / `UnitSnapshot` 新增 `artType` / `artId`（仅用于资源定位，不参与战斗计算；PET=宠物、BOSS=Boss 核心、null=无资源测试敌人），`BattleService` / `BossEncounterSnapshotService` / `WildEncounterService` 分别映射玩家宠、Boss 核心、Boss 支援与野生单位；`UnitSnapshotMappingTest`（4 用例）覆盖宠物 / Boss 核心 / Boss 支援 / 无资源测试敌人。② 战斗立绘与特效——`BattleView` 按 `artType/artId` 渲染宠物或 Boss 立绘（无资源时保留文字卡片，不请求不存在路径）；四帧特效精灵图动画终点由 `-512px` 修正为 `-384px`，基础时长由 333ms 调整为 600ms 并随 1×/2×/3× 速度缩放，最后一帧不闪空白。③ 缺失道具图标——补齐 10 张被动技能书图标（`ITEM_SKILL_BOOK_VANGUARD/FORTIFY/ENTRY_BOOST/STURDY/RECUPERATE/ON_KILL_ATK/DEATH_FIRE/THORN_AURA/LAST_STAND/AVENGE`），统一转为 `item_{ID}.png` 命名，背包与商店无 404。④ 核心页面资源复用——`game-assets.ts` 新增 `petIconUrl` / `petPortraitUrl` 辅助函数；首页队伍条目（ART-05）、队伍六个槽位（ART-06）显示 64px 宠物图标，新游戏初始宠物卡片（ART-07）与宠物详情首屏（ART-08）显示宠物立绘，均与 `speciesId` 对应。资源一致性检查通过（27 种族图标 / 27 立绘 / 8 Boss 立绘 / 45 道具图标全部存在）。

**测试验证**：阶段 14 完成时后端 507 项测试通过；桌面重构阶段 0 复验更新为 512 项全部通过（Failures: 0, Errors: 0, Skipped: 0），Java 21 + Maven Surefire；前端 `vue-tsc -b` 类型检查通过（零错误），Vite 生产构建通过。

## 4. 遗留问题与已知限制

按阶段整理当前仍存在的遗留与限制：

- **阶段 14（已完成）**：数值平衡配置初调已完成，仍需结合战斗调试（无敌 / 一击必杀 / 固定随机种子 / 伤害明细）在实机游玩中持续校准；E2E 脚本仅覆盖 API 可验证部分，Phaser 地图交互（移动、营地接触、采集、宝箱）留作手动验收。存档导入当前只校验 saveVersion；桌面重构新增字段迁移由阶段 2 基于三类基线样本实现。
- **桌面重构阶段 1（验收待补）**：代码已通过类型、构建、后端回归和本地 MySQL/后端/Vite 烟测，但尚未在真实存档上完成世界状态保持、焦点、冻结和浏览器返回测试。浏览器连接组件目前不可用；详见 `PHASE1_WORLD_CONTEXT.md`。
- **阶段 12**：互动动作 ID（actionId）为表现预留，前端无对应动画资源时降级为纯文本 / 旁白；防重复队列为内存态（重启清空，可接受）；TRAINER 互动目前主要覆盖开发者模式简化测试战斗入口，正式 NPC 训练师战斗随后续阶段实装；普通野怪不记录挑战 / 连续战败统计。
- **阶段 11**：Boss 挑战目标仅判定 5 个已实装主 Boss，隐藏 / 精英 Boss 不设目标；完成度「隐藏区域」以隐藏 / 精英 Boss 击败比例近似（权重 5%）；完成度不要求 S 资质 / 特殊外观 / 稀有技能 / Boss 噩梦全通；成就 / 统计 / 挑战 / 完成度 / 履历均只作展示与奖励，不反向影响战斗数值。
- **阶段 10**：净化药仍为 `KEY_ITEM` 配置预留，尚未纳入 R-043 的恢复/复苏战斗道具范围；随机事件 TRIGGER_CAPTURE 复用遭遇战斗入口；埋伏点（ambushSpots）配置结构已就位但未在 maps.yml 填充具体数据；商店无每日刷新 / 限购（明确不做）。
- **阶段 8**：进化资料仅占位（真实进化资料属后续阶段）；图鉴奖励发放属后续阶段；Lv.5 野外识别返回资质预估等级标签，不泄露完整六维资质；历史记录放生不清除。
- **阶段 7**：当前 5 主 Boss + 3 隐藏 / 精英 Boss 已实装；BOSS 解锁类型区域为预留；幸运兑换成本与数量配置化。
- **阶段 6**：历史地图美术占位已在阶段 14 的美术资源批次中替换。
- **阶段 5**：捕捉球正式获取途径（商店 / 掉落）属阶段 10，当前仅新游戏赠送 + 开发者模式临时补充入口。

## 5. 临时技术债务

- Phaser 场景仅实现 BootScene / MapScene，BattleScene 表现层未接入（战斗以 Vue 页面呈现）。
- 桌面运行态截图待浏览器连接恢复后补采；阶段 0 已冻结视口与操作路径。
- 阶段 1 真实浏览器 Context E2E 待与上述同一环境问题一并补验；在补验通过前不得删除旧功能路由或推进阶段 2。
- 敌方胜利互动防重复队列为内存态。

## 6. 后续阶段

- **桌面重构阶段 2（当前）**：WorldGraph、PlayerKnowledge 与世界状态基础、Flyway V14、`/api/world` 接口与校验器已完成，后端全量测试/前后端构建与桌面运行态补验均通过（补验中修复 `PlayerWorldStateEntity` 缺 `@TableId` 的坏绑定缺陷）。
- **桌面重构阶段 3（后续）**：世界图谱前端投影与目录/快捷按钮交互，以及基于校验报告修复 Tiled 内容契约（森林出口缺失、遗迹返回等）。
- **历史阶段 14（第一阶段最终阶段，已完成）**：存档备份 / 恢复、开发者工具、教学、内容、美术、响应式与 E2E 等均已完成；当前回归基线为后端全量测试全绿，前端类型检查与生产构建通过。

---

> 阶段范围与验收标准详见《宠物精灵游戏分阶段开发规划 V1.0.md》。
