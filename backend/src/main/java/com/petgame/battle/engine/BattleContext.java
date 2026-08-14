package com.petgame.battle.engine;

import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.common.GameRandom;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗上下文（技术方案 §20）。
 * <p>
 * 战斗开始后创建，保存全部战斗临时数据：
 * battleId、当前回合、双方队伍、HP、技能冷却、Buff/Debuff/异常、
 * 行动顺序、随机数生成器、战斗事件。
 * <p>
 * 第一阶段只保存在服务器内存，服务重启后未完成战斗直接丢弃。
 */
@Data
public class BattleContext {

    /** 战斗 ID。 */
    private final String battleId;

    /** 随机种子（开发者模式可固定，用于复现完全一致的战斗流程）。 */
    private final long randomSeed;

    /** 统一随机工具（禁止直接使用 Math.random / new Random）。 */
    private final GameRandom random;

    /** 当前回合号（从 1 开始）。 */
    private int currentRound = 0;

    /** 战斗类型：TEST（测试战斗）/ WILD（野生遭遇，可捕捉可逃跑）/ BOSS（Boss 战斗）。 */
    private String battleType = "TEST";

    /** 野生战斗的遭遇组 ID（结算奖励与稀有度系数计算用）。 */
    private String encounterGroupId;

    /** Boss ID（BOSS 战斗类型时使用，阶段 7）。 */
    private String bossId;

    /** Boss 难度（NORMAL/HARD/NIGHTMARE，阶段 7）。 */
    private String bossDifficulty;

    /** true 时禁止 CAPTURE 行动（Boss 不可捕捉，阶段 7）。 */
    private boolean uncapturable;

    /** 玩家方。 */
    private BattleSide playerSide;

    /** 敌方。 */
    private BattleSide enemySide;

    /** 战斗是否已结束。 */
    private boolean finished;

    /** 胜方：PLAYER / ENEMY / null（未结束或逃跑无胜方）。 */
    private String winner;

    /** 本场战斗被捕捉的野生单位（结算时落库）。 */
    private List<BattleUnit> capturedUnits = new ArrayList<>();

    /** 本场战斗已使用的捕捉球：itemId → 数量（战斗过程不落库，结算时统一扣除）。 */
    private Map<String, Integer> consumedCaptureBalls = new HashMap<>();

    /** 开战时玩家背包捕捉球存量快照：itemId → 数量（用于战斗内数量校验）。 */
    private Map<String, Integer> availableCaptureBalls = new HashMap<>();

    /** 玩家是否逃跑成功（同战败结算：HP 回写、无奖励、无胜方）。 */
    private boolean fled;

    /** 战斗级自动战斗设置（阶段 10；null 或 enabled=false 时为手动战斗）。 */
    private com.petgame.battle.ai.AutoBattleSettings autoSettings;

    /** 开战时玩家背包恢复/复苏道具存量快照：itemId → 数量（阶段 10，同捕捉球模式）。 */
    private Map<String, Integer> availableRecoveryItems = new HashMap<>();

    /** 本场战斗已使用的恢复/复苏道具：itemId → 数量（战斗过程不落库，结算时统一扣除）。 */
    private Map<String, Integer> consumedRecoveryItems = new HashMap<>();

    /** 本场战斗全部事件序列。 */
    private List<BattleEvent> events = new ArrayList<>();

    // ---- 新机制运行时数据（REV-006/REV-009）----

    /** 延迟效果队列（DelayedEffect 效果类型注册，达到回合时触发）。 */
    private List<DelayedEffect> delayedEffects = new ArrayList<>();

    /** 当前被动触发深度（防无限递归，达到上限后不再嵌套触发）。 */
    private int passiveDepth = 0;

    /** 被动触发深度上限（技术方案 §78：防止 被动A→被动B→被动A 无限递归）。 */
    public static final int MAX_PASSIVE_DEPTH = 4;

    /**
     * 延迟效果记录（REV-006 DELAYED）。
     */
    @Data
    public static class DelayedEffect {
        /** 触发回合号。 */
        private int triggerRound;
        /** 释放者单位 ID。 */
        private String casterId;
        /** 目标单位 ID。 */
        private String targetId;
        /** 延迟执行的附加效果配置。 */
        private com.petgame.config.model.SkillConfig.SkillEffectConfig effect;
        /** 效果基础值（注册时预计算）。 */
        private double baseValue;
        /** 来源技能 ID（事件展示用）。 */
        private String skillId;
    }

    public BattleContext(String battleId, long randomSeed) {
        this.battleId = battleId;
        this.randomSeed = randomSeed;
        this.random = new GameRandom(randomSeed);
    }

    /** 发出战斗事件并记录到事件序列。 */
    public BattleEvent emit(BattleEvent event) {
        events.add(event);
        return event;
    }

    /** 根据单位 ID 在任意一方查找单位。 */
    public com.petgame.battle.model.BattleUnit findUnit(String unitId) {
        var unit = playerSide.findUnit(unitId);
        return unit != null ? unit : enemySide.findUnit(unitId);
    }

    /** 返回单位所在方，找不到返回 null。 */
    public BattleSide findSideOf(String unitId) {
        if (playerSide.findUnit(unitId) != null) {
            return playerSide;
        }
        if (enemySide.findUnit(unitId) != null) {
            return enemySide;
        }
        return null;
    }

    /** 返回指定方的敌对方。 */
    public BattleSide getOpposite(BattleSide side) {
        return side == playerSide ? enemySide : playerSide;
    }
}
