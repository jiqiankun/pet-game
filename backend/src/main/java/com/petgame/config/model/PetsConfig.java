package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物种族配置根对象（pets/pets.yml）。
 * <p>
 * 阶段 5 补齐全部 27 种基础宠物（需求 §59）。
 */
@Data
@NoArgsConstructor
public class PetsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 种族列表。 */
    private List<PetSpeciesConfig> species = new ArrayList<>();
}
