package com.petgame.save;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.petgame.achievement.entity.PlayerAchievementEntity;
import com.petgame.achievement.mapper.PlayerAchievementMapper;
import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.entity.BossDifficultyUnlockEntity;
import com.petgame.boss.entity.BossDropUnlockEntity;
import com.petgame.boss.entity.BossEncounterSnapshotEntity;
import com.petgame.boss.entity.BossLuckEntity;
import com.petgame.boss.entity.BossManualClearEntity;
import com.petgame.boss.entity.PlayerBossChallengeEntity;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.boss.mapper.BossDifficultyUnlockMapper;
import com.petgame.boss.mapper.BossDropUnlockMapper;
import com.petgame.boss.mapper.BossEncounterSnapshotMapper;
import com.petgame.boss.mapper.BossLuckMapper;
import com.petgame.boss.mapper.BossManualClearMapper;
import com.petgame.boss.mapper.PlayerBossChallengeMapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameProperties;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerAmbushTriggeredEntity;
import com.petgame.map.entity.PlayerCampActivationEntity;
import com.petgame.map.entity.PlayerChestLootEntity;
import com.petgame.map.entity.PlayerGatherUsedEntity;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.entity.PlayerRandomEventUsedEntity;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerAmbushTriggeredMapper;
import com.petgame.map.mapper.PlayerCampActivationMapper;
import com.petgame.map.mapper.PlayerChestLootMapper;
import com.petgame.map.mapper.PlayerGatherUsedMapper;
import com.petgame.map.mapper.PlayerMapSessionMapper;
import com.petgame.map.mapper.PlayerRandomEventUsedMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.pokedex.entity.PokedexEntity;
import com.petgame.pokedex.entity.PokedexHistoryEntity;
import com.petgame.pokedex.mapper.PokedexHistoryMapper;
import com.petgame.pokedex.mapper.PokedexMapper;
import com.petgame.quest.entity.PlayerDialogueEntity;
import com.petgame.quest.entity.PlayerHiddenTriggerEntity;
import com.petgame.quest.entity.PlayerMapChangeEntity;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.entity.PlayerQuestObjectiveEntity;
import com.petgame.quest.entity.PlayerTutorialEntity;
import com.petgame.quest.mapper.PlayerDialogueMapper;
import com.petgame.quest.mapper.PlayerHiddenTriggerMapper;
import com.petgame.quest.mapper.PlayerMapChangeMapper;
import com.petgame.quest.mapper.PlayerQuestMapper;
import com.petgame.quest.mapper.PlayerQuestObjectiveMapper;
import com.petgame.quest.mapper.PlayerTutorialMapper;
import com.petgame.save.mapper.GameSettingEntity;
import com.petgame.save.mapper.GameSettingMapper;
import com.petgame.statistics.entity.PlayerStatisticEntity;
import com.petgame.statistics.mapper.PlayerStatisticMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * SaveBackupService 单元测试（阶段 14 存档备份）。
 * <p>
 * 覆盖：存档存在性、导出（zip 含 manifest + save）、导入（版本校验 / 导入前自动备份 /
 * 失败回滚由事务保证）、自动备份、重置游戏、备份列表。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaveBackupServiceTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerPetMapper playerPetMapper;
    @Mock private PlayerPetSkillMapper playerPetSkillMapper;
    @Mock private PlayerTeamMapper playerTeamMapper;
    @Mock private PlayerTeamMemberMapper playerTeamMemberMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;
    @Mock private GameSettingMapper gameSettingMapper;
    @Mock private PlayerRegionUnlockMapper regionUnlockMapper;
    @Mock private PlayerCampActivationMapper campActivationMapper;
    @Mock private PlayerChestLootMapper chestLootMapper;
    @Mock private PlayerMapSessionMapper mapSessionMapper;
    @Mock private PlayerGatherUsedMapper gatherUsedMapper;
    @Mock private PlayerAmbushTriggeredMapper ambushTriggeredMapper;
    @Mock private PlayerRandomEventUsedMapper randomEventUsedMapper;
    @Mock private BossDefeatCountMapper bossDefeatCountMapper;
    @Mock private BossDifficultyUnlockMapper bossDifficultyUnlockMapper;
    @Mock private BossLuckMapper bossLuckMapper;
    @Mock private BossDropUnlockMapper bossDropUnlockMapper;
    @Mock private BossManualClearMapper bossManualClearMapper;
    @Mock private PlayerBossChallengeMapper bossChallengeMapper;
    @Mock private BossEncounterSnapshotMapper bossEncounterSnapshotMapper;
    @Mock private PokedexMapper pokedexMapper;
    @Mock private PokedexHistoryMapper pokedexHistoryMapper;
    @Mock private PlayerQuestMapper questMapper;
    @Mock private PlayerQuestObjectiveMapper questObjectiveMapper;
    @Mock private PlayerDialogueMapper dialogueMapper;
    @Mock private PlayerTutorialMapper tutorialMapper;
    @Mock private PlayerMapChangeMapper mapChangeMapper;
    @Mock private PlayerHiddenTriggerMapper hiddenTriggerMapper;
    @Mock private PlayerAchievementMapper achievementMapper;
    @Mock private PlayerStatisticMapper statisticMapper;

    private ObjectMapper objectMapper;
    private GameProperties props;
    private SaveBackupService service;

    @TempDir
    Path tempDir;

    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        props = new GameProperties();
        props.setBackupDir(tempDir.resolve("backups").toString());

        service = new SaveBackupService(objectMapper, props,
                playerMapper, playerPetMapper, playerPetSkillMapper, playerTeamMapper,
                playerTeamMemberMapper, playerInventoryMapper, gameSettingMapper,
                regionUnlockMapper, campActivationMapper, chestLootMapper, mapSessionMapper,
                gatherUsedMapper, ambushTriggeredMapper, randomEventUsedMapper,
                bossDefeatCountMapper, bossDifficultyUnlockMapper, bossLuckMapper,
                bossDropUnlockMapper, bossManualClearMapper, bossChallengeMapper,
                bossEncounterSnapshotMapper, pokedexMapper, pokedexHistoryMapper,
                questMapper, questObjectiveMapper, dialogueMapper, tutorialMapper,
                mapChangeMapper, hiddenTriggerMapper, achievementMapper, statisticMapper);

        player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setSaveVersion(1);
        player.setGameVersion("1.0.0");
        player.setPlayerName("测试玩家");
        player.setGold(100);
    }

    // ==================== 存在性 ====================

    @Test
    void hasSave_trueWhenPlayerExists() {
        when(playerMapper.selectCount(isNull())).thenReturn(1L);
        assertTrue(service.hasSave());
    }

    @Test
    void hasSave_falseWhenNoPlayer() {
        when(playerMapper.selectCount(isNull())).thenReturn(0L);
        assertFalse(service.hasSave());
    }

    @Test
    void currentPlayer_returnsPlayer() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        assertEquals("SAVE_1", service.currentPlayer().getSaveId());
    }

    // ==================== 导出 ====================

    @Test
    void exportSaveBytes_producesZipWithManifestAndSave() throws Exception {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectList(any())).thenReturn(List.of());
        when(playerTeamMapper.selectList(any())).thenReturn(List.of());
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of());
        when(gameSettingMapper.selectList(any())).thenReturn(List.of());
        when(regionUnlockMapper.selectList(any())).thenReturn(List.of());
        when(campActivationMapper.selectList(any())).thenReturn(List.of());
        when(chestLootMapper.selectList(any())).thenReturn(List.of());
        when(mapSessionMapper.selectList(any())).thenReturn(List.of());
        when(gatherUsedMapper.selectList(any())).thenReturn(List.of());
        when(ambushTriggeredMapper.selectList(any())).thenReturn(List.of());
        when(randomEventUsedMapper.selectList(any())).thenReturn(List.of());
        when(bossDefeatCountMapper.selectList(any())).thenReturn(List.of());
        when(bossDifficultyUnlockMapper.selectList(any())).thenReturn(List.of());
        when(bossLuckMapper.selectList(any())).thenReturn(List.of());
        when(bossDropUnlockMapper.selectList(any())).thenReturn(List.of());
        when(bossManualClearMapper.selectList(any())).thenReturn(List.of());
        when(bossChallengeMapper.selectList(any())).thenReturn(List.of());
        when(bossEncounterSnapshotMapper.selectList(any())).thenReturn(List.of());
        when(pokedexMapper.selectList(any())).thenReturn(List.of());
        when(pokedexHistoryMapper.selectList(any())).thenReturn(List.of());
        when(questMapper.selectList(any())).thenReturn(List.of());
        when(questObjectiveMapper.selectList(any())).thenReturn(List.of());
        when(dialogueMapper.selectList(any())).thenReturn(List.of());
        when(tutorialMapper.selectList(any())).thenReturn(List.of());
        when(mapChangeMapper.selectList(any())).thenReturn(List.of());
        when(hiddenTriggerMapper.selectList(any())).thenReturn(List.of());
        when(achievementMapper.selectList(any())).thenReturn(List.of());
        when(statisticMapper.selectList(any())).thenReturn(List.of());

        byte[] zip = service.exportSaveBytes();

        // 解包校验
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                bos.write(zis.readAllBytes());
            }
        }
        assertTrue(zip.length > 0, "导出 zip 不应为空");
        // 存在 manifest.json 与 save.json 两个条目
        int manifestCount = countEntry(zip, "manifest.json");
        int saveCount = countEntry(zip, "save.json");
        assertEquals(1, manifestCount);
        assertEquals(1, saveCount);
    }

    @Test
    void exportSaveBytes_noSave_throws() {
        when(playerMapper.selectOne(isNull())).thenReturn(null);
        assertThrows(BusinessException.class, service::exportSaveBytes);
    }

    @Test
    void exportFileName_containsSaveIdAndExtension() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        String name = service.exportFileName();
        assertTrue(name.startsWith("pet-save-SAVE_1-"));
        assertTrue(name.endsWith(".pet-save.zip"));
    }

    // ==================== 导入 ====================

    @Test
    void importSave_appliesSnapshotAndBacksUpOldSave() throws Exception {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectList(any())).thenReturn(List.of());
        when(playerTeamMapper.selectList(any())).thenReturn(List.of());
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of());
        when(gameSettingMapper.selectList(any())).thenReturn(List.of());
        when(regionUnlockMapper.selectList(any())).thenReturn(List.of());
        when(campActivationMapper.selectList(any())).thenReturn(List.of());
        when(chestLootMapper.selectList(any())).thenReturn(List.of());
        when(mapSessionMapper.selectList(any())).thenReturn(List.of());
        when(gatherUsedMapper.selectList(any())).thenReturn(List.of());
        when(ambushTriggeredMapper.selectList(any())).thenReturn(List.of());
        when(randomEventUsedMapper.selectList(any())).thenReturn(List.of());
        when(bossDefeatCountMapper.selectList(any())).thenReturn(List.of());
        when(bossDifficultyUnlockMapper.selectList(any())).thenReturn(List.of());
        when(bossLuckMapper.selectList(any())).thenReturn(List.of());
        when(bossDropUnlockMapper.selectList(any())).thenReturn(List.of());
        when(bossManualClearMapper.selectList(any())).thenReturn(List.of());
        when(bossChallengeMapper.selectList(any())).thenReturn(List.of());
        when(bossEncounterSnapshotMapper.selectList(any())).thenReturn(List.of());
        when(pokedexMapper.selectList(any())).thenReturn(List.of());
        when(pokedexHistoryMapper.selectList(any())).thenReturn(List.of());
        when(questMapper.selectList(any())).thenReturn(List.of());
        when(questObjectiveMapper.selectList(any())).thenReturn(List.of());
        when(dialogueMapper.selectList(any())).thenReturn(List.of());
        when(tutorialMapper.selectList(any())).thenReturn(List.of());
        when(mapChangeMapper.selectList(any())).thenReturn(List.of());
        when(hiddenTriggerMapper.selectList(any())).thenReturn(List.of());
        when(achievementMapper.selectList(any())).thenReturn(List.of());
        when(statisticMapper.selectList(any())).thenReturn(List.of());

        // 构造一个导入用快照（新玩家）
        PlayerEntity importPlayer = new PlayerEntity();
        importPlayer.setSaveId("SAVE_NEW");
        importPlayer.setSaveVersion(1);
        importPlayer.setGameVersion("1.0.0");
        importPlayer.setPlayerName("新玩家");
        importPlayer.setGold(500);

        SaveSnapshot snapshot = new SaveSnapshot();
        snapshot.setPlayer(importPlayer);
        snapshot.setPets(List.of());
        snapshot.setPetSkills(List.of());
        snapshot.setTeams(List.of());
        snapshot.setTeamMembers(List.of());
        snapshot.setInventory(List.of());
        snapshot.setSettings(List.of());

        SaveManifest manifest = new SaveManifest();
        manifest.setGameVersion("1.0.0");
        manifest.setSaveVersion(1);
        manifest.setPlayerName("新玩家");

        byte[] zip = buildZip(manifest, snapshot);
        service.importSave(zip);

        // 导入前应自动备份当前存档
        verify(playerPetMapper).selectList(any()); // 备份读取
        // 新的玩家数据被插入
        verify(playerMapper, atLeast(1)).insert(any(PlayerEntity.class));
    }

    @Test
    void importSave_versionTooNew_throws() throws Exception {
        when(playerMapper.selectOne(isNull())).thenReturn(player);

        SaveManifest manifest = new SaveManifest();
        manifest.setSaveVersion(99); // 高于当前
        SaveSnapshot snapshot = new SaveSnapshot();
        PlayerEntity p = new PlayerEntity();
        p.setSaveId("SAVE_X");
        snapshot.setPlayer(p);

        byte[] zip = buildZip(manifest, snapshot);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.importSave(zip));
        assertTrue(ex.getErrorCode().startsWith("SAVE_VERSION"));
    }

    @Test
    void importSave_emptyBytes_throws() {
        assertThrows(BusinessException.class, () -> service.importSave(new byte[0]));
    }

    @Test
    void importSave_invalidZip_throws() {
        assertThrows(BusinessException.class, () -> service.importSave("not-a-zip".getBytes()));
    }

    // ==================== 自动备份 / 重置 / 列表 ====================

    @Test
    void createBackup_writesFileToBackupDir() throws Exception {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectList(any())).thenReturn(List.of());
        when(playerTeamMapper.selectList(any())).thenReturn(List.of());
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of());
        when(gameSettingMapper.selectList(any())).thenReturn(List.of());

        String filename = service.createBackup("manual");
        assertTrue(filename.startsWith("backup-SAVE_1-manual-"));
        Path written = tempDir.resolve("backups").resolve(filename);
        assertTrue(Files.exists(written), "备份文件应已写入");
    }

    @Test
    void resetGame_backsUpThenDeletes() {
        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectList(any())).thenReturn(List.of());
        when(playerTeamMapper.selectList(any())).thenReturn(List.of());
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of());
        when(gameSettingMapper.selectList(any())).thenReturn(List.of());

        service.resetGame();
        // 重置前自动备份
        verify(playerPetMapper, atLeastOnce()).selectList(any());
        // 清空存档（含 player 删除）
        verify(playerMapper).delete(any());
    }

    @Test
    void listBackups_returnsSortedBackupNames() throws Exception {
        Path dir = tempDir.resolve("backups");
        Files.createDirectories(dir);
        Files.write(dir.resolve("backup-SAVE_1-a.pet-save.zip"), new byte[]{1});
        Files.write(dir.resolve("backup-SAVE_1-b.pet-save.zip"), new byte[]{2});

        List<String> names = service.listBackups();
        assertEquals(2, names.size());
        // 按文件名倒序
        assertTrue(names.get(0).compareTo(names.get(1)) > 0);
    }

    @Test
    void listBackups_noDir_returnsEmpty() {
        assertTrue(service.listBackups().isEmpty());
    }

    // ==================== 工具 ====================

    private int countEntry(byte[] zip, String name) throws Exception {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    count++;
                }
            }
        }
        return count;
    }

    private byte[] buildZip(SaveManifest manifest, SaveSnapshot snapshot) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(objectMapper.writeValueAsBytes(manifest));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("save.json"));
            zos.write(objectMapper.writeValueAsBytes(snapshot));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}