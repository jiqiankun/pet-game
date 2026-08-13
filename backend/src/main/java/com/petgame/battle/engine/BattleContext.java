package com.petgame.battle.engine;

import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.model.BattleSide;
import com.petgame.common.GameRandom;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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

    /** 玩家方。 */
    private BattleSide playerSide;

    /** 敌方。 */
    private BattleSide enemySide;

    /** 战斗是否已结束。 */
    private boolean finished;

    /** 胜方：PLAYER / ENEMY / null（未结束）。 */
    private String winner;

    /** 本场战斗全部事件序列。 */
    private List<BattleEvent> events = new ArrayList<>();

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
