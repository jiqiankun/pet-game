package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个宠物属性定义。
 * <p>
 * 对应 elements.yml 中 elements 列表的每一项。
 */
@Data
@NoArgsConstructor
public class GameElementConfig {

    /** 属性唯一标识（如 METAL、WOOD），用作全局引用键。 */
    private String id;

    /** 属性中文名称。 */
    private String name;
}
