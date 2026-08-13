package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物种族配置（pets/pets.yml）。
 * <p>
 * 全部基础宠物种族的唯一数据来源（需求 §59：27 种，9 属性 × 每属性 3 种）。
 * 玩家存档只保存 speciesId 引用，不复制配置内容（数据分离原则）。
 * 阶段 5 起取代 initial-pets.yml 中的种族快照，成为 {@code registry.getSpecies()} 的统一来源。
 */
@Data
@NoArgsConstructor
public class PetSpeciesConfig {

    /** 种族 ID（如 PET_FIRE_001）。 */
    private String id;

    /** 种族名称（如 烬牙兽）。 */
    private String name;

    /** 属性 ID（9 属性之一）。 */
    private String element;

    /** 种族描述（定位说明）。 */
    private String description;

    /** 稀有度：COMMON / RARE / EPIC / LEGENDARY（需求 §13）。 */
    private String rarity = "COMMON";

    /** 基础捕获率（0~1，捕捉公式基数，需求 §46）。 */
    private double captureRate = 0.5;

    // ---- 种族六维基础（Lv.1 无个体浮动的基础值）----

    private int baseHp;
    private int baseStrength;
    private int baseSpirit;
    private int baseDefense;
    private int baseResistance;
    private int baseSpeed;

    /** 种族技能列表（等级解锁）。 */
    private List<SpeciesSkillSlot> skills = new ArrayList<>();

    /** 稀有技能池（野生遭遇低概率携带，需求「低概率稀有技能」）。 */
    private List<String> rareSkills = new ArrayList<>();

    /** 被动技能 ID 列表。 */
    private List<String> passives = new ArrayList<>();

    /**
     * 种族技能槽位（与初始宠物技能槽位结构一致）。
     */
    @Data
    @NoArgsConstructor
    public static class SpeciesSkillSlot {
        private String skillId;
        /** 默认装备槽位 1~4，null 表示仅学习未装备。 */
        private Integer slot;
        /** 解锁等级，达到该等级自动学会。 */
        private int unlockLevel = 1;
    }
}
