package com.petgame.pokedex.vo;

import lombok.Data;

import java.util.List;

/**
 * 图鉴历史记录 VO（阶段 8，Lv.5+ 可见）。
 */
@Data
public class PokedexHistoryVo {
    private int totalCaptures;
    private int totalDefeats;
    private int eliteEncounters;
    private int specialAppearances;
    private int bestCombinedAptitude;
    private int bestHp;
    private int bestStrength;
    private int bestSpirit;
    private int bestDefense;
    private int bestResistance;
    private int bestSpeed;
    private List<String> discoveredRareSkills;
}
