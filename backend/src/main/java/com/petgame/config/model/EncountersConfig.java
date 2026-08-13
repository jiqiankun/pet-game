package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 野生遭遇配置根对象（encounters/encounters.yml）。
 * <p>
 * 阶段 5 简化遭遇入口使用的刷新组配置：按权重生成野生阵容（种族、等级范围）。
 * 地图只声明「哪里用哪个刷新组」（阶段 6），宠物与概率全部在此配置中（数据分离原则）。
 */
@Data
@NoArgsConstructor
public class EncountersConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 刷新组列表。 */
    private List<EncounterGroup> encounterGroups = new ArrayList<>();

    /**
     * 单个刷新组（一片区域可能遭遇的野生宠物池）。
     */
    @Data
    @NoArgsConstructor
    public static class EncounterGroup {
        /** 刷新组 ID。 */
        private String id;

        /** 名称。 */
        private String name;

        /** 野生阵容数量范围（最小/最大只）。 */
        private int teamSizeMin = 1;
        private int teamSizeMax = 1;

        /** 种族池（按权重抽取，可重复抽到同一种）。 */
        private List<SpeciesEntry> species = new ArrayList<>();

        /** 战斗奖励：每级经验基础值（最终 = expPerLevel × 敌等级 × 稀有度系数）。 */
        private double expPerLevel = 0;

        /** 战斗奖励：每级金币基础值（最终 = goldPerLevel × 敌等级 × 稀有度系数）。 */
        private double goldPerLevel = 0;
    }

    /**
     * 刷新组内的种族条目。
     */
    @Data
    @NoArgsConstructor
    public static class SpeciesEntry {
        /** 种族 ID（引用 pets 配置）。 */
        private String speciesId;

        /** 抽取权重（>0）。 */
        private int weight = 1;

        /** 生成等级范围（含边界）。 */
        private int levelMin = 1;
        private int levelMax = 1;
    }
}
