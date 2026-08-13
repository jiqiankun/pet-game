package com.petgame.pokedex.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 图鉴种族详情 VO（阶段 8）。
 * <p>
 * 继承列表字段，按研究等级逐级填充已解锁信息；未解锁字段为 null。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PokedexDetailVo extends PokedexEntryVo {

    // ---- Lv.1+ 可见 ----
    /** 种族描述（活动区域暂用种族描述）。 */
    private String description;

    // ---- Lv.2+ 可见 ----
    /** 基础捕获率。 */
    private Double captureRate;

    // ---- Lv.3+ 可见 ----
    /** 种族技能列表（skillId + unlockLevel + signature）。 */
    private List<SkillInfoVo> skills;
    /** 种族被动列表（passiveId + unlockLevel + signature）。 */
    private List<PassiveInfoVo> passives;
    /** 六维基础值（成长倾向）。 */
    private Map<String, Integer> baseStats;

    // ---- Lv.4+ 可见 ----
    /** 稀有技能池名称列表。 */
    private List<String> rareSkills;
    /** 出现区域列表（mapId）。 */
    private List<String> encounterRegions;

    // ---- Lv.5+ 可见 ----
    /** 历史记录。 */
    private PokedexHistoryVo history;
    /** 特殊外观发现次数。 */
    private Integer specialAppearanceCount;
    /** 进化资料占位。 */
    private String evolutionPlaceholder;

    /** 种族技能信息（Lv.3+ 可见）。 */
    @Data
    public static class SkillInfoVo {
        private String skillId;
        private String skillName;
        private int unlockLevel;
        private boolean signature;
    }

    /** 种族被动信息（Lv.3+ 可见）。 */
    @Data
    public static class PassiveInfoVo {
        private String passiveId;
        private String passiveName;
        private int unlockLevel;
        private boolean signature;
    }
}
