package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 敌方胜利互动配置（阶段 12）。
 * <p>
 * 数据来源 game-config/victory/victory-interactions.yml。
 * 训练师台词与野外宠物互动统一使用同一结构（VictoryInteraction），仅表现类型不同
 * （需求 §152）：训练师以台词为主，野外宠物以动作 + 旁白 + 叫声为主。
 * <p>
 * 本系统为战败表现增强，数据驱动、低耦合：无专属配置时回退公共池，无动画资源时
 * 回退纯文本/旁白。不参与战斗数值计算，不改变战败结算。
 */
@Data
public class VictoryInteractionConfig {

    private int configVersion = 1;

    /** 互动条目列表。 */
    private List<Interaction> interactions = new ArrayList<>();

    /** 单条胜利互动。 */
    @Data
    public static class Interaction {
        /** 互动 ID（如 VI_NPC_CLOSE_001）。 */
        private String id;
        /**
         * 获胜方类型：TRAINER / WILD_PET / BOSS。
         * 由战斗类型映射：BOSS → BOSS、WILD → WILD_PET、TEST → TRAINER。
         */
        private String winnerType;
        /** 风格：训练师 victoryStyle 或 野外宠物 victoryBehavior。 */
        private String style;
        /**
         * 指定获胜方 ID（可选，用于 Boss/精英/霸主专属内容）。
         * null 表示通用互动（所有同 winnerType 敌人均可使用）。
         */
        private String targetId;
        /** 战况标签列表（空 = 适用所有战况）。 */
        private List<String> contexts = new ArrayList<>();
        /**
         * 表现方式：DIALOGUE（台词）/ ACTION_NARRATION（动作旁白）/ CRY（叫声）。
         */
        private String presentationType;
        /** 可选动作 ID（如 YAWN / PROWL），无对应资源时前端降级。 */
        private String actionId;
        /** 可选叫声文本（如「嗷呜——！」）。 */
        private String cry;
        /** 权重（越大越常见）。 */
        private int weight = 100;
        /** 稀有度：COMMON（普通）/ EASTER_EGG（彩蛋，低概率 1%~3%）。 */
        private String rarity = "COMMON";
        /** 互动文本（台词或旁白）。 */
        private String text;
    }
}