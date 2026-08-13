package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能配置根对象（skills/skills.yml）。
 */
@Data
@NoArgsConstructor
public class SkillsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 技能列表。 */
    private List<SkillConfig> skills = new ArrayList<>();

    /** 被动技能列表（配置驱动的被动框架）。 */
    private List<PassiveSkillConfig> passives = new ArrayList<>();
}
