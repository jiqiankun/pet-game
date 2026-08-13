package com.petgame.pokedex.vo;

import lombok.Data;

/**
 * 图鉴列表条目 VO（阶段 8）。
 */
@Data
public class PokedexEntryVo {
    /** 种族 ID。 */
    private String speciesId;
    /** 种族名称（未解锁时 null）。 */
    private String name;
    /** 属性 ID（未解锁时 null）。 */
    private String element;
    /** 稀有度（Lv.2+ 可见）。 */
    private String rarity;
    /** 研究等级 0~5。 */
    private int researchLevel;
    /** 累计研究值。 */
    private int researchPoints;
    /** 是否已发现。 */
    private boolean seen;
    /** 是否已捕获。 */
    private boolean caught;
}
