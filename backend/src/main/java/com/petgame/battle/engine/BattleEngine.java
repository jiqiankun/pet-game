package com.petgame.battle.engine;

import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.calculator.AccuracyCalculator;
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

        // 3. 依次执行
        for (BattleUnit unit : order) {
            if (ctx.isFinished()) {
                break;
            }
            if (!unit.isAlive() || !unit.isActive()) {
                continue; // 本回合内已倒下或已换下
            }
            ctx.emit(BattleEvent.of(BattleEventType.ACTION_STARTED, round).source(unit.getUnitId()));

            // 蓄力释放优先于新行动
            if (unit.getChargingSkillId() != null) {
                executeCharging(ctx, unit);
                processAfterAction(ctx);
                continue;
            }

            // 控制状态：概率跳过行动
            StatusModifiers mod = StatusModifiers.of(unit, registry.getStatusIndex());
            if (ctx.getRandom().chance(mod.getSkipActionChance())) {
                ctx.emit(BattleEvent.of(BattleEventType.ACTION_SKIPPED, round)
                        .source(unit.getUnitId()).put("reason", "CONTROL"));
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
            effectiveSpeed.put(unit.getUnitId(), unit.getSpeed() * mod.getSpeedMultiplier());
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
            default -> log.warn("未知行动类型: {}", type);
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

        for (BattleUnit target : targets) {
            if (!target.isAlive()) {
                continue;
            }
            applyEffect(ctx, caster, target, skill, skill.getEffectType(), baseValue, singleTarget);
        }

        // 附加效果（多效果组合）
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            double effectBase = DamageCalculator.computeBaseValue(effect.getValue(), effect.getScaling(), caster);
            for (BattleUnit target : targets) {
                if (!target.isAlive()) {
                    continue;
                }
                if ("APPLY_STATUS".equalsIgnoreCase(effect.getType())) {
                    if (ctx.getRandom().chance(effect.getChance())) {
                        applyStatus(ctx, caster, target, effect.getStatusId());
                    }
                } else {
                    applyEffect(ctx, caster, target, skill, effect.getType(), effectBase, singleTarget);
                }
            }
        }

        passiveManager.trigger(ctx, "ON_ATTACK", caster);
    }

    /** 目标解析：群体=全部存活上场；单体=嘲讽重定向 + 合法性兜底。 */
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
                yield List.of(target);
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
                if (target == null || !target.isAlive() || !target.isActive()) {
                    // 目标已失效（如蓄力期间倒下）：改选第一个存活上场单位
                    yield enemySide.getActiveAliveUnits().stream().findFirst()
                            .map(List::of).orElse(List.of());
                }
                yield List.of(target);
            }
        };
    }

    private void applyEffect(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                             String effectType, double baseValue, boolean singleTarget) {
        if (effectType == null) {
            return;
        }
        switch (effectType.toUpperCase()) {
            case "DAMAGE" -> applyDamage(ctx, caster, target, skill, baseValue, singleTarget);
            case "HEAL" -> {
                int heal = (int) Math.round(baseValue * healBonus(caster, skill));
                int healed = Math.min(heal, target.getMaxHp() - target.getCurrentHp());
                target.setCurrentHp(target.getCurrentHp() + healed);
                ctx.emit(BattleEvent.of(BattleEventType.HEAL, ctx.getCurrentRound())
                        .source(caster.getUnitId()).target(target.getUnitId()).value(healed)
                        .skill(skill.getId()));
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

    private void applyDamage(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                             double baseValue, boolean singleTarget) {
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
                    dealDamageToUnit(ctx, caster, guard, transferred, skill);
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
            passiveManager.trigger(ctx, "ON_CRIT", caster);
        }

        dealDamageToUnit(ctx, caster, target, damage, skill);
        passiveManager.trigger(ctx, "ON_HIT_TAKEN", target);
    }

    /**
     * 伤害落实到单位：护盾先吸收（不再次套用防御计算）→ 扣 HP →
     * 致命伤害触发不屈类被动 → 倒下判定。
     */
    private void dealDamageToUnit(BattleContext ctx, BattleUnit source, BattleUnit target,
                                  int damage, SkillConfig skill) {
        if (damage <= 0 || !target.isAlive()) {
            return;
        }
        target.setLastDamageSourceId(source != null ? source.getUnitId() : null);

        int remaining = damage;
        if (target.getShield() > 0) {
            int absorbed = Math.min(target.getShield(), remaining);
            target.setShield(target.getShield() - absorbed);
            remaining -= absorbed;
        }
        if (remaining > 0) {
            target.setCurrentHp(target.getCurrentHp() - remaining);
        }

        if (target.getCurrentHp() <= 0) {
            if (passiveManager.consumeSurviveLethal(ctx, target)) {
                target.setCurrentHp(1);
            } else {
                target.setCurrentHp(0);
            }
        }
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
            existing.setRemainingTurns(config.getDefaultDuration());
        } else {
            target.getStatuses().add(new StatusInstance(statusId, config.getDefaultDuration(),
                    source.getUnitId()));
        }
        // 援护状态：建立援护关系
        if (config.getGuardTransferPercent() > 0) {
            source.setGuardTargetId(target.getUnitId());
        }
        BattleEventType eventType = switch (config.getCategory()) {
            case "BUFF" -> BattleEventType.BUFF_APPLIED;
            case "DOT", "CONTROL", "DEBUFF" -> BattleEventType.STATUS_APPLIED;
            default -> BattleEventType.DEBUFF_APPLIED;
        };
        ctx.emit(BattleEvent.of(eventType, ctx.getCurrentRound())
                .source(source.getUnitId()).target(target.getUnitId())
                .status(statusId).put("duration", config.getDefaultDuration()));
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
        // 倒下被动（如余烬）
        passiveManager.trigger(ctx, "ON_DEATH", unit);

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
        if (ctx.getEnemySide().isAllDefeated()) {
            winner = "PLAYER";
        } else if (ctx.getPlayerSide().isAllDefeated()) {
            winner = "ENEMY";
        }
        if (winner != null) {
            ctx.setFinished(true);
            ctx.setWinner(winner);
            ctx.emit(BattleEvent.of(BattleEventType.BATTLE_ENDED, ctx.getCurrentRound())
                    .put("winner", winner));
        }
    }

    // ---- 回合结束结算 ----

    private void endRound(BattleContext ctx) {
        int round = ctx.getCurrentRound();

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
            }
        }

        // DOT 导致的倒下统一处理
        processDefeats(ctx);

        // 回合结束被动
        for (BattleSide side : List.of(ctx.getPlayerSide(), ctx.getEnemySide())) {
            for (BattleUnit unit : side.getActiveAliveUnits()) {
                passiveManager.trigger(ctx, "ON_ROUND_END", unit);
            }
        }

        checkBattleEnd(ctx);
        ctx.emit(BattleEvent.of(BattleEventType.TURN_ENDED, round));
    }
}
