package com.petgame.config;

import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.PetSpeciesConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置全量加载测试（阶段 3 建立，阶段 5 扩展）。
 * <p>
 * 使用真实 YAML 走完整的「加载 → 校验 → 建索引」启动链路，验证配置间引用一致性：
 * 技能/状态/测试战斗（阶段 3）、道具（阶段 4）、
 * 宠物种族/野生遭遇/放生礼物/捕捉球（阶段 5）。
 */
class GameConfigPhase3LoadTest {

    @Test
    void init_shouldLoadAndValidateAllConfigs() {
        GameProperties properties = new GameProperties();
        properties.setConfigDir(null); // 只加载 JAR 内部配置
        GameConfigLoader loader = new GameConfigLoader(properties);
        GameConfigRegistry registry = new GameConfigRegistry(loader, new GameConfigValidator());

        assertDoesNotThrow(registry::init, "全部配置必须通过启动校验");

        // 技能与被动
        assertNotNull(registry.getSkill("SKILL_FIRE_CLAW"), "初始宠物技能必须存在");
        assertNull(registry.getSkill("SKILL_NOT_EXIST"));
        assertFalse(registry.getSkillsConfig().getSkills().isEmpty());
        assertNotNull(registry.getPassive("PASSIVE_UNYIELDING"), "被动配置必须注册");

        // 状态与联动
        assertNotNull(registry.getStatus("BURN"), "灼烧状态必须存在");
        assertFalse(registry.getStatusIndex().isEmpty());
        assertFalse(registry.getSynergies().isEmpty(), "状态联动规则必须配置化");

        // 测试战斗
        assertEquals(3, registry.getTestBattleConfig().getEnemies().size(), "测试战斗应为 3 个敌方单位");

        // ---- 阶段 5：宠物种族 ----
        assertEquals(27, registry.getAllSpecies().size(), "第一阶段固定 27 种基础宠物");
        Map<String, Integer> rarityCount = new HashMap<>();
        for (PetSpeciesConfig species : registry.getAllSpecies()) {
            rarityCount.merge(species.getRarity(), 1, Integer::sum);
            // 种族技能与被动引用必须可解析
            species.getSkills().forEach(slot ->
                    assertNotNull(registry.getSkill(slot.getSkillId()),
                            "种族技能引用缺失: " + slot.getSkillId()));
            species.getPassives().forEach(slot ->
                    assertNotNull(registry.getPassive(slot.getPassiveId()),
                            "种族被动引用缺失: " + slot.getPassiveId()));
        }
        assertEquals(12, rarityCount.getOrDefault("COMMON", 0), "稀有度分布 COMMON=12");
        assertEquals(9, rarityCount.getOrDefault("RARE", 0), "稀有度分布 RARE=9");
        assertEquals(5, rarityCount.getOrDefault("EPIC", 0), "稀有度分布 EPIC=5");
        assertEquals(1, rarityCount.getOrDefault("LEGENDARY", 0), "稀有度分布 LEGENDARY=1");

        // 初始宠物 speciesId 必须可解析
        registry.getInitialPetsConfig().getInitialPets().forEach(opt ->
                assertNotNull(registry.getSpecies(opt.getSpeciesId()),
                        "初始宠物种族引用缺失: " + opt.getSpeciesId()));

        // ---- 阶段 5：三档捕捉球 ----
        for (String ballId : new String[]{
                "ITEM_CAPTURE_BALL_NORMAL", "ITEM_CAPTURE_BALL_GREAT", "ITEM_CAPTURE_BALL_ULTRA"}) {
            assertNotNull(registry.getItem(ballId), "捕捉球必须存在: " + ballId);
            assertEquals("CAPTURE_BALL", registry.getItem(ballId).getItemType());
        }
        assertTrue(registry.getItem("ITEM_CAPTURE_BALL_GREAT").getValue()
                > registry.getItem("ITEM_CAPTURE_BALL_NORMAL").getValue(), "高级球倍率高于普通球");
        assertTrue(registry.getItem("ITEM_CAPTURE_BALL_ULTRA").getValue()
                > registry.getItem("ITEM_CAPTURE_BALL_GREAT").getValue(), "特级球倍率高于高级球");

        // ---- 阶段 5：野生遭遇与放生礼物 ----
        assertFalse(registry.getEncountersConfig().getEncounterGroups().isEmpty(),
                "遭遇组必须配置");
        assertNotNull(registry.getEncountersConfig().getEncounterGroups().stream()
                        .filter(g -> "ENCOUNTER_GENERAL".equals(g.getId())).findFirst().orElse(null),
                "阶段 5 通用遭遇组必须存在");
        assertFalse(registry.getReleaseGiftsConfig().getGifts().isEmpty(), "放生礼物池必须配置");
    }
}
