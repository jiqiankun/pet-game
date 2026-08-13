package com.petgame.battle.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 战斗中的状态实例（运行时）。
 * <p>
 * 技能冷却、Buff、Debuff、异常状态不跨战斗保留，仅存在于战斗内存数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusInstance {

    /** 状态配置 ID。 */
    private String statusId;

    /** 剩余持续回合数。 */
    private int remainingTurns;

    /** 施加者单位 ID（用于事件展示）。 */
    private String sourceId;
}
