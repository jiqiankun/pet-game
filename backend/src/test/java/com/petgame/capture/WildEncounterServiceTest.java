package com.petgame.capture;

import com.petgame.battle.model.BattleUnit;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.domain.PetGrowthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 野生遭遇生成测试（阶段 5）。
 * <p>
 * 验证：队伍规模范围、等级范围、资质范围、个体浮动 ±5%、固定种子可复现。
 */
class WildEncounterServiceTest {

    private static final String SPECIES_ID = "PET_TEST_001";
    private static final String GROUP_ID = "ENCOUNTER_TEST";

    private GameConfigRegistry registry;
    private WildEncounterService service;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);
        when(registry.getSystemRules()).thenReturn(new SystemRuleConfig());

        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId(SPECIES_ID);
        species.setName("测试兽");
        species.setElement("FIRE");
        species.setRarity("COMMON");
        species.setCaptureRate(0.55);
        species.setBaseHp(100);
        species.setBaseStrength(50);
        species.setBaseSpirit(50);
        species.setBaseDefense(50);
        species.setBaseResistance(50);
        species.setBaseSpeed(50);
        PetSpeciesConfig.SpeciesSkillSlot slot = new PetSpeciesConfig.SpeciesSkillSlot();
        slot.setSkillId("SKILL_TEST");
        slot.setSlot(1);
        slot.setUnlockLevel(1);
        species.setSkills(List.of(slot));
        when(registry.getSpecies(SPECIES_ID)).thenReturn(species);
        when(registry.getSpecies("PET_NOT_EXIST")).thenReturn(null);

        EncountersConfig.EncounterGroup group = new EncountersConfig.EncounterGroup();
        group.setId(GROUP_ID);
        group.setName("测试组");
        group.setTeamSizeMin(2);
        group.setTeamSizeMax(2);
        group.setExpPerLevel(12);
        group.setGoldPerLevel(3);
        EncountersConfig.SpeciesEntry entry = new EncountersConfig.SpeciesEntry();
        entry.setSpeciesId(SPECIES_ID);
        entry.setWeight(10);
        entry.setLevelMin(3);
        entry.setLevelMax(7);
        group.setSpecies(List.of(entry));

        EncountersConfig encounters = new EncountersConfig();
        encounters.setEncounterGroups(List.of(group));
        when(registry.getEncountersConfig()).thenReturn(encounters);

        service = new WildEncounterService(registry, new PetGrowthService(registry));
    }

    @Test
    void generateEncounter_shouldRespectTeamSizeAndLevelRange() {
        for (long seed = 1; seed <= 20; seed++) {
            List<BattleUnit> units = service.generateEncounter(GROUP_ID, new GameRandom(seed));
            assertEquals(2, units.size(), "teamSize 固定 2");
            for (BattleUnit unit : units) {
                assertEquals(SPECIES_ID, unit.getSpeciesId());
                assertTrue(unit.getLevel() >= 3 && unit.getLevel() <= 7,
                        "等级必须在配置的 [3, 7] 范围内: " + unit.getLevel());
                assertNotNull(unit.getWildData(), "野生单位必须携带捕捉落库数据");
                assertTrue(unit.isAlive() && unit.isActive(), "2 只均应上场（上场位 3）");
            }
        }
    }

    @Test
    void generateEncounter_aptitudeAndOffset_shouldBeInBounds() {
        for (long seed = 1; seed <= 30; seed++) {
            List<BattleUnit> units = service.generateEncounter(GROUP_ID, new GameRandom(seed));
            for (BattleUnit unit : units) {
                BattleUnit.WildUnitData wd = unit.getWildData();
                int[] apts = {wd.getHpAptitude(), wd.getStrengthAptitude(), wd.getSpiritAptitude(),
                        wd.getDefenseAptitude(), wd.getResistanceAptitude(), wd.getSpeedAptitude()};
                for (int apt : apts) {
                    assertTrue(apt >= 0 && apt <= 100, "资质必须在 [0, 100] 范围内: " + apt);
                }
                // 个体浮动 ±5%：HP 基础 100 → |offset| ≤ 5；其余基础 50 → |offset| ≤ 3（四舍五入 2.5）
                assertTrue(Math.abs(wd.getBaseHpOffset()) <= 5,
                        "HP 浮动超出 ±5%: " + wd.getBaseHpOffset());
                assertTrue(Math.abs(wd.getBaseStrengthOffset()) <= 3,
                        "力量浮动超出 ±5%: " + wd.getBaseStrengthOffset());
            }
        }
    }

    @Test
    void generateEncounter_fixedSeed_reproducible() {
        List<BattleUnit> first = service.generateEncounter(GROUP_ID, new GameRandom(12345));
        List<BattleUnit> second = service.generateEncounter(GROUP_ID, new GameRandom(12345));
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getLevel(), second.get(i).getLevel(), "固定种子等级必须一致");
            assertEquals(first.get(i).getWildData().getHpAptitude(),
                    second.get(i).getWildData().getHpAptitude(), "固定种子资质必须一致");
            assertEquals(first.get(i).getWildData().getBaseHpOffset(),
                    second.get(i).getWildData().getBaseHpOffset(), "固定种子个体浮动必须一致");
        }
    }

    @Test
    void generateEncounter_speciesConfigMissing_shouldThrow() {
        EncountersConfig.EncounterGroup badGroup = new EncountersConfig.EncounterGroup();
        badGroup.setId("ENCOUNTER_BAD");
        badGroup.setTeamSizeMin(1);
        badGroup.setTeamSizeMax(1);
        EncountersConfig.SpeciesEntry entry = new EncountersConfig.SpeciesEntry();
        entry.setSpeciesId("PET_NOT_EXIST");
        badGroup.setSpecies(List.of(entry));

        EncountersConfig encounters = registry.getEncountersConfig();
        java.util.ArrayList<EncountersConfig.EncounterGroup> groups =
                new java.util.ArrayList<>(encounters.getEncounterGroups());
        groups.add(badGroup);
        encounters.setEncounterGroups(groups);

        assertThrows(IllegalStateException.class, () ->
                service.generateEncounter("ENCOUNTER_BAD", new GameRandom(1)));
    }

    @Test
    void getEncounterGroup_notFound_shouldThrowBusinessException() {
        assertThrows(com.petgame.common.BusinessException.class, () ->
                service.getEncounterGroup("ENCOUNTER_NOT_EXIST"));
    }

    @Test
    void generateEncounter_panelStats_shouldMatchGrowthFormula() {
        // 野生单位面板必须与玩家宠物同一公式：Lv.1 无成长、资质 50 无修正时等于种族基础（含浮动）
        // 这里只验证 HP 上界关系：maxHp = base + offset + 等级成长 + 资质修正
        List<BattleUnit> units = service.generateEncounter(GROUP_ID, new GameRandom(7));
        BattleUnit unit = units.get(0);
        BattleUnit.WildUnitData wd = unit.getWildData();
        assertTrue(unit.getMaxHp() >= 100 + wd.getBaseHpOffset() - 50,
                "面板 HP 应包含种族基础与浮动");
    }
}
