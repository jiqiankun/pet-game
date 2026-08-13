package com.petgame.battle.passive;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 被动技能框架（阶段 3）。
 * <p>
 * 配置驱动：按 trigger + effectType 统一解释，不针对具体被动 ID 写分支。
 * 触发时机：登场/退场/受击/攻击/暴击/击败/倒下/回合开始/回合结束。
 */
public class PassiveManager {

    private static final Logger log = LoggerFactory.getLogger(PassiveManager.class);

    private final GameConfigRegistry registry;

    public PassiveManager(GameConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * 触发指定时机、指定单位的全部被动。
     *
     * @param ctx           战斗上下文
     * @param triggerPoint  触发时机（ON_ENTER / ON_DEATH / ...）
     * @param unit          被动持有单位
     */
    public void trigger(BattleContext ctx, String triggerPoint, BattleUnit unit) {
        for (PassiveSkillConfig passive : unit.getPassives()) {
            if (!triggerPoint.equals(passive.getTrigger())) {
                continue;
            }
            // SURVIVE_LETHAL 由 consumeSurviveLethal 单独处理
            if ("SURVIVE_LETHAL".equals(passive.getEffectType())) {
                continue;
            }
            if (!canTrigger(unit, passive)) {
                continue;
            }
            applyEffect(ctx, passive, unit);
            unit.getPassiveTriggerCounts().merge(passive.getId(), 1, Integer::sum);
        }
    }

    /**
     * 致命伤害存活判定（不屈类被动）。
     * <p>
     * 若单位持有未消耗的 ON_HIT_TAKEN + SURVIVE_LETHAL 被动，保留 1 点生命。
     *
     * @return true 表示触发存活（单位 HP 应设为 1）
     */
    public boolean consumeSurviveLethal(BattleContext ctx, BattleUnit unit) {
        for (PassiveSkillConfig passive : unit.getPassives()) {
            if ("ON_HIT_TAKEN".equals(passive.getTrigger())
                    && "SURVIVE_LETHAL".equals(passive.getEffectType())
                    && canTrigger(unit, passive)) {
                unit.getPassiveTriggerCounts().merge(passive.getId(), 1, Integer::sum);
                ctx.emit(BattleEvent.of(BattleEventType.PASSIVE_TRIGGERED, ctx.getCurrentRound())
                        .source(unit.getUnitId())
                        .put("passiveId", passive.getId())
                        .put("passiveName", passive.getName())
                        .put("effectType", "SURVIVE_LETHAL"));
                return true;
            }
        }
        return false;
    }

    // ---- 内部实现 ----

    private boolean canTrigger(BattleUnit unit, PassiveSkillConfig passive) {
        int max = passive.getMaxTriggerPerBattle();
        if (max <= 0) {
            return true;
        }
        return unit.getPassiveTriggerCounts().getOrDefault(passive.getId(), 0) < max;
    }

    private void applyEffect(BattleContext ctx, PassiveSkillConfig passive, BattleUnit unit) {
        BattleSide ownSide = ctx.findSideOf(unit.getUnitId());
        BattleSide enemySide = ctx.getOpposite(ownSide);

        ctx.emit(BattleEvent.of(BattleEventType.PASSIVE_TRIGGERED, ctx.getCurrentRound())
                .source(unit.getUnitId())
                .put("passiveId", passive.getId())
                .put("passiveName", passive.getName())
                .put("effectType", passive.getEffectType()));

        switch (passive.getEffectType()) {
            case "APPLY_STATUS_ALLY_ALL" -> {
                for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                    applyStatus(ctx, passive.getStatusId(), ally, unit);
                }
            }
            case "APPLY_STATUS_SELF" -> applyStatus(ctx, passive.getStatusId(), unit, unit);
            case "HEAL_SELF" -> {
                if (unit.isAlive()) {
                    int heal = (int) Math.round(passive.getValue()
                            + passive.getSpiritScale() * unit.getSpirit());
                    int healed = Math.min(heal, unit.getMaxHp() - unit.getCurrentHp());
                    unit.setCurrentHp(unit.getCurrentHp() + healed);
                    ctx.emit(BattleEvent.of(BattleEventType.HEAL, ctx.getCurrentRound())
                            .source(unit.getUnitId()).target(unit.getUnitId()).value(healed));
                }
            }
            case "DAMAGE_ENEMY_RANDOM" -> {
                List<BattleUnit> targets = enemySide.getActiveAliveUnits();
                if (!targets.isEmpty()) {
                    BattleUnit target = targets.get(ctx.getRandom().nextInt(0, targets.size() - 1));
                    int damage = Math.max(1, (int) Math.round(passive.getValue()
                            + passive.getSpiritScale() * unit.getSpirit()));
                    target.setCurrentHp(Math.max(0, target.getCurrentHp() - damage));
                    ctx.emit(BattleEvent.of(BattleEventType.DAMAGE, ctx.getCurrentRound())
                            .source(unit.getUnitId()).target(target.getUnitId())
                            .value(damage).critical(false)
                            .put("passive", true)
                            .put("element", passive.getElement()));
                    // 倒下与补位由引擎统一处理（processDefeats）
                }
            }
            default -> log.warn("未知被动效果类型: {}（被动 {}）", passive.getEffectType(), passive.getId());
        }
    }

    private void applyStatus(BattleContext ctx, String statusId, BattleUnit target, BattleUnit source) {
        StatusEffectConfig config = registry.getStatus(statusId);
        if (config == null) {
            log.warn("被动引用的状态不存在: {}", statusId);
            return;
        }
        // 已存在同类状态时刷新持续时间
        StatusInstance existing = target.getStatuses().stream()
                .filter(s -> s.getStatusId().equals(statusId))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.setRemainingTurns(config.getDefaultDuration());
        } else {
            target.getStatuses().add(new StatusInstance(statusId, config.getDefaultDuration(),
                    source != null ? source.getUnitId() : null));
        }
        BattleEventType eventType = "BUFF".equals(config.getCategory())
                ? BattleEventType.BUFF_APPLIED : BattleEventType.STATUS_APPLIED;
        ctx.emit(BattleEvent.of(eventType, ctx.getCurrentRound())
                .source(source != null ? source.getUnitId() : null)
                .target(target.getUnitId())
                .status(statusId));
    }
}
