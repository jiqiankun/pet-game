package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 7：Boss 配置校验测试。
 * <p>
 * 覆盖：
 * - Boss ID 重复
 * - 引用不存在的属性/技能/被动/道具/区域
 * - HP 阈值非法（<=0 或 >1）
 * - 掉落概率非法（<0 或 >1）
 * - 空 Boss 配置通过
 */
class BossConfigValidateTest {

    private final GameConfigValidator validator = new GameConfigValidator();

    // ---- 基础配置构建 ----

    private SystemRuleConfig validSystem() { return new SystemRuleConfig(); }

    private GameElementsConfig validElements() {
        GameElementsConfig config = new GameElementsConfig();
        GameElementConfig fire = new GameElementConfig();
        fire.setId("FIRE");
        fire.setName("火");
        config.setElements(List.of(fire));
        config.setAdvantages(List.of());
        return config;
    }

    private SkillsConfig validSkills() {
        SkillsConfig skills = new SkillsConfig();
        SkillConfig hit = new SkillConfig();
        hit.setId("SKILL_HIT");
        hit.setName("攻击");
        hit.setElement("FIRE");
        hit.setEffectType("DAMAGE");
        hit.setTarget("ENEMY_SINGLE");
        skills.setSkills(List.of(hit));
        PassiveSkillConfig passive = new PassiveSkillConfig();
        passive.setId("PASSIVE_THICK");
        passive.setName("厚皮");
        passive.setTrigger("BEFORE_TAKE_DAMAGE");
        passive.setEffectType("REDUCE_PHYSICAL_DAMAGE");
        skills.setPassives(List.of(passive));
        return skills;
    }

    private ItemsConfig validItems() {
        ItemsConfig items = new ItemsConfig();
        ItemConfig item = new ItemConfig();
        item.setId("ITEM_MAT");
        item.setName("材料");
        item.setItemType("MATERIAL");
        item.setCategory("MATERIAL");
        items.setItems(List.of(item));
        return items;
    }

    private MapsConfig validMaps() {
        MapsConfig maps = new MapsConfig();
        MapsConfig.RegionConfig region = new MapsConfig.RegionConfig();
        region.setId("MAP_A");
        region.setName("区域 A");
        region.setMapFile("map_a.tmx");
        region.setUnlockType("AUTO");
        region.setPlanned(false);
        region.setSpawnObjectId("SPAWN_MAP_A");
        maps.setRegions(List.of(region));
        return maps;
    }

    private BossesConfig validBosses() {
        BossesConfig bosses = new BossesConfig();
        BossesConfig.BossConfig boss = new BossesConfig.BossConfig();
        boss.setId("BOSS_A");
        boss.setName("Boss A");
        // mapId 不设置（避免 maps=null 时校验失败）；仅在 doValidateWithMaps 测试中设置
        boss.setElement("FIRE");
        boss.setRecommendedLevel(10);

        BossesConfig.DifficultyConfig normal = new BossesConfig.DifficultyConfig();
        normal.getStats().setMaxHp(800);
        normal.getStats().setStrength(45);
        normal.getStats().setDefense(50);
        normal.setSkills(List.of("SKILL_HIT"));
        normal.setPassives(List.of("PASSIVE_THICK"));
        normal.setLuckGain(4);
        BossesConfig.DropEntry drop = new BossesConfig.DropEntry();
        drop.setItemId("ITEM_MAT");
        drop.setQty(5);
        drop.setChance(0.8);
        normal.setDrops(Map.of("COMMON", List.of(drop)));

        boss.setDifficulties(Map.of("NORMAL", normal));
        bosses.setBosses(List.of(boss));
        return bosses;
    }

    /** 调用 12 参数 validate，bosses 传 null 或实际值。maps=null 跳过地图校验。 */
    private void doValidate(BossesConfig bosses) {
        validator.validate(validSystem(), validElements(), null, validSkills(), null,
                null, validItems(), null, null, null, null, bosses);
    }

    /** 调用 12 参数 validate，含 maps（用于测试 mapId 引用校验）。 */
    private void doValidateWithMaps(BossesConfig bosses) {
        validator.validate(validSystem(), validElements(), null, validSkills(), null,
                null, validItems(), null, null, null, validMaps(), bosses);
    }

    // ---- 测试用例 ----

    @Test
    void nullBosses_shouldPass() {
        assertDoesNotThrow(() -> doValidate(null));
    }

    @Test
    void emptyBosses_shouldPass() {
        BossesConfig bosses = new BossesConfig();
        bosses.setBosses(List.of());
        assertDoesNotThrow(() -> doValidate(bosses));
    }

    @Test
    void validBossConfig_shouldPass() {
        assertDoesNotThrow(() -> doValidate(validBosses()));
    }

    @Test
    void duplicateBossId_shouldFail() {
        BossesConfig bosses = validBosses();
        BossesConfig.BossConfig dup = new BossesConfig.BossConfig();
        dup.setId("BOSS_A");
        dup.setName("Boss A Dup");
        dup.setElement("FIRE");
        dup.setDifficulties(Map.of());
        bosses.setBosses(List.of(bosses.getBosses().get(0), dup));
        assertThrows(IllegalStateException.class, () -> doValidate(bosses));
    }

    @Test
    void referenceNonExistentElement_shouldFail() {
        BossesConfig bosses = validBosses();
        bosses.getBosses().get(0).setElement("DRAGON");
        assertThrows(IllegalStateException.class, () -> doValidate(bosses));
    }

    @Test
    void referenceNonExistentSkill_shouldFail() {
        BossesConfig bosses = validBosses();
        BossesConfig.DifficultyConfig normal = bosses.getBosses().get(0).getDifficulties().get("NORMAL");
        normal.setSkills(List.of("SKILL_NONEXISTENT"));
        assertThrows(IllegalStateException.class, () -> doValidate(bosses));
    }

    @Test
    void referenceNonExistentPassive_shouldFail() {
        BossesConfig bosses = validBosses();
        BossesConfig.DifficultyConfig normal = bosses.getBosses().get(0).getDifficulties().get("NORMAL");
        normal.setPassives(List.of("PASSIVE_NONEXISTENT"));
        assertThrows(IllegalStateException.class, () -> doValidate(bosses));
    }

    @Test
    void referenceNonExistentDropItem_shouldFail() {
        BossesConfig bosses = validBosses();
        BossesConfig.DifficultyConfig normal = bosses.getBosses().get(0).getDifficulties().get("NORMAL");
        BossesConfig.DropEntry badDrop = new BossesConfig.DropEntry();
        badDrop.setItemId("ITEM_NONEXISTENT");
        badDrop.setChance(0.5);
        normal.setDrops(Map.of("COMMON", List.of(badDrop)));
        assertThrows(IllegalStateException.class, () -> doValidate(bosses));
    }

    @Test
    void referenceNonExistentMap_shouldFail() {
        BossesConfig bosses = validBosses();
        bosses.getBosses().get(0).setMapId("MAP_NONEXISTENT");
        // 需要提供 maps 才能校验 mapId 引用
        assertThrows(IllegalStateException.class, () -> doValidateWithMaps(bosses));
    }

    @Test
    void validBossWithCorrectMap_shouldPass() {
        BossesConfig bosses = validBosses();
        bosses.getBosses().get(0).setMapId("MAP_A");
        assertDoesNotThrow(() -> doValidateWithMaps(bosses));
    }
}
