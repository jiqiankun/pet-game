package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐 Build 配置模型（阶段 10）。
 * <p>
 * 对应 builds/build-recommendations.yml，按种族推荐加点与技能组合方案。
 * 纯展示，不修改玩家数据。
 */
@Data
@NoArgsConstructor
public class BuildRecommendationConfig {

    /** 推荐方案列表（按种族分组）。 */
    private List<SpeciesBuildConfig> recommendations = new ArrayList<>();

    /**
     * 单个种族的推荐 Build 配置。
     */
    @Data
    @NoArgsConstructor
    public static class SpeciesBuildConfig {

        /** 种族 ID（引用 pets.yml）。 */
        private String speciesId;

        /** 该种族的推荐方案列表（通常 2~3 套）。 */
        private List<BuildConfig> builds = new ArrayList<>();
    }

    /**
     * 单套推荐方案。
     */
    @Data
    @NoArgsConstructor
    public static class BuildConfig {

        /** 方案名称（如"物理爆发"、"生存输出"）。 */
        private String name;

        /** 方案描述。 */
        private String description;

        /** 属性优先级列表（如 [STRENGTH, SPEED, HP]，靠前优先级更高）。 */
        private List<String> statPriority = new ArrayList<>();

        /** 推荐技能 ID 列表（按优先级排序）。 */
        private List<String> skillPriority = new ArrayList<>();
    }
}
