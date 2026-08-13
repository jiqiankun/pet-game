package com.petgame.pokedex.vo;

import lombok.Data;

/**
 * Lv.5 野外识别结果 VO（阶段 8）。
 */
@Data
public class WildIdentificationVo {
    private String speciesId;
    /** 资质预估等级标签（如 S/A/B/C/D）。 */
    private String gradeLabel;
}
