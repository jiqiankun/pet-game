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
        private Integer slot;
    }
}
