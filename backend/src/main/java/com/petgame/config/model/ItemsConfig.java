package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 道具配置根对象（items/items.yml）。
 */
@Data
@NoArgsConstructor
public class ItemsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 道具列表。 */
    private List<ItemConfig> items = new ArrayList<>();
}
