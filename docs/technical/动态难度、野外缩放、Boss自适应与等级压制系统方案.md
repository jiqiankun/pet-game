# 动态难度、野外缩放、Boss自适应与等级压制系统方案

## 1. 目标与边界

本方案作为第一阶段新增的阶段 13，提供可切换的全局难度、有限野外缩放、Boss 首次遭遇快照与高难等级压制。它只调整战斗入场数据，不修改宠物存档真实等级、成长、技能解锁、经验与掉落的既有规则。

全局难度固定为 `NORMAL`、`ELITE`、`NIGHTMARE`、`HELL`。它与既有 Boss 内容难度 `NORMAL`、`HARD`、`NIGHTMARE` 是两套独立维度：前者决定本次存档的全局挑战规则，后者决定某个 Boss 的原有配置、解锁与奖励。

明确不做：世界等级、无限追踪野外单位、动态降低 Boss 来照顾弱队、第二套战斗引擎、真实等级写回、行为树、复杂职业克制表、全队硬性等级一致化。

## 2. 配置模型

所有新数值位于 `system.yml` 的 `gameDifficulty` 下，并由 `SystemRuleConfig.GameDifficultyConfig` 读取、`GameConfigValidator` 校验。每个难度档只配置以下内容：

- 野外普通/精英的基础偏移、动态偏移范围、等级上下界、队伍数量范围、简单协同强度；
- Boss 等级偏移、有限向上自适应上限、可选位数、轻量属性倍率与 AI 强度；
- Boss 战玩家有效等级上限开关与相对 Boss 的上限偏移。

地图区域新增数值字段 `recommendedEnemyLevel`、`minEnemyLevel`、`maxEnemyLevel`，保留原有 `recommendedLevel` 文本仅作展示。最终野外等级始终夹在地图、难度和游戏等级上限的交集内；没有有效交集时启动校验直接失败。

## 3. 野外遭遇

队伍参考等级取当前生效预设中等级最高的至多三只宠物平均值，真实不足三只就按实际数量平均。它只提供有限向上或向下修正，不能突破地图和难度边界。

```text
候选等级 = 地图推荐等级 + 全局难度基础偏移
         + clamp(队伍参考等级 - 地图推荐等级, 动态下限, 动态上限)
         + 刷新组等级波动
最终等级 = clamp(候选等级, 地图下限, 地图上限, 难度下限, 难度上限, Lv.50)
```

精英在普通野生等级基础上使用同一套边界再施加其既有精英等级奖励；不会另建精英成长公式。最终敌方数量取刷新组随机数量与难度数量范围的交集，并受 `standardBattleSlots` 限制。高难仅依据已有 `PetSpeciesConfig.role` 让后续抽取优先补足 `DAMAGE + CONTROL` 或 `TANK + SUPPORT`，不读取玩家元素来构造完美反制。

野外单位仍然是即时生成的 `BattleUnit`，不持久化，不追踪跨战斗成长。捕获时仍按其生成时的真实等级与原有捕捉数据落库。

## 4. Boss 自适应与快照

Boss 的主题核心始终来自既有 `bosses.yml` 内容难度配置，绝不被玩家低等级下调。可选支援位由 Boss 配置的候选种族池生成：优先补足固定主题所需的坦克、治疗、控制等角色，最多按全局难度增加配置规定的数量；不重复种族，不按玩家单一弱点配出完美克制队。

首次点击挑战时，在创建战斗前生成并持久化 `BossEncounterSnapshot`。快照包含：

- `saveId`、Boss ID、Boss 内容难度、当时全局难度、版本和随机种子；
- Boss 生成等级、玩家有效等级上限、Boss AI 强度；
- 已生成的核心/可选单位、等级、属性、技能、被动、阶段配置与位置；
- 创建时间、最近使用时间和首次击败标记。

同一存档、同一 Boss、同一 Boss 内容难度只保留一份快照。战败、服务重启、自动挑战、再次挑战都复用它；全局难度变更后旧快照仍有效且在 Boss 页明确提示。仅当当前全局难度与快照难度不同，才允许玩家在确认提示后显式重置为当前难度；相同难度下不提供重掷入口。

## 5. 有效等级与自由点投影

高难 Boss 战可设置玩家有效等级上限：

```text
effectiveLevel = min(actualLevel, playerLevelCap)
playerLevelCap = min(gameLevelCap, bossGeneratedLevel + difficulty.playerCapOffset)
```

只在本场 Boss 战的 `BattleUnit` 快照上生效，真实 `PlayerPetEntity` 永不修改。实际等级、已学技能、获得经验、图鉴和战后 HP 写回都仍使用真实数据；属性、速度、伤害、治疗、护盾等则统一读取重算后的战斗六维。

属性重算复用 `PetGrowthService.computePanelStatsAtLevel`，不复制成长公式。自由点使用既有 `freePointsEarned(effectiveLevel, rarity)` 预算投影：若已消耗点数未超过有效预算则原样保留；超出时按当前六维的已消耗点数比例分配，先取整，再按最大余数和既有点数成本补齐，确保有效消耗不超过预算。当前第一阶段自由点仅来源于等级成长，因此无需另行区分来源。

玩家战斗单位和前端快照同时暴露 `actualLevel`、`effectiveLevel`；野外与普通 Boss 默认两者相同。所有战斗计算仍只通过同一 `BattleEngine`，引擎不读取数据库实体。

## 6. 接入与数据流

```text
设置页修改 gameDifficulty → player.game_difficulty
当前预设 + 当前地图 + 难度配置 → WildEncounterService → BattleUnit
Boss 首次挑战 → BossEncounterSnapshotService → 快照 → BattleService → BattleUnit
Boss 战玩家单位 → BattleLevelResolver → 有效属性 BattleUnit → BattleEngine
结算 → 真实宠物 HP / 奖励 / 捕获按原流程处理
```

后端职责：`GameService` 负责全局难度读写；`WildEncounterService` 负责野外入场生成；`BossEncounterSnapshotService` 负责快照创建、读取和受限重置；`BattleLevelResolver` 只负责玩家战斗临时属性。`BattleService` 保持为唯一编排与 BattleEngine 入口。

前端设置页提供难度说明和保存；Boss 页显示快照全局难度、是否与当前设置不同及受限重置按钮；战斗页显示实际等级与有效等级不同时的压制提示。

## 7. 校验与测试

- 启动校验：四档难度齐全、偏移/队伍数/倍率/AI 强度合法，地图数值边界合法，Boss 可选种族与角色引用存在；
- 单元测试：队伍参考等级、地图/难度夹取、不同难度等级与队伍数、精英边界、角色协同不重复；
- 单元测试：Boss 快照首次生成、失败和重启复用、跨难度保留、仅跨全局难度允许显式重置；
- 单元测试：有效等级不改实体、属性复用成长公式、自由点预算投影与最大余数稳定、HP 比例映射；
- 回归测试：伤害、治疗、速度、护盾、状态与结算都经同一 `BattleUnit` 六维；正常难度无等级压制；捕获和奖励不被有效等级污染；
- 集成验证：设置难度 → 野外遭遇 → Boss 首战失败重试 → 切换难度提示与重置 → 自动挑战复用快照。

