package com.petgame.boss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petgame.boss.entity.BossEncounterSnapshotEntity;
import com.petgame.boss.mapper.BossEncounterSnapshotMapper;
import com.petgame.boss.service.BossEncounterSnapshotService;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 阶段 13：Boss 遭遇首次固定、跨难度显式重置。 */
@ExtendWith(MockitoExtension.class)
class BossEncounterSnapshotServiceTest {

    @Mock
    private GameConfigRegistry registry;
    @Mock
    private BossEncounterSnapshotMapper mapper;

    private final AtomicReference<BossEncounterSnapshotEntity> stored = new AtomicReference<>();
    private BossEncounterSnapshotService service;
    private BossesConfig.BossConfig boss;
    private BossesConfig.DifficultyConfig bossDifficulty;

    @BeforeEach
    void setUp() {
        SystemRuleConfig rules = new SystemRuleConfig();
        rules.setLevelCap(50);
        rules.getControlResistance().put("BOSS", 0.6);
        configureProfile(rules.getGameDifficulty().getProfiles().get("ELITE"), 2, 0, 1, 5, 2);
        configureProfile(rules.getGameDifficulty().getProfiles().get("NIGHTMARE"), 4, 0, 1, 3, 3);
        when(registry.getSystemRules()).thenReturn(rules);

        PetSpeciesConfig support = species("PET_SUPPORT", "SUPPORT");
        when(registry.getSpecies("PET_SUPPORT")).thenReturn(support);

        boss = new BossesConfig.BossConfig();
        boss.setId("BOSS_TEST");
        boss.setName("测试首领");
        boss.setElement("FIRE");
        boss.setRecommendedLevel(10);
        boss.setMinTeamSize(1);
        boss.setMaxTeamSize(2);
        boss.setOptionalSpeciesIds(List.of("PET_SUPPORT"));
        bossDifficulty = new BossesConfig.DifficultyConfig();
        BossesConfig.StatsConfig stats = new BossesConfig.StatsConfig();
        stats.setMaxHp(500);
        stats.setStrength(80);
        stats.setSpirit(80);
        stats.setDefense(40);
        stats.setResistance(40);
        stats.setSpeed(30);
        bossDifficulty.setStats(stats);

        when(mapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            BossEncounterSnapshotEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            stored.set(entity);
            return 1;
        }).when(mapper).insert(any(BossEncounterSnapshotEntity.class));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(mapper).updateById(any(BossEncounterSnapshotEntity.class));

        service = new BossEncounterSnapshotService(registry, new PetGrowthService(registry), mapper,
                new ObjectMapper());
    }

    @Test
    void getOrCreate_reusesFirstRoster_andOnlyAllowsCrossDifficultyReset() {
        List<PlayerPetEntity> initialTeam = List.of(teamPet(30));
        BossEncounterSnapshotService.EncounterData first = service.getOrCreate(
                "SAVE", boss, bossDifficulty, "NORMAL", "ELITE", initialTeam, 123L);
        BossEncounterSnapshotService.EncounterData retry = service.getOrCreate(
                "SAVE", boss, bossDifficulty, "NORMAL", "HELL", List.of(teamPet(1)), 999L);

        assertEquals(1L, first.getSnapshotId());
        assertEquals(first.getGeneratedLevel(), retry.getGeneratedLevel());
        assertEquals(first.getGameDifficulty(), retry.getGameDifficulty(), "重试必须复用原快照难度");
        assertEquals(first.getUnits(), retry.getUnits(), "重试不得重掷 Boss 阵容");
        assertThrows(BusinessException.class, () -> service.resetForCurrentDifficulty(
                "SAVE", boss, bossDifficulty, "NORMAL", "ELITE", initialTeam));

        BossEncounterSnapshotService.SnapshotView reset = service.resetForCurrentDifficulty(
                "SAVE", boss, bossDifficulty, "NORMAL", "NIGHTMARE", initialTeam);
        assertEquals("NIGHTMARE", reset.getGameDifficulty());
        assertEquals(2, reset.getSnapshotVersion());
        assertTrue(reset.isCanReset() == false);
    }

    private static void configureProfile(SystemRuleConfig.DifficultyProfile profile,
                                         int bossOffset, int upwardLimit, int optionalSlots,
                                         int playerCapOffset, int aiLevel) {
        profile.setBossLevelOffset(bossOffset);
        profile.setBossPlayerLevelUpwardLimit(upwardLimit);
        profile.setBossOptionalSlotCount(optionalSlots);
        profile.setEffectiveLevelCapEnabled(true);
        profile.setPlayerLevelCapOffset(playerCapOffset);
        profile.setBossAiLevel(aiLevel);
    }

    private static PetSpeciesConfig species(String id, String role) {
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId(id);
        species.setName("支援兽");
        species.setElement("WATER");
        species.setRole(role);
        species.setRarity("COMMON");
        species.setBaseHp(100);
        species.setBaseStrength(30);
        species.setBaseSpirit(30);
        species.setBaseDefense(30);
        species.setBaseResistance(30);
        species.setBaseSpeed(30);
        return species;
    }

    private static PlayerPetEntity teamPet(int level) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setSpeciesId("PET_SUPPORT");
        pet.setLevel(level);
        return pet;
    }
}
