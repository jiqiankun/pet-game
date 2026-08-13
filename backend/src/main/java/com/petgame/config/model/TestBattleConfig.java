package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试战斗配置（test-battle.yml）。
 * <p>
 * 阶段 3 验收用：固定敌方阵容。敌方单位数据全部配置化，
 * 战斗引擎以与玩家宠物完全相同的路径构建与结算（同一 BattleEngine）。
 */
@Data
@NoArgsConstructor
public class TestBattleConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 敌方阵容。 */
    private List<TestEnemyUnit> enemies = new ArrayList<>();

    /**
     * 测试战斗敌方单位。
     */
    @Data
    @NoArgsConstructor
    public static class TestEnemyUnit {

        /** 单位 ID（战斗内唯一），如 ENEMY_TEST_1。 */
        private String unitId;

        /** 名称。 */
        private String name;

        /** 属性（9 属性之一）。 */
        private String element;

        /** 等级。 */
        private int level = 1;

        /** 最大 HP。 */
        private int maxHp = 50;

        /** 力量。 */
        private int strength = 40;

        /** 灵力。 */
        private int spirit = 40;

        /** 防御。 */
        private int defense = 40;

        /** 抗性。 */
        private int resistance = 40;

        /** 速度。 */
        private int speed = 40;

        /** 技能 ID 列表（引用技能配置）。 */
        private List<String> skillIds = new ArrayList<>();

        /** 被动技能 ID 列表（引用被动配置，可为空）。 */
        private List<String> passiveIds = new ArrayList<>();
    }
}
