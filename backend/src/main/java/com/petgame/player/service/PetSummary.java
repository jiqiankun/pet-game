package com.petgame.player.service;

import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物摘要视图（Bootstrap 聚合用）。
 * <p>
 * 为首页提供每只宠物的关键信息：存档实体、种族名称、面板属性（含分解）、已装备技能。
 * 比 {@link com.petgame.pet.service.PetDetail} 轻量，不含升级预览、可学技能等详情页专用数据。
 */
@Data
public class PetSummary {

    /** 宠物存档实体。 */
    private PlayerPetEntity pet;

    /** 种族名称。 */
    private String speciesName;

    /** 属性 ID。 */
    private String element;

    /** 稀有度。 */
    private String rarity;

    /** 面板属性（含分解）。 */
    private PetPanelStats panelStats;

    /** 已装备技能列表（slot 1~4），按槽位升序。 */
    private List<EquippedSkillView> equippedSkills = new ArrayList<>();

    /** 装备技能视图。 */
    @Data
    public static class EquippedSkillView {
        private String skillId;
        private String name;
        private Integer slot;
    }
}
