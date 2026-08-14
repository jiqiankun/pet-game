package com.petgame.config;

import com.petgame.battle.calculator.CaptureCalculator;
import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.BossesConfig.BossConfig;
import com.petgame.config.model.BossesConfig.DifficultyConfig;
import com.petgame.config.model.EncountersConfig.EncounterGroup;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.domain.PetGrowthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数值平衡验证测试（阶段 14 数值平衡）。
 * <p>
 * 使用真实 YAML 配置验证关键数值曲线，记录阶段 14 平衡调整后的结论，
 * 防止后续配置改动破坏平衡目标：
 * <ul>
 *   <li>经验曲线：expGrowthFactor / 累计经验 / 各区域经验产出。</li>
 *   <li>捕捉曲线：低血收益 / 异常加成 / clamp 到 [0,1]。</li>
 *   <li>Boss 有效血线与推荐等级关系：主 Boss 血量随区域递增、难度越高血量越高。</li>
 * </ul>
 */
class BalanceVerificationTest {

    private static GameConfigRegistry registry;
    private static PetGrowthService growth;

    @BeforeAll
    static void setup() {
        GameProperties properties = new GameProperties();
        properties.setConfigDir(null); // 只加载 JAR 内部配置
        GameConfigLoader loader = new GameConfigLoader(properties);
        registry = new GameConfigRegistry(loader, new GameConfigValidator());
        registry.init();
        growth = new PetGrowthService(registry);
    }

    // ==================== 经验曲线 ====================

    @Test
    void expCurve_shouldSmoothLateGame() {
        SystemRuleConfig rules = registry.getSystemRules();
        assertEquals(100, rules.getExpBase(), "经验基数应保持 100");
        assertEquals(1.13, rules.getExpGrowthFactor(), 1e-9,
                "阶段 14 平衡：指数因子 1.15→1.13 放缓后期升级曲线");

        // Lv1→Lv50 累计经验应落在合理区间（注释目标约 30 万，给 20~45 万容差）
        int totalTo50 = growth.totalExpToReach(1, 50);
        assertTrue(totalTo50 > 200_000 && totalTo50 < 450_000,
                "Lv1→50 累计经验应约 30 万，实际 " + totalTo50);
    }

    @Test
    void encounterExp_shouldIncreaseWithRegion() {
        List<EncounterGroup> groups = registry.getEncountersConfig().getEncounterGroups();
        double meadow = expOf(groups, "ENCOUNTER_MEADOW");
        double ruins = expOf(groups, "ENCOUNTER_RUINS");
        assertTrue(meadow > 0, "初始区域经验产出必须为正");
        assertTrue(ruins > meadow, "后期区域经验产出应高于初始区域");
        assertEquals(15, meadow, 1e-9, "青草原 expPerLevel 阶段 14 上调为 15");
    }

    private double expOf(List<EncounterGroup> groups, String id) {
        return groups.stream()
                .filter(g -> g.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少遭遇组 " + id))
                .getExpPerLevel();
    }

    // ==================== 捕捉曲线 ====================

    @Test
    void captureCurve_shouldRewardLowHpAndStatus() {
        SystemRuleConfig rules = registry.getSystemRules();
        // 基础捕获率 0.5：满血 vs 低血 vs 低血 + 双异常
        double full = CaptureCalculator.computeCaptureRate(0.5, 1.0, 0, 1.0, 1.0, rules);
        double low = CaptureCalculator.computeCaptureRate(0.5, 0.1, 0, 1.0, 1.0, rules);
        double lowStatus = CaptureCalculator.computeCaptureRate(0.5, 0.1, 2, 1.0, 1.0, rules);
        assertTrue(full < low && low <= lowStatus, "低血 + 异常应显著提升捕获率");
        assertTrue(lowStatus <= 1.0, "捕获率必须 clamp 到 [0,1]");
        // 满血惩罚：0.5 × (1 - 0.5×1.0) = 0.25
        assertEquals(0.25, full, 1e-9);
    }

    // ==================== Boss 有效血线 vs 推荐等级 ====================

    @Test
    void bossNormalEffectiveHp_shouldScaleWithRecommendedLevel() {
        // 5 个主 Boss：推荐等级越高，NORMAL 血量越高（有效血线 / 推荐等级落在合理区间）
        String[] bossIds = {
                "BOSS_MEADOW_GUARDIAN", "BOSS_FOREST_KING", "BOSS_WATERS_GUARDIAN",
                "BOSS_THUNDER_GUARDIAN", "BOSS_RUINS_GUARDIAN"
        };
        for (String bossId : bossIds) {
            BossConfig boss = registry.getBoss(bossId);
            assertNotNull(boss, "Boss 必须存在: " + bossId);
            int hp = boss.getDifficulties().get("NORMAL").getStats().getMaxHp();
            // 阶段 14 加厚后：单位推荐等级血量应保持合理（掉血节奏可控）
            // 各主 Boss 推荐等级对应血量（MEADOW 10/1200、FOREST 15/1500、WATERS 20/1800、
            // THUNDER 20/1650、RUINS 35/3000），均落在 70~140 区间
            double ratio = (double) hp / boss.getRecommendedLevel();
            assertTrue(ratio >= 70 && ratio <= 140,
                    "NORMAL 有效血线/推荐等级应落在合理区间: " + bossId + " hp=" + hp
                            + " 推荐=" + boss.getRecommendedLevel() + " ratio=" + ratio);
        }
    }

    @Test
    void bossDifficulty_shouldIncreaseHp() {
        for (BossConfig boss : registry.getBossesConfig().getBosses()) {
            DifficultyConfig normal = boss.getDifficulties().get("NORMAL");
            DifficultyConfig hard = boss.getDifficulties().get("HARD");
            DifficultyConfig nightmare = boss.getDifficulties().get("NIGHTMARE");
            assertTrue(hard.getStats().getMaxHp() > normal.getStats().getMaxHp(),
                    "HARD 血量应高于 NORMAL: " + boss.getId());
            assertTrue(nightmare.getStats().getMaxHp() > hard.getStats().getMaxHp(),
                    "NIGHTMARE 血量应高于 HARD: " + boss.getId());
        }
    }
}