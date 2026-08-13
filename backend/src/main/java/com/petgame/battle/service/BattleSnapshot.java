package com.petgame.battle.service;

import com.petgame.battle.event.BattleEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗快照（返回给前端的完整战斗状态视图）。
 * <p>
 * 开战/提交行动/查询战斗均返回此结构；events 为本次调用新增的事件序列
 * （开战时为登场被动等初始事件，提交行动时为本回合事件）。
 */
@Data
public class BattleSnapshot {

    private String battleId;

    /** 战斗类型：TEST / WILD。 */
    private String battleType;

    /** 随机种子（开发者模式可用于复现）。 */
    private long seed;

    /** 当前回合号（0 = 尚未开始回合）。 */
    private int currentRound;

    private boolean finished;

    /** 胜方：PLAYER / ENEMY / null（未结束或逃跑）。 */
    private String winner;

    /** 玩家是否已逃跑（野生战斗，同战败结算）。 */
    private boolean fled;

    private List<UnitSnapshot> playerUnits = new ArrayList<>();

    private List<UnitSnapshot> enemyUnits = new ArrayList<>();

    /** 本次调用新增的战斗事件序列。 */
    private List<BattleEvent> events = new ArrayList<>();
}
