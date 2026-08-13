package com.petgame.pet;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.petgame.pet.PetGrowthTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PetGrowthService 单元测试（阶段 4 验收标准）。
 * <p>
 * 覆盖：面板属性公式（种族基础 + 个体浮动 + 等级成长 + 资质修正 + 自由点数）、
 * Lv.1 资质无影响、资质差异随等级逐渐体现、升级经验公式、自由点数（每级 3 + 稀有度每 10 级额外）、
 * 加点成本（速度 2 / 其他 1）、洗点返还、技能解锁、升级预览。
 */
class PetGrowthServiceTest {

    private PetGrowthService service(List<PetSpeciesConfig> pets) {
        return new PetGrowthService(buildRegistry(pets));
    }

    private PlayerPetEntity pet(int level, int aptitude) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(1L);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId("SPEC_TEST");
        pet.setLevel(level);
        pet.setCapturedLevel(1);
        pet.setHpAptitude(aptitude);
        pet.setStrengthAptitude(aptitude);
        pet.setSpiritAptitude(aptitude);
        pet.setDefenseAptitude(aptitude);
        pet.setResistanceAptitude(aptitude);
        pet.setSpeedAptitude(aptitude);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(100);
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        return pet;
    }

    // ==================== 面板属性公式 ====================

    @Test
    void panelStats_level1_aptitudeDoesNotAffect() {
        // Lv.1：资质不影响（levelBonus=0，资质修正基于等级成长 → 0）
        PetSpeciesConfig species =
                species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity petAvg = pet(1, 50);
        PlayerPetEntity petHigh = pet(1, 100);
        PlayerPetEntity petLow = pet(1, 0);

        PetPanelStats avg = svc.computePanelStats(petAvg, species);
        PetPanelStats high = svc.computePanelStats(petHigh, species);
        PetPanelStats low = svc.computePanelStats(petLow, species);

        // Lv.1 三种资质最终值完全相同（资质修正项为 0）
        assertEquals(avg.getMaxHp(), high.getMaxHp());
        assertEquals(avg.getMaxHp(), low.getMaxHp());
        assertEquals(avg.getStrength(), high.getStrength());
        assertEquals(avg.getStrength(), low.getStrength());
        assertEquals(avg.getSpeed(), high.getSpeed());

        // 基础值 = 种族基础（个体浮动 = 0）
        assertEquals(100, avg.getMaxHp());
        assertEquals(20, avg.getStrength());
        assertEquals(20, avg.getDefense());
        assertEquals(20, avg.getSpeed());

        // 分解：growth=0, aptBonus=0, freeBonus=0
        PetPanelStats.Breakdown hpBd = avg.getBreakdowns().get(PetPanelStats.HP);
        assertEquals(100, hpBd.getBase());
        assertEquals(0, hpBd.getGrowth());
        assertEquals(0, hpBd.getAptBonus());
        assertEquals(0, hpBd.getFreeBonus());
        assertEquals(100, hpBd.getTotal());
    }

    @Test
    void panelStats_level10_aptitudeDifferentiallyAffects() {
        // Lv.10：资质 100 比 50 多出 growth*(50/100) 的资质修正
        PetSpeciesConfig species =
                species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity petAvg = pet(10, 50);
        PlayerPetEntity petHigh = pet(10, 100);

        PetPanelStats avg = svc.computePanelStats(petAvg, species);
        PetPanelStats high = svc.computePanelStats(petHigh, species);

        // 力量：base=20，growth=2.0*9=18，aptBonus_avg=18*0/100=0，aptBonus_high=18*50/100=9
        // total_avg = 20+18+0 = 38；total_high = 20+18+9 = 47
        assertEquals(38, avg.getStrength());
        assertEquals(47, high.getStrength());

        // HP：base=100，growth=8.0*9=72，aptBonus_avg=0，aptBonus_high=72*50/100=36
        // total_avg = 100+72+0 = 172；total_high = 100+72+36 = 208
        assertEquals(172, avg.getMaxHp());
        assertEquals(208, high.getMaxHp());
    }

    @Test
    void panelStats_individualVarianceAddedToBase() {
        // 个体浮动直接加到基础值上（捕获时固化）
        PetSpeciesConfig species =
                species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity pet = pet(5, 50);
        pet.setBaseStrengthOffset(3);  // 个体浮动 +3
        pet.setBaseHpOffset(10);

        PetPanelStats stats = svc.computePanelStats(pet, species);
        PetPanelStats.Breakdown strBd = stats.getBreakdowns().get(PetPanelStats.STRENGTH);
        PetPanelStats.Breakdown hpBd = stats.getBreakdowns().get(PetPanelStats.HP);

        // base = 种族基础 + 个体浮动
        assertEquals(20 + 3, strBd.getBase());
        assertEquals(100 + 10, hpBd.getBase());
    }

    @Test
    void panelStats_freePointsContributeByDimension() {
        // 自由点数：HP 每点 +5，非 HP 每点 +1，速度每点 +1
        PetSpeciesConfig species =
                species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity pet = pet(1, 50);
        pet.setFreePointHp(2);
        pet.setFreePointStrength(3);
        pet.setFreePointSpeed(1);

        PetPanelStats stats = svc.computePanelStats(pet, species);
        PetPanelStats.Breakdown hpBd = stats.getBreakdowns().get(PetPanelStats.HP);
        PetPanelStats.Breakdown strBd = stats.getBreakdowns().get(PetPanelStats.STRENGTH);
        PetPanelStats.Breakdown spdBd = stats.getBreakdowns().get(PetPanelStats.SPEED);

        assertEquals(2 * 5, hpBd.getFreeBonus());
        assertEquals(3 * 1, strBd.getFreeBonus());
        assertEquals(1 * 1, spdBd.getFreeBonus());

        // 总值包含自由点数贡献
        assertEquals(100 + 2 * 5, stats.getMaxHp());
        assertEquals(20 + 3, stats.getStrength());
        assertEquals(20 + 1, stats.getSpeed());
    }

    // ==================== 升级经验 ====================

    @Test
    void expToNextLevel_formulaAndCap() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));

        // exp = expBase * expGrowthFactor^(level-1)
        assertEquals(100, svc.expToNextLevel(1));   // 100 * 1.15^0
        assertEquals(115, svc.expToNextLevel(2));   // 100 * 1.15^1
        assertEquals(132, svc.expToNextLevel(3));   // round(100 * 1.15^2) = round(132.25) = 132
        // 等级上限返回 0
        assertEquals(0, svc.expToNextLevel(50));
    }

    @Test
    void totalExpToReach_cumulative() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));

        // 1→3 累计 = 100 + 115 = 215
        assertEquals(215, svc.totalExpToReach(1, 3));
        // 同级返回 0
        assertEquals(0, svc.totalExpToReach(5, 5));
        // 反向返回 0
        assertEquals(0, svc.totalExpToReach(5, 3));
    }

    // ==================== 自由属性点 ====================

    @Test
    void freePointsEarned_perLevelAndRarityMilestone() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));

        // Lv.1 = 0（无升级）
        assertEquals(0, svc.freePointsEarned(1, "COMMON"));
        // Lv.2 = 3
        assertEquals(3, svc.freePointsEarned(2, "COMMON"));
        // Lv.10 COMMON = 9*3 + 0*1 = 27
        assertEquals(27, svc.freePointsEarned(10, "COMMON"));
        // Lv.10 RARE = 27 + 2*1 = 29
        assertEquals(29, svc.freePointsEarned(10, "RARE"));
        // Lv.10 EPIC = 27 + 4 = 31
        assertEquals(31, svc.freePointsEarned(10, "EPIC"));
        // Lv.10 LEGENDARY = 27 + 6 = 33
        assertEquals(33, svc.freePointsEarned(10, "LEGENDARY"));
        // Lv.20 LEGENDARY = 19*3 + 6*2 = 57 + 12 = 69
        assertEquals(69, svc.freePointsEarned(20, "LEGENDARY"));
        // Lv.50 LEGENDARY = 49*3 + 6*5 = 147 + 30 = 177
        assertEquals(177, svc.freePointsEarned(50, "LEGENDARY"));
    }

    @Test
    void freePointsEarned_nullRarityReturnsZero() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));
        assertEquals(0, svc.getRarityExtraPoints(null));
        assertEquals(0, svc.getRarityExtraPoints("UNKNOWN"));
    }

    @Test
    void freePointsAvailable_earnedMinusAllocated() {
        PetSpeciesConfig species =
                species("SPEC_TEST", "RARE", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        // Lv.10 RARE 已获得 29；已分配：速度 2 次（消耗 2*2=4）+ 力量 3 次（消耗 3*1=3），共消耗 7
        PlayerPetEntity pet = pet(10, 50);
        pet.setFreePointSpeed(2);
        pet.setFreePointStrength(3);

        // allocatedFreePoints 按点次累加（2+3=5），不折算速度双倍消耗
        assertEquals(5, svc.allocatedFreePoints(pet));
        // consumedFreePoints 按需求 §20 转换表折算（4+3=7）
        assertEquals(7, svc.consumedFreePoints(pet));
        // available = 29 - 7 = 22
        assertEquals(22, svc.freePointsAvailable(pet, species));
    }

    @Test
    void pointCostForStat_speedCostsDouble() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));

        // 速度每点消耗 2，其他维度每点消耗 1
        assertEquals(1, svc.pointCostForStat(PetPanelStats.STRENGTH, 1));
        assertEquals(1, svc.pointCostForStat(PetPanelStats.HP, 1));
        assertEquals(2, svc.pointCostForStat(PetPanelStats.SPEED, 1));
        assertEquals(6, svc.pointCostForStat(PetPanelStats.SPEED, 3));
        assertEquals(0, svc.pointCostForStat(PetPanelStats.STRENGTH, 0));
        assertEquals(0, svc.pointCostForStat(PetPanelStats.STRENGTH, -1));
    }

    @Test
    void resetPoints_refundsAllAllocated() {
        PetGrowthService svc = service(List.of(species("SPEC_TEST", "COMMON", 50, List.of())));

        PlayerPetEntity pet = pet(5, 50);
        pet.setFreePointHp(2);
        pet.setFreePointStrength(1);
        pet.setFreePointSpeed(3);

        // 洗点返还 = 已消耗自由点数（第一阶段免费）：2*1 + 1*1 + 3*2 = 9
        assertEquals(9, svc.freePointsRefundOnReset(pet));
    }

    // ==================== 技能解锁 ====================

    @Test
    void skillsUnlockedBetween_rangeInclusiveToLevel() {
        PetSpeciesConfig species = species("SPEC_TEST", "COMMON", 50,
                List.of(
                        skillSlot("SKILL_A", 1),   // 初始技能
                        skillSlot("SKILL_B", 5),  // 5 级解锁
                        skillSlot("SKILL_C", 10), // 10 级解锁
                        skillSlot("SKILL_D", 20)  // 20 级解锁
                ));
        PetGrowthService svc = service(List.of(species));

        // 从 1 升到 10：(1, 10] → 解锁 5、10 级技能
        List<PetGrowthService.UnlockedSkill> unlocked = svc.skillsUnlockedBetween(species, 1, 10);
        assertEquals(2, unlocked.size());
        assertEquals("SKILL_B", unlocked.get(0).getSkillId());
        assertEquals(5, unlocked.get(0).getUnlockLevel());
        assertEquals("SKILL_C", unlocked.get(1).getSkillId());

        // 边界：fromLevel=5 不包含 5 级技能（已学会），toLevel=10 包含 10 级
        List<PetGrowthService.UnlockedSkill> from5 = svc.skillsUnlockedBetween(species, 5, 10);
        assertEquals(1, from5.size());
        assertEquals("SKILL_C", from5.get(0).getSkillId());
    }

    @Test
    void learnedSpeciesSkills_filterByLevel() {
        PetSpeciesConfig species = species("SPEC_TEST", "COMMON", 50,
                List.of(
                        skillSlot("SKILL_A", 1),
                        skillSlot("SKILL_B", 5),
                        skillSlot("SKILL_C", 10)
                ));
        PetGrowthService svc = service(List.of(species));

        // Lv.3：仅学会 1 级技能
        assertEquals(1, svc.learnedSpeciesSkills(species, 3).size());
        // Lv.10：学会 1、5、10 级技能
        assertEquals(3, svc.learnedSpeciesSkills(species, 10).size());
    }

    @Test
    void skillsUnlockedBetween_nullSkillsReturnsEmpty() {
        PetSpeciesConfig species = species("SPEC_TEST", "COMMON", 50, null);
        PetGrowthService svc = service(List.of(species));

        assertTrue(svc.skillsUnlockedBetween(species, 1, 10).isEmpty());
    }

    // ==================== 升级预览 ====================

    @Test
    void previewLevelUp_expAndPointsAndSkills() {
        PetSpeciesConfig species = species("SPEC_TEST", "RARE", 50,
                List.of(
                        skillSlot("SKILL_A", 1),
                        skillSlot("SKILL_B", 10)
                ));
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity pet = pet(5, 50);
        PetGrowthService.LevelUpPreview preview = svc.previewLevelUp(pet, species, 10);

        assertEquals(5, preview.getFromLevel());
        assertEquals(10, preview.getToLevel());
        // 累计经验 1→10 与 1→5 之差
        int expectedExp = svc.totalExpToReach(5, 10);
        assertEquals(expectedExp, preview.getExpRequired());
        // RARE：Lv.10 已获得 29 - Lv.5 已获得 12 = 17
        // Lv.5 RARE = 4*3 + 2*0 = 12（5/10=0，无稀有度额外）
        assertEquals(17, preview.getPointsGained());
        // 1→10 区间解锁 SKILL_B
        assertEquals(1, preview.getSkillsUnlocked().size());
        assertEquals("SKILL_B", preview.getSkillsUnlocked().get(0).getSkillId());
        // 升级前后面板属性
        assertNotNull(preview.getBeforeStats());
        assertNotNull(preview.getAfterStats());
        // Lv.10 力量 > Lv.5 力量（成长 + 资质修正）
        assertTrue(preview.getAfterStats().getStrength() > preview.getBeforeStats().getStrength());
    }

    @Test
    void previewLevelUp_invalidTargetThrows() {
        PetSpeciesConfig species = species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity pet = pet(5, 50);

        // 目标等级 <= 当前等级
        assertThrows(IllegalArgumentException.class, () -> svc.previewLevelUp(pet, species, 5));
        assertThrows(IllegalArgumentException.class, () -> svc.previewLevelUp(pet, species, 3));
        // 超过等级上限
        assertThrows(IllegalArgumentException.class, () -> svc.previewLevelUp(pet, species, 51));
    }

    @Test
    void previewLevelUp_toCapValid() {
        PetSpeciesConfig species = species("SPEC_TEST", "COMMON", 50, List.of());
        PetGrowthService svc = service(List.of(species));

        PlayerPetEntity pet = pet(48, 50);
        PetGrowthService.LevelUpPreview preview = svc.previewLevelUp(pet, species, 50);

        assertEquals(48, preview.getFromLevel());
        assertEquals(50, preview.getToLevel());
        assertTrue(preview.getExpRequired() > 0);
    }

    // ==================== REV-019：解锁区分主动/被动 + 跨多级一次全显 ====================

    @Test
    void skillsUnlockedBetween_includesPassivesWithType() {
        PetSpeciesConfig species = species("SPEC_P", "COMMON", 50,
                List.of(skillSlot("SKILL_A", 1), skillSlot("SKILL_B", 10)));
        PetSpeciesConfig.SpeciesPassiveSlot passive = new PetSpeciesConfig.SpeciesPassiveSlot();
        passive.setPassiveId("PASSIVE_X");
        passive.setUnlockLevel(15);
        species.setPassives(List.of(passive));
        PetGrowthService svc = service(List.of(species));

        // 跨多级（1→20）：主动 SKILL_B(10) + 被动 PASSIVE_X(15) 一次全部返回
        List<PetGrowthService.UnlockedSkill> unlocked = svc.skillsUnlockedBetween(species, 1, 20);
        assertEquals(2, unlocked.size(), "跨多级应一次返回全部新技能（需求 §17/§150）");
        assertEquals("SKILL_B", unlocked.get(0).getSkillId());
        assertEquals("ACTIVE", unlocked.get(0).getSkillType());
        assertEquals("PASSIVE_X", unlocked.get(1).getSkillId());
        assertEquals("PASSIVE", unlocked.get(1).getSkillType(), "预览必须区分主动/被动（REV-013）");
    }
}
