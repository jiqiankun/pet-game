package com.petgame.pet.domain;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宠物面板属性值对象（需求 §9 属性组成）。
 * <p>
 * 最终属性 = 种族基础（含个体浮动） + 等级固定成长 + 资质成长修正 + 自由属性点。
 * 战斗 Buff/Debuff 不进面板，由引擎状态体系在运行时叠加。
 * <p>
 * 维度键统一使用：HP / STRENGTH / SPIRIT / DEFENSE / RESISTANCE / SPEED。
 */
@Data
public class PetPanelStats {

    /** 维度键常量。 */
    public static final String HP = "HP";
    public static final String STRENGTH = "STRENGTH";
    public static final String SPIRIT = "SPIRIT";
    public static final String DEFENSE = "DEFENSE";
    public static final String RESISTANCE = "RESISTANCE";
    public static final String SPEED = "SPEED";

    /** 最大 HP（最终值）。 */
    private int maxHp;
    /** 力量（最终值）。 */
    private int strength;
    /** 灵力（最终值）。 */
    private int spirit;
    /** 防御（最终值）。 */
    private int defense;
    /** 抗性（最终值）。 */
    private int resistance;
    /** 速度（最终值）。 */
    private int speed;

    /** 各维度分解明细，key 为维度键（HP/STRENGTH/...）。 */
    private Map<String, Breakdown> breakdowns = new LinkedHashMap<>();

    /** 单维度属性分解。 */
    @Data
    public static class Breakdown {
        /** 种族基础 + 个体浮动。 */
        private int base;
        /** 等级固定成长。 */
        private int growth;
        /** 资质成长修正。 */
        private int aptBonus;
        /** 自由属性点贡献。 */
        private int freeBonus;
        /** 最终值。 */
        private int total;

        public Breakdown(int base, int growth, int aptBonus, int freeBonus, int total) {
            this.base = base;
            this.growth = growth;
            this.aptBonus = aptBonus;
            this.freeBonus = freeBonus;
            this.total = total;
        }
    }
}
