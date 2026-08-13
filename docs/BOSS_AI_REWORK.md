# Boss AI 改造说明（阶段 7 增强）

本文档描述 `BossDecisionProvider` 的智能决策改造：现状分析、问题清单、决策模型、评分规则与实施方案。所有内容基于当前代码库实际实现。

---

## 1. 当前实现分析

### 1.1 决策架构

- `DecisionProvider` 接口（`battle/ai/DecisionProvider.java`）：`decide(ctx, side)` 为指定方每个存活上场单位返回一条 `BattleAction`。
- `BattleEngine` 构造时绑定单个 `enemyDecisionProvider`，`playTurn` 中统一调用 `enemyDecisionProvider.decide(ctx, ctx.getEnemySide())`。手动/自动/野生/Boss 全部走同一引擎，差异仅在 DecisionProvider。
- `WildEnemyDecisionProvider`：就绪技能随机选一个，目标随机；沉默/无技能时 DEFEND。随机数统一来自 `ctx.getRandom()`（GameRandom，固定种子可复现）。
- `BossDecisionProvider`（现状）：对就绪技能按 effectType 评分（DAMAGE=基础值+strength/spirit 系数×克制最大值；HEAL=低血时高分；其余=20+chance×30），目标固定选 HP% 最低者。

### 1.2 引擎已有可复用能力

| 能力 | 位置 | AI 复用方式 |
|---|---|---|
| 伤害公式 | `DamageCalculator.computeBaseValue / mitigate` | 估算技能期望伤害（不含暴击/随机） |
| 治疗/护盾估算 | `HealCalculator.calculateHeal / calculateShield` | 估算治疗量、判断过量治疗 |
| 属性克制 | `GameConfigRegistry.getElementAdvantageMultiplier` | 读取现有克制表，不重新硬编码 |
| 控制状态分类 | `StatusEffectConfig.category == SPECIAL_CONTROL` | 识别控制技能/目标已受控 |
| 控制抗性/连续衰减 | `BattleEngine.computeFinalStatusChance`（私有） | AI 按同一公式估算最终成功率（chance × controlResistance × consecutiveControlDecay），不修改引擎算法 |
| 阶段触发 | `BattleUnit.phaseTriggers / phaseActivated`（引擎 `checkPhaseTriggers` 维护） | AI 只读取激活数量判断当前阶段，不重建阶段系统 |
| 技能冷却 | `BattleUnit.getReadySkillIds()` | 只从就绪技能中生成候选 |
| 沉默判定 | `StatusModifiers.of(unit, statusIndex).isSilenced()` | 沉默时 DEFEND |

### 1.3 注入链路（现状，存在缺陷）

- `BattleService` 构造单个 `engine = new BattleEngine(registry, WildEnemyDecisionProvider)`，`submitActions` 与 `startBossBattle` 均使用它。
- `BossService.autoChallenge` 中 `battleService.startBossBattle(...)` 内部已调用 `engine.startBattle(ctx)`，随后又创建 `autoEngine.runFullBattle(ctx, ...)`（内部再次 `startBattle`）。

## 2. 存在的问题

1. **【链路 BUG】手动 Boss 战未接入 Boss AI**：`submitActions` 始终使用绑定 `WildEnemyDecisionProvider` 的 engine，Boss 战中敌方实际是随机野怪 AI，`BossDecisionProvider` 只在自动挑战路径生效。
2. **【链路 BUG】自动挑战双 startBattle**：`startBossBattle` 与 `runFullBattle` 各调用一次 `startBattle`，ON_ENTER / BATTLE_START 被动重复触发。
3. **未使用阶段机制**：仅硬编码 `selfHp < 0.30 → 攻击分 ×1.3`，完全不读 `phaseTriggers / phaseActivated`，无「均衡 → 进攻 → 爆发」阶段策略。
4. **技能与目标评分分离**：评分取「全体目标中的最大克制」，目标选择固定最低 HP%，两者可能错配（克制技能打到非克制目标）。应按「技能 × 目标」组合生成候选行动统一评分。
5. **伤害估算不完整**：未复用 `DamageCalculator.computeBaseValue`（漏 maxOf/maxOfCoefficient），无防御/抗性减伤与本属性加成估算；scaling key 硬编码 strength/spirit。
6. **无控制技能策略**：不检查目标是否已受控、不参考控制抗性与连续控制衰减 → 会机械连续控制。
7. **无斩杀奖励**：不判断「预计伤害 ≥ 目标当前 HP+护盾」的击杀机会。
8. **治疗策略粗糙**：无过量治疗检查、无 ALLY_ALL/SELF 目标类型处理、ALLY_SINGLE 两个分支代码重复。
9. **完全确定性**：无任何随机，行为完全可预测；应在评分接近的候选间做小幅随机（使用 `ctx.getRandom()` 保持可复现）。
10. **魔法数字散落**：0.30/1.3/0.50/1.5/0.3/20/30 等硬编码，未配置化。

## 3. Boss AI 决策模型

规则型评分 AI（候选行动生成 → 过滤 → 评分 → 选最高分，接近分随机）：

```text
对每个存活上场 Boss 单位：
  沉默 或 无就绪技能 → DEFEND（兜底）
  生成候选行动（技能 × 目标组合）：
    攻击单体：每个存活敌方目标一个候选
    攻击群体：一个候选（目标 = null）
    治疗：每个合法友方目标一个候选（SELF/ALLY_ALL 目标 = null/自身）
    控制/减益/增益：每个合法敌方目标一个候选
    护盾：按目标类型一个候选
  评分 = 基础估算分 × 阶段修正 + 克制/斩杀/低血/控制收益/治疗收益修正
  过滤死亡单位、非法目标
  在评分 ≥ 最高分 × (1 - tieTolerance) 的候选间用 ctx.getRandom() 随机选择
```

明确约束：
- **不读取玩家等级/战力/属性总和做动态缩放**（需求 §80）；仅依据战场状态（目标残血/受控/克制/可击杀）决策。
- 伤害/克制/控制成功率的**实际结算**仍由 BattleEngine 负责，AI 只做估算排序。

## 4. 候选行动评分规则

### 4.1 攻击（effectType=DAMAGE）

```text
估算伤害 = mitigate(computeBaseValue(skill, caster), 目标防御/抗性, K)
           × 克制倍率 × 本属性加成
score   = 估算伤害 × (1 + lowHpTargetWeight × (1 - 目标HP%))        # 低血目标加权
if 估算伤害 ≥ 目标(currentHp + shield)：score += 估算伤害 × killBonusPercent   # 斩杀奖励
score  += 附加状态效果 chance × statusEffectBonus × 估算伤害          # DOT/减益附加值
score  ×= phaseAttackMultipliers[阶段]
群体技能：对所有存活目标求和（含各自斩杀奖励），目标数越多越优。
```

### 4.2 治疗（effectType=HEAL）

```text
missing = 目标 maxHp - currentHp；missing ≤ 0 → 候选无效（不治疗满血单位）
估算治疗量 = HealCalculator.calculateHeal(caster, skill)
effective  = min(估算治疗量, missing)                     # 过量部分不计分
score = effective
目标 HP% < healTriggerHpPercent(0.40) → score ×= healUrgencyMultiplier(1.8)
目标 HP% > healNoNeedHpPercent(0.90) → score ×= 0.1（接近满血不浪费治疗）
score ×= phaseHealMultipliers[阶段]（三阶段下降，允许爆发优先于小额治疗）
目标选择：每个合法友方生成候选，评分自然选出 HP% 最低者。
```

### 4.3 控制（APPLY_STATUS 引用 SPECIAL_CONTROL 状态的技能）

```text
目标已携带任一 SPECIAL_CONTROL 状态 → score ×= existingControlPenalty(0.10)（不机械重复控制）
估算成功率 = chance × target.controlResistance × consecutiveControlDecay[target.consecutiveControlCount]
            （与 BattleEngine.computeFinalStatusChance 同公式，仅估算）
score = controlBaseScore(60) × 估算成功率 × phaseControlMultipliers[阶段]
```

连续控制衰减由引擎维护 `consecutiveControlCount`，AI 读取后自然降低重复控制评分。

### 4.4 减益/增益（effectType=NONE 且附加非控制状态，如 CURSE/TAUNT/ARMOR_BREAK）

```text
目标已携带同名状态 → score ×= existingControlPenalty
score = utilityBaseScore(40) × chance × phaseControlMultipliers[阶段]
```

### 4.5 护盾（effectType=SHIELD）

```text
score = calculateShield(caster, skill) × (1 + (1 - 自身HP%) × 0.5) × phaseHealMultipliers[阶段]
```

## 5. 阶段策略

阶段判断**直接读取引擎维护的运行时状态**，不重建阶段系统：

```java
阶段索引 = unit.phaseActivated 中 true 的数量（0 = 第一阶段）
```

| 阶段 | 条件（按 phaseTriggers 配置） | 攻击倍率 | 控制倍率 | 治疗倍率 | 行为倾向 |
|---|---|---|---|---|---|
| 1 | 未触发任何阶段 | ×1.0 | ×1.0 | ×1.0 | 均衡：攻击/控制/治疗按评分自然竞争 |
| 2 | 第 1 个触发器已激活（通常 HP≤50%） | ×1.3 | ×0.8 | ×0.8 | 进攻：提高攻击权重，降低非必要辅助 |
| 3 | 第 2 个及更多触发器已激活（通常 HP≤30%/25%） | ×1.6 | ×1.2 | ×0.6 | 爆发：攻击/斩杀大幅加权，关键控制回升，治疗降权 |

倍率数组均配置化（`system.yml bossAi`），索引越界时取最后一项。仅配置单阶段的 Boss 最多进入阶段 2，符合其配置意图。

## 6. 与 BattleEngine 的集成方式（链路修复）

- `BattleService` 构造两个引擎实例（同一 BattleEngine 类，不同 DecisionProvider，符合既定设计）：
  - `engine`（WildEnemyDecisionProvider）：TEST / WILD 战斗。
  - `bossEngine`（BossDecisionProvider）：BOSS 战斗。
- `submitActions` 按 `ctx.getBattleType()` 路由到对应引擎。
- `startBossBattle` 使用 `bossEngine.startBattle`。
- 新增 `createBossBattle(...)`（创建上下文不 startBattle），供自动挑战使用，消除双 startBattle。
- `BossService.autoChallenge` 改用 `createBossBattle` + `autoEngine.runFullBattle`。
- BattleEngine 本身不新增任何 Boss 专属判断。

## 7. 需要修改的文件

| 文件 | 修改 |
|---|---|
| `battle/ai/BossDecisionProvider.java` | 重写为候选行动评分模型 |
| `battle/service/BattleService.java` | bossEngine + engineFor 路由 + createBossBattle |
| `boss/service/BossService.java` | autoChallenge 改用 createBossBattle |
| `config/model/SystemRuleConfig.java` | 新增 BossAiConfig 嵌套配置 |
| `resources/game-config/system.yml` | 新增 bossAi 配置块 |
| `test .../BossDecisionProviderTest.java` | 扩充至 18 类场景 |
| `test .../BattleServiceSettlementTest.java` | 适配构造函数新参数 |
| `docs/BACKEND_STANDARDS.md` | §18.6 Boss AI 约定更新 |

## 8. 测试方案

固定种子 + 程序化配置夹具（`BattleTestFixtures`），验证行为倾向而非随机结果。覆盖：

1. 优先克制目标属性的技能 2. 倾向攻击 HP% 最低目标 3. 冷却技能不被选择 4. 多攻击技能倾向高收益
5. 一阶段均衡 6. 二阶段攻击权重提升 7. 三阶段爆发/斩杀倾向 8. HP<40% 治疗优先提高
9. 满血不治疗 10. 已受控目标不重复控制 11. 控制抗性/衰减影响控制决策 12. 可斩杀目标优先击杀
13. 全冷却 fallback DEFEND 14. 死亡单位过滤 15. 阶段切换后策略变化 16/17. Provider 路由正确
18. 不读取玩家属性做动态缩放（AI 仅读战场状态，由实现约束 + 代码审查保证）

## 9. 明确不修改的范围

- `WildEnemyDecisionProvider`（普通敌人仍随机 AI）。
- `BattleEngine` 回合流程、伤害/状态/控制结算算法、阶段触发逻辑。
- `DecisionProvider` 接口、`BattleAction`/`BattleUnit` 数据结构。
- 玩家决策链路、捕捉/逃跑/结算逻辑。
- 前端。
