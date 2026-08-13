package com.petgame.pokedex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 图鉴种族历史记录（阶段 8，复合主键 save_id + species_id）。
 * <p>
 * 放生不清除。记录捕获总数、击败总数、精英遭遇、特殊外观、历史最高资质等。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_pokedex_history")
public class PokedexHistoryEntity {
    private String saveId;
    private String speciesId;
    private Integer totalCaptures;
    private Integer totalDefeats;
    private Integer eliteEncounters;
    private Integer specialAppearances;
    private Integer bestCombinedAptitude;
    private Integer bestHp;
    private Integer bestStrength;
    private Integer bestSpirit;
    private Integer bestDefense;
    private Integer bestResistance;
    private Integer bestSpeed;
    /** 逗号分隔的稀有技能 ID 列表。 */
    private String discoveredRareSkills;
}
