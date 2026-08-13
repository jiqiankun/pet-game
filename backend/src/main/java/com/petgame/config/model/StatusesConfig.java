package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态体系配置根对象（statuses/statuses.yml）。
 * <p>
 * 包含状态定义与有限的状态联动（synergies）规则。
 * 联动保持有限且配置化，不做复杂元素反应网络。
 */
@Data
@NoArgsConstructor
public class StatusesConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 状态列表。 */
    private List<StatusEffectConfig> statuses = new ArrayList<>();

    /** 状态联动规则（有限、配置化）。 */
    private List<StatusSynergyConfig> synergies = new ArrayList<>();

    /**
     * 状态联动规则。
     * <p>
     * 例：目标处于浸湿 + 受到雷属性技能 = 伤害提高。
     */
    @Data
    @NoArgsConstructor
    public static class StatusSynergyConfig {

        /** 目标需携带的状态 ID。 */
        private String requiredStatus;

        /** 触发联动的技能属性。 */
        private String skillElement;

        /** 伤害倍率。 */
        private double damageMultiplier = 1.0;

        /** 联动说明。 */
        private String description;
    }
}
