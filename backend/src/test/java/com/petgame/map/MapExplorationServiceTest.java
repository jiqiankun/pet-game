package com.petgame.map;

import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.GameProperties;
import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerCampActivationEntity;
import com.petgame.map.entity.PlayerGatherUsedEntity;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerCampActivationMapper;
import com.petgame.map.mapper.PlayerChestLootMapper;
import com.petgame.map.mapper.PlayerGatherUsedMapper;
import com.petgame.quest.mapper.PlayerMapChangeMapper;
import com.petgame.map.mapper.PlayerMapSessionMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.map.service.MapExplorationService;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.quest.service.QuestService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * MapExplorationService 单元测试（阶段 6 验收标准）。
 * <p>
 * 覆盖：区域解锁懒写入与进入校验、营地免费恢复与激活、营地传送、
 * 采集点单次访问一次性、隐藏宝箱全局一次性、刷新组区域校验、
 * 战败流程（零惩罚 + 最近恢复点 + 队伍恢复 + 提示）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapExplorationServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;
    @Mock
    private PlayerTeamMapper playerTeamMapper;
    @Mock
    private PlayerTeamMemberMapper playerTeamMemberMapper;
    @Mock
    private PlayerInventoryMapper playerInventoryMapper;
    @Mock
    private PlayerRegionUnlockMapper regionUnlockMapper;
    @Mock
    private PlayerCampActivationMapper campActivationMapper;
    @Mock
    private PlayerChestLootMapper chestLootMapper;
    @Mock
    private PlayerMapSessionMapper mapSessionMapper;
    @Mock
    private PlayerGatherUsedMapper gatherUsedMapper;
    @Mock
    private QuestService questService;
    @Mock
    private PlayerMapChangeMapper playerMapChangeMapper;

    private GameConfigRegistry registry;
    private MapExplorationService mapService;

    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        // 真实配置体系（含 maps.yml）+ 真实成长公式，Mapper 全部 mock
        GameProperties props = new GameProperties();
        props.setConfigDir(null);
        registry = new GameConfigRegistry(new GameConfigLoader(props), new GameConfigValidator());
        registry.init();
        PetGrowthService growthService = new PetGrowthService(registry);

        mapService = new MapExplorationService(registry, growthService,
                playerMapper, playerPetMapper, playerTeamMapper, playerTeamMemberMapper,
                playerInventoryMapper, regionUnlockMapper, campActivationMapper,
                chestLootMapper, mapSessionMapper, gatherUsedMapper, questService, playerMapChangeMapper);

        player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setPlayerName("TEST");
        player.setGold(100);
        player.setCurrentMapId("MAP_START_VILLAGE");
        when(playerMapper.selectOne(isNull())).thenReturn(player);
    }

    // ==================== 大地图 / 区域解锁 ====================

    @Test
    void getWorldMap_lazyUnlocksAutoRegions() {
        // 首次查询（ensureAutoUnlocks）无解锁记录 → 懒写入；再次查询（视图构建）返回已解锁
        List<PlayerRegionUnlockEntity> unlocks = registry.getImplementedRegions().stream()
                .map(r -> new PlayerRegionUnlockEntity("SAVE_1", r.getId(), LocalDateTime.now()))
                .toList();
        when(regionUnlockMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(unlocks);
        when(campActivationMapper.selectList(any())).thenReturn(List.of());

        MapExplorationService.WorldMapView view = mapService.getWorldMap();

        assertEquals("MAP_START_VILLAGE", view.getCurrentMapId());
        // 已实装区域 = 6 个（阶段 9 全部开放：3 AUTO + 3 QUEST）
        assertEquals(6, view.getRegions().size());
        assertTrue(view.getRegions().stream().allMatch(
                MapExplorationService.WorldMapView.RegionView::isUnlocked));
        // AUTO 解锁懒写入（3 个 AUTO 区域）
        verify(regionUnlockMapper, times(3)).insert(any(PlayerRegionUnlockEntity.class));
        // 起始据点营地自动激活懒写入
        ArgumentCaptor<PlayerCampActivationEntity> campCaptor =
                ArgumentCaptor.forClass(PlayerCampActivationEntity.class);
        verify(campActivationMapper, atLeastOnce()).insert(campCaptor.capture());
        assertEquals("CAMP_VILLAGE_1", campCaptor.getValue().getCampId());
        // Boss 状态占位
        assertTrue(view.getRegions().stream()
                .allMatch(r -> "NOT_OPEN".equals(r.getBossStatus())));
    }

    @Test
    void enterRegion_withoutUnlock_throwsRegionLocked() {
        // mock 不落库：ensureAutoUnlocks 写入后查询仍为空 → 视为未解锁
        when(regionUnlockMapper.selectList(any())).thenReturn(List.of());
        when(campActivationMapper.selectList(any())).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.enterRegion("MAP_AREA_MEADOW", null));
        assertEquals("REGION_LOCKED", e.getErrorCode());
    }

    @Test
    void enterRegion_unlocked_updatesPositionAndStartsSession() {
        unlockAllAutoRegions();
        when(mapSessionMapper.selectOne(any())).thenReturn(null);

        // 大地图进入（不传出口）：落到区域默认出生点
        MapExplorationService.MapEnterView view =
                mapService.enterRegion("MAP_AREA_MEADOW", null);

        assertEquals("MAP_AREA_MEADOW", view.getMapId());
        assertEquals("meadow", view.getMapFile());
        assertEquals("SPAWN_MEADOW", view.getSpawnObjectId());
        assertNotNull(view.getSessionId());
        assertEquals("MAP_AREA_MEADOW", player.getCurrentMapId());
        verify(playerMapper).updateById(player);
        verify(mapSessionMapper).insert(any(PlayerMapSessionEntity.class));
    }

    @Test
    void enterRegion_plannedRegion_throwsNotFound() {
        unlockAllAutoRegions();
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.enterRegion("MAP_AREA_NOT_EXISTS", null));
        assertEquals("REGION_NOT_FOUND", e.getErrorCode());
    }

    @Test
    void enterRegion_viaExit_resolvesEntryObject() {
        unlockAllAutoRegions();
        when(mapSessionMapper.selectOne(any())).thenReturn(null);

        MapExplorationService.MapEnterView view =
                mapService.enterRegion("MAP_AREA_MEADOW", "EXIT_VILLAGE_TO_MEADOW");

        // 后端权威解析出口对应入口对象
        assertEquals("ENTRY_MEADOW_FROM_VILLAGE", view.getSpawnObjectId());
    }

    @Test
    void enterRegion_invalidExit_throws() {
        unlockAllAutoRegions();
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.enterRegion("MAP_AREA_FOREST", "EXIT_VILLAGE_TO_MEADOW"));
        assertEquals("EXIT_NOT_FOUND", e.getErrorCode());
    }

    // ==================== 营地 ====================

    @Test
    void restAtCamp_activatesAndHealsTeamAndRefreshes() {
        unlockAllAutoRegions();
        when(campActivationMapper.selectCount(any())).thenReturn(0L);
        stubActiveTeamWithPet(petWithHp("PET_FIRE_001", 5));
        when(mapSessionMapper.selectOne(any())).thenReturn(null);

        MapExplorationService.CampRestView view = mapService.restAtCamp("CAMP_MEADOW_1");

        assertTrue(view.isFirstActivation());
        assertEquals("MAP_AREA_MEADOW", view.getMapId());
        assertEquals(1, view.getHealedPets());
        verify(campActivationMapper).insert(any(PlayerCampActivationEntity.class));
        // 宠物 HP 恢复到上限（> 原值 5）
        ArgumentCaptor<PlayerPetEntity> petCaptor = ArgumentCaptor.forClass(PlayerPetEntity.class);
        verify(playerPetMapper).updateById(petCaptor.capture());
        assertTrue(petCaptor.getValue().getCurrentHp() > 5);
        // 休息触发地图刷新（新会话）
        verify(mapSessionMapper).insert(any(PlayerMapSessionEntity.class));
        assertEquals("MAP_AREA_MEADOW", player.getCurrentMapId());
    }

    @Test
    void restAtCamp_unknownCamp_throws() {
        unlockAllAutoRegions();
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.restAtCamp("CAMP_NOT_EXISTS"));
        assertEquals("CAMP_NOT_FOUND", e.getErrorCode());
    }

    @Test
    void teleportToCamp_unactivated_throws() {
        unlockAllAutoRegions();
        when(campActivationMapper.selectCount(any())).thenReturn(0L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.teleportToCamp("CAMP_FOREST_1"));
        assertEquals("CAMP_NOT_ACTIVATED", e.getErrorCode());
    }

    @Test
    void teleportToCamp_activated_movesAndRefreshes() {
        unlockAllAutoRegions();
        when(campActivationMapper.selectCount(any())).thenReturn(1L);
        when(mapSessionMapper.selectOne(any())).thenReturn(null);

        MapExplorationService.MapEnterView view = mapService.teleportToCamp("CAMP_FOREST_1");

        assertEquals("MAP_AREA_FOREST", view.getMapId());
        assertEquals("CAMP_FOREST_1", view.getSpawnObjectId());
        assertEquals("MAP_AREA_FOREST", player.getCurrentMapId());
        verify(mapSessionMapper).insert(any(PlayerMapSessionEntity.class));
    }

    // ==================== 采集 / 宝箱 ====================

    @Test
    void gather_grantsRewardsAndMarksUsed() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        stubSession("SESSION_1");
        when(gatherUsedMapper.selectCount(any())).thenReturn(0L);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);

        MapExplorationService.RewardResultView result = mapService.gather("GATHER_MEADOW_1");

        assertEquals("草药丛", result.getObjectName());
        assertTrue(result.getGoldGained() >= 5 && result.getGoldGained() <= 15);
        assertEquals(1, result.getItems().size());
        assertEquals("ITEM_POTION_SMALL", result.getItems().get(0).getItemId());
        assertTrue(result.getItems().get(0).getQuantity() >= 1
                && result.getItems().get(0).getQuantity() <= 2);
        verify(playerInventoryMapper).insert(any(PlayerInventoryEntity.class));
        verify(gatherUsedMapper).insert(any(PlayerGatherUsedEntity.class));
        verify(playerMapper).updateById(player);
    }

    @Test
    void gather_twiceInSameSession_throws() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        stubSession("SESSION_1");
        when(gatherUsedMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.gather("GATHER_MEADOW_1"));
        assertEquals("GATHER_ALREADY_USED", e.getErrorCode());
    }

    @Test
    void gather_notInCurrentRegion_throws() {
        player.setCurrentMapId("MAP_START_VILLAGE");
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.gather("GATHER_MEADOW_1"));
        assertEquals("GATHER_NOT_FOUND", e.getErrorCode());
    }

    @Test
    void openChest_onceOnly() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        when(chestLootMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.openChest("CHEST_MEADOW_HIDDEN_1"));
        assertEquals("CHEST_ALREADY_LOOTED", e.getErrorCode());
    }

    @Test
    void openChest_firstTime_grantsRewards() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        when(chestLootMapper.selectCount(any())).thenReturn(0L);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);

        MapExplorationService.RewardResultView result = mapService.openChest("CHEST_MEADOW_HIDDEN_1");

        assertEquals("草原隐藏宝箱", result.getObjectName());
        assertEquals(2, result.getItems().size());
        assertTrue(result.getGoldGained() >= 50 && result.getGoldGained() <= 80);
        verify(chestLootMapper).insert(any(com.petgame.map.entity.PlayerChestLootEntity.class));
    }

    // ==================== 遭遇校验 ====================

    @Test
    void validateEncounterGroup_allowedInCurrentRegion() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        assertDoesNotThrow(() -> mapService.validateEncounterGroup("ENCOUNTER_MEADOW"));
    }

    @Test
    void validateEncounterGroup_foreignGroup_throws() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        BusinessException e = assertThrows(BusinessException.class,
                () -> mapService.validateEncounterGroup("ENCOUNTER_FOREST"));
        assertEquals("ENCOUNTER_GROUP_NOT_ALLOWED", e.getErrorCode());
    }

    // ==================== 战败流程 ====================

    @Test
    void handleDefeat_healsTeamAndReturnsNearestCamp() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        stubActiveTeamWithPet(petWithHp("PET_FIRE_001", 0));
        // 当前区域存在已激活营地
        PlayerCampActivationEntity camp = new PlayerCampActivationEntity(
                "SAVE_1", "CAMP_MEADOW_1", LocalDateTime.now());
        when(campActivationMapper.selectList(any())).thenReturn(List.of(camp));

        MapExplorationService.DefeatView defeat = mapService.handleDefeat(player);

        assertNotNull(defeat.getMessage());
        assertFalse(defeat.getMessage().isBlank());
        assertEquals("MAP_AREA_MEADOW", defeat.getRespawnMapId());
        assertEquals("CAMP_MEADOW_1", defeat.getRespawnObjectId());
        assertEquals(1, defeat.getHealedPets());
        // 倒下宠物恢复到上限
        ArgumentCaptor<PlayerPetEntity> petCaptor = ArgumentCaptor.forClass(PlayerPetEntity.class);
        verify(playerPetMapper).updateById(petCaptor.capture());
        assertTrue(petCaptor.getValue().getCurrentHp() > 0);
    }

    @Test
    void handleDefeat_noCamp_respawnsAtRegionSpawn() {
        player.setCurrentMapId("MAP_AREA_MEADOW");
        stubActiveTeamWithPet(petWithHp("PET_FIRE_001", 0));
        when(campActivationMapper.selectList(any())).thenReturn(List.of());

        MapExplorationService.DefeatView defeat = mapService.handleDefeat(player);

        assertEquals("SPAWN_MEADOW", defeat.getRespawnObjectId());
        // 战败零惩罚：不扣金币
        assertEquals(100, player.getGold());
    }

    // ==================== 工具 ====================

    /** stub：全部 AUTO 区域已解锁。 */
    private void unlockAllAutoRegions() {
        List<PlayerRegionUnlockEntity> unlocks = registry.getImplementedRegions().stream()
                .map(r -> new PlayerRegionUnlockEntity("SAVE_1", r.getId(), LocalDateTime.now()))
                .toList();
        when(regionUnlockMapper.selectList(any())).thenReturn(unlocks);
        when(campActivationMapper.selectList(any())).thenReturn(List.of());
    }

    /** stub：当前区域存在访问会话。 */
    private void stubSession(String sessionId) {
        PlayerMapSessionEntity session = new PlayerMapSessionEntity(
                "SAVE_1", player.getCurrentMapId(), sessionId, LocalDateTime.now());
        when(mapSessionMapper.selectOne(any())).thenReturn(session);
    }

    /** stub：激活队伍含一只指定宠物。 */
    private void stubActiveTeamWithPet(PlayerPetEntity pet) {
        PlayerTeamEntity team = new PlayerTeamEntity();
        team.setId(100L);
        team.setSaveId("SAVE_1");
        team.setSlot(1);
        team.setIsActive(true);
        team.setName("队伍 1");
        when(playerTeamMapper.selectOne(any())).thenReturn(team);

        PlayerTeamMemberEntity member = new PlayerTeamMemberEntity();
        member.setId(1L);
        member.setTeamId(100L);
        member.setPetId(pet.getId());
        member.setPosition(1);
        when(playerTeamMemberMapper.selectList(any())).thenReturn(List.of(member));
        when(playerPetMapper.selectById(pet.getId())).thenReturn(pet);
    }

    private PlayerPetEntity petWithHp(String speciesId, int hp) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(1L);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId(speciesId);
        pet.setLevel(5);
        pet.setHpAptitude(80);
        pet.setStrengthAptitude(80);
        pet.setSpiritAptitude(80);
        pet.setDefenseAptitude(80);
        pet.setResistanceAptitude(80);
        pet.setSpeedAptitude(80);
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(hp);
        return pet;
    }
}
