package com.petgame.developer;

import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.entity.BossDifficultyUnlockEntity;
import com.petgame.boss.entity.BossDropUnlockEntity;
import com.petgame.boss.entity.BossLuckEntity;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.boss.mapper.BossDifficultyUnlockMapper;
import com.petgame.boss.mapper.BossDropUnlockMapper;
import com.petgame.boss.mapper.BossLuckMapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.GameProperties;
import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.developer.mapper.DevOperationLogMapper;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerMapSessionMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.save.SaveBackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * DevService 单元测试（阶段 14 数据操作类开发者工具）。
 * <p>
 * 覆盖：资源（金币/经验池/道具）、宠物（添加/重置）、地图（解锁/强制刷新/强制精英/强制随机事件）、
 * Boss（解锁难度/次数/幸运值/强制掉落）、操作日志。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevServiceTest {

    @Mock
    private SaveBackupService saveBackupService;
    @Mock
    private DevOperationLogMapper operationLogMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;
    @Mock
    private PlayerPetSkillMapper playerPetSkillMapper;
    @Mock
    private PlayerInventoryMapper playerInventoryMapper;
    @Mock
    private PlayerRegionUnlockMapper regionUnlockMapper;
    @Mock
    private PlayerMapSessionMapper mapSessionMapper;
    @Mock
    private BossDefeatCountMapper bossDefeatCountMapper;
    @Mock
    private BossDifficultyUnlockMapper bossDifficultyUnlockMapper;
    @Mock
    private BossLuckMapper bossLuckMapper;
    @Mock
    private BossDropUnlockMapper bossDropUnlockMapper;

    private GameConfigRegistry registry;
    private DevContext devContext;
    private DevService devService;
    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        GameProperties props = new GameProperties();
        props.setConfigDir(null);
        registry = new GameConfigRegistry(new GameConfigLoader(props), new GameConfigValidator());
        registry.init();
        PetGrowthService growthService = new PetGrowthService(registry);
        devContext = new DevContext();

        devService = new DevService(registry, growthService, devContext, saveBackupService,
                operationLogMapper, playerMapper, playerPetMapper, playerPetSkillMapper,
                playerInventoryMapper, regionUnlockMapper, mapSessionMapper,
                bossDefeatCountMapper, bossDifficultyUnlockMapper, bossLuckMapper, bossDropUnlockMapper);

        player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setPlayerName("TEST");
        player.setGold(100);
        player.setExpPool(0);
        player.setCurrentMapId("MAP_START_VILLAGE");
        when(playerMapper.selectOne(isNull())).thenReturn(player);
    }

    // ==================== 资源 ====================

    @Test
    void grantGold_increasesPlayerGoldAndLogs() {
        devService.grantGold(500);
        assertEquals(600, player.getGold());
        verify(playerMapper).updateById(player);
        verify(operationLogMapper).insert(argThat((DevOperationLogEntity r) -> "dev.grantGold".equals(r.getAction())));
    }

    @Test
    void grantGold_negativeAmount_throws() {
        assertThrows(BusinessException.class, () -> devService.grantGold(0));
    }

    @Test
    void grantExp_increasesExpPool() {
        devService.grantExp(1000);
        assertEquals(1000, player.getExpPool());
    }

    @Test
    void grantItem_exists_accumulates() {
        PlayerInventoryEntity existing = new PlayerInventoryEntity();
        existing.setId(1L);
        existing.setSaveId("SAVE_1");
        existing.setItemId(registry.getItem("ITEM_POTION_SMALL").getId());
        existing.setQuantity(3);
        when(playerInventoryMapper.selectOne(any())).thenReturn(existing);

        devService.grantItem("ITEM_POTION_SMALL", 2);
        assertEquals(5, existing.getQuantity());
        verify(playerInventoryMapper).updateById(existing);
    }

    @Test
    void grantItem_missingConfig_throws() {
        assertThrows(BusinessException.class, () -> devService.grantItem("ITEM_NOT_EXIST", 1));
    }

    // ==================== 宠物 ====================

    @Test
    void addPet_createsPetWithLevelSkills() {
        String speciesId = registry.getAllSpecies().get(0).getId();
        when(playerPetMapper.insert(any(PlayerPetEntity.class))).thenAnswer(inv -> {
            PlayerPetEntity p = inv.getArgument(0);
            p.setId(99L);
            return 1;
        });

        DevService.AddPetRequest req = new DevService.AddPetRequest();
        req.setSpeciesId(speciesId);
        req.setLevel(10);
        Long petId = devService.addPet(req);

        assertEquals(99L, petId);
        ArgumentCaptor<PlayerPetEntity> petCaptor = ArgumentCaptor.forClass(PlayerPetEntity.class);
        verify(playerPetMapper).insert(petCaptor.capture());
        assertEquals(speciesId, petCaptor.getValue().getSpeciesId());
        assertEquals(10, petCaptor.getValue().getLevel());
        // 已解锁技能写入
        verify(playerPetSkillMapper, atLeastOnce()).insert(any(PlayerPetSkillEntity.class));
    }

    @Test
    void addPet_unknownSpecies_throws() {
        DevService.AddPetRequest req = new DevService.AddPetRequest();
        req.setSpeciesId("PET_NOPE");
        assertThrows(BusinessException.class, () -> devService.addPet(req));
    }

    @Test
    void resetPet_clearsBattleStatsAndFillsHp() {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(7L);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId(registry.getAllSpecies().get(0).getId());
        pet.setLevel(10);
        pet.setBattleCount(20);
        pet.setWinCount(15);
        pet.setKillCount(5);
        pet.setCurrentHp(1);
        pet.setHpAptitude(70);
        pet.setStrengthAptitude(70);
        pet.setSpiritAptitude(70);
        pet.setDefenseAptitude(70);
        pet.setResistanceAptitude(70);
        pet.setSpeedAptitude(70);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        when(playerPetMapper.selectById(7L)).thenReturn(pet);

        devService.resetPet(7L);
        assertEquals(0, pet.getBattleCount());
        assertEquals(0, pet.getWinCount());
        assertTrue(pet.getCurrentHp() > 1);
        verify(playerPetMapper).updateById(pet);
    }

    // ==================== 地图 ====================

    @Test
    void unlockRegion_insertsUnlockRecord() {
        when(regionUnlockMapper.selectCount(any())).thenReturn(0L);
        devService.unlockRegion("MAP_AREA_MEADOW");

        ArgumentCaptor<PlayerRegionUnlockEntity> captor =
                ArgumentCaptor.forClass(PlayerRegionUnlockEntity.class);
        verify(regionUnlockMapper).insert(captor.capture());
        assertEquals("MAP_AREA_MEADOW", captor.getValue().getRegionId());
        assertEquals("SAVE_1", captor.getValue().getSaveId());
    }

    @Test
    void forceRefresh_generatesNewSession() {
        when(mapSessionMapper.selectOne(any())).thenReturn(null);
        devService.forceRefresh();
        ArgumentCaptor<PlayerMapSessionEntity> captor =
                ArgumentCaptor.forClass(PlayerMapSessionEntity.class);
        verify(mapSessionMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getSessionId());
        assertEquals("MAP_START_VILLAGE", captor.getValue().getMapId());
    }

    @Test
    void forceElite_setsContextFlag() {
        assertFalse(devContext.consumeForceElite());
        devService.forceElite();
        assertTrue(devContext.consumeForceElite());
    }

    @Test
    void forceRandomEvent_setsContextFlag() {
        assertFalse(devContext.consumeForceRandomEvent());
        devService.forceRandomEvent();
        assertTrue(devContext.consumeForceRandomEvent());
    }

    // ==================== Boss ====================

    @Test
    void unlockBossDifficulty_insertsRecord() {
        when(bossDifficultyUnlockMapper.selectCount(any())).thenReturn(0L);
        devService.unlockBossDifficulty("BOSS_MEADOW_GUARDIAN", "NORMAL");
        verify(bossDifficultyUnlockMapper).insert(any(BossDifficultyUnlockEntity.class));
    }

    @Test
    void setBossLuck_updatesExisting() {
        BossLuckEntity luck = new BossLuckEntity("SAVE_1", "BOSS_MEADOW_GUARDIAN", 10);
        when(bossLuckMapper.selectOne(any())).thenReturn(luck);

        devService.setBossLuck("BOSS_MEADOW_GUARDIAN", 66);
        assertEquals(66, luck.getLuckValue());
        verify(bossLuckMapper).updateById(luck);
    }

    @Test
    void setBossDefeatCount_insertsWhenMissing() {
        when(bossDefeatCountMapper.selectOne(any())).thenReturn(null);
        devService.setBossDefeatCount("BOSS_MEADOW_GUARDIAN", "NORMAL", 5);
        ArgumentCaptor<BossDefeatCountEntity> captor =
                ArgumentCaptor.forClass(BossDefeatCountEntity.class);
        verify(bossDefeatCountMapper).insert(captor.capture());
        assertEquals(5, captor.getValue().getDefeatCount());
    }

    @Test
    void forceBossDrop_unlocksAllRarities() {
        when(bossDropUnlockMapper.selectCount(any())).thenReturn(0L);
        devService.forceBossDrop("BOSS_MEADOW_GUARDIAN");
        verify(bossDropUnlockMapper, atLeastOnce()).insert(any(BossDropUnlockEntity.class));
    }

    @Test
    void bossOperations_backupBeforeHighRisk() {
        when(bossLuckMapper.selectOne(any())).thenReturn(null);
        devService.setBossLuck("BOSS_MEADOW_GUARDIAN", 50);
        verify(saveBackupService).createBackup("dev-before");
    }

    // ==================== 战斗调试 ====================

    @Test
    void battleDebug_togglesPersistentSwitches() {
        devService.setPlayerInvincible(true);
        devService.setPlayerOneHitKill(true);
        devService.setPlayerFixedCrit(true);
        devService.setDebugDamage(true);

        Map<String, Object> state = devService.getBattleDebugState();
        assertEquals(Boolean.TRUE, state.get("playerInvincible"));
        assertEquals(Boolean.TRUE, state.get("playerOneHitKill"));
        assertEquals(Boolean.TRUE, state.get("playerFixedCrit"));
        assertEquals(Boolean.TRUE, state.get("debugDamage"));

        // 关闭后恢复
        devService.setPlayerInvincible(false);
        devService.setPlayerOneHitKill(false);
        devService.setPlayerFixedCrit(false);
        devService.setDebugDamage(false);
        Map<String, Object> off = devService.getBattleDebugState();
        assertEquals(Boolean.FALSE, off.get("playerInvincible"));
        assertEquals(Boolean.FALSE, off.get("playerOneHitKill"));
        assertEquals(Boolean.FALSE, off.get("playerFixedCrit"));
        assertEquals(Boolean.FALSE, off.get("debugDamage"));
    }

    @Test
    void battleDebug_fixedSeedIsOneShot() {
        devService.setFixedBattleSeed(424242L);
        assertEquals(424242L, devService.getBattleDebugState().get("fixedSeed"));
        // 消费后清除（BattleService 消费）
        devContext.consumeFixedBattleSeed();
        assertNull(devService.getBattleDebugState().get("fixedSeed"));
    }

    @Test
    void battleDebug_logsEachToggle() {
        devService.setPlayerInvincible(true);
        verify(operationLogMapper).insert(argThat((DevOperationLogEntity r) ->
                "dev.battle.invincible".equals(r.getAction())));
    }
}