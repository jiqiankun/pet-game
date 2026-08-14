package com.petgame.save;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 存档备份服务（阶段 14）。
 * <p>
 * 提供存档导出 / 导入 / 自动备份 / 重置游戏能力。导出为自定义
 * {@code .pet-save.zip}（含 manifest.json + save.json），不导出数据库物理文件。
 * <p>
 * 导入流程：校验文件 → 检查 saveVersion → 自动备份当前存档 → 事务内导入 → 校验 → 提交；
 * 失败回滚，原存档不受影响。
 */
@Service
public class SaveBackupService {

    private static final Logger log = LoggerFactory.getLogger(SaveBackupService.class);

    private static final String MANIFEST_FILE = "manifest.json";
    private static final String SAVE_FILE = "save.json";
    private static final DateTimeFormatter FILE_TS = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd-HHmmss")
            .toFormatter();

    private final ObjectMapper objectMapper;
    private final GameProperties gameProperties;

    // 玩家 / 宠物 / 队伍 / 背包
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final GameSettingMapper gameSettingMapper;

    // 地图探索
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final PlayerCampActivationMapper campActivationMapper;
    private final PlayerChestLootMapper chestLootMapper;
    private final PlayerMapSessionMapper mapSessionMapper;
    private final PlayerGatherUsedMapper gatherUsedMapper;
    private final PlayerAmbushTriggeredMapper ambushTriggeredMapper;
    private final PlayerRandomEventUsedMapper randomEventUsedMapper;

    // Boss
    private final BossDefeatCountMapper bossDefeatCountMapper;
    private final BossDifficultyUnlockMapper bossDifficultyUnlockMapper;
    private final BossLuckMapper bossLuckMapper;
    private final BossDropUnlockMapper bossDropUnlockMapper;
    private final BossManualClearMapper bossManualClearMapper;
    private final PlayerBossChallengeMapper bossChallengeMapper;
    private final BossEncounterSnapshotMapper bossEncounterSnapshotMapper;

    // 图鉴
    private final PokedexMapper pokedexMapper;
    private final PokedexHistoryMapper pokedexHistoryMapper;

    // 任务 / 教学 / 对话
    private final PlayerQuestMapper questMapper;
    private final PlayerQuestObjectiveMapper questObjectiveMapper;
    private final PlayerDialogueMapper dialogueMapper;
    private final PlayerTutorialMapper tutorialMapper;
    private final PlayerMapChangeMapper mapChangeMapper;
    private final PlayerHiddenTriggerMapper hiddenTriggerMapper;

    // 成就 / 统计
    private final PlayerAchievementMapper achievementMapper;
    private final PlayerStatisticMapper statisticMapper;

    public SaveBackupService(ObjectMapper objectMapper,
                             GameProperties gameProperties,
                             PlayerMapper playerMapper,
                             PlayerPetMapper playerPetMapper,
                             PlayerPetSkillMapper playerPetSkillMapper,
                             PlayerTeamMapper playerTeamMapper,
                             PlayerTeamMemberMapper playerTeamMemberMapper,
                             PlayerInventoryMapper playerInventoryMapper,
                             GameSettingMapper gameSettingMapper,
                             PlayerRegionUnlockMapper regionUnlockMapper,
                             PlayerCampActivationMapper campActivationMapper,
                             PlayerChestLootMapper chestLootMapper,
                             PlayerMapSessionMapper mapSessionMapper,
                             PlayerGatherUsedMapper gatherUsedMapper,
                             PlayerAmbushTriggeredMapper ambushTriggeredMapper,
                             PlayerRandomEventUsedMapper randomEventUsedMapper,
                             BossDefeatCountMapper bossDefeatCountMapper,
                             BossDifficultyUnlockMapper bossDifficultyUnlockMapper,
                             BossLuckMapper bossLuckMapper,
                             BossDropUnlockMapper bossDropUnlockMapper,
                             BossManualClearMapper bossManualClearMapper,
                             PlayerBossChallengeMapper bossChallengeMapper,
                             BossEncounterSnapshotMapper bossEncounterSnapshotMapper,
                             PokedexMapper pokedexMapper,
                             PokedexHistoryMapper pokedexHistoryMapper,
                             PlayerQuestMapper questMapper,
                             PlayerQuestObjectiveMapper questObjectiveMapper,
                             PlayerDialogueMapper dialogueMapper,
                             PlayerTutorialMapper tutorialMapper,
                             PlayerMapChangeMapper mapChangeMapper,
                             PlayerHiddenTriggerMapper hiddenTriggerMapper,
                             PlayerAchievementMapper achievementMapper,
                             PlayerStatisticMapper statisticMapper) {
        this.objectMapper = objectMapper;
        this.gameProperties = gameProperties;
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.gameSettingMapper = gameSettingMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.campActivationMapper = campActivationMapper;
        this.chestLootMapper = chestLootMapper;
        this.mapSessionMapper = mapSessionMapper;
        this.gatherUsedMapper = gatherUsedMapper;
        this.ambushTriggeredMapper = ambushTriggeredMapper;
        this.randomEventUsedMapper = randomEventUsedMapper;
        this.bossDefeatCountMapper = bossDefeatCountMapper;
        this.bossDifficultyUnlockMapper = bossDifficultyUnlockMapper;
        this.bossLuckMapper = bossLuckMapper;
        this.bossDropUnlockMapper = bossDropUnlockMapper;
        this.bossManualClearMapper = bossManualClearMapper;
        this.bossChallengeMapper = bossChallengeMapper;
        this.bossEncounterSnapshotMapper = bossEncounterSnapshotMapper;
        this.pokedexMapper = pokedexMapper;
        this.pokedexHistoryMapper = pokedexHistoryMapper;
        this.questMapper = questMapper;
        this.questObjectiveMapper = questObjectiveMapper;
        this.dialogueMapper = dialogueMapper;
        this.tutorialMapper = tutorialMapper;
        this.mapChangeMapper = mapChangeMapper;
        this.hiddenTriggerMapper = hiddenTriggerMapper;
        this.achievementMapper = achievementMapper;
        this.statisticMapper = statisticMapper;
    }

    /** 是否存在存档。 */
    public boolean hasSave() {
        return playerMapper.selectCount(null) > 0;
    }

    /** 当前玩家（无存档返回 null）。 */
    public PlayerEntity currentPlayer() {
        return playerMapper.selectOne(null);
    }

    // ============================================================
    // 导出
    // ============================================================

    /**
     * 导出当前存档为 {@code .pet-save.zip} 字节流。
     */
    public byte[] exportSaveBytes() {
        PlayerEntity player = requirePlayer();
        SaveSnapshot snapshot = readAll(player.getSaveId());

        SaveManifest manifest = new SaveManifest();
        manifest.setGameVersion(player.getGameVersion() != null ? player.getGameVersion() : gameProperties.getVersion());
        manifest.setSaveVersion(player.getSaveVersion() != null ? player.getSaveVersion() : gameProperties.getSaveVersion());
        manifest.setExportedAt(LocalDateTime.now());
        manifest.setPlayerName(player.getPlayerName());

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry(MANIFEST_FILE));
                zos.write(objectMapper.writeValueAsBytes(manifest));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry(SAVE_FILE));
                zos.write(objectMapper.writeValueAsBytes(snapshot));
                zos.closeEntry();
            }
            log.info("存档导出完成：saveId={}, 玩家={}, saveVersion={}", player.getSaveId(), player.getPlayerName(), manifest.getSaveVersion());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("SAVE_EXPORT_FAILED", "存档导出失败：" + e.getMessage());
        }
    }

    /**
     * 生成导出的下载文件名。
     */
    public String exportFileName() {
        PlayerEntity player = requirePlayer();
        return "pet-save-" + player.getSaveId() + "-" + LocalDateTime.now().format(FILE_TS) + ".pet-save.zip";
    }

    // ============================================================
    // 导入
    // ============================================================

    /**
     * 导入存档：先自动备份当前存档，再事务内导入，失败回滚。
     *
     * @param zipBytes 上传的 .pet-save.zip
     */
    @Transactional
    public void importSave(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException("INVALID_SAVE_FILE", "存档文件为空");
        }
        ImportedData data = parseZip(zipBytes);
        SaveManifest manifest = data.manifest();
        SaveSnapshot snapshot = data.snapshot();

        // 校验 saveVersion：仅允许兼容（等于或低于当前）的存档
        int currentVersion = gameProperties.getSaveVersion();
        if (manifest.getSaveVersion() > currentVersion) {
            throw new BusinessException("SAVE_VERSION_TOO_NEW",
                    "存档数据结构版本 " + manifest.getSaveVersion() + " 高于当前 " + currentVersion
                            + "，请升级游戏后再导入");
        }

        PlayerEntity oldPlayer = playerMapper.selectOne(null);

        // 导入前必须先自动备份当前数据
        if (oldPlayer != null) {
            createBackup("import-before");
        }

        // 事务内导入（含旧的引数据清理 + 快照重插入）
        applySnapshot(snapshot);

        log.info("存档导入完成：saveId={}, 玩家={}, saveVersion={}",
                snapshot.getPlayer().getSaveId(), snapshot.getPlayer().getPlayerName(), manifest.getSaveVersion());
    }

    /** 解析 zip：读取 manifest + save.json。 */
    private ImportedData parseZip(byte[] zipBytes) {
        SaveManifest manifest = null;
        SaveSnapshot snapshot = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] content = readAll(zis);
                if (MANIFEST_FILE.equals(entry.getName())) {
                    manifest = objectMapper.readValue(content, SaveManifest.class);
                } else if (SAVE_FILE.equals(entry.getName())) {
                    snapshot = objectMapper.readValue(content, SaveSnapshot.class);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("INVALID_SAVE_FILE", "存档文件解析失败（非法的 .pet-save.zip）：" + e.getMessage());
        }
        if (manifest == null || snapshot == null || snapshot.getPlayer() == null) {
            throw new BusinessException("INVALID_SAVE_FILE", "存档文件缺少 manifest 或 save 数据");
        }
        return new ImportedData(manifest, snapshot);
    }

    // ============================================================
    // 自动备份 / 重置
    // ============================================================

    /**
     * 自动备份当前存档到备份目录。
     *
     * @param reason 备份原因（用于文件名标识）
     * @return 生成的备份文件名
     */
    public String createBackup(String reason) {
        PlayerEntity player = requirePlayer();
        byte[] zip = exportSaveBytes();
        String safeReason = reason == null ? "manual" : reason.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = "backup-" + player.getSaveId() + "-" + safeReason + "-"
                + LocalDateTime.now().format(FILE_TS) + ".pet-save.zip";
        try {
            Path dir = Paths.get(gameProperties.getBackupDir());
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.write(target, zip);
            log.info("自动备份完成：{}（原因={}）", target, reason);
            return filename;
        } catch (IOException e) {
            throw new BusinessException("BACKUP_FAILED", "自动备份失败：" + e.getMessage());
        }
    }

    /**
     * 重置游戏：先自动备份，再清空当前存档数据。
     */
    @Transactional
    public void resetGame() {
        PlayerEntity player = requirePlayer();
        createBackup("reset-before");
        deleteAll(player.getSaveId());
        log.info("游戏重置完成：saveId={}", player.getSaveId());
    }

    /** 列出备份目录中的备份文件。 */
    public List<String> listBackups() {
        Path dir = Paths.get(gameProperties.getBackupDir());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".pet-save.zip"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            log.warn("读取备份目录失败：{}", e.getMessage());
        }
        return names;
    }

    // ============================================================
    // 快照读取
    // ============================================================

    /** 读取某存档的全部玩家逻辑数据。 */
    public SaveSnapshot readAll(String saveId) {
        SaveSnapshot s = new SaveSnapshot();
        s.setPlayer(playerMapper.selectOne(new LambdaQueryWrapper<PlayerEntity>().eq(PlayerEntity::getSaveId, saveId)));
        s.setPets(playerPetMapper.selectList(new LambdaQueryWrapper<PlayerPetEntity>().eq(PlayerPetEntity::getSaveId, saveId)));
        s.setTeams(playerTeamMapper.selectList(new LambdaQueryWrapper<PlayerTeamEntity>().eq(PlayerTeamEntity::getSaveId, saveId)));
        s.setInventory(playerInventoryMapper.selectList(new LambdaQueryWrapper<PlayerInventoryEntity>().eq(PlayerInventoryEntity::getSaveId, saveId)));
        s.setSettings(gameSettingMapper.selectList(new LambdaQueryWrapper<GameSettingEntity>().eq(GameSettingEntity::getSaveId, saveId)));

        // 宠物技能 / 队伍成员 通过父级 id 关联（非 save_id 直接键）
        List<Long> petIds = s.getPets().stream().map(PlayerPetEntity::getId).toList();
        if (!petIds.isEmpty()) {
            s.setPetSkills(playerPetSkillMapper.selectList(
                    new LambdaQueryWrapper<PlayerPetSkillEntity>().in(PlayerPetSkillEntity::getPetId, petIds)));
        }
        List<Long> teamIds = s.getTeams().stream().map(PlayerTeamEntity::getId).toList();
        if (!teamIds.isEmpty()) {
            s.setTeamMembers(playerTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<PlayerTeamMemberEntity>().in(PlayerTeamMemberEntity::getTeamId, teamIds)));
        }

        s.setRegionUnlocks(regionUnlockMapper.selectList(new LambdaQueryWrapper<PlayerRegionUnlockEntity>().eq(PlayerRegionUnlockEntity::getSaveId, saveId)));
        s.setCampActivations(campActivationMapper.selectList(new LambdaQueryWrapper<PlayerCampActivationEntity>().eq(PlayerCampActivationEntity::getSaveId, saveId)));
        s.setChestLoots(chestLootMapper.selectList(new LambdaQueryWrapper<PlayerChestLootEntity>().eq(PlayerChestLootEntity::getSaveId, saveId)));
        s.setMapSessions(mapSessionMapper.selectList(new LambdaQueryWrapper<PlayerMapSessionEntity>().eq(PlayerMapSessionEntity::getSaveId, saveId)));
        s.setGatherUsed(gatherUsedMapper.selectList(new LambdaQueryWrapper<PlayerGatherUsedEntity>().eq(PlayerGatherUsedEntity::getSaveId, saveId)));
        s.setAmbushTriggered(ambushTriggeredMapper.selectList(new LambdaQueryWrapper<PlayerAmbushTriggeredEntity>().eq(PlayerAmbushTriggeredEntity::getSaveId, saveId)));
        s.setRandomEventUsed(randomEventUsedMapper.selectList(new LambdaQueryWrapper<PlayerRandomEventUsedEntity>().eq(PlayerRandomEventUsedEntity::getSaveId, saveId)));

        s.setBossDefeatCounts(bossDefeatCountMapper.selectList(new LambdaQueryWrapper<BossDefeatCountEntity>().eq(BossDefeatCountEntity::getSaveId, saveId)));
        s.setBossDifficultyUnlocks(bossDifficultyUnlockMapper.selectList(new LambdaQueryWrapper<BossDifficultyUnlockEntity>().eq(BossDifficultyUnlockEntity::getSaveId, saveId)));
        s.setBossLucks(bossLuckMapper.selectList(new LambdaQueryWrapper<BossLuckEntity>().eq(BossLuckEntity::getSaveId, saveId)));
        s.setBossDropUnlocks(bossDropUnlockMapper.selectList(new LambdaQueryWrapper<BossDropUnlockEntity>().eq(BossDropUnlockEntity::getSaveId, saveId)));
        s.setBossManualClears(bossManualClearMapper.selectList(new LambdaQueryWrapper<BossManualClearEntity>().eq(BossManualClearEntity::getSaveId, saveId)));
        s.setBossChallenges(bossChallengeMapper.selectList(new LambdaQueryWrapper<PlayerBossChallengeEntity>().eq(PlayerBossChallengeEntity::getSaveId, saveId)));
        s.setBossEncounterSnapshots(bossEncounterSnapshotMapper.selectList(new LambdaQueryWrapper<BossEncounterSnapshotEntity>().eq(BossEncounterSnapshotEntity::getSaveId, saveId)));

        s.setPokedex(pokedexMapper.selectList(new LambdaQueryWrapper<PokedexEntity>().eq(PokedexEntity::getSaveId, saveId)));
        s.setPokedexHistory(pokedexHistoryMapper.selectList(new LambdaQueryWrapper<PokedexHistoryEntity>().eq(PokedexHistoryEntity::getSaveId, saveId)));

        s.setQuests(questMapper.selectList(new LambdaQueryWrapper<PlayerQuestEntity>().eq(PlayerQuestEntity::getSaveId, saveId)));
        s.setQuestObjectives(questObjectiveMapper.selectList(new LambdaQueryWrapper<PlayerQuestObjectiveEntity>().eq(PlayerQuestObjectiveEntity::getSaveId, saveId)));
        s.setDialogues(dialogueMapper.selectList(new LambdaQueryWrapper<PlayerDialogueEntity>().eq(PlayerDialogueEntity::getSaveId, saveId)));
        s.setTutorials(tutorialMapper.selectList(new LambdaQueryWrapper<PlayerTutorialEntity>().eq(PlayerTutorialEntity::getSaveId, saveId)));
        s.setMapChanges(mapChangeMapper.selectList(new LambdaQueryWrapper<PlayerMapChangeEntity>().eq(PlayerMapChangeEntity::getSaveId, saveId)));
        s.setHiddenTriggers(hiddenTriggerMapper.selectList(new LambdaQueryWrapper<PlayerHiddenTriggerEntity>().eq(PlayerHiddenTriggerEntity::getSaveId, saveId)));

        s.setAchievements(achievementMapper.selectList(new LambdaQueryWrapper<PlayerAchievementEntity>().eq(PlayerAchievementEntity::getSaveId, saveId)));
        s.setStatistics(statisticMapper.selectList(new LambdaQueryWrapper<PlayerStatisticEntity>().eq(PlayerStatisticEntity::getSaveId, saveId)));
        return s;
    }

    // ============================================================
    // 事务内应用快照
    // ============================================================

    /** 事务内导入：清空旧数据 → 重插入快照（宠物/队伍 id 重映射）。 */
    private void applySnapshot(SaveSnapshot snapshot) {
        PlayerEntity player = snapshot.getPlayer();
        String newSaveId = player.getSaveId();

        // 1. player
        player.setId(null);
        playerMapper.insert(player);

        // 2. 宠物（记录旧 id → 新 id）
        Map<Long, Long> petIdMap = new HashMap<>();
        for (PlayerPetEntity pet : snapshot.getPets()) {
            Long oldId = pet.getId();
            pet.setId(null);
            pet.setSaveId(newSaveId);
            playerPetMapper.insert(pet);
            petIdMap.put(oldId, pet.getId());
        }
        // 宠物技能（重映射 petId）
        for (PlayerPetSkillEntity skill : snapshot.getPetSkills()) {
            skill.setId(null);
            skill.setPetId(petIdMap.get(skill.getPetId()));
            playerPetSkillMapper.insert(skill);
        }

        // 3. 队伍（记录旧 id → 新 id）
        Map<Long, Long> teamIdMap = new HashMap<>();
        for (PlayerTeamEntity team : snapshot.getTeams()) {
            Long oldId = team.getId();
            team.setId(null);
            team.setSaveId(newSaveId);
            playerTeamMapper.insert(team);
            teamIdMap.put(oldId, team.getId());
        }
        // 队伍成员（重映射 teamId / petId）
        for (PlayerTeamMemberEntity member : snapshot.getTeamMembers()) {
            member.setId(null);
            member.setTeamId(teamIdMap.get(member.getTeamId()));
            member.setPetId(petIdMap.get(member.getPetId()));
            playerTeamMemberMapper.insert(member);
        }

        // 4. 其余 save_id 键表
        for (PlayerInventoryEntity e : snapshot.getInventory()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            playerInventoryMapper.insert(e);
        }
        for (GameSettingEntity e : snapshot.getSettings()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            gameSettingMapper.insert(e);
        }
        for (PlayerRegionUnlockEntity e : snapshot.getRegionUnlocks()) {
            e.setSaveId(newSaveId);
            regionUnlockMapper.insert(e);
        }
        for (PlayerCampActivationEntity e : snapshot.getCampActivations()) {
            e.setSaveId(newSaveId);
            campActivationMapper.insert(e);
        }
        for (PlayerChestLootEntity e : snapshot.getChestLoots()) {
            e.setSaveId(newSaveId);
            chestLootMapper.insert(e);
        }
        for (PlayerMapSessionEntity e : snapshot.getMapSessions()) {
            e.setSaveId(newSaveId);
            mapSessionMapper.insert(e);
        }
        for (PlayerGatherUsedEntity e : snapshot.getGatherUsed()) {
            e.setSaveId(newSaveId);
            gatherUsedMapper.insert(e);
        }
        for (PlayerAmbushTriggeredEntity e : snapshot.getAmbushTriggered()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            ambushTriggeredMapper.insert(e);
        }
        for (PlayerRandomEventUsedEntity e : snapshot.getRandomEventUsed()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            randomEventUsedMapper.insert(e);
        }
        for (BossDefeatCountEntity e : snapshot.getBossDefeatCounts()) {
            e.setSaveId(newSaveId);
            bossDefeatCountMapper.insert(e);
        }
        for (BossDifficultyUnlockEntity e : snapshot.getBossDifficultyUnlocks()) {
            e.setSaveId(newSaveId);
            bossDifficultyUnlockMapper.insert(e);
        }
        for (BossLuckEntity e : snapshot.getBossLucks()) {
            e.setSaveId(newSaveId);
            bossLuckMapper.insert(e);
        }
        for (BossDropUnlockEntity e : snapshot.getBossDropUnlocks()) {
            e.setSaveId(newSaveId);
            bossDropUnlockMapper.insert(e);
        }
        for (BossManualClearEntity e : snapshot.getBossManualClears()) {
            e.setSaveId(newSaveId);
            bossManualClearMapper.insert(e);
        }
        for (PlayerBossChallengeEntity e : snapshot.getBossChallenges()) {
            e.setSaveId(newSaveId);
            bossChallengeMapper.insert(e);
        }
        for (BossEncounterSnapshotEntity e : snapshot.getBossEncounterSnapshots()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            bossEncounterSnapshotMapper.insert(e);
        }
        for (PokedexEntity e : snapshot.getPokedex()) {
            e.setSaveId(newSaveId);
            pokedexMapper.insert(e);
        }
        for (PokedexHistoryEntity e : snapshot.getPokedexHistory()) {
            e.setSaveId(newSaveId);
            pokedexHistoryMapper.insert(e);
        }
        for (PlayerQuestEntity e : snapshot.getQuests()) {
            e.setSaveId(newSaveId);
            questMapper.insert(e);
        }
        for (PlayerQuestObjectiveEntity e : snapshot.getQuestObjectives()) {
            e.setSaveId(newSaveId);
            questObjectiveMapper.insert(e);
        }
        for (PlayerDialogueEntity e : snapshot.getDialogues()) {
            e.setSaveId(newSaveId);
            dialogueMapper.insert(e);
        }
        for (PlayerTutorialEntity e : snapshot.getTutorials()) {
            e.setSaveId(newSaveId);
            tutorialMapper.insert(e);
        }
        for (PlayerMapChangeEntity e : snapshot.getMapChanges()) {
            e.setSaveId(newSaveId);
            mapChangeMapper.insert(e);
        }
        for (PlayerHiddenTriggerEntity e : snapshot.getHiddenTriggers()) {
            e.setSaveId(newSaveId);
            hiddenTriggerMapper.insert(e);
        }
        for (PlayerAchievementEntity e : snapshot.getAchievements()) {
            e.setSaveId(newSaveId);
            achievementMapper.insert(e);
        }
        for (PlayerStatisticEntity e : snapshot.getStatistics()) {
            e.setId(null);
            e.setSaveId(newSaveId);
            statisticMapper.insert(e);
        }
    }

    /** 清空某存档的全部数据（重置游戏 / 导入前清理共用）。 */
    private void deleteAll(String saveId) {
        // 先取关联 id（宠物技能 / 队伍成员）
        List<Long> petIds = playerPetMapper.selectList(
                        new LambdaQueryWrapper<PlayerPetEntity>().eq(PlayerPetEntity::getSaveId, saveId))
                .stream().map(PlayerPetEntity::getId).toList();
        List<Long> teamIds = playerTeamMapper.selectList(
                        new LambdaQueryWrapper<PlayerTeamEntity>().eq(PlayerTeamEntity::getSaveId, saveId))
                .stream().map(PlayerTeamEntity::getId).toList();
        if (!petIds.isEmpty()) {
            playerPetSkillMapper.delete(new LambdaQueryWrapper<PlayerPetSkillEntity>().in(PlayerPetSkillEntity::getPetId, petIds));
        }
        if (!teamIds.isEmpty()) {
            playerTeamMemberMapper.delete(new LambdaQueryWrapper<PlayerTeamMemberEntity>().in(PlayerTeamMemberEntity::getTeamId, teamIds));
        }

        playerPetMapper.delete(new LambdaQueryWrapper<PlayerPetEntity>().eq(PlayerPetEntity::getSaveId, saveId));
        playerTeamMapper.delete(new LambdaQueryWrapper<PlayerTeamEntity>().eq(PlayerTeamEntity::getSaveId, saveId));
        playerInventoryMapper.delete(new LambdaQueryWrapper<PlayerInventoryEntity>().eq(PlayerInventoryEntity::getSaveId, saveId));
        gameSettingMapper.delete(new LambdaQueryWrapper<GameSettingEntity>().eq(GameSettingEntity::getSaveId, saveId));
        regionUnlockMapper.delete(new LambdaQueryWrapper<PlayerRegionUnlockEntity>().eq(PlayerRegionUnlockEntity::getSaveId, saveId));
        campActivationMapper.delete(new LambdaQueryWrapper<PlayerCampActivationEntity>().eq(PlayerCampActivationEntity::getSaveId, saveId));
        chestLootMapper.delete(new LambdaQueryWrapper<PlayerChestLootEntity>().eq(PlayerChestLootEntity::getSaveId, saveId));
        mapSessionMapper.delete(new LambdaQueryWrapper<PlayerMapSessionEntity>().eq(PlayerMapSessionEntity::getSaveId, saveId));
        gatherUsedMapper.delete(new LambdaQueryWrapper<PlayerGatherUsedEntity>().eq(PlayerGatherUsedEntity::getSaveId, saveId));
        ambushTriggeredMapper.delete(new LambdaQueryWrapper<PlayerAmbushTriggeredEntity>().eq(PlayerAmbushTriggeredEntity::getSaveId, saveId));
        randomEventUsedMapper.delete(new LambdaQueryWrapper<PlayerRandomEventUsedEntity>().eq(PlayerRandomEventUsedEntity::getSaveId, saveId));
        bossDefeatCountMapper.delete(new LambdaQueryWrapper<BossDefeatCountEntity>().eq(BossDefeatCountEntity::getSaveId, saveId));
        bossDifficultyUnlockMapper.delete(new LambdaQueryWrapper<BossDifficultyUnlockEntity>().eq(BossDifficultyUnlockEntity::getSaveId, saveId));
        bossLuckMapper.delete(new LambdaQueryWrapper<BossLuckEntity>().eq(BossLuckEntity::getSaveId, saveId));
        bossDropUnlockMapper.delete(new LambdaQueryWrapper<BossDropUnlockEntity>().eq(BossDropUnlockEntity::getSaveId, saveId));
        bossManualClearMapper.delete(new LambdaQueryWrapper<BossManualClearEntity>().eq(BossManualClearEntity::getSaveId, saveId));
        bossChallengeMapper.delete(new LambdaQueryWrapper<PlayerBossChallengeEntity>().eq(PlayerBossChallengeEntity::getSaveId, saveId));
        bossEncounterSnapshotMapper.delete(new LambdaQueryWrapper<BossEncounterSnapshotEntity>().eq(BossEncounterSnapshotEntity::getSaveId, saveId));
        pokedexMapper.delete(new LambdaQueryWrapper<PokedexEntity>().eq(PokedexEntity::getSaveId, saveId));
        pokedexHistoryMapper.delete(new LambdaQueryWrapper<PokedexHistoryEntity>().eq(PokedexHistoryEntity::getSaveId, saveId));
        questMapper.delete(new LambdaQueryWrapper<PlayerQuestEntity>().eq(PlayerQuestEntity::getSaveId, saveId));
        questObjectiveMapper.delete(new LambdaQueryWrapper<PlayerQuestObjectiveEntity>().eq(PlayerQuestObjectiveEntity::getSaveId, saveId));
        dialogueMapper.delete(new LambdaQueryWrapper<PlayerDialogueEntity>().eq(PlayerDialogueEntity::getSaveId, saveId));
        tutorialMapper.delete(new LambdaQueryWrapper<PlayerTutorialEntity>().eq(PlayerTutorialEntity::getSaveId, saveId));
        mapChangeMapper.delete(new LambdaQueryWrapper<PlayerMapChangeEntity>().eq(PlayerMapChangeEntity::getSaveId, saveId));
        hiddenTriggerMapper.delete(new LambdaQueryWrapper<PlayerHiddenTriggerEntity>().eq(PlayerHiddenTriggerEntity::getSaveId, saveId));
        achievementMapper.delete(new LambdaQueryWrapper<PlayerAchievementEntity>().eq(PlayerAchievementEntity::getSaveId, saveId));
        statisticMapper.delete(new LambdaQueryWrapper<PlayerStatisticEntity>().eq(PlayerStatisticEntity::getSaveId, saveId));
        playerMapper.delete(new LambdaQueryWrapper<PlayerEntity>().eq(PlayerEntity::getSaveId, saveId));
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    private static byte[] readAll(ZipInputStream zis) throws IOException {
        return zis.readAllBytes();
    }

    /** zip 解析结果。 */
    private record ImportedData(SaveManifest manifest, SaveSnapshot snapshot) {
    }
}