package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始宠物配置根对象。
 * <p>
 * 对应 initial-pets.yml，定义新游戏三选一的初始宠物选项及初始资源。
 */
@Data
@NoArgsConstructor
public class InitialPetsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 初始金币。 */
    private int initialGold = 500;

    /** 初始经验池。 */
    private int initialExpPool = 0;

    /** 初始地图 ID。 */
    private String initialMapId = "MAP_START_VILLAGE";

    /** 初始宠物选项列表（三选一）。 */
    private List<InitialPetOption> initialPets = new ArrayList<>();

    /**
     * 单个初始宠物选项。
     */
    @Data
    @NoArgsConstructor
    public static class InitialPetOption {
        private String speciesId;
        private String name;
        private String element;
        private String description;
        /** 种族稀有度（COMMON/RARE/EPIC/LEGENDARY，需求 §13）。初始宠物均为 COMMON。 */
        private String rarity = "COMMON";
        private int baseHp;
        private int baseStrength;
        private int baseSpirit;
        private int baseDefense;
        private int baseResistance;
        private int baseSpeed;
        private int aptitudeHp;
        private int aptitudeStrength;
        private int aptitudeSpirit;
        private int aptitudeDefense;
        private int aptitudeResistance;
        private int aptitudeSpeed;
        private List<InitialSkillSlot> skills = new ArrayList<>();
        /** 被动技能 ID 列表（可选，阶段 3 起支持）。 */
        private List<String> passives = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class InitialSkillSlot {
        private String skillId;
        /** 装备槽位 1~4，null 表示仅学习未装备。 */
        private Integer slot;
        /** 解锁等级，达到该等级自动学会（需求 §23 等级解锁）。初始技能默认 1。 */
        private int unlockLevel = 1;
    }
}
