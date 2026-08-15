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
    private int actualLevel;
    private int effectiveLevel;

    /** 展示资源类型：PET=宠物、BOSS=Boss 核心、null=无资源（测试敌人）。 */
    private String artType;

    /** 展示资源 ID：PET 对应 speciesId，BOSS 对应 Boss ID；无资源时为 null。 */
    private String artId;

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

    /** 是否已被捕捉（野生战斗）。 */
    private boolean captured;

    /** 是否精英个体（阶段 10，野生战斗前端展示用）。 */
    private boolean elite;

    /** 蓄力中（chargingSkillId 非空）。 */
    private boolean charging;
    private String chargingSkillId;
    private int chargeRemaining;

    private List<String> skillIds = new ArrayList<>();

    /** 技能冷却：skillId → 剩余回合数。 */
    private Map<String, Integer> cooldowns = new LinkedHashMap<>();

    private List<StatusView> statuses = new ArrayList<>();

    /**
     * 状态展示视图（REV-015：含叠层与震慑标识）。
     */
    @Data
    @AllArgsConstructor
    public static class StatusView {
        private String statusId;
        private String name;
        private String category;
        private int remainingTurns;
        /** 当前层数（叠层状态，默认 1）。 */
        private int stack;
        /** 是否捕获震慑（安全捕捉窗口标识，需求 §142）。 */
        private boolean captureStun;
    }
}
