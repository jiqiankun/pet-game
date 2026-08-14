package com.petgame.battle.victory;

import lombok.Data;

/**
 * 胜利互动视图（阶段 12）。
 * <p>
 * 返回给前端播放的胜利互动结果。若为 null 表示无可用互动（前端隐藏互动区）。
 * 只作展示，不参与任何战斗数值结算。
 */
@Data
public class VictoryInteractionView {

    /** 互动 ID。 */
    private String id;

    /** 获胜方类型：TRAINER / WILD_PET / BOSS。 */
    private String winnerType;

    /** 获胜方名称（Boss 名 / 敌方单位名 / 训练师名），前端作主话者展示。 */
    private String winnerName;

    /** 表现方式：DIALOGUE / ACTION_NARRATION / CRY。 */
    private String presentationType;

    /** 可选动作 ID（前端映射动作表现，无资源时降级）。 */
    private String actionId;

    /** 可选叫声文本。 */
    private String cry;

    /** 命中战况标签。 */
    private String context;

    /** 互动文本（台词或旁白）。 */
    private String text;
}