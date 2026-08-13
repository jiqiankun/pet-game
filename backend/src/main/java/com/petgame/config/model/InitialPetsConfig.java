package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始宠物配置根对象。
 * <p>
 * 对应 initial-pets.yml，定义新游戏三选一的初始宠物选项、初始资源与初始道具。
 * 阶段 5 起种族数据唯一来源为 pets/pets.yml，本配置仅声明「选哪个种族 + 资质覆盖」；
 * 初始宠物资质至少 A（各项 >= 80，启动校验强制）。
 */
@Data
@NoArgsConstructor
public class InitialPetsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 初始金币。 */
    private int initialGold = 500;

    /** 初始经验池。 */
    private int initialExpPool = 0;

    /** 初始地图 ID。 */
    private String initialMapId = "MAP_START_VILLAGE";

    /** 初始道具（阶段 5：新游戏赠送三档捕捉球，用户裁决）。 */
    private List<InitialItemEntry> initialItems = new ArrayList<>();

    /** 初始宠物选项列表（三选一）。 */
    private List<InitialPetOption> initialPets = new ArrayList<>();

    /**
     * 单个初始宠物选项（种族数据从 pets 配置读取，此处仅声明物种与资质覆盖）。
     */
    @Data
    @NoArgsConstructor
    public static class InitialPetOption {
        /** 种族 ID（引用 pets 配置）。 */
        private String speciesId;
        /** 资质覆盖（初始宠物至少 A 级，各项 >= 80）。 */
        private int aptitudeHp;
        private int aptitudeStrength;
        private int aptitudeSpirit;
        private int aptitudeDefense;
        private int aptitudeResistance;
        private int aptitudeSpeed;
    }

    /**
     * 初始道具条目。
     */
    @Data
    @NoArgsConstructor
    public static class InitialItemEntry {
        /** 道具 ID（引用 items 配置）。 */
        private String itemId;
        /** 赠送数量。 */
        private int quantity = 1;
    }
}
