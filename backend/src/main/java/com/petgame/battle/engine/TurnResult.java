package com.petgame.battle.engine;

import com.petgame.battle.event.BattleEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 单回合结算结果。
 */
@Data
@AllArgsConstructor
public class TurnResult {

    /** 本回合回合号。 */
    private int round;

    /** 本回合新增的战斗事件序列。 */
    private List<BattleEvent> events;

    /** 战斗是否已结束。 */
    private boolean finished;

    /** 胜方：PLAYER / ENEMY / null（未结束）。 */
    private String winner;
}
