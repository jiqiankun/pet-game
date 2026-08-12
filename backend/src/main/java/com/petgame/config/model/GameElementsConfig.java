package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性体系配置根对象。
 * <p>
 * 对应 elements.yml，定义 9 种属性及其克制关系。
 */
@Data
@NoArgsConstructor
public class GameElementsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 全部属性定义列表。 */
    private List<GameElementConfig> elements = new ArrayList<>();

    /** 克制关系列表（单向声明，对称关系如光⇄暗需两条）。 */
    private List<ElementAdvantageConfig> advantages = new ArrayList<>();
}
