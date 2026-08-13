package com.petgame.battle.engine;

import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.calculator.AccuracyCalculator;
import com.petgame.battle.calculator.CaptureCalculator;
import com.petgame.battle.calculator.DamageCalculator;
import com.petgame.battle.calculator.HealCalculator;
import com.petgame.battle.calculator.StatusModifiers;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.battle.passive.PassiveManager;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗引擎（技术方案 §17-§18）。
 * <p>
 * 整个项目技术核心。手动/自动/普通/精英/Boss 战斗全部使用同一个 BattleEngine，
 * 唯一差异是「谁决定行动」（DecisionProvider）。战斗结果必须由后端计算，
 * 前端只提交行动意图。战斗过程不产生任何数据库写入。
 * <p>
 * 回合流程：收集双方行动 → 按速度计算行动顺序（每回合重算，同速随机）→
 * 依次执行（技能/防御/换宠/蓄力释放）→ 结算状态（DOT、持续时间、冷却）→ 胜负判定。
 */
public class BattleEngine {

    private static final Logger log = LoggerFactory.getLogger(BattleEngine.class);

    private final GameConfigRegistry registry;
    private final DamageCalculator damageCalculator;
    private final HealCalculator healCalculator;
    private final PassiveManager passiveManager;
    private final DecisionProvider enemyDecisionProvider;

    public BattleEngine(GameConfigRegistry registry, DecisionProvider enemyDecisionProvider) {
        this.registry = registry;
        this.damageCalculator = new DamageCalculator(registry);
        this.healCalculator = new HealCalculator(registry);
        this.passiveManager = new PassiveManager(registry);
        this.enemyDecisionProvider = enemyDecisionProvider;
    }

    public PassiveManager getPassiveManager() {
        return passiveManager;
    }

    /**
     * 开始战斗：触发双方上场单位的登场被动，随后处理开局已倒下单位
     * （HP=0 参战 → 倒下、候补补位、胜负判定，需求 §45 战斗后倒下保持 0HP）。
     */
    public void startBattle(BattleContext ctx) {
        for (BattleUnit unit : ctx.getPlayerSide().getActiveAliveUnits()) {
            passiveManager.trigger(ctx, "ON_ENTER", unit);
        }
        for (BattleUnit unit : ctx.getEnemySide().getActiveAliveUnits()) {
            passiveManager.trigger(ctx, "ON_ENTER", unit);
        }
        // 开战被动（REV-009，技术方案 §78 BATTLE_START）
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getActiveAliveUnits()) {
                passiveManager.trigger(ctx, "BATTLE_START", unit);
            }
        }
        // 开局倒下处理：0HP 单位立即倒下（含候补补位），若一方全灭则直接判负
        processDefeats(ctx);
        checkBattleEnd(ctx);
    }

    /**
     * 结算一整个回合（技术方案 §43：后端完成一整个回合结算）。
     *
     * @param ctx           战斗上下文
     * @param playerActions 玩家提交的行动意图（每只存活上场宠物一条）
     */
    public TurnResult playTurn(BattleContext ctx, List<BattleAction> playerActions) {
        if (ctx.isFinished()) {
            throw new BusinessException("BATTLE_FINISHED", "战斗已结束，不能再行动");
        }
        int eventStart = ctx.getEvents().size();
        int round = ctx.getCurrentRound() + 1;
        ctx.setCurrentRound(round);

        // 1. 收集双方行动（玩家意图 + 敌方 AI 决策，同一引擎入口）
        Map<String, BattleAction> actionMap = validateAndCollectPlayerActions(ctx, playerActions);
        for (BattleAction action : enemyDecisionProvider.decide(ctx, ctx.getEnemySide())) {
            actionMap.put(action.getPetId(), action);
        }

        // 2. 行动顺序：速度从高到低，每回合重新计算；速度相同随机决定先后
        List<BattleUnit> order = computeActionOrder(ctx);
        ctx.emit(BattleEvent.of(BattleEventType.TURN_STARTED, round));
        BattleEvent orderEvent = BattleEvent.of(BattleEventType.ACTION_ORDER, round);
        orderEvent.put("order", order.stream().map(BattleUnit::getUnitId).toList());
        ctx.emit(orderEvent);

        // 回合开始：清空 oncePerTurn 标记 + 行动标记，触发 TURN_START 被动（REV-009）
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getUnits()) {
                unit.getPassiveTurnMarks().clear();
                unit.getPassiveActionMarks().clear();
            }
        }
        for (BattleUnit unit : order) {
            passiveManager.trigger(ctx, "TURN_START", unit);
        }

        // 3. 依次执行
        for (BattleUnit unit : order) {
            if (ctx.isFinished()) {
                break;
            }
            if (!unit.isAlive() || !unit.isActive()) {
                continue; // 本回合内已倒下或已换下
            }
            ctx.emit(BattleEvent.of(BattleEventType.ACTION_STARTED, round).source(unit.getUnitId()));
            // 每次行动清空 oncePerAction 标记（REV-009）
            unit.getPassiveActionMarks().clear();
            passiveManager.trigger(ctx, "BEFORE_ACTION", unit);

            // 蓄力释放优先于新行动
            if (unit.getChargingSkillId() != null) {
                executeCharging(ctx, unit);
                processAfterAction(ctx);
                continue;
            }

            // 控制状态：概率跳过行动；震慑等 consumeOnSkip 状态跳过时立即消耗（REV-008，需求 §142）
            StatusModifiers mod = StatusModifiers.of(unit, registry.getStatusIndex());
            if (ctx.getRandom().chance(mod.getSkipActionChance())) {
                ctx.emit(BattleEvent.of(BattleEventType.ACTION_SKIPPED, round)
                        .source(unit.getUnitId()).put("reason", "CONTROL"));
                for (StatusInstance status : new ArrayList<>(unit.getStatuses())) {
                    StatusEffectConfig statusConfig = registry.getStatus(status.getStatusId());
                    if (statusConfig != null && statusConfig.isConsumeOnSkip()) {
                        unit.getStatuses().remove(status);
                        ctx.emit(BattleEvent.of(BattleEventType.STATUS_EXPIRED, round)
                                .target(unit.getUnitId()).status(status.getStatusId())
                                .put("consumed", true));
                    }
                }
                processAfterAction(ctx);
                continue;
            }

            // 防御姿态持续到本次行动开始时解除
            unit.setDefending(false);

            BattleAction action = actionMap.getOrDefault(unit.getUnitId(),
                    BattleAction.defend(unit.getUnitId()));
            executeAction(ctx, unit, action);
            processAfterAction(ctx);
        }

        // 4. 回合结束结算
        if (!ctx.isFinished()) {
            endRound(ctx);
        }

        List<BattleEvent> turnEvents = new ArrayList<>(ctx.getEvents().subList(eventStart, ctx.getEvents().size()));
        return new TurnResult(round, turnEvents, ctx.isFinished(), ctx.getWinner());
    }

    // ---- 行动顺序 ----

    /**
     * 计算行动顺序：存活上场单位按有效速度降序，同速随机（每回合重算）。
     */
    private List<BattleUnit> computeActionOrder(BattleContext ctx) {
        List<BattleUnit> units = new ArrayList<>();
        units.addAll(ctx.getPlayerSide().getActiveAliveUnits());
        units.addAll(ctx.getEnemySide().getActiveAliveUnits());

        Map<String, Double> effectiveSpeed = new HashMap<>();
        Map<String, Double> tieBreaker = new HashMap<>();
        for (BattleUnit unit : units) {
            StatusModifiers mod = StatusModifiers.of(unit, registry.getStatusIndex());
            // 行动顺序干预加成仅作用当前回合（REV-007：不修改基础速度）
            effectiveSpeed.put(unit.getUnitId(),
                    unit.getSpeed() * mod.getSpeedMultiplier() + unit.getActionOrderBoost());
            tieBreaker.put(unit.getUnitId(), ctx.getRandom().nextDouble(0, 1));
        }
        units.sort((a, b) -> {
            int cmp = Double.compare(effectiveSpeed.get(b.getUnitId()), effectiveSpeed.get(a.getUnitId()));
            if (cmp != 0) {
                return cmp;
            }
            return Double.compare(tieBreaker.get(b.getUnitId()), tieBreaker.get(a.getUnitId()));
        });
        return units;
    }

    // ---- 玩家行动校验 ----

    private Map<String, BattleAction> validateAndCollectPlayerActions(BattleContext ctx, List<BattleAction> playerActions) {
        Map<String, BattleAction> actionMap = new HashMap<>();
        if (playerActions == null) {
            return actionMap;
        }
        for (BattleAction action : playerActions) {
            BattleUnit unit = ctx.getPlayerSide().findUnit(action.getPetId());
            if (unit == null || !unit.isAlive() || !unit.isActive()) {
                throw new BusinessException("INVALID_ACTION", "行动单位不存在或无法行动: " + action.getPetId());
            }
            if ("SKILL".equalsIgnoreCase(action.getType())) {
                validateSkillAction(ctx, unit, action);
            } else if ("SWITCH".equalsIgnoreCase(action.getType())) {
                BattleUnit bench = ctx.getPlayerSide().findUnit(action.getSwitchPetId());
                if (bench == null || !bench.isAlive() || bench.isActive()) {
                    throw new BusinessException("INVALID_SWITCH", "换宠目标不是存活候补单位: " + action.getSwitchPetId());
                }
            } else if ("CAPTURE".equalsIgnoreCase(action.getType())) {
                if (ctx.isUncapturable()) {
                    throw new BusinessException("INVALID_ACTION", "当前战斗不允许捕捉（Boss 不可捕捉）");
                }
                validateCaptureAction(ctx, action);
            } else if ("FLEE".equalsIgnoreCase(action.getType())) {
                requireWildBattle(ctx);
            } else if (!"DEFEND".equalsIgnoreCase(action.getType())) {
                throw new BusinessException("INVALID_ACTION", "未知行动类型: " + action.getType());
            }
            actionMap.put(unit.getUnitId(), action);
        }
        return actionMap;
    }

    private void validateSkillAction(BattleContext ctx, BattleUnit unit, BattleAction action) {
        SkillConfig skill = registry.getSkill(action.getSkillId());
        if (skill == null) {
            throw new BusinessException("INVALID_SKILL", "技能不存在: " + action.getSkillId());
        }
        if (!unit.getSkillIds().contains(action.getSkillId())) {
            throw new BusinessException("INVALID_SKILL", "单位未持有技能: " + action.getSkillId());
        }
        if (unit.getCooldowns().getOrDefault(action.getSkillId(), 0) > 0) {
            throw new BusinessException("SKILL_ON_COOLDOWN", "技能冷却中: " + action.getSkillId());
        }
        BattleSide enemyOfPlayer = ctx.getEnemySide();
        switch (skill.getTarget()) {
            case "ENEMY_SINGLE" -> {
                BattleUnit target = enemyOfPlayer.findUnit(action.getTargetId());
                if (target == null || !target.isAlive() || !target.isActive()) {
                    throw new BusinessException("INVALID_TARGET", "技能目标不是存活上场敌方单位: " + action.getTargetId());
                }
            }
            case "ALLY_SINGLE" -> {
                BattleUnit target = ctx.getPlayerSide().findUnit(action.getTargetId());
                if (target == null || !target.isAlive() || !target.isActive()) {
                    throw new BusinessException("INVALID_TARGET", "技能目标不是存活上场己方单位: " + action.getTargetId());
                }
            }
            default -> {
                // 群体/自身技能不需要目标
            }
        }
    }

    /** 捕捉行动校验（阶段 5）：仅野生战斗可用，目标须为存活上场野生单位，捕捉球库存充足。 */
    private void validateCaptureAction(BattleContext ctx, BattleAction action) {
        requireWildBattle(ctx);
        BattleUnit target = ctx.getEnemySide().findUnit(action.getTargetId());
        if (target == null || !target.isAlive() || !target.isActive() || target.isCaptured()) {
            throw new BusinessException("INVALID_TARGET", "捕捉目标不是存活上场野生单位: " + action.getTargetId());
        }
        ItemConfig ball = requireCaptureBall(ctx, action.getItemId());
        int used = ctx.getConsumedCaptureBalls().getOrDefault(ball.getId(), 0);
        int available = ctx.getAvailableCaptureBalls().getOrDefault(ball.getId(), 0);
        if (used >= available) {
            throw new BusinessException("CAPTURE_BALL_EXHAUSTED", "捕捉球已用完: " + ball.getId());
        }
    }

    /** 仅野生遭遇允许捕捉/逃跑（测试战斗不开启阶段 5 行动）。 */
    private void requireWildBattle(BattleContext ctx) {
        if (!"WILD".equals(ctx.getBattleType())) {
            throw new BusinessException("INVALID_ACTION", "当前战斗类型不支持该行动: " + ctx.getBattleType());
        }
    }

    /** 校验捕捉球道具存在且类型为 CAPTURE_BALL。 */
    private ItemConfig requireCaptureBall(BattleContext ctx, String itemId) {
        ItemConfig ball = registry.getItem(itemId);
        if (ball == null || !"CAPTURE_BALL".equals(ball.getItemType())) {
            throw new BusinessException("INVALID_ITEM", "捕捉球道具不存在: " + itemId);
        }
        return ball;
    }

    // ---- 行动执行 ----

    private void executeAction(BattleContext ctx, BattleUnit unit, BattleAction action) {
        String type = action.getType() != null ? action.getType().toUpperCase() : "DEFEND";
        switch (type) {
            case "SKILL" -> {
                SkillConfig skill = registry.getSkill(action.getSkillId());
                // 沉默：无法使用技能，强制防御
                StatusModifiers mod = StatusModifiers.of(unit, registry.getStatusIndex());
                if (mod.isSilenced()) {
                    unit.setDefending(true);
                    ctx.emit(BattleEvent.of(BattleEventType.DEFEND, ctx.getCurrentRound())
                            .source(unit.getUnitId()).put("reason", "SILENCE"));
                    return;
                }
                // 蓄力技能：本回合进入蓄力
                if (skill.getChargeTurns() > 0) {
                    unit.setChargingSkillId(skill.getId());
                    unit.setChargingTargetId(action.getTargetId());
                    unit.setChargeRemaining(skill.getChargeTurns());
                    ctx.emit(BattleEvent.of(BattleEventType.CHARGING, ctx.getCurrentRound())
                            .source(unit.getUnitId()).skill(skill.getId())
                            .put("chargeTurns", skill.getChargeTurns()));
                    return;
                }
                executeSkill(ctx, unit, skill, action.getTargetId());
            }
            case "DEFEND" -> {
                unit.setDefending(true);
                ctx.emit(BattleEvent.of(BattleEventType.DEFEND, ctx.getCurrentRound()).source(unit.getUnitId()));
            }
            case "SWITCH" -> executeSwitch(ctx, unit, action.getSwitchPetId());
            case "CAPTURE" -> executeCapture(ctx, unit, action);
            case "FLEE" -> executeFlee(ctx, unit);
            default -> log.warn("未知行动类型: {}", type);
        }
    }

    /**
     * 捕捉执行（阶段 5，需求 §46/§49）：
     * <ul>
     *   <li>捕捉球无论成败均消耗（记入战斗上下文，结算时统一扣除背包）。</li>
     *   <li>捕捉率 = 基础捕获率 × HP 系数 × 异常加成 × 球倍率 × 精英系数。</li>
     *   <li>成功：目标退出敌方队伍（不触发倒下/击败被动），进入 capturedUnits；若有候补则补位。</li>
     * </ul>
     */
    private void executeCapture(BattleContext ctx, BattleUnit caster, BattleAction action) {
        BattleUnit target = ctx.getEnemySide().findUnit(action.getTargetId());
        if (target == null || !target.isAlive() || !target.isActive() || target.isCaptured()) {
            throw new BusinessException("INVALID_TARGET", "捕捉目标不是存活上场野生单位: " + action.getTargetId());
        }
        ItemConfig ball = requireCaptureBall(ctx, action.getItemId());
        int used = ctx.getConsumedCaptureBalls().getOrDefault(ball.getId(), 0);
        int available = ctx.getAvailableCaptureBalls().getOrDefault(ball.getId(), 0);
        if (used >= available) {
            throw new BusinessException("CAPTURE_BALL_EXHAUSTED", "捕捉球已用完: " + ball.getId());
        }
        // 捕捉球消耗（无论成败）
        ctx.getConsumedCaptureBalls().merge(ball.getId(), 1, Integer::sum);

        PetSpeciesConfig species = registry.getSpecies(target.getSpeciesId());
        if (species == null) {
            throw new BusinessException("SPECIES_CONFIG_MISSING", "野生单位种族配置缺失: " + target.getSpeciesId());
        }
        double hpRatio = target.getMaxHp() > 0
                ? (double) target.getCurrentHp() / target.getMaxHp() : 0.0;
        int statusCount = CaptureCalculator.countCaptureBonusStatuses(target, registry.getStatusIndex());
        // 精英个体捕捉倍率（决策一，本阶段无精英个体，固定 1.0）
        double eliteMultiplier = 1.0;
        double rate = CaptureCalculator.computeCaptureRate(species.getCaptureRate(), hpRatio,
                statusCount, ball.getValue(), eliteMultiplier, registry.getSystemRules());

        ctx.emit(BattleEvent.of(BattleEventType.CAPTURE_ATTEMPT, ctx.getCurrentRound())
                .source(caster.getUnitId()).target(target.getUnitId())
                .skill(ball.getId()).put("rate", rate));

        if (ctx.getRandom().chance(rate)) {
            // 成功：立即退出敌方队伍（需求 §49），不触发倒下/击败被动
            int position = target.getPosition();
            target.setCaptured(true);
            target.setActive(false);
            target.setPosition(-1);
            ctx.getCapturedUnits().add(target);
            ctx.emit(BattleEvent.of(BattleEventType.CAPTURE_SUCCESS, ctx.getCurrentRound())
                    .source(caster.getUnitId()).target(target.getUnitId()));

            // 候补补位（同倒下处理，不消耗行动）
            BattleUnit replacement = ctx.getEnemySide().getBenchAliveUnits().stream()
                    .findFirst().orElse(null);
            if (replacement != null) {
                replacement.setActive(true);
                replacement.setPosition(position);
                ctx.emit(BattleEvent.of(BattleEventType.PET_REPLACED, ctx.getCurrentRound())
                        .target(replacement.getUnitId()).put("position", position)
                        .put("capturedId", target.getUnitId()));
                passiveManager.trigger(ctx, "ON_ENTER", replacement);
            }
        } else {
            ctx.emit(BattleEvent.of(BattleEventType.CAPTURE_FAIL, ctx.getCurrentRound())
                    .source(caster.getUnitId()).target(target.getUnitId()));
        }
    }

    /**
     * 逃跑执行（阶段 5，用户裁决：必定成功，同战败结算）。
     * 成功：战斗立即结束、无胜方、fled=true；失败：消耗本次行动。
     */
    private void executeFlee(BattleContext ctx, BattleUnit unit) {
        double rate = registry.getSystemRules().getFleeSuccessRate();
        if (ctx.getRandom().chance(rate)) {
            ctx.setFled(true);
            ctx.setFinished(true);
            ctx.emit(BattleEvent.of(BattleEventType.FLEE_SUCCESS, ctx.getCurrentRound())
                    .source(unit.getUnitId()));
            ctx.emit(BattleEvent.of(BattleEventType.BATTLE_ENDED, ctx.getCurrentRound())
                    .put("winner", "FLEE"));
        } else {
            ctx.emit(BattleEvent.of(BattleEventType.FLEE_FAIL, ctx.getCurrentRound())
                    .source(unit.getUnitId()));
        }
    }

    /** 蓄力推进：剩余回合数归零时释放蓄力技能。 */
    private void executeCharging(BattleContext ctx, BattleUnit unit) {
        unit.setChargeRemaining(unit.getChargeRemaining() - 1);
        if (unit.getChargeRemaining() > 0) {
            ctx.emit(BattleEvent.of(BattleEventType.CHARGING, ctx.getCurrentRound())
                    .source(unit.getUnitId()).skill(unit.getChargingSkillId())
                    .put("remaining", unit.getChargeRemaining()));
            return;
        }
        SkillConfig skill = registry.getSkill(unit.getChargingSkillId());
        String targetId = unit.getChargingTargetId();
        unit.setChargingSkillId(null);
        unit.setChargingTargetId(null);
        if (skill == null) {
            return;
        }
        // 蓄力期间目标可能已倒下：单体技能自动改选存活目标
        executeSkill(ctx, unit, skill, targetId);
    }

    /** 主动换宠：消耗该宠物当回合行动，新宠物立即进场。 */
    private void executeSwitch(BattleContext ctx, BattleUnit outgoing, String incomingId) {
        BattleSide side = ctx.findSideOf(outgoing.getUnitId());
        BattleUnit incoming = side.findUnit(incomingId);
        if (incoming == null || !incoming.isAlive() || incoming.isActive()) {
            throw new BusinessException("INVALID_SWITCH", "换宠目标不是存活候补单位: " + incomingId);
        }
        int position = outgoing.getPosition();
        ctx.emit(BattleEvent.of(BattleEventType.PET_SWITCHED, ctx.getCurrentRound())
                .source(outgoing.getUnitId()).put("inId", incoming.getUnitId()).put("position", position));

        outgoing.setActive(false);
        outgoing.setPosition(-1);
        passiveManager.trigger(ctx, "ON_EXIT", outgoing);

        incoming.setActive(true);
        incoming.setPosition(position);
        passiveManager.trigger(ctx, "ON_ENTER", incoming);
    }

    // ---- 技能结算 ----

    private void executeSkill(BattleContext ctx, BattleUnit caster, SkillConfig skill, String requestedTargetId) {
        BattleSide casterSide = ctx.findSideOf(caster.getUnitId());
        BattleSide enemySide = ctx.getOpposite(casterSide);

        // 命中判定：仅对敌技能生效；己方/自身技能必定生效
        boolean targetsEnemy = skill.getTarget().startsWith("ENEMY");
        StatusModifiers casterMod = StatusModifiers.of(caster, registry.getStatusIndex());
        if (targetsEnemy && !AccuracyCalculator.roll(ctx.getRandom(), skill, casterMod)) {
            ctx.emit(BattleEvent.of(BattleEventType.SKILL_CAST, ctx.getCurrentRound())
                    .source(caster.getUnitId()).skill(skill.getId()));
            ctx.emit(BattleEvent.of(BattleEventType.MISS, ctx.getCurrentRound())
                    .source(caster.getUnitId()).skill(skill.getId()));
            caster.getCooldowns().put(skill.getId(), skill.getCooldown());
            return;
        }

        ctx.emit(BattleEvent.of(BattleEventType.SKILL_CAST, ctx.getCurrentRound())
                .source(caster.getUnitId()).skill(skill.getId()));
        caster.getCooldowns().put(skill.getId(), skill.getCooldown());

        List<BattleUnit> targets = resolveTargets(ctx, caster, skill, casterSide, enemySide, requestedTargetId);
        double baseValue = DamageCalculator.computeBaseValue(skill, caster);
        boolean singleTarget = "ENEMY_SINGLE".equals(skill.getTarget());
        boolean leaveAtOneHp = hasEffectType(skill, "LEAVE_AT_ONE_HP");
        boolean dealtDamage = false;

        // 主效果（DAMAGE 记录实际损失与保护情况，供吸血/留生一击联动）
        Map<String, DamageOutcome> outcomes = new HashMap<>();
        for (BattleUnit target : targets) {
            if (!target.isAlive()) {
                continue;
            }
            if ("DAMAGE".equalsIgnoreCase(skill.getEffectType())) {
                outcomes.put(target.getUnitId(),
                        applyDamage(ctx, caster, target, skill, baseValue, singleTarget, leaveAtOneHp, false));
                dealtDamage = true;
            } else {
                applyEffect(ctx, caster, target, skill, skill.getEffectType(), baseValue, singleTarget, false);
            }
        }

        // 附加效果（Effect 组合框架，REV-006，技术方案 §76）
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("LEAVE_AT_ONE_HP".equalsIgnoreCase(effect.getType())) {
                continue; // 已参与主伤害结算
            }
            double effectBase = DamageCalculator.computeBaseValue(effect.getValue(), effect.getScaling(), caster);
            for (BattleUnit target : targets) {
                // onProtect：仅当留生一击保护实际触发时执行（需求 §142.3：普通命中未触发保护时不清除任何状态）
                if (effect.isOnProtect()) {
                    DamageOutcome oc = outcomes.get(target.getUnitId());
                    if (oc == null || !oc.protectedToOneHp || !target.isAlive()) {
                        continue;
                    }
                    // capturableOnly：仅可捕捉目标（Boss 不附加震慑，需求 §142.5）
                    if (effect.isCapturableOnly() && target.getWildData() == null) {
                        continue;
                    }
                } else if (!target.isAlive()) {
                    continue;
                }
                if (!ctx.getRandom().chance(computeFinalStatusChance(ctx, caster, target, effect))) {
                    continue;
                }
                applySkillEffect(ctx, caster, target, skill, effect, effectBase, singleTarget, outcomes);
                if ("DAMAGE".equalsIgnoreCase(effect.getType())) {
                    dealtDamage = true;
                }
            }
        }

        // 隐匿：主动攻击后解除（需求 §144.5）
        if (dealtDamage) {
            removeStealthOnAttack(ctx, caster);
        }
        passiveManager.trigger(ctx, "AFTER_SKILL", caster);
    }

    /**
     * 附加效果分发（REV-006）。
     */
    private void applySkillEffect(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                  SkillConfig.SkillEffectConfig effect, double effectBase,
                                  boolean singleTarget, Map<String, DamageOutcome> outcomes) {
        String type = effect.getType() != null ? effect.getType().toUpperCase() : "";
        switch (type) {
            case "APPLY_STATUS", "STACK" -> applyStatus(ctx, caster, target, effect.getStatusId());
            case "DAMAGE" -> applyDamage(ctx, caster, target, skill, effectBase, singleTarget, false, false);
            case "HEAL" -> healUnit(ctx, caster, target, skill, (int) Math.round(effectBase));
            case "SHIELD" -> {
                int shield = (int) Math.round(effectBase);
                target.setShield(target.getShield() + shield);
                ctx.emit(BattleEvent.of(BattleEventType.SHIELD_CREATED, ctx.getCurrentRound())
                        .source(caster.getUnitId()).target(target.getUnitId()).value(shield)
                        .skill(skill.getId()));
            }
            case "LIFE_STEAL" -> applyLifeSteal(ctx, caster, target, skill, effect, outcomes);
            case "REMOVE_STATUS" -> removeStatuses(ctx, caster, target, effect);
            case "DISPEL" -> dispelBuffs(ctx, caster, target, false);
            case "STEAL_BUFF" -> stealBuff(ctx, caster, target);
            case "HP_PERCENT_EXCHANGE" -> exchangeHpPercent(ctx, caster, target);
            case "SWITCH_PET" -> switchPetBySkill(ctx, caster);
            case "CHANGE_ACTION_ORDER" -> {
                // 行动顺序干预：仅作用当前回合，不修改基础速度（REV-007）
                target.setActionOrderBoost(target.getActionOrderBoost() + effect.getValue());
                ctx.emit(BattleEvent.of(BattleEventType.ACTION_ORDER_CHANGED, ctx.getCurrentRound())
                        .source(caster.getUnitId()).target(target.getUnitId())
                        .put("boost", effect.getValue()));
            }
            case "MODIFY_COOLDOWN" -> {
                int delta = (int) Math.round(effect.getValue());
                target.getCooldowns().replaceAll((k, v) -> Math.max(0, v + delta));
            }
            case "DELAYED" -> registerDelayed(ctx, caster, target, skill, effect, effectBase);
            case "LIFE_COST" -> {
                // 生命代价：按最大 HP 比例扣除，不致死（最低保留 1HP）
                int cost = (int) Math.round(caster.getMaxHp() * effect.getPercent());
                if (cost > 0) {
                    caster.setCurrentHp(Math.max(1, caster.getCurrentHp() - cost));
                    ctx.emit(BattleEvent.of(BattleEventType.DAMAGE, ctx.getCurrentRound())
                            .source(caster.getUnitId()).target(caster.getUnitId())
                            .value(cost).critical(false).skill(skill.getId())
                            .put("lifeCost", true));
                }
            }
            case "PROTECT_FROM_DEFEAT" -> target.setProtectCharges(target.getProtectCharges() + 1);
            default -> log.warn("未实现的技能效果类型: {}（技能 {}）", type, skill.getId());
        }
    }

    /**
     * 吸血（REV-007，需求 §143）：按实际 HP 损失计算（护盾吸收/过量伤害不计）；
     * DOT/反击/反射不走技能效果路径故默认不吸血；受禁疗影响。
     */
    private void applyLifeSteal(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                SkillConfig.SkillEffectConfig effect, Map<String, DamageOutcome> outcomes) {
        DamageOutcome oc = outcomes.get(target.getUnitId());
        int actualLoss = oc != null ? oc.actualHpLoss : 0;
        int heal = (int) Math.round(actualLoss * effect.getPercent());
        if (heal <= 0 || isHealBlocked(caster)) {
            return;
        }
        int healed = Math.min(heal, caster.getMaxHp() - caster.getCurrentHp());
        caster.setCurrentHp(caster.getCurrentHp() + healed);
        ctx.emit(BattleEvent.of(BattleEventType.LIFE_STEAL, ctx.getCurrentRound())
                .source(caster.getUnitId()).target(target.getUnitId()).value(healed)
                .skill(skill.getId()));
    }

    /** 移除状态（REV-006 REMOVE_STATUS）：dotOnly 仅移除持续伤害；categories 按类别过滤。 */
    private void removeStatuses(BattleContext ctx, BattleUnit caster, BattleUnit target,
                                SkillConfig.SkillEffectConfig effect) {
        List<StatusInstance> toRemove = new ArrayList<>();
        for (StatusInstance status : target.getStatuses()) {
            StatusEffectConfig config = registry.getStatus(status.getStatusId());
            if (config == null) {
                continue;
            }
            if (effect.isDotOnly() && config.getDotPercent() > 0) {
                toRemove.add(status);
            } else if (effect.getCategories() != null && !effect.getCategories().isEmpty()
                    && effect.getCategories().stream()
                            .anyMatch(c -> c.equalsIgnoreCase(config.getCategory()))) {
                toRemove.add(status);
            }
        }
        for (StatusInstance status : toRemove) {
            target.getStatuses().remove(status);
            ctx.emit(BattleEvent.of(BattleEventType.STATUS_REMOVED, ctx.getCurrentRound())
                    .source(caster.getUnitId()).target(target.getUnitId())
                    .status(status.getStatusId()).put("removed", true));
        }
    }

    /** 驱散（REV-006 DISPEL）：移除目标至多 max(1,value) 个可驱散 BUFF。 */
    private void dispelBuffs(BattleContext ctx, BattleUnit caster, BattleUnit target, boolean stolen) {
        int count = 1;
        for (StatusInstance status : new ArrayList<>(target.getStatuses())) {
            if (count <= 0) {
                break;
            }
            StatusEffectConfig config = registry.getStatus(status.getStatusId());
            if (config != null && "BUFF".equals(config.getCategory()) && config.isDispellable()) {
                target.getStatuses().remove(status);
                count--;
                if (!stolen) {
                    ctx.emit(BattleEvent.of(BattleEventType.STATUS_REMOVED, ctx.getCurrentRound())
                            .source(caster.getUnitId()).target(target.getUnitId())
                            .status(status.getStatusId()).put("dispelled", true));
                }
            }
        }
    }

    /** 偷取 Buff（REV-006 STEAL_BUFF）：随机移除目标 1 个可偷取 Buff 并复制给自己。 */
    private void stealBuff(BattleContext ctx, BattleUnit caster, BattleUnit target) {
        List<StatusInstance> candidates = target.getStatuses().stream()
                .filter(s -> {
                    StatusEffectConfig c = registry.getStatus(s.getStatusId());
                    return c != null && "BUFF".equals(c.getCategory()) && c.isDispellable();
                }).toList();
        if (candidates.isEmpty()) {
            return;
        }
        StatusInstance stolenStatus = candidates.get(ctx.getRandom().nextInt(0, candidates.size() - 1));
        target.getStatuses().remove(stolenStatus);
        ctx.emit(BattleEvent.of(BattleEventType.BUFF_STOLEN, ctx.getCurrentRound())
                .source(caster.getUnitId()).target(target.getUnitId())
                .status(stolenStatus.getStatusId()));
        applyStatus(ctx, caster, caster, stolenStatus.getStatusId());
    }

    /**
     * HP 百分比交换（REV-007，需求 §147 命运天平）：非伤害，不触发暴击/吸血/反击/受击被动，
     * 不受防御/护盾影响，不清除 DOT/不附加震慑；Boss（不可捕捉单位）受交换幅度上限约束。
     */
    private void exchangeHpPercent(BattleContext ctx, BattleUnit caster, BattleUnit target) {
        if (!target.isAlive() || target.getMaxHp() <= 0 || caster.getMaxHp() <= 0) {
            return;
        }
        double casterPct = (double) caster.getCurrentHp() / caster.getMaxHp();
        double targetPct = (double) target.getCurrentHp() / target.getMaxHp();
        double newCasterPct = targetPct;
        double newTargetPct = casterPct;
        // Boss（不可捕捉单位）交换幅度上限（配置化，默认 0.20）
        if (target.getWildData() == null) {
            double limit = registry.getSystemRules().getBossHpExchangeLimit();
            double delta = targetPct - casterPct;
            if (Math.abs(delta) > limit) {
                double clamped = Math.signum(delta) * limit;
                newCasterPct = casterPct + clamped;
                newTargetPct = targetPct - clamped;
            }
        }
        caster.setCurrentHp(Math.max(1, (int) Math.round(newCasterPct * caster.getMaxHp())));
        target.setCurrentHp(Math.max(1, (int) Math.round(newTargetPct * target.getMaxHp())));
        ctx.emit(BattleEvent.of(BattleEventType.HP_PERCENT_EXCHANGED, ctx.getCurrentRound())
                .source(caster.getUnitId()).target(target.getUnitId())
                .put("casterBefore", Math.round(casterPct * 100))
                .put("targetBefore", Math.round(targetPct * 100)));
    }

    /** 换宠技能（REV-006 SWITCH_PET，需求 §146）：技能本身即本回合行动，后续换宠不额外消耗行动。 */
    private void switchPetBySkill(BattleContext ctx, BattleUnit caster) {
        BattleSide side = ctx.findSideOf(caster.getUnitId());
        BattleUnit incoming = side.getBenchAliveUnits().stream().findFirst().orElse(null);
        if (incoming == null) {
            return;
        }
        int position = caster.getPosition();
        ctx.emit(BattleEvent.of(BattleEventType.PET_FORCED_SWITCH, ctx.getCurrentRound())
                .source(caster.getUnitId()).put("inId", incoming.getUnitId()).put("position", position));
        caster.setActive(false);
        caster.setPosition(-1);
        passiveManager.trigger(ctx, "ON_EXIT", caster);
        incoming.setActive(true);
        incoming.setPosition(position);
        passiveManager.trigger(ctx, "ON_ENTER", incoming);
    }

    /** 隐匿解除（主动攻击后，需求 §144.5）。 */
    private void removeStealthOnAttack(BattleContext ctx, BattleUnit caster) {
        for (StatusInstance status : new ArrayList<>(caster.getStatuses())) {
            StatusEffectConfig config = registry.getStatus(status.getStatusId());
            if (config != null && config.isStealth()) {
                caster.getStatuses().remove(status);
                ctx.emit(BattleEvent.of(BattleEventType.STATUS_REMOVED, ctx.getCurrentRound())
                        .source(caster.getUnitId()).target(caster.getUnitId())
                        .status(status.getStatusId()).put("stealthBroken", true));
            }
        }
    }

    /** 延迟效果注册（REV-006 DELAYED）。 */
    private void registerDelayed(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                 SkillConfig.SkillEffectConfig effect, double effectBase) {
        BattleContext.DelayedEffect delayed = new BattleContext.DelayedEffect();
        delayed.setTriggerRound(ctx.getCurrentRound() + Math.max(1, effect.getDelayRounds()));
        delayed.setCasterId(caster.getUnitId());
        delayed.setTargetId(target.getUnitId());
        delayed.setEffect(effect);
        delayed.setBaseValue(effectBase);
        delayed.setSkillId(skill.getId());
        ctx.getDelayedEffects().add(delayed);
    }

    /** 目标解析：群体=全部存活上场；单体=嘲讽重定向 + 混乱改向 + 隐匿排除 + 合法性兜底。 */
    private List<BattleUnit> resolveTargets(BattleContext ctx, BattleUnit caster, SkillConfig skill,
                                            BattleSide casterSide, BattleSide enemySide, String requestedTargetId) {
        return switch (skill.getTarget()) {
            case "ENEMY_ALL" -> enemySide.getActiveAliveUnits();
            case "ALLY_ALL" -> casterSide.getActiveAliveUnits();
            case "SELF" -> List.of(caster);
            case "ALLY_SINGLE" -> {
                BattleUnit target = casterSide.findUnit(requestedTargetId);
                if (target == null || !target.isAlive() || !target.isActive()) {
                    yield List.of();
                }
                // 混乱（REV-008，需求 §144.1）：单体治疗/Buff 也可能错误作用于其他合法单位
                BattleUnit confused = maybeConfuseSingleTarget(ctx, caster, target);
                yield confused == null ? List.of() : List.of(confused);
            }
            default -> { // ENEMY_SINGLE
                // 嘲讽只影响单体技能目标
                BattleUnit taunter = enemySide.getActiveAliveUnits().stream()
                        .filter(u -> StatusModifiers.of(u, registry.getStatusIndex()).isTaunt())
                        .findFirst().orElse(null);
                if (taunter != null) {
                    yield List.of(taunter);
                }
                BattleUnit target = enemySide.findUnit(requestedTargetId);
                // 隐匿（REV-008，需求 §144.5）：单体不可选中隐匿单位，改选非隐匿存活目标
                if (target == null || !target.isAlive() || !target.isActive() || isStealthed(target)) {
                    target = enemySide.getActiveAliveUnits().stream()
                            .filter(u -> !isStealthed(u)).findFirst().orElse(null);
                    if (target == null) {
                        yield List.of();
                    }
                }
                BattleUnit confused = maybeConfuseSingleTarget(ctx, caster, target);
                yield confused == null ? List.of() : List.of(confused);
            }
        };
    }
    
    /**
     * 混乱（REV-008，需求 §144.1）：单体技能可能随机改向除施法者外的场上合法存活单位；
     * 群体与自身技能不受影响。未混乱或无候选时返回原目标。
     */
    private BattleUnit maybeConfuseSingleTarget(BattleContext ctx, BattleUnit caster, BattleUnit original) {
        StatusModifiers casterMod = StatusModifiers.of(caster, registry.getStatusIndex());
        if (!casterMod.isConfused() || original == null) {
            return original;
        }
        List<BattleUnit> pool = new ArrayList<>();
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getActiveAliveUnits()) {
                if (!unit.getUnitId().equals(caster.getUnitId()) && !isStealthed(unit)) {
                    pool.add(unit);
                }
            }
        }
        if (pool.isEmpty()) {
            return original;
        }
        BattleUnit chosen = pool.get(ctx.getRandom().nextInt(0, pool.size() - 1));
        if (!chosen.getUnitId().equals(original.getUnitId())) {
            ctx.emit(BattleEvent.of(BattleEventType.CONFUSED_TARGET_CHANGED, ctx.getCurrentRound())
                    .source(caster.getUnitId()).target(chosen.getUnitId())
                    .put("originalTarget", original.getUnitId()));
        }
        return chosen;
    }
    
    /** 隐匿判定（REV-008）。 */
    private boolean isStealthed(BattleUnit unit) {
        return StatusModifiers.of(unit, registry.getStatusIndex()).isStealthed();
    }

    /**
     * 效果分发（REV-006 Effect 组合框架，技术方案 §76）。
     * 返回伤害类效果的实际 HP 损失结果（供吸血/留生一击联动使用）。
     */
    private DamageOutcome applyEffect(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                      String effectType, double baseValue, boolean singleTarget,
                                      boolean leaveAtOneHp) {
        if (effectType == null) {
            return null;
        }
        switch (effectType.toUpperCase()) {
            case "DAMAGE" -> {
                return applyDamage(ctx, caster, target, skill, baseValue, singleTarget, leaveAtOneHp, false);
            }
            case "HEAL" -> {
                healUnit(ctx, caster, target, skill, (int) Math.round(baseValue * healBonus(caster, skill)));
            }
            case "SHIELD" -> {
                int shield = (int) Math.round(baseValue * healBonus(caster, skill));
                target.setShield(target.getShield() + shield);
                ctx.emit(BattleEvent.of(BattleEventType.SHIELD_CREATED, ctx.getCurrentRound())
                        .source(caster.getUnitId()).target(target.getUnitId()).value(shield)
                        .skill(skill.getId()));
            }
            case "NONE" -> {
                // 仅附加效果型技能
            }
            default -> log.warn("未知技能效果类型: {}", effectType);
        }
        return null;
    }

    /** 治疗单位（REV-007：受禁疗影响；治疗不暴击）。 */
    private void healUnit(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill, int heal) {
        if (heal <= 0 || !target.isAlive()) {
            return;
        }
        if (isHealBlocked(target)) {
            return;
        }
        int healed = Math.min(heal, target.getMaxHp() - target.getCurrentHp());
        target.setCurrentHp(target.getCurrentHp() + healed);
        ctx.emit(BattleEvent.of(BattleEventType.HEAL, ctx.getCurrentRound())
                .source(caster != null ? caster.getUnitId() : null)
                .target(target.getUnitId()).value(healed)
                .skill(skill != null ? skill.getId() : null));
        passiveManager.trigger(ctx, "AFTER_HEAL", target);
    }

    /** 禁疗判定（REV-008：HEAL_BLOCK 状态）。 */
    private boolean isHealBlocked(BattleUnit unit) {
        return unit.getStatuses().stream()
                .map(s -> registry.getStatus(s.getStatusId()))
                .anyMatch(c -> c != null && c.isHealBlock());
    }

    /** 技能是否携带指定类型附加效果。 */
    private boolean hasEffectType(SkillConfig skill, String type) {
        return skill.getEffects().stream()
                .anyMatch(e -> type.equalsIgnoreCase(e.getType()));
    }

    /**
     * 伤害结算与落实（REV-007 结算边界）。
     * <ul>
     *   <li>援护：单体伤害部分转移给援护者；</li>
     *   <li>反击：直接单体技能伤害后目标可能反击（群攻/DOT/反击不触发，需求 §144.2）；</li>
     *   <li>返回实际 HP 损失（护盾吸收/过量不计，吸血用，需求 §143）。</li>
     * </ul>
     */
    private DamageOutcome applyDamage(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                      double baseValue, boolean singleTarget, boolean leaveAtOneHp,
                                      boolean fromCounter) {
        DamageCalculator.DamageResult result = damageCalculator.calculate(
                caster, target, skill, baseValue, ctx.getRandom(), false);

        // 援护：单体伤害部分转移给援护者
        int damage = result.getFinalDamage();
        if (singleTarget) {
            BattleUnit guard = findGuardOf(ctx, target);
            if (guard != null) {
                StatusModifiers guardMod = StatusModifiers.of(guard, registry.getStatusIndex());
                int transferred = (int) Math.round(damage * guardMod.getGuardTransferPercent());
                if (transferred > 0) {
                    damage -= transferred;
                    dealDamageToUnit(ctx, caster, guard, transferred, false, true);
                }
            }
        }

        ctx.emit(BattleEvent.of(BattleEventType.DAMAGE, ctx.getCurrentRound())
                .source(caster.getUnitId()).target(target.getUnitId()).skill(skill.getId())
                .value(result.getFinalDamage()).critical(result.isCritical())
                .elementRelation(result.getElementRelation())
                .put("elementMultiplier", result.getElementMultiplier())
                .put("sameElementMultiplier", result.getSameElementMultiplier())
                .put("synergyMultiplier", result.getSynergyMultiplier())
                .put("critMultiplier", result.getCritMultiplier())
                .put("rawBase", result.getRawBase()));
        if (result.isCritical()) {
            ctx.emit(BattleEvent.of(BattleEventType.CRITICAL, ctx.getCurrentRound())
                    .source(caster.getUnitId()).target(target.getUnitId()).skill(skill.getId()));
            passiveManager.trigger(ctx, "ON_CRITICAL", caster);
        }

        passiveManager.trigger(ctx, "BEFORE_DAMAGE", target);
        DamageOutcome outcome = dealDamageToUnit(ctx, caster, target, damage, leaveAtOneHp, fromCounter);
        passiveManager.trigger(ctx, "AFTER_DAMAGE", caster);
        if (target.isAlive()) {
            passiveManager.trigger(ctx, "AFTER_TAKE_DAMAGE", target);
        }

        // 反击（REV-008，需求 §144.2）：直接单体技能伤害后触发；群攻/DOT 不走此路径；反击不触发反击
        if (!fromCounter && singleTarget && target.isAlive()) {
            maybeCounter(ctx, target, caster);
        }
        return outcome;
    }

    /** 反击判定与执行（REV-008）：反击伤害直接落实，不触发反击/吸血/受击被动链。 */
    private void maybeCounter(BattleContext ctx, BattleUnit target, BattleUnit attacker) {
        StatusModifiers mod = StatusModifiers.of(target, registry.getStatusIndex());
        if (mod.getCounterRate() <= 0 || !ctx.getRandom().chance(mod.getCounterRate())) {
            return;
        }
        int counterDamage = Math.max(1, (int) Math.round(
                mod.getCounterValue() + mod.getCounterScaling() * target.getStrength()));
        ctx.emit(BattleEvent.of(BattleEventType.COUNTER_TRIGGERED, ctx.getCurrentRound())
                .source(target.getUnitId()).target(attacker.getUnitId()).value(counterDamage));
        dealDamageToUnit(ctx, target, attacker, counterDamage, false, true);
    }

    /**
     * 伤害落实到单位：护盾先吸收（不再次套用防御计算）→ 扣 HP →
     * 留生一击保护（REV-007：暴击不可绕过，仅当前 HP>1 时触发）→ 濒死保护/不屈被动 → 倒下判定。
     *
     * @return 实际 HP 损失与保护触发情况（护盾吸收不计入实际损失）
     */
    private DamageOutcome dealDamageToUnit(BattleContext ctx, BattleUnit source, BattleUnit target,
                                           int damage, boolean leaveAtOneHp, boolean fromCounter) {
        DamageOutcome outcome = new DamageOutcome();
        if (damage <= 0 || !target.isAlive()) {
            return outcome;
        }
        target.setLastDamageSourceId(source != null ? source.getUnitId() : null);

        int remaining = damage;
        if (target.getShield() > 0) {
            int absorbed = Math.min(target.getShield(), remaining);
            target.setShield(target.getShield() - absorbed);
            remaining -= absorbed;
            if (target.getShield() <= 0 && absorbed > 0) {
                ctx.emit(BattleEvent.of(BattleEventType.SHIELD_BROKEN, ctx.getCurrentRound())
                        .source(source != null ? source.getUnitId() : null)
                        .target(target.getUnitId()));
            }
        }

        int hpBefore = target.getCurrentHp();
        if (remaining > 0) {
            // 留生一击（REV-007，需求 §142）：致死伤害保留 1HP，对一切目标生效（含 Boss，§142.5）；
            // 暴击不可绕过（此处为最终落实阶段）；目标已 1HP 时不再触发（不无限刷新震慑）；
            // 震慑是否附加由 onProtect + capturableOnly 效果层决定
            boolean wouldKill = remaining >= hpBefore;
            if (leaveAtOneHp && wouldKill && hpBefore > 1) {
                target.setCurrentHp(1);
                outcome.protectedToOneHp = true;
            } else if (remaining >= hpBefore) {
                // 濒死保护次数（PROTECT_FROM_DEFEAT）→ 不屈类被动 → 倒下
                if (target.getProtectCharges() > 0) {
                    target.setProtectCharges(target.getProtectCharges() - 1);
                    target.setCurrentHp(1);
                } else if (passiveManager.consumeSurviveLethal(ctx, target)) {
                    target.setCurrentHp(1);
                } else {
                    target.setCurrentHp(0);
                }
            } else {
                target.setCurrentHp(hpBefore - remaining);
            }
        }
        // 实际 HP 损失 = 真实扣除的 HP（护盾吸收/过量伤害不计，吸血用，需求 §143）
        outcome.actualHpLoss = Math.max(0, hpBefore - Math.max(0, target.getCurrentHp()));
        return outcome;
    }

    /** 伤害落实结果（REV-007）。 */
    private static class DamageOutcome {
        /** 实际 HP 损失（不含护盾吸收与过量）。 */
        int actualHpLoss;
        /** 是否触发留生一击保护（致死被保留 1HP）。 */
        boolean protectedToOneHp;
    }

    /** 治疗/护盾的本属性加成（效果 ×1.20，治疗不暴击）。 */
    private double healBonus(BattleUnit caster, SkillConfig skill) {
        String element = skill.getElement();
        if (element != null && !"NONE".equalsIgnoreCase(element)
                && element.equalsIgnoreCase(caster.getElement())) {
            return registry.getSystemRules().getSameElementBonus();
        }
        return 1.0;
    }

    /** 查找目标的援护者（存活上场、援护目标指向本单位且仍携带援护状态）。 */
    private BattleUnit findGuardOf(BattleContext ctx, BattleUnit target) {
        BattleSide side = ctx.findSideOf(target.getUnitId());
        if (side == null) {
            return null;
        }
        for (BattleUnit unit : side.getActiveAliveUnits()) {
            if (unit == target || !target.getUnitId().equals(unit.getGuardTargetId())) {
                continue;
            }
            StatusModifiers mod = StatusModifiers.of(unit, registry.getStatusIndex());
            if (mod.getGuardTransferPercent() > 0) {
                return unit;
            }
        }
        return null;
    }

    private void applyStatus(BattleContext ctx, BattleUnit source, BattleUnit target, String statusId) {
        StatusEffectConfig config = registry.getStatus(statusId);
        if (config == null) {
            log.warn("技能引用的状态不存在: {}", statusId);
            return;
        }
        StatusInstance existing = target.getStatuses().stream()
                .filter(s -> s.getStatusId().equals(statusId))
                .findFirst().orElse(null);
        if (existing != null) {
            // 再次附加只刷新到允许的最大持续时间（需求 §144.1/技术方案 §77）
            existing.setRemainingTurns(config.getDefaultDuration());
            // 叠层（REV-002，需求 §144.6）：层数 +1，达到 maxStack 且 stackTrigger=DAMAGE 时触发并清空
            if (config.isStack() && existing.getStack() < config.getMaxStack()) {
                existing.setStack(existing.getStack() + 1);
                ctx.emit(BattleEvent.of(BattleEventType.MARK_STACK_CHANGED, ctx.getCurrentRound())
                        .source(source.getUnitId()).target(target.getUnitId())
                        .status(statusId).put("stack", existing.getStack()));
                if (existing.getStack() >= config.getMaxStack()
                        && "DAMAGE".equals(config.getStackTrigger())) {
                    target.getStatuses().remove(existing);
                    int triggerDamage = Math.max(1, (int) Math.round(config.getStackTriggerValue()));
                    dealDamageToUnit(ctx, source, target, triggerDamage, false, true);
                }
            }
        } else {
            target.getStatuses().add(new StatusInstance(statusId, config.getDefaultDuration(),
                    source.getUnitId()));
        }
        // 阶段 7：SPECIAL_CONTROL 命中后递增连续控制计数
        if ("SPECIAL_CONTROL".equals(config.getCategory())) {
            target.setConsecutiveControlCount(target.getConsecutiveControlCount() + 1);
            target.setRoundsWithoutControl(0);
        }
        // 援护状态：建立援护关系
        if (config.getGuardTransferPercent() > 0) {
            source.setGuardTargetId(target.getUnitId());
        }
        // 震慑事件（REV-010，需求 §142：安全捕捉窗口）
        if (config.isCaptureStun()) {
            ctx.emit(BattleEvent.of(BattleEventType.STUNNED, ctx.getCurrentRound())
                    .source(source.getUnitId()).target(target.getUnitId()).status(statusId));
        }
        BattleEventType eventType = switch (config.getCategory()) {
            case "BUFF" -> BattleEventType.BUFF_APPLIED;
            case "DEBUFF" -> BattleEventType.DEBUFF_APPLIED;
            // CONTINUOUS / SPECIAL_CONTROL / MARK
            default -> BattleEventType.STATUS_APPLIED;
        };
        ctx.emit(BattleEvent.of(eventType, ctx.getCurrentRound())
                .source(source.getUnitId()).target(target.getUnitId())
                .status(statusId).put("duration", config.getDefaultDuration()));
        passiveManager.trigger(ctx, "ON_STATUS_APPLIED", target);
    }

    // ---- 倒下 / 补位 / 胜负 ----

    /** 行动后统一处理倒下、补位与胜负判定。 */
    private void processAfterAction(BattleContext ctx) {
        processDefeats(ctx);
        checkBattleEnd(ctx);
    }

    /** 处理全部 HP 归零单位：倒下被动、击败被动、候补补位（循环直到稳定）。 */
    private void processDefeats(BattleContext ctx) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
                for (BattleUnit unit : side.getUnits()) {
                    if (unit.isAlive() && unit.getCurrentHp() <= 0) {
                        handleDefeat(ctx, side, unit);
                        changed = true;
                    }
                }
            }
        }
    }

    private void handleDefeat(BattleContext ctx, BattleSide side, BattleUnit unit) {
        unit.setAlive(false);
        boolean wasActive = unit.isActive();
        int position = unit.getPosition();
        unit.setActive(false);
        unit.setPosition(-1);

        ctx.emit(BattleEvent.of(BattleEventType.PET_DEFEATED, ctx.getCurrentRound())
                .target(unit.getUnitId()));

        // 击败被动（击杀者）
        if (unit.getLastDamageSourceId() != null) {
            BattleUnit killer = ctx.findUnit(unit.getLastDamageSourceId());
            if (killer != null && killer.isAlive()) {
                passiveManager.trigger(ctx, "ON_KILL", killer);
            }
        }
        // 倒下被动（如余烬，REV-009 命名 ON_DEFEAT）
        passiveManager.trigger(ctx, "ON_DEFEAT", unit);
        // 友方倒下被动（REV-009 ON_ALLY_DEFEAT）
        for (BattleUnit ally : side.getActiveAliveUnits()) {
            passiveManager.trigger(ctx, "ON_ALLY_DEFEAT", ally);
        }

        // 候补补位：当前行动结算完成后进行，不消耗下一回合行动
        if (wasActive) {
            BattleUnit replacement = side.getBenchAliveUnits().stream().findFirst().orElse(null);
            if (replacement != null) {
                replacement.setActive(true);
                replacement.setPosition(position);
                ctx.emit(BattleEvent.of(BattleEventType.PET_REPLACED, ctx.getCurrentRound())
                        .target(replacement.getUnitId()).put("position", position)
                        .put("defeatedId", unit.getUnitId()));
                passiveManager.trigger(ctx, "ON_ENTER", replacement);
            }
        }
    }

    private void checkBattleEnd(BattleContext ctx) {
        if (ctx.isFinished()) {
            return;
        }
        String winner = null;
        // 野生战斗：敌方全部退出战斗（倒下或被捕捉）即玩家获胜
        if (ctx.getEnemySide().isAllGone()) {
            winner = "PLAYER";
        } else if (ctx.getPlayerSide().isAllDefeated()) {
            winner = "ENEMY";
        }
        if (winner != null) {
            ctx.setFinished(true);
            ctx.setWinner(winner);
            ctx.emit(BattleEvent.of(BattleEventType.BATTLE_ENDED, ctx.getCurrentRound())
                    .put("winner", winner));
            // 战斗结束被动（REV-009 BATTLE_END）
            for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
                for (BattleUnit unit : side.getActiveAliveUnits()) {
                    passiveManager.trigger(ctx, "BATTLE_END", unit);
                }
            }
        }
    }

    // ---- 回合结束结算 ----

    private void endRound(BattleContext ctx) {
        int round = ctx.getCurrentRound();

        // 阶段 7：Boss 阶段触发检查（在 DOT 结算前）
        checkPhaseTriggers(ctx);

        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getUnits()) {
                if (!unit.isAlive()) {
                    continue;
                }
                // DOT 结算
                for (StatusInstance status : new ArrayList<>(unit.getStatuses())) {
                    StatusEffectConfig config = registry.getStatus(status.getStatusId());
                    if (config == null || config.getDotPercent() <= 0) {
                        continue;
                    }
                    int dotDamage = Math.max(1, (int) Math.round(unit.getMaxHp() * config.getDotPercent()));
                    unit.setLastDamageSourceId(status.getSourceId());
                    unit.setCurrentHp(Math.max(0, unit.getCurrentHp() - dotDamage));
                    ctx.emit(BattleEvent.of(BattleEventType.STATUS_TICK, round)
                            .target(unit.getUnitId()).status(status.getStatusId()).value(dotDamage));
                    if (unit.getCurrentHp() <= 0) {
                        if (passiveManager.consumeSurviveLethal(ctx, unit)) {
                            unit.setCurrentHp(1);
                        } else {
                            unit.setCurrentHp(0);
                        }
                    }
                }
                // 再生（REV-008，需求 §144.3）：回合结束恢复，属于治疗、受禁疗影响
                StatusModifiers regenMod = StatusModifiers.of(unit, registry.getStatusIndex());
                if (unit.isAlive() && regenMod.getHealPercent() > 0 && !isHealBlocked(unit)) {
                    int regenHeal = (int) Math.round(unit.getMaxHp() * regenMod.getHealPercent());
                    int healed = Math.min(regenHeal, unit.getMaxHp() - unit.getCurrentHp());
                    if (healed > 0) {
                        unit.setCurrentHp(unit.getCurrentHp() + healed);
                        ctx.emit(BattleEvent.of(BattleEventType.HEAL, round)
                                .target(unit.getUnitId()).value(healed).put("regen", true));
                    }
                }
                // 持续时间递减
                for (StatusInstance status : new ArrayList<>(unit.getStatuses())) {
                    status.setRemainingTurns(status.getRemainingTurns() - 1);
                    if (status.getRemainingTurns() <= 0) {
                        unit.getStatuses().remove(status);
                        // 援护状态到期时解除本单位发起的援护关系
                        StatusEffectConfig config = registry.getStatus(status.getStatusId());
                        if (config != null && config.getGuardTransferPercent() > 0
                                && unit.getStatuses().stream().noneMatch(s -> {
                                    StatusEffectConfig cfg = registry.getStatus(s.getStatusId());
                                    return cfg != null && cfg.getGuardTransferPercent() > 0;
                                })) {
                            unit.setGuardTargetId(null);
                        }
                        ctx.emit(BattleEvent.of(BattleEventType.STATUS_EXPIRED, round)
                                .target(unit.getUnitId()).status(status.getStatusId()));
                    }
                }
                // 技能冷却递减（每宠独立计算）
                unit.getCooldowns().replaceAll((k, v) -> Math.max(0, v - 1));
                // 行动顺序干预仅当前回合有效（REV-007）
                unit.setActionOrderBoost(0);
                // 阶段 7：连续控制衰减重置检查
                if (unit.getConsecutiveControlCount() > 0) {
                    boolean hasControl = unit.getStatuses().stream().anyMatch(s -> {
                        StatusEffectConfig cfg = registry.getStatus(s.getStatusId());
                        return cfg != null && "SPECIAL_CONTROL".equals(cfg.getCategory());
                    });
                    if (!hasControl) {
                        unit.setRoundsWithoutControl(unit.getRoundsWithoutControl() + 1);
                        if (unit.getRoundsWithoutControl() >= registry.getSystemRules().getControlDecayResetRounds()) {
                            unit.setConsecutiveControlCount(0);
                            unit.setRoundsWithoutControl(0);
                        }
                    } else {
                        unit.setRoundsWithoutControl(0);
                    }
                }
            }
        }

        // 延迟效果触发（REV-006 DELAYED）
        for (BattleContext.DelayedEffect delayed : new ArrayList<>(ctx.getDelayedEffects())) {
            if (delayed.getTriggerRound() > round) {
                continue;
            }
            ctx.getDelayedEffects().remove(delayed);
            BattleUnit dCaster = ctx.findUnit(delayed.getCasterId());
            BattleUnit dTarget = ctx.findUnit(delayed.getTargetId());
            if (dTarget == null || !dTarget.isAlive() || delayed.getEffect() == null) {
                continue;
            }
            ctx.emit(BattleEvent.of(BattleEventType.DELAYED_EFFECT_TRIGGERED, round)
                    .source(delayed.getCasterId()).target(delayed.getTargetId())
                    .skill(delayed.getSkillId()));
            String dType = delayed.getEffect().getType() != null
                    ? delayed.getEffect().getType().toUpperCase() : "";
            switch (dType) {
                case "DAMAGE" -> dealDamageToUnit(ctx, dCaster, dTarget,
                        Math.max(1, (int) Math.round(delayed.getBaseValue())), false, true);
                case "HEAL" -> healUnit(ctx, dCaster, dTarget, null,
                        (int) Math.round(delayed.getBaseValue()));
                case "APPLY_STATUS" -> applyStatus(ctx, dCaster, dTarget, delayed.getEffect().getStatusId());
                default -> log.warn("延迟效果不支持的内层类型: {}", dType);
            }
        }

        // DOT 导致的倒下统一处理
        processDefeats(ctx);

        // 回合结束被动（REV-009 命名 TURN_END）
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getActiveAliveUnits()) {
                passiveManager.trigger(ctx, "TURN_END", unit);
            }
        }

        checkBattleEnd(ctx);
        ctx.emit(BattleEvent.of(BattleEventType.TURN_ENDED, round));
    }

    // ---- 阶段 7：控制抗性、连续衰减、阶段触发、runFullBattle ----

    /**
     * 计算状态施加的最终成功概率（阶段 7：控制抗性 + 连续控制衰减）。
     * <p>
     * 对 SPECIAL_CONTROL 类状态：baseChance × controlResistance × consecutiveControlDecay。
     * 非控制类状态不受影响，直接返回原始 chance。
     */
    private double computeFinalStatusChance(BattleContext ctx, BattleUnit caster, BattleUnit target,
                                             SkillConfig.SkillEffectConfig effect) {
        double baseChance = effect.getChance();
        if (effect.getStatusId() == null) {
            return baseChance;
        }
        StatusEffectConfig statusConfig = registry.getStatus(effect.getStatusId());
        if (statusConfig == null || !"SPECIAL_CONTROL".equals(statusConfig.getCategory())) {
            return baseChance;
        }
        // 控制抗性
        double resistance = target.getControlResistance();
        double chance = baseChance * resistance;

        // 连续控制衰减
        SystemRuleConfig system = registry.getSystemRules();
        List<Double> decay = system.getConsecutiveControlDecay();
        int controlCount = target.getConsecutiveControlCount();
        double decayFactor;
        if (decay != null && !decay.isEmpty()) {
            if (controlCount < decay.size()) {
                decayFactor = decay.get(controlCount);
            } else {
                decayFactor = system.getConsecutiveControlMin();
            }
        } else {
            decayFactor = 1.0;
        }
        chance *= decayFactor;

        if (chance < baseChance) {
            ctx.emit(BattleEvent.of(BattleEventType.CONTROL_RESISTED, ctx.getCurrentRound())
                    .target(target.getUnitId()).status(effect.getStatusId())
                    .put("originalChance", baseChance).put("finalChance", chance)
                    .put("resistance", resistance).put("decayFactor", decayFactor));
        }
        return Math.max(0, Math.min(1.0, chance));
    }

    /**
     * Boss 阶段触发检查（阶段 7：在回合结束 DOT 结算前执行）。
     * <p>
     * 检查敌方 Boss 单位的 phaseTriggers：
     * 条件：currentHp / maxHp <= trigger.hpPercent 且 !trigger.activated。
     */
    private void checkPhaseTriggers(BattleContext ctx) {
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getUnits()) {
                if (!unit.isAlive() || unit.getPhaseTriggers() == null || unit.getPhaseTriggers().isEmpty()) {
                    continue;
                }
                double hpPercent = (double) unit.getCurrentHp() / unit.getMaxHp();
                for (int i = 0; i < unit.getPhaseTriggers().size(); i++) {
                    BossesConfig.PhaseTrigger trigger = unit.getPhaseTriggers().get(i);
                    if (i < unit.getPhaseActivated().size() && Boolean.TRUE.equals(unit.getPhaseActivated().get(i))) {
                        continue; // 已激活
                    }
                    if (hpPercent <= trigger.getHpPercent()) {
                        // 激活阶段
                        if (i >= unit.getPhaseActivated().size()) {
                            while (unit.getPhaseActivated().size() <= i) {
                                unit.getPhaseActivated().add(false);
                            }
                        }
                        unit.getPhaseActivated().set(i, true);
                        // 执行效果
                        for (BossesConfig.PhaseEffect effect : trigger.getEffects()) {
                            executePhaseEffect(ctx, unit, effect);
                        }
                        ctx.emit(BattleEvent.of(BattleEventType.PHASE_TRANSITION, ctx.getCurrentRound())
                                .target(unit.getUnitId())
                                .put("hpPercent", hpPercent)
                                .put("phaseIndex", i));
                    }
                }
            }
        }
    }

    /** 执行阶段效果。 */
    private void executePhaseEffect(BattleContext ctx, BattleUnit unit, BossesConfig.PhaseEffect effect) {
        switch (effect.getType()) {
            case "ADD_SKILL" -> {
                if (effect.getSkillId() != null && !unit.getSkillIds().contains(effect.getSkillId())) {
                    unit.getSkillIds().add(effect.getSkillId());
                    unit.getCooldowns().put(effect.getSkillId(), 0);
                }
            }
            case "ADD_SHIELD" -> {
                unit.setShield(unit.getShield() + effect.getShieldValue());
                ctx.emit(BattleEvent.of(BattleEventType.SHIELD_CREATED, ctx.getCurrentRound())
                        .source(unit.getUnitId()).target(unit.getUnitId())
                        .value(effect.getShieldValue()).put("phase", true));
            }
            case "BUFF_SELF" -> {
                if (effect.getStatusId() != null) {
                    applyStatus(ctx, unit, unit, effect.getStatusId());
                }
            }
            default -> log.warn("未知的阶段效果类型: {}", effect.getType());
        }
    }

    /**
     * AI vs AI 跑完整个战斗（自动挑战使用，阶段 7）。
     * <p>
     * 双方都使用 AI DecisionProvider，同步执行完整战斗直到结束。
     *
     * @param ctx 已初始化的战斗上下文
     * @param playerAI 玩家方 AI（自动挑战时使用）
     */
    public void runFullBattle(BattleContext ctx, DecisionProvider playerAI) {
        startBattle(ctx);
        while (!ctx.isFinished()) {
            List<BattleAction> playerActions = playerAI.decide(ctx, ctx.getPlayerSide());
            playTurn(ctx, playerActions);
        }
    }
}
