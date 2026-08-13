package com.petgame.battle.calculator;

import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.model.StatusEffectConfig;
import lombok.Getter;

import java.util.Map;

/**
 * 状态修正聚合。
 * <p>
 * 将单位携带的全部状态配置字段聚合为战斗结算所需的修正值，
 * 引擎与计算器统一通过本类读取状态效果，不针对具体状态 ID 分支。
 */
@Getter
public class StatusModifiers {

    /** 速度乘数（1 + Σ speedPercent）。 */
    private double speedMultiplier = 1.0;

    /** 防御乘数。 */
    private double defenseMultiplier = 1.0;

    /** 抗性乘数。 */
    private double resistanceMultiplier = 1.0;

    /** 造成伤害乘数。 */
    private double damageDealtMultiplier = 1.0;

    /** 受到伤害乘数。 */
    private double damageTakenMultiplier = 1.0;

    /** 受到物理伤害额外乘数（猎杀印记等标记，REV-002）。 */
    private double physicalTakenMultiplier = 1.0;

    /** 命中惩罚合计（致盲等）。 */
    private double accuracyPenalty = 0.0;

    /** 行动跳过概率（取携带状态中的最大值）。 */
    private double skipActionChance = 0.0;

    /** 是否沉默。 */
    private boolean silenced;

    /** 是否嘲讽。 */
    private boolean taunt;

    /** 援护伤害转移比例（本单位为援护者时）。 */
    private double guardTransferPercent = 0.0;

    // ---- 新机制聚合（REV-002/REV-008）----

    /** 是否混乱（单体技能随机改目标）。 */
    private boolean confused;

    /** 是否隐匿（单体不可选中）。 */
    private boolean stealthed;

    /** 是否禁疗。 */
    private boolean healBlocked;

    /** 再生恢复合计（每回合结束恢复占最大 HP 比例，随层数累加）。 */
    private double healPercent = 0.0;

    /** 反击概率（取最大值）。 */
    private double counterRate = 0.0;

    /** 反击基础值（对应最大反击概率状态）。 */
    private double counterValue = 0.0;

    /** 反击力量系数（对应最大反击概率状态）。 */
    private double counterScaling = 0.0;

    /** 是否处于捕获震慑（安全捕捉窗口）。 */
    private boolean captureStunned;

    /**
     * 聚合单位全部状态的修正。
     *
     * @param unit        战斗单位
     * @param statusIndex 状态配置索引
     */
    public static StatusModifiers of(BattleUnit unit, Map<String, StatusEffectConfig> statusIndex) {
        StatusModifiers mod = new StatusModifiers();
        for (StatusInstance instance : unit.getStatuses()) {
            StatusEffectConfig config = statusIndex.get(instance.getStatusId());
            if (config == null) {
                continue;
            }
            // 可叠层状态的数值修正随层数放大（默认层数 1，不改变既有行为）
            int stack = Math.max(1, instance.getStack());
            mod.speedMultiplier += config.getSpeedPercent() * stack;
            mod.defenseMultiplier += config.getDefensePercent() * stack;
            mod.resistanceMultiplier += config.getResistancePercent() * stack;
            mod.damageDealtMultiplier += config.getDamageDealtPercent() * stack;
            mod.damageTakenMultiplier += config.getDamageTakenPercent() * stack;
            mod.physicalTakenMultiplier += config.getPhysicalTakenPercent() * stack;
            mod.accuracyPenalty += config.getAccuracyPenalty() * stack;
            mod.healPercent += config.getHealPercent() * stack;
            mod.skipActionChance = Math.max(mod.skipActionChance, config.getSkipActionChance());
            mod.silenced |= config.isSilence();
            mod.taunt |= config.isTaunt();
            mod.confused |= config.isConfusion();
            mod.stealthed |= config.isStealth();
            mod.healBlocked |= config.isHealBlock();
            mod.captureStunned |= config.isCaptureStun();
            mod.guardTransferPercent = Math.max(mod.guardTransferPercent, config.getGuardTransferPercent());
            if (config.getCounterRate() > mod.counterRate) {
                mod.counterRate = config.getCounterRate();
                mod.counterValue = config.getCounterValue();
                mod.counterScaling = config.getCounterScaling();
            }
        }
        // 乘数下限保护，避免配置叠加出负值
        mod.speedMultiplier = Math.max(0.1, mod.speedMultiplier);
        mod.defenseMultiplier = Math.max(0.1, mod.defenseMultiplier);
        mod.resistanceMultiplier = Math.max(0.1, mod.resistanceMultiplier);
        mod.damageDealtMultiplier = Math.max(0.1, mod.damageDealtMultiplier);
        mod.damageTakenMultiplier = Math.max(0.1, mod.damageTakenMultiplier);
        mod.physicalTakenMultiplier = Math.max(0.1, mod.physicalTakenMultiplier);
        return mod;
    }
}
