package com.petgame.battle.victory;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 战败上下文（阶段 12，技术方案 §83.3）。
 * <p>
 * 玩家战败后在结算处从既有 {@link com.petgame.battle.engine.BattleContext} + 事件序列
 * 聚合生成的轻量上下文。只保留少数字段，优先复用既有数据；不为此系统改造战斗引擎。
 * 若某字段现有系统不易获得，则直接降级（该项不参与战况标签判定）。
 */
@Data
public class BattleDefeatContext {

    /** 获胜方固定 ID（Boss id；野外宠物无固定实例 id 为 null）。 */
    private String winnerId;

    /** 获胜方类型：TRAINER / WILD_PET / BOSS。 */
    private String winnerType;

    /** 获胜方风格：训练师 victoryStyle 或 野外宠物 victoryBehavior（可能为 null，表示无已知风格）。 */
    private String winnerStyle;

    /** 主话者名称（Boss 名 / 敌方单位名 / 训练师名）。用于旁白与前端展示。 */
    private String winnerName;

    /** 获胜方存活单位平均 HP 百分比（0~1）。 */
    private double winnerHpPercent;

    /** 战斗回合数。 */
    private int turnCount;

    /** 固定敌人的累计挑战次数（可选，能否获取取决于统计接入）。 */
    private int challengeCount;

    /** 固定敌人的连续战败次数（可选，用于 REPEATED_DEFEAT 标签）。 */
    private int consecutiveDefeatCount;

    /** 玩家主动换宠次数（由事件统计）。 */
    private int playerSwitchCount;

    /** 玩家治疗次数（由事件统计）。 */
    private int playerHealCount;

    /** 玩家同一技能最高使用次数（由事件统计）。 */
    private int repeatedSkillCount;

    /** 玩家击败的敌方单位数量（由 PET_DEFEATED 事件推导，用于 COMEBACK_LOSS 判定）。 */
    private int enemyKnockoutCount;

    /** 战况标签列表（首版 8 个，见 §83.4）。 */
    private List<String> tags = new ArrayList<>();

    /** 是否已有标签（NORMAL_LOSS 兜底）。 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}