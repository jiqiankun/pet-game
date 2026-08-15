package com.petgame.battle.service;

import com.petgame.battle.ai.AutoBattleDecisionProvider;
import com.petgame.battle.ai.BossDecisionProvider;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.battle.model.BattleUnit;
import com.petgame.capture.WildEncounterService;
import com.petgame.config.GameConfigRegistry;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.boss.service.BossEncounterSnapshotService;
import com.petgame.team.service.TeamService;
import com.petgame.map.service.MapExplorationService;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.quest.service.QuestService;
import com.petgame.statistics.service.StatisticsService;
import com.petgame.pet.service.PetHistoryService;
import com.petgame.boss.service.BossChallengeService;
import com.petgame.achievement.service.AchievementService;
import com.petgame.battle.victory.VictoryInteractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static com.petgame.pet.PetGrowthTestFixtures.buildRegistry;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UnitSnapshot 展示标识（artType/artId）映射测试（阶段 14 美术验收 ART-01）。
 * <p>
 * 覆盖：玩家宠物、Boss 核心、Boss 支援单位与无资源测试敌人的展示标识映射。
 */
@ExtendWith(MockitoExtension.class)
class UnitSnapshotMappingTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerPetMapper playerPetMapper;
    @Mock private PlayerPetSkillMapper playerPetSkillMapper;
    @Mock private PlayerTeamMapper playerTeamMapper;
    @Mock private PlayerTeamMemberMapper playerTeamMemberMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;
    @Mock private WildEnemyDecisionProvider enemyDecisionProvider;
    @Mock private WildEncounterService wildEncounterService;
    @Mock private BossEncounterSnapshotService bossEncounterSnapshotService;
    @Mock private TeamService teamService;
    @Mock private MapExplorationService mapExplorationService;
    @Mock private PokedexService pokedexService;
    @Mock private QuestService questService;
    @Mock private StatisticsService statisticsService;
    @Mock private PetHistoryService petHistoryService;
    @Mock private BossChallengeService bossChallengeService;
    @Mock private AchievementService achievementService;
    @Mock private VictoryInteractionService victoryInteractionService;

    private BattleService battleService;
    private Method toUnitSnapshotMethod;

    @BeforeEach
    void setUp() throws Exception {
        GameConfigRegistry registry = buildRegistry(of());
        PetGrowthService growthService = new PetGrowthService(registry);
        battleService = new BattleService(registry, enemyDecisionProvider,
                new BossDecisionProvider(registry),
                new AutoBattleDecisionProvider(registry),
                playerMapper, playerPetMapper, playerPetSkillMapper,
                playerTeamMapper, playerTeamMemberMapper, playerInventoryMapper,
                growthService, new BattleLevelResolver(growthService), wildEncounterService,
                bossEncounterSnapshotService, teamService, mapExplorationService,
                pokedexService, questService, statisticsService, petHistoryService,
                bossChallengeService, achievementService, victoryInteractionService,
                new com.petgame.developer.DevContext());
        toUnitSnapshotMethod = BattleService.class.getDeclaredMethod("toUnitSnapshot", BattleUnit.class);
        toUnitSnapshotMethod.setAccessible(true);
    }

    private UnitSnapshot map(BattleUnit unit) throws Exception {
        return (UnitSnapshot) toUnitSnapshotMethod.invoke(battleService, unit);
    }

    @Test
    @DisplayName("玩家宠物单位映射为 PET 展示标识，artId=speciesId")
    void mapsPlayerPet() throws Exception {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("P_1");
        unit.setArtType("PET");
        unit.setArtId("PET_FIRE_001");

        UnitSnapshot snap = map(unit);
        assertEquals("PET", snap.getArtType());
        assertEquals("PET_FIRE_001", snap.getArtId());
    }

    @Test
    @DisplayName("Boss 核心单位映射为 BOSS 展示标识，artId=Boss ID")
    void mapsBossCore() throws Exception {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("BOSS_BOSS_MEADOW_GUARDIAN_NORMAL_CORE");
        unit.setArtType("BOSS");
        unit.setArtId("BOSS_MEADOW_GUARDIAN");

        UnitSnapshot snap = map(unit);
        assertEquals("BOSS", snap.getArtType());
        assertEquals("BOSS_MEADOW_GUARDIAN", snap.getArtId());
    }

    @Test
    @DisplayName("Boss 支援单位映射为 PET 展示标识，artId=支援种族 ID")
    void mapsBossSupport() throws Exception {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("BOSS_SUPPORT_1_PET_EARTH_001");
        unit.setArtType("PET");
        unit.setArtId("PET_EARTH_001");

        UnitSnapshot snap = map(unit);
        assertEquals("PET", snap.getArtType());
        assertEquals("PET_EARTH_001", snap.getArtId());
    }

    @Test
    @DisplayName("无资源测试敌人展示标识为空")
    void mapsTestEnemyNoArt() throws Exception {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("TES_ENEMY");
        unit.setArtType(null);
        unit.setArtId(null);

        UnitSnapshot snap = map(unit);
        assertNull(snap.getArtType());
        assertNull(snap.getArtId());
    }
}