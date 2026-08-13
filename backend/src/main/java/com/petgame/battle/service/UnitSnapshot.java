package com.petgame.battle.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗单位快照（返回给前端的单位状态视图）。
 * <p>
 * 前端只依赖快照渲染，不持有任何战斗计算逻辑。
 */
@Data
public class UnitSnapshot {

    private String unitId;
    private String name;
    private String element;
    private int level;

    private int maxHp;
    private int currentHp;
    private int shield;

    private int strength;
    private int spirit;
    private int defense;
    private int resistance;
    private int speed;

    private boolean alive;
    private boolean active;
    private int position;
    private boolean defending;

    /** 蓄力中（chargingSkillId 非空）。 */
    private boolean charging;
    private String chargingSkillId;
    private int chargeRemaining;

    private List<String> skillIds = new ArrayList<>();

    /** 技能冷却：skillId → 剩余回合数。 */
    private Map<String, Integer> cooldowns = new LinkedHashMap<>();

    private List<StatusView> statuses = new ArrayList<>();

    /**
     * 状态展示视图。
     */
    @Data
    @AllArgsConstructor
    public static class StatusView {
        private String statusId;
        private String name;
        private String category;
        private int remainingTurns;
    }
}
