package com.petgame.pet.service;

import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物详情视图（阶段 4）。
 * <p>
 * 聚合宠物存档、种族配置摘要、面板属性分解、已学技能、可学技能、经验池、自由点数等信息，
 * 供前端宠物详情页一次拉取渲染。
 */
@Data
public class PetDetail {

    /** 宠物存档实体。 */
    private PlayerPetEntity pet;

    /** 种族配置摘要（不含技能列表，避免冗余）。 */
    private SpeciesView species;

    /** 面板属性（含分解）。 */
    private PetPanelStats panelStats;

    /** 已学习技能列表（含槽位）。 */
    private List<LearnedSkillView> learnedSkills = new ArrayList<>();

    /** 未来可解锁的技能列表（unlockLevel > currentLevel），按解锁等级升序。 */
    private List<AvailableSkillView> availableSkills = new ArrayList<>();

    /** 玩家公共经验池当前值。 */
    private Integer expPool;

    /** 已消耗自由点数（按需求 §20 转换表折算：速度每点次 2 点，其余 1 点）。 */
    private Integer allocatedFreePoints;

    /** 剩余可分配自由点数 = 已获得 - 已消耗。 */
    private Integer freePointsAvailable;

    /** 升到下一级所需经验（已达上限则返回 0）。 */
    private Integer expToNextLevel;

    /** 种族配置摘要。 */
    @Data
    public static class SpeciesView {
        private String speciesId;
        private String name;
        private String element;
        private String rarity;
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
    }

    /** 已学技能视图。 */
    @Data
    public static class LearnedSkillView {
        private String skillId;
        private String name;
        private String element;
        private String damageType;
        private String effectType;
        private int cooldown;
        /** 装备槽位 1~4，null 表示已学习但未装备。 */
        private Integer slot;
        private String sourceType;
    }

    /** 待解锁技能视图。 */
    @Data
    public static class AvailableSkillView {
        private String skillId;
        private String name;
        private String element;
        private int unlockLevel;
    }
}
