# 第十阶段：自动战斗策略系统设计说明

> 目标：在现有战斗规则之上构建一套**轻量、稳定、可配置、可解释、可测试、具有明显战术差异**的玩家侧自动战斗决策系统。
> 本文档为阶段 10 自动战斗子系统的实现说明（对应《第十阶段：智能自动战斗策略开发提示词.md》）。

---

## 1. 当前实现分析

### 1.1 决策架构现状

| 组件 | 职责 | 现状 |
|---|---|---|
| `DecisionProvider` | 决策来源接口：`decide(ctx, side) -> List<BattleAction>` | 已存在；注释明确预留「自动战斗接入 AutoDecisionProvider」 |
| `BattleEngine.playTurn(ctx, playerActions)` | 单回合结算入口；玩家行动来自参数，敌方行动由引擎持有的 DecisionProvider 决策 | 已存在；玩家侧决策在引擎之外注入 |
| `BattleEngine.runFullBattle(ctx, playerAI)` | 双方全 AI 自动跑完整战斗 | 已存在；Boss 自动挑战使用，玩家方目前复用 `WildEnemyDecisionProvider` |
| `WildEnemyDecisionProvider` | 野怪随机 AI（随机就绪技能 + 随机目标，沉默防御） | 已存在，保持不变 |
| `BossDecisionProvider` | 评分式 AI：候选生成 → 过滤 → 评分 → 接近分随机 | 已存在；含可复用的伤害估算/治疗估值/控制概率/接近分选择 |
| `BattleService.submitActions(battleId, actions)` | 玩家手动提交行动意图，按战斗类型路由 engine/bossEngine | 已存在；自动战斗的切换点 |

### 1.2 可复用能力

- **伤害估算**：`DamageCalculator.computeBaseValue/mitigate` + `registry.getElementAdvantageMultiplier`（现有克制表 ×1.50/×0.75）+ 本属性加成（Boss AI 的 `estimateDamage` 模式）。
- **治疗/护盾估值**：`HealCalculator.calculateHeal/calculateShield`。
- **控制概率估算**：`chance × controlResistance × consecutiveControlDecay`（与引擎 `computeFinalStatusChance` 同公式，仅估算不改结算）。
- **捕捉率**：`CaptureCalculator.computeCaptureRate`（纯函数）+ `countCaptureBonusStatuses`；战斗内捕捉球快照 `ctx.availableCaptureBalls/consumedCaptureBalls`（开战快照、结算统一扣库模式）。
- **状态识别**：状态五类模型（CONTINUOUS/BUFF/DEBUFF/SPECIAL_CONTROL/MARK）、`StatusModifiers`（沉默检测）、`captureStun`（震慑）标识。
- **技能语义**：`SkillConfig.tags`（AI 语义标签字段已存在）、`SkillConfig.effects`（LEAVE_AT_ONE_HP / LIFE_STEAL / DISPEL / HP_PERCENT_EXCHANGE / CHANGE_ACTION_ORDER / PROTECT_FROM_DEFEAT 等效果类型）。
- **统一随机**：`ctx.getRandom()`（GameRandom，种子可复现）；禁止 Math.random/new Random。
- **合法性校验**：引擎 `validateAndCollectPlayerActions`（技能持有/冷却/目标合法、换宠候补存活、捕捉合法性）；AI 候选生成端同步遵循，保证只产出合法行动。

### 1.3 缺失能力（本阶段新增）

1. 玩家侧 `AutoBattleDecisionProvider`（四策略统一评分引擎）。
2. 技能 AI 标签覆盖不全（61 技能仅 14 个有 tags）→ **tags 优先 + 效果结构推断兜底**，并补齐关键技能 tags。
3. 宠物战斗定位（`PetSpeciesConfig.role` 可选字段 + 基础属性自动推断）。
4. 战斗内道具行动（ITEM：恢复/复苏）——引擎新增 ITEM 行动分支，沿用捕捉球「开战快照 + 结算扣库」模式。
5. 玩家自动战斗偏好持久化（player 表新列，Flyway V9）。
6. 战斗级自动开关与策略选择（`BattleContext.autoSettings`，内存态）。

---

## 2. 自动战斗总体架构

```text
前端开关/策略选择 ──> POST /api/battles/{id}/auto（开启/关闭 + 策略 + 捕捉目标）
                              │
                              ▼
              BattleContext.autoSettings（内存态）
                              │
前端「结束回合」（actions 可为空）──> BattleService.submitActions
                              │
              autoSettings != null ?
                 ├── 是 → AutoBattleDecisionProvider.decide(ctx, playerSide) 生成玩家行动
                 └── 否 → 使用前端提交的行动（手动战斗，完全不变）
                              │
                              ▼
              engineFor(ctx).playTurn(ctx, playerActions)
                              │（引擎校验合法性 → 回合结算，全部规则不变）
```

- **AI 只负责选择行动**：伤害/命中/控制成功率/捕捉率/治疗量等实际结算仍由 BattleEngine 负责；AI 只做估算排序。
- **关闭自动战斗后**：submitActions 行为与现在完全一致，手动战斗零影响。
- **WildEnemyDecisionProvider / BossDecisionProvider 不受影响**（玩家 AI 独立实现，仅复用公共估算工具）。

---

## 3. AutoBattleDecisionProvider 设计

```java
@Component
public class AutoBattleDecisionProvider implements DecisionProvider {
    decide(ctx, side)               // 遍历存活上场单位逐个决策
      └─ decideForUnit(ctx, unit)
           ├─ collectCandidates()   // SKILL × 目标 / SWITCH / CAPTURE / ITEM / DEFEND 候选
           ├─ score 各候选          // 拆分为小方法（§5）
           ├─ pickBest()            // 最高分；接近分（tieTolerance 内）用 ctx.getRandom() 随机
           └─ fallback DEFEND       // 无候选时兜底
}
```

- 构造函数注入 `GameConfigRegistry`；读取 `SystemRuleConfig.autoBattle`（新增配置段）。
- 决策读取 `ctx.getAutoSettings()`（策略/开关/阈值/捕捉目标）。
- 方法拆分（避免巨型 decide）：`scoreDamage / scoreHeal / scoreControl / scoreSurvival / scoreCapture / scoreSwitch / scoreItem / scoreSpecialSkill / applyRoleModifier / applyStrategyModifier`。
- 调试日志：`log.debug("AutoBattle: strategy={} unit={} action={} score={} reasons={}")`。

---

## 4. 候选行动模型

```java
record Candidate(String type,     // SKILL / SWITCH / CAPTURE / ITEM / DEFEND
                 String skillId,  // SKILL 时非空
                 String targetId, // SKILL/CAPTURE/ITEM 目标
                 String itemId,   // CAPTURE 捕捉球 / ITEM 道具
                 double score)
```

候选生成规则：

| 类型 | 生成条件 |
|---|---|
| SKILL | 每个就绪技能（`getReadySkillIds()`，沉默时无候选）× 每个合法目标（单体逐一候选，群体一个候选求和） |
| SWITCH | `autoSwitch=true` 且存在存活候补；单位自身触发条件满足（低 HP / 被克制）时才生成 |
| CAPTURE | WILD 战斗且非 uncapturable 且有可用捕捉球且目标存活可捕捉；策略=CAPTURE 或任意策略目标 1HP+震慑时评分显著放大 |
| ITEM | `autoUseRecoveryItem=true` 且友方 HP < 阈值（恢复）；`autoRevive=true` 且存在倒下宠物（复苏） |
| DEFEND | 始终作为保底候选（低分），保证永不空手 |

---

## 5. 行动评分模型

```text
finalScore = baseValueScore          // 技能基础收益估算（伤害/治疗/护盾/控制期望）
           × contextScore            // 状态相关修正（目标血量/已有状态/护盾/驱散价值…）
           + bonusScore              // 斩杀奖励 / 捕捉奖励 / 特殊规则奖励
           × strategyWeight          // 策略预设权重（按技能语义标签查表）
           × roleWeight              // 宠物定位修正（按技能语义标签查表）
           − penaltyScore            // 误杀风险 / 高冷却浪费 / 过量治疗
```

评分来源拆分清晰（每个候选记录 reasons 字符串用于 debug 日志）：

- `baseValueScore`：伤害用 `estimateDamage`（复用 Boss AI 模式）；治疗用 `HealCalculator` × 缺失 HP 有效率；控制用基础分 × `estimateControlChance`。
- 关键修正：
  - **斩杀**：预计伤害 ≥ 目标有效生命（HP+护盾）→ +killBonus（进攻策略放大、CAPTURE 策略对可捕捉目标反转惩罚）。
  - **低血目标**：HP% 越低攻击评分越高（lowHpTargetWeight）。
  - **高伤浪费**：目标剩余 HP 极低（<5%）且技能高冷却 → 降权（overkillWastePenalty），除非进攻策略/可斩杀。
  - **FINISHER**：目标 HP ≤ finisherHpThreshold(30%) 时按 (1−HP%) 线性加权。
  - **HEAL**：过量部分不计分；HP < 70% 开始有价值，< 50% 提权，< 30% 高优先（三段阈值配置化）；满血≈0 分。
  - **SURVIVAL**：自身 HP% 越低、敌方存活威胁越高 → 权重越高。
  - **CONTROL**：目标已受控 ×existingControlPenalty；估算成功率含控制抗性 × 连续衰减。
  - **DISPEL**：敌方无 BUFF / 己方无 DEBUFF 时近 0 分；存在高价值可驱散状态时按数量/类别加分。
  - **SHIELD_BREAK**：目标护盾 > 0 时按护盾值加分，否则低分。
  - **ACTION_ORDER**：敌方高威胁单位速度领先 / 己方残血单位危险时加分；默认低基础分避免无脑使用。
  - **LIFE_STEAL**：攻击价值 + 恢复价值（按自身缺血程度计吸血收益），满血时不额外加成。
- **随机性**：仅最高分 ± tieTolerance(5%) 内的候选间随机（复用 Boss AI pickBest 模式）。

---

## 6. 技能 AI 标签策略

- **tags 优先**：读取 `SkillConfig.tags`（DAMAGE/HEAL/CONTROL/CAPTURE_ASSIST/SURVIVAL/FINISHER/SHIELD_BREAK/DISPEL/ACTION_ORDER/LIFE_STEAL/SWITCH）。
- **结构推断兜底**：无 tags 的技能按 effectType 与 effects 推断语义标签：
  - effectType=DAMAGE → DAMAGE（effects 含 LIFE_STEAL → +LIFE_STEAL；含 LEAVE_AT_ONE_HP → +CAPTURE_ASSIST；含 CHANGE_ACTION_ORDER → +ACTION_ORDER）
  - effectType=HEAL → HEAL；effectType=SHIELD → SURVIVAL
  - effects 含 APPLY_STATUS(SPECIAL_CONTROL) → CONTROL；含 DISPEL → DISPEL；含 HP_PERCENT_EXCHANGE → 特殊处理（§10）；含 PROTECT_FROM_DEFEAT → SURVIVAL
- **禁止按技能名称硬编码行为**。
- 补齐关键技能 tags（skills.yml）：命运天平（SURVIVAL 之外加显式标记）、吸血/破盾/驱散/集气/留生一击等已具备，缺失的基础攻击技能补 DAMAGE。

---

## 7. 四种自动策略

统一评分引擎 + 策略权重表差异化（配置化，system.yml）：

| 语义维度 | BALANCED | AGGRESSIVE | DEFENSIVE | CAPTURE |
|---|---|---|---|---|
| DAMAGE | 1.0 | 1.4 | 0.8 | 0.7 |
| FINISHER | 1.1 | 1.5 | 0.9 | 0.3 |
| HEAL | 1.0 | 0.6 | 1.5 | 1.0 |
| SURVIVAL | 1.0 | 0.7 | 1.4 | 1.1 |
| CONTROL | 1.0 | 0.8 | 1.2 | 1.2 |
| CAPTURE_ASSIST | 0.3 | 0.2 | 0.3 | 2.0 |
| CAPTURE 行动 | 0.2 | 0.1 | 0.2 | 1.0×（+1HP/震慑巨额加成） |
| SWITCH 倾向 | 1.0 | 0.6 | 1.4 | 1.0 |

- **AGGRESSIVE** 仍保留生存底线：自身 HP < aggressiveSurvivalFloor(15%) 且有治疗/换宠方案时生存修正回升。
- **DEFENSIVE** 不无限防御：敌方存在可斩杀目标时斩杀奖励保持生效；提前恢复阈值 defensiveHealEarly(50%)。
- **CAPTURE** 见 §9。

---

## 8. 宠物定位

- `PetSpeciesConfig` 新增可选 `role`（DAMAGE/TANK/SUPPORT/CONTROL）；未配置时按基础属性自动推断：
  - `baseHp + baseDefense + baseResistance` 相对最高 → TANK
  - `baseSpirit` 最高且技能含 HEAL/SURVIVAL → SUPPORT
  - 含 CONTROL 类技能占比高 → CONTROL
  - 其余 → DAMAGE
- 定位修正（roleWeight 表，配置化）：输出提高 DAMAGE/FINISHER/SHIELD_BREAK；坦克提高 SURVIVAL/CONTROL；辅助提高 HEAL/DISPEL/ACTION_ORDER/SURVIVAL；控制提高 CONTROL/ACTION_ORDER。
- **定位只影响倾向，不禁止行为**：可斩杀时任何定位都可完成击杀（斩杀奖励不受定位压制）。

---

## 9. 捕捉策略（CAPTURE）

核心链路：削弱 → 避免误杀 → 捕捉辅助 → 捕捉。

1. **普通阶段**（目标 HP > captureDangerHp 40%）：正常攻击削血，CAPTURE_ASSIST 技能小幅加权。
2. **危险血量区**（目标 HP ≤ 40%）：对可捕捉目标，预计伤害 ≥ 目标当前 HP 的攻击候选施加大额误杀惩罚（captureKillPenalty），除非技能含 LEAVE_AT_ONE_HP 效果。
3. **留生一击优先**：技能 effects 含 `LEAVE_AT_ONE_HP`（按效果类型识别，不按名称）且目标低 HP + 普通技能有误杀风险时，给予 captureAssistLeaveAliveBonus 高额加成。
4. **1 HP + 震慑 → 捕捉**：目标 `currentHp == 1` 且携带 captureStun 状态时，CAPTURE 候选 +captureReadyBonus（巨额），原则上优先于一切攻击。
5. **捕捉候选评分**：`CaptureCalculator.computeCaptureRate`（复用纯函数）× 球倍率价值 × 策略权重；多个球时优先选高倍率球（捕捉率更高），但考虑稀缺性微调（高级球数量少时小幅降权，配置化）。
6. **捕捉目标指定**：`autoSettings.captureTargetId`（前端指定）优先；未指定默认选可捕捉的最低 HP 敌人。
7. 合法性：Boss 战（uncapturable）/ 无捕捉球 / 目标已被捕捉 → 不生成 CAPTURE 候选。

---

## 10. 命运天平（HP_PERCENT_EXCHANGE）特殊规则

不按 SURVIVAL/HEAL 标签给分，独立收益模型：

```text
selfGainPercent  = 交换后自身HP% − 当前自身HP%
enemyGainPercent = 交换后目标HP% − 当前目标HP%
netBenefit       = selfGainPercent − enemyGainPercent × enemyWeight(1.0)
```

- `netBenefit ≥ balanceMinBenefit(0.25)` 才成为有效候选；目标为 Boss 时阈值提高到 `balanceBossMinBenefit(0.45)`。
- 自身 HP% ≥ 目标 HP% 时直接过滤（无收益）。
- 技能冷却本身限制频繁使用；评分再乘 lowFrequencyFactor 避免与其他技能竞争时轻易胜出。

---

## 11. 自动换宠

- 开关 `autoSwitch`（默认开）+ 阈值 `autoSwitchHpThreshold`（默认 0.25）。
- 触发条件（满足其一才生成 SWITCH 候选）：当前宠物 HP% < 阈值；当前宠物被敌方主力技能属性克制且 HP% < 0.5；DEFENSIVE 策略阈值上浮 0.1。
- 候补评分：候补 HP%（残血候补大幅降权，除非无其他选择）+ 对当前敌方属性克制适配 + 定位适配。
- 切换消耗一次行动（现有规则），因此换宠评分与当前最优技能评分同台竞争，不会无条件换宠。

---

## 12. 自动恢复道具

- 开关 `autoUseRecoveryItem`（**默认关闭**）+ 阈值 `autoRecoveryHpThreshold`（默认 0.35）。
- ITEM 候选：上场宠物 HP% < 阈值时，为每个可用恢复道具 × 目标生成候选。
- 道具评分：有效恢复量（min(恢复量, 缺失HP)，过量不计）× 道具效率系数；**最小有效方案优先**——恢复量足够时选最小满足项，大药仅在小药不足时高分（避免缺 50 HP 用 500 恢复）。
- 战斗内执行：引擎 ITEM 行动分支 → HP 增加（不超上限）；消耗记录 `ctx.consumedRecoveryItems`，结算时统一扣背包（同捕捉球模式，战斗内零 DB 写入）。
- 背包快照：开战时快照恢复/复苏道具存量到 ctx（`availableRecoveryItems`），战斗内数量校验。

---

## 13. 自动复苏

- 开关 `autoRevive`（**默认关闭**）。
- 触发判断（非"有死亡就复苏"）：存在倒下宠物 且（己方存活上场数 ≤ 1 或 敌方仍有多数存活 且 当前局势危险）；敌人即将被斩杀（任一敌人 HP < 10%）时复苏降权。
- 候选：复苏道具 × 倒下宠物（评分考虑倒下宠物定位与复苏后 HP）。
- 引擎执行：倒下候补/上场宠物复活至道具指定 HP 值（value 字段），同样走快照扣库。

---

## 14. 配置结构

### 14.1 system.yml 新增 `autoBattle` 段（全局参数，配置化）

```yaml
autoBattle:
  tieTolerance: 0.05
  killBonusPercent: 0.6
  lowHpTargetWeight: 0.5
  overkillWastePenalty: 0.4        # 高伤打极低血目标降权比例
  finisherHpThreshold: 0.30
  healHpThresholds: [0.70, 0.50, 0.30]   # 治疗三段阈值
  healUrgencyMultipliers: [1.2, 1.6, 2.2]
  healNoNeedHpPercent: 0.90
  controlBaseScore: 60.0
  utilityBaseScore: 40.0
  existingControlPenalty: 0.15
  aggressiveSurvivalFloor: 0.15
  defensiveHealEarly: 0.50
  captureDangerHp: 0.40
  captureKillPenalty: 0.85         # 误杀风险惩罚比例
  captureAssistLeaveAliveBonus: 80.0
  captureReadyBonus: 300.0         # 1HP+震慑捕捉加成
  balanceMinBenefit: 0.25
  balanceBossMinBenefit: 0.45
  strategyWeights:                 # 策略 × 语义标签权重表（略，见 §7）
    BALANCED: { DAMAGE: 1.0, ... }
    ...
  roleWeights:                     # 定位 × 语义标签权重表（略，见 §8）
    DAMAGE: { ... }
    ...
```

### 14.2 玩家偏好（Flyway V9，player 表新列）

```text
auto_strategy              VARCHAR(16)  DEFAULT 'BALANCED'
auto_switch                TINYINT(1)   DEFAULT 1
auto_switch_hp_threshold   INT          DEFAULT 25    -- 百分比
auto_use_recovery_item     TINYINT(1)   DEFAULT 0    -- 默认关闭
auto_recovery_hp_threshold INT          DEFAULT 35
auto_revive                TINYINT(1)   DEFAULT 0    -- 默认关闭
```

### 14.3 战斗级开关（内存）

`BattleContext.autoSettings`：`{ enabled, strategy, captureTargetId }`（开关与阈值从玩家偏好读取，战斗开始/开启自动时载入）。`POST /api/battles/{id}/auto` 开启/关闭并更新偏好。

---

## 15. BattleEngine 集成

1. **BattleService.submitActions**：`ctx.getAutoSettings() != null && enabled` 时用 `autoBattleDecisionProvider.decide(ctx, playerSide)` 替代前端行动列表，再交给 `engineFor(ctx).playTurn`。引擎校验照常执行（AI 只产出合法行动，双保险）。
2. **BattleEngine 新增 ITEM 行动分支**（validate + execute）：
   - 校验：道具在 `ctx.availableRecoveryItems` 且剩余可用数量 > 已消耗；目标合法（恢复=存活单位，复苏=倒下单位）。
   - 执行：HEAL_HP 恢复（不超上限）/ REVIVE 复活；累计 `ctx.consumedRecoveryItems`。
   - 事件：`BattleEventType.ITEM_USED`（新增枚举值，前端日志展示）。
3. **BattleContext 新增**：`autoSettings`、`availableRecoveryItems`、`consumedRecoveryItems`。
4. **BattleService 开战链路**（startTestBattle/startWildBattle/createBossBattle）：快照恢复/复苏道具存量；从玩家偏好构建默认 autoSettings（enabled=false）。
5. **结算**：`settleBattle` 中扣除 `consumedRecoveryItems`（同捕捉球扣库位置）。
6. **runFullBattle**：Boss 自动挑战玩家方 AI 从 WildEnemyDecisionProvider 切换为 AutoBattleDecisionProvider（BALANCED）。
7. **手动战斗零影响**：autoSettings 为 null 或 enabled=false 时链路完全不变。

---

## 16. 需要修改的文件

### 新增
- `battle/ai/AutoBattleDecisionProvider.java`（核心评分引擎）
- `battle/ai/AutoBattleSettings.java`（战斗级自动设置模型）
- `config/model/SystemRuleConfig.java` 内新增 `AutoBattleConfig` 内部类（含 StrategyWeights/RoleWeights）
- Flyway `V9__auto_battle.sql`
- 测试 `AutoBattleDecisionProviderTest.java`
- 前端 `types/battle.ts` 扩展 + BattleView 自动战斗面板

### 修改
- `engine/BattleContext.java`：+autoSettings/+availableRecoveryItems/+consumedRecoveryItems
- `engine/BattleEngine.java`：+ITEM 行动校验与执行分支（validateAndCollectPlayerActions + 行动执行循环）
- `event/BattleEventType.java`：+ITEM_USED
- `service/BattleService.java`：submitActions 自动切换、开战道具快照、结算扣道具、runFullBattle AI 替换、auto 开关端点
- `controller/BattleController.java`：+POST /api/battles/{id}/auto、+GET /api/battles/auto-preference、+PUT /api/battles/auto-preference（偏好读写）
- `config/model/PetSpeciesConfig.java`：+role（可选）
- `config/loader/GameConfigValidator.java`：autoBattle 段校验（权重表策略枚举、阈值范围）
- `player/entity/PlayerEntity.java`：+6 个自动战斗偏好字段
- `game-config/system.yml`：+autoBattle 段
- `game-config/skills/skills.yml`：补齐缺失 tags

---

## 17. 测试方案

### 单元测试 `AutoBattleDecisionProviderTest`（构造 BattleContext 直测 decide）

| 分组 | 用例 |
|---|---|
| 基础决策 | 只选合法技能；冷却技能不被选；死亡目标不被攻击；属性克制目标优先；残血斩杀优先；无技能时 fallback DEFEND |
| 均衡策略 | 危险时治疗；可斩杀时完成击杀 |
| 进攻策略 | DAMAGE 权重高于均衡；FINISHER 更积极；HP 极低时仍会生存行动 |
| 稳健策略 | 低 HP 优先 HEAL/SURVIVAL；更愿意换宠；敌方残血时仍会击杀 |
| 捕捉策略 | 高血正常削血；低血避免误杀技能；有留生一击时优先压 1HP；1HP+震慑优先捕捉；CAPTURE_ASSIST 随状态变化 |
| 治疗 | 满血不治疗；低血治疗权重增加；避免严重过量 |
| 控制 | 已受控目标不重复控制；控制抗性/衰减降低收益 |
| 驱散 | 无可驱散状态时低分；有高价值 BUFF/DEBUFF 时提权 |
| 破盾 | 有护盾提权；无护盾不因标签释放 |
| 吸血 | 自身残血价值增加；满血无不合理高分 |
| 命运天平 | 自身明显低于目标才考虑；小收益不触发；Boss 高阈值 |
| 换宠 | autoSwitch=false 不换；开启后危险时换；不优先换残血候补；考虑克制 |
| 道具 | autoUseRecoveryItem=false 绝不用；开启后阈值触发；最小有效方案优先；autoRevive=false 不复苏 |

### 回归
- 现有全部战斗测试（BattleEngine 18 场景、捕捉、Boss AI、结算）必须通过。
- 手动战斗提交链路不受影响（submitActions 手动用例）。

---

## 18. 不在本阶段实现

- 机器学习 AI / LLM 在线决策 / 行为树编辑器 / AI 个性系统
- 复杂多回合预测 / 蒙特卡洛搜索 / 深层博弈树
- PVP AI、自动战斗脚本语言、几十项玩家自定义 AI 参数
- 动态难度系统（AI 不根据玩家等级/战力作弊式调整）
- 捕捉球自动选择的高级稀缺性经济策略（仅基础倍率优先）

---

## 附：与既有裁决的关系说明

阶段 3/4 曾裁决「恢复道具仅战斗外使用，战斗内无道具行动」，阶段 10 随后为自动战斗实现了 ITEM 行动。桌面重构阶段 0 的 D0-02 现已按 R-043 统一裁决：手动与自动战斗均可使用共享库存中 `usableInBattle=true` 的 `HEAL_HP`/`REVIVE` 道具，自动使用仍默认关闭；捕捉球继续走 CAPTURE 行动。前端过滤不替代服务端校验，当前服务端 `usableInBattle` 强制校验缺口归桌面重构阶段 5。
