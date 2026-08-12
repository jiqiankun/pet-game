package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 属性克制关系定义。
 * <p>
 * attacker 克制 defender，倍率为全局 advantageMultiplier。
 */
@Data
@NoArgsConstructor
public class ElementAdvantageConfig {

    /** 攻击方属性 ID。 */
    private String attacker;

    /** 防御方属性 ID。 */
    private String defender;
}
