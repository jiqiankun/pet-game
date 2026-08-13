package com.petgame.config;

import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 3 配置全量加载测试。
 * <p>
 * 使用真实 YAML（skills/skills.yml、statuses/statuses.yml、test-battle.yml）
 * 走完整的「加载 → 校验 → 建索引」启动链路，验证配置间引用一致性。
 */
class GameConfigPhase3LoadTest {

    @Test
    void init_shouldLoadAndValidateAllPhase3Configs() {
        GameProperties properties = new GameProperties();
        properties.setConfigDir(null); // 只加载 JAR 内部配置
        GameConfigLoader loader = new GameConfigLoader(properties);
        GameConfigRegistry registry = new GameConfigRegistry(loader, new GameConfigValidator());

        assertDoesNotThrow(registry::init, "阶段 3 配置必须通过启动校验");

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

        // 初始宠物技能/被动引用必须可解析
        registry.getInitialPetsConfig().getInitialPets().forEach(pet -> {
            pet.getSkills().forEach(slot ->
                    assertNotNull(registry.getSkill(slot.getSkillId()),
                            "初始宠物技能引用缺失: " + slot.getSkillId()));
            pet.getPassives().forEach(id ->
                    assertNotNull(registry.getPassive(id), "初始宠物被动引用缺失: " + id));
        });
    }
}
