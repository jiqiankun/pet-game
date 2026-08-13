package com.petgame.battle.ai;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;

import java.util.List;

/**
 * 决策来源接口（技术方案 §19）。
 * <p>
 * 手动/自动/普通/精英/Boss 战斗全部使用同一个 BattleEngine，
 * 唯一差异是「谁决定行动」。玩家手动 = 前端提交意图；
 * 后续自动战斗 = 为同一入口接入 AutoDecisionProvider。
 */
public interface DecisionProvider {

    /**
     * 为指定方的每个存活上场单位生成本回合行动。
     *
     * @param ctx  战斗上下文
     * @param side 需要决策的一方
     */
    List<BattleAction> decide(BattleContext ctx, BattleSide side);
}
