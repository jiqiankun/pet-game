package com.petgame.developer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.developer.mapper.DevOperationLogMapper;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.map.mapper.PlayerMapSessionMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.save.SaveBackupService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 开发者工具数据操作服务（阶段 14）。
 * <p>
 * 覆盖「数据操作类」开发者工具：资源（金币/经验池/道具）、宠物（添加/重置）、
 * 地图（解锁/强制刷新/强制精英/强制随机事件）、Boss（解锁难度/次数/幸运值/强制掉落/直达难度）。
 * <p>
 * 战斗调试类（无敌/一击必杀/固定暴击/固定随机种子/伤害明细）通过 DevContext 持久开关，
 * 开战时由 BattleService 快照到 BattleContext 生效。
 * <p>
 * 高风险数据修改操作前自动备份当前存档（需求文档 §50「开发者高风险操作前创建备份」）。
 * 所有操作写入开发者操作日志（dev_operation_log）。
 */
@Service
public class DevService {

    private static final Logger log = LoggerFactory.getLogger(DevService.class);

    /** 默认资质（未指定时使用）。 */
    private static final int DEFAULT_APTITUDE = 70;

    private final GameConfigRegistry registry;
    private final PetGrowthService growthService;
    private final DevContext devContext;
    private final SaveBackupService saveBackupService;
    private final DevOperationLogMapper operationLogMapper;

    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final PlayerMapSessionMapper mapSessionMapper;
    private final BossDefeatCountMapper bossDefeatCountMapper;
    private final BossDifficultyUnlockMapper bossDifficultyUnlockMapper;
    private final BossLuckMapper bossLuckMapper;
    private final BossDropUnlockMapper bossDropUnlockMapper;

    public DevService(GameConfigRegistry registry,
                      PetGrowthService growthService,
                      DevContext devContext,
                      SaveBackupService saveBackupService,
                      DevOperationLogMapper operationLogMapper,
                      PlayerMapper playerMapper,
                      PlayerPetMapper playerPetMapper,
                      PlayerPetSkillMapper playerPetSkillMapper,
                      PlayerInventoryMapper playerInventoryMapper,
                      PlayerRegionUnlockMapper regionUnlockMapper,
                      PlayerMapSessionMapper mapSessionMapper,
                      BossDefeatCountMapper bossDefeatCountMapper,
                      BossDifficultyUnlockMapper bossDifficultyUnlockMapper,
                      BossLuckMapper bossLuckMapper,
                      BossDropUnlockMapper bossDropUnlockMapper) {
        this.registry = registry;
        this.growthService = growthService;
        this.devContext = devContext;
        this.saveBackupService = saveBackupService;
        this.operationLogMapper = operationLogMapper;
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.mapSessionMapper = mapSessionMapper;
        this.bossDefeatCountMapper = bossDefeatCountMapper;
        this.bossDifficultyUnlockMapper = bossDifficultyUnlockMapper;
        this.bossLuckMapper = bossLuckMapper;
        this.bossDropUnlockMapper = bossDropUnlockMapper;
    }

    // ============================================================
    // 资源
    // ============================================================

    /** 增加金币。 */
    @Transactional
    public void grantGold(int amount) {
        if (amount <= 0) {
            throw new BusinessException("DEV_INVALID_AMOUNT", "数量必须为正数");
        }
        PlayerEntity player = requirePlayer();
        player.setGold(player.getGold() + amount);
        playerMapper.updateById(player);
        log("dev.grantGold", "金币 +" + amount);
    }

    /** 增加经验池。 */
    @Transactional
    public void grantExp(int amount) {
        if (amount <= 0) {
            throw new BusinessException("DEV_INVALID_AMOUNT", "数量必须为正数");
        }
        PlayerEntity player = requirePlayer();
        player.setExpPool(player.getExpPool() + amount);
        playerMapper.updateById(player);
        log("dev.grantExp", "经验池 +" + amount);
    }

    /** 添加道具（已存在则累加）。 */
    @Transactional
    public void grantItem(String itemId, int quantity) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessException("DEV_INVALID_ITEM", "道具 ID 不能为空");
        }
        if (quantity <= 0) {
            throw new BusinessException("DEV_INVALID_AMOUNT", "数量必须为正数");
        }
        ItemConfig item = registry.getItem(itemId);
        if (item == null) {
            throw new BusinessException("DEV_ITEM_NOT_FOUND", "道具配置不存在: " + itemId);
        }
        PlayerEntity player = requirePlayer();
        PlayerInventoryEntity existing = playerInventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, player.getSaveId())
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            playerInventoryMapper.updateById(existing);
        } else {
            PlayerInventoryEntity inv = new PlayerInventoryEntity();
            inv.setSaveId(player.getSaveId());
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            playerInventoryMapper.insert(inv);
        }
        log("dev.grantItem", itemId + " x" + quantity);
    }

    // ============================================================
    // 宠物
    // ============================================================

    /**
     * 添加宠物（指定等级 / 资质 / 稀有技能 / 特殊外观）。
     * 自动学习该等级已解锁的种族主动技能并装备到前 4 槽位。
     */
    @Transactional
    public Long addPet(AddPetRequest req) {
        PetSpeciesConfig species = registry.getSpecies(req.speciesId);
        if (species == null) {
            throw new BusinessException("DEV_SPECIES_NOT_FOUND", "种族不存在: " + req.speciesId);
        }
        int cap = registry.getSystemRules().getLevelCap();
        int level = req.level == null ? 1 : Math.max(1, Math.min(cap, req.level));
        PlayerEntity player = requirePlayer();
        backupBefore();

        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setSaveId(player.getSaveId());
        pet.setSpeciesId(species.getId());
        pet.setLevel(level);
        pet.setCapturedLevel(level);
        pet.setNickname(req.nickname);
        pet.setHpAptitude(apt(req.hpAptitude));
        pet.setStrengthAptitude(apt(req.strengthAptitude));
        pet.setSpiritAptitude(apt(req.spiritAptitude));
        pet.setDefenseAptitude(apt(req.defenseAptitude));
        pet.setResistanceAptitude(apt(req.resistanceAptitude));
        pet.setSpeedAptitude(apt(req.speedAptitude));
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        pet.setCurrentHp(growthService.computePanelStats(pet, species).getMaxHp());
        pet.setSpecialAppearance(req.specialAppearance);
        pet.setLocked(false);
        pet.setFavorite(false);
        pet.setBattleCount(0);
        pet.setWinCount(0);
        pet.setKillCount(0);
        pet.setBossBattleCount(0);
        pet.setBossWinCount(0);
        pet.setTotalDamage(0L);
        pet.setTotalDamageTaken(0L);
        pet.setTotalHeal(0L);
        pet.setCaptureAssistCount(0);
        pet.setCapturedMapId(player.getCurrentMapId());
        pet.setCapturedAt(LocalDateTime.now());
        playerPetMapper.insert(pet);

        // 学习已解锁的种族主动技能，装备到前 4 槽位
        int slot = 1;
        for (PetSpeciesConfig.SpeciesSkillSlot skillSlot : species.getSkills()) {
            if (skillSlot.getUnlockLevel() > level) {
                continue;
            }
            PlayerPetSkillEntity ps = new PlayerPetSkillEntity();
            ps.setPetId(pet.getId());
            ps.setSkillId(skillSlot.getSkillId());
            ps.setSourceType("LEVEL_UP");
            ps.setSlot(slot <= 4 ? slot : null);
            slot++;
            playerPetSkillMapper.insert(ps);
        }
        // 指定稀有技能（仅学习，不装备）
        if (req.rareSkillIds != null) {
            for (String skillId : req.rareSkillIds) {
                if (registry.getSkill(skillId) == null) {
                    continue;
                }
                PlayerPetSkillEntity ps = new PlayerPetSkillEntity();
                ps.setPetId(pet.getId());
                ps.setSkillId(skillId);
                ps.setSourceType("RARE");
                ps.setSlot(null);
                playerPetSkillMapper.insert(ps);
            }
        }

        log("dev.addPet", species.getId() + " Lv." + level + " → petId=" + pet.getId());
        return pet.getId();
    }

    /**
     * 重置宠物：清空战绩统计并把 HP 回满（开发调试用）。
     * 不删除宠物、不改变等级/资质/技能。
     */
    @Transactional
    public void resetPet(Long petId) {
        PlayerEntity player = requirePlayer();
        PlayerPetEntity pet = playerPetMapper.selectById(petId);
        if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
            throw new BusinessException("DEV_PET_NOT_FOUND", "宠物不存在或不属于当前存档");
        }
        backupBefore();
        pet.setBattleCount(0);
        pet.setWinCount(0);
        pet.setKillCount(0);
        pet.setBossBattleCount(0);
        pet.setBossWinCount(0);
        pet.setTotalDamage(0L);
        pet.setTotalDamageTaken(0L);
        pet.setTotalHeal(0L);
        pet.setCaptureAssistCount(0);
        PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
        if (species != null) {
            pet.setCurrentHp(growthService.computePanelStats(pet, species).getMaxHp());
        }
        playerPetMapper.updateById(pet);
        log("dev.resetPet", "petId=" + petId);
    }

    // ============================================================
    // 地图
    // ============================================================

    /** 解锁区域（写入解锁记录）。 */
    @Transactional
    public void unlockRegion(String mapId) {
        PlayerEntity player = requirePlayer();
        Long count = regionUnlockMapper.selectCount(
                new LambdaQueryWrapper<PlayerRegionUnlockEntity>()
                        .eq(PlayerRegionUnlockEntity::getSaveId, player.getSaveId())
                        .eq(PlayerRegionUnlockEntity::getRegionId, mapId));
        if (count == null || count == 0) {
            backupBefore();
            regionUnlockMapper.insert(new PlayerRegionUnlockEntity(
                    player.getSaveId(), mapId, LocalDateTime.now()));
        }
        log("dev.unlockRegion", mapId);
    }

    /** 强制刷新当前区域（生成新的访问会话，触发野怪/采集刷新）。 */
    @Transactional
    public void forceRefresh() {
        PlayerEntity player = requirePlayer();
        if (player.getCurrentMapId() == null || player.getCurrentMapId().isBlank()) {
            player.setCurrentMapId(registry.getMapsConfig().getRegions().get(0).getId());
            playerMapper.updateById(player);
        }
        backupBefore();
        PlayerMapSessionEntity existing = mapSessionMapper.selectOne(
                new LambdaQueryWrapper<PlayerMapSessionEntity>()
                        .eq(PlayerMapSessionEntity::getSaveId, player.getSaveId())
                        .eq(PlayerMapSessionEntity::getMapId, player.getCurrentMapId())
                        .last("LIMIT 1"));
        if (existing != null) {
            mapSessionMapper.delete(new LambdaQueryWrapper<PlayerMapSessionEntity>()
                    .eq(PlayerMapSessionEntity::getSaveId, player.getSaveId())
                    .eq(PlayerMapSessionEntity::getMapId, player.getCurrentMapId()));
        }
        PlayerMapSessionEntity session = new PlayerMapSessionEntity(
                player.getSaveId(), player.getCurrentMapId(), UUID.randomUUID().toString(), LocalDateTime.now());
        mapSessionMapper.insert(session);
        log("dev.forceRefresh", player.getCurrentMapId());
    }

    /** 强制下一次野生遭遇为精英（一次性标志）。 */
    @Transactional
    public void forceElite() {
        devContext.setForceElite(true);
        log("dev.forceElite", "下一次野生遭遇强制精英");
    }

    /** 强制下一次探索触发随机事件（一次性标志）。 */
    @Transactional
    public void forceRandomEvent() {
        devContext.setForceRandomEvent(true);
        log("dev.forceRandomEvent", "下一次探索强制随机事件");
    }

    // ============================================================
    // Boss
    // ============================================================

    /** 解锁 Boss 指定难度。 */
    @Transactional
    public void unlockBossDifficulty(String bossId, String difficulty) {
        requireBoss(bossId);
        PlayerEntity player = requirePlayer();
        Long count = bossDifficultyUnlockMapper.selectCount(
                new LambdaQueryWrapper<BossDifficultyUnlockEntity>()
                        .eq(BossDifficultyUnlockEntity::getSaveId, player.getSaveId())
                        .eq(BossDifficultyUnlockEntity::getBossId, bossId)
                        .eq(BossDifficultyUnlockEntity::getDifficulty, difficulty));
        if (count == null || count == 0) {
            backupBefore();
            bossDifficultyUnlockMapper.insert(new BossDifficultyUnlockEntity(
                    player.getSaveId(), bossId, difficulty, LocalDateTime.now()));
        }
        log("dev.unlockBossDifficulty", bossId + "/" + difficulty);
    }

    /** 直达 Boss 难度：解锁该 Boss 指定难度（直达入口与解锁等价）。 */
    @Transactional
    public void directBossDifficulty(String bossId, String difficulty) {
        unlockBossDifficulty(bossId, difficulty);
        log("dev.directBossDifficulty", bossId + "/" + difficulty);
    }

    /** 设置 Boss 击败次数。 */
    @Transactional
    public void setBossDefeatCount(String bossId, String difficulty, int count) {
        requireBoss(bossId);
        if (count < 0) {
            throw new BusinessException("DEV_INVALID_COUNT", "次数不能为负");
        }
        PlayerEntity player = requirePlayer();
        backupBefore();
        BossDefeatCountEntity existing = bossDefeatCountMapper.selectOne(
                new LambdaQueryWrapper<BossDefeatCountEntity>()
                        .eq(BossDefeatCountEntity::getSaveId, player.getSaveId())
                        .eq(BossDefeatCountEntity::getBossId, bossId)
                        .eq(BossDefeatCountEntity::getDifficulty, difficulty));
        if (existing != null) {
            existing.setDefeatCount(count);
            bossDefeatCountMapper.updateById(existing);
        } else {
            bossDefeatCountMapper.insert(new BossDefeatCountEntity(player.getSaveId(), bossId, difficulty, count));
        }
        log("dev.setBossDefeatCount", bossId + "/" + difficulty + " = " + count);
    }

    /** 设置 Boss 幸运值。 */
    @Transactional
    public void setBossLuck(String bossId, int luck) {
        requireBoss(bossId);
        if (luck < 0) {
            throw new BusinessException("DEV_INVALID_LUCK", "幸运值不能为负");
        }
        PlayerEntity player = requirePlayer();
        backupBefore();
        BossLuckEntity existing = bossLuckMapper.selectOne(
                new LambdaQueryWrapper<BossLuckEntity>()
                        .eq(BossLuckEntity::getSaveId, player.getSaveId())
                        .eq(BossLuckEntity::getBossId, bossId));
        if (existing != null) {
            existing.setLuckValue(luck);
            bossLuckMapper.updateById(existing);
        } else {
            bossLuckMapper.insert(new BossLuckEntity(player.getSaveId(), bossId, luck));
        }
        log("dev.setBossLuck", bossId + " = " + luck);
    }

    /** 强制 Boss 掉落：解锁该 Boss 全部难度的全部掉落情报。 */
    @Transactional
    public void forceBossDrop(String bossId) {
        BossesConfig.BossConfig boss = requireBoss(bossId);
        PlayerEntity player = requirePlayer();
        backupBefore();
        for (BossesConfig.DifficultyConfig diff : boss.getDifficulties().values()) {
            for (String rarity : diff.getDrops().keySet()) {
                Long count = bossDropUnlockMapper.selectCount(
                        new LambdaQueryWrapper<BossDropUnlockEntity>()
                                .eq(BossDropUnlockEntity::getSaveId, player.getSaveId())
                                .eq(BossDropUnlockEntity::getBossId, bossId)
                                .eq(BossDropUnlockEntity::getRarity, rarity));
                if (count == null || count == 0) {
                    bossDropUnlockMapper.insert(new BossDropUnlockEntity(
                            player.getSaveId(), bossId, rarity, LocalDateTime.now()));
                }
            }
        }
        log("dev.forceBossDrop", bossId);
    }

    // ============================================================
    // 战斗调试（阶段 14 开发者工具「战斗调试类」）
    // ============================================================
    // 开关均为持久状态，开战时由 BattleService 快照到 BattleContext；
    // 固定随机种子为一次性，下一次战斗消费后清除。

    /** 设置玩家方无敌开关。 */
    public void setPlayerInvincible(boolean on) {
        devContext.setPlayerInvincible(on);
        log("dev.battle.invincible", "玩家方无敌 = " + on);
    }

    /** 设置玩家方一击必杀开关。 */
    public void setPlayerOneHitKill(boolean on) {
        devContext.setPlayerOneHitKill(on);
        log("dev.battle.oneHitKill", "玩家方一击必杀 = " + on);
    }

    /** 设置玩家方固定暴击开关。 */
    public void setPlayerFixedCrit(boolean on) {
        devContext.setPlayerFixedCrit(on);
        log("dev.battle.fixedCrit", "玩家方固定暴击 = " + on);
    }

    /** 设置伤害明细/随机数调试开关。 */
    public void setDebugDamage(boolean on) {
        devContext.setDebugDamage(on);
        log("dev.battle.debugDamage", "伤害明细/随机数调试 = " + on);
    }

    /** 设置下一次战斗的固定随机种子（一次性）。 */
    public void setFixedBattleSeed(long seed) {
        devContext.setFixedBattleSeed(seed);
        log("dev.battle.fixedSeed", "固定随机种子 = " + seed);
    }

    /** 查询当前全部战斗调试开关状态（供前端展示）。 */
    public Map<String, Object> getBattleDebugState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerInvincible", devContext.isPlayerInvincible());
        state.put("playerOneHitKill", devContext.isPlayerOneHitKill());
        state.put("playerFixedCrit", devContext.isPlayerFixedCrit());
        state.put("debugDamage", devContext.isDebugDamage());
        state.put("fixedSeed", devContext.peekFixedBattleSeed());
        return state;
    }

    // ============================================================
    // 操作日志
    // ============================================================

    /** 查询最近的操作日志（默认 50 条）。 */
    public List<DevOperationLogEntity> listOperationLogs(int limit) {
        int size = limit > 0 && limit <= 200 ? limit : 50;
        return operationLogMapper.selectList(
                new LambdaQueryWrapper<DevOperationLogEntity>()
                        .orderByDesc(DevOperationLogEntity::getCreatedAt)
                        .last("LIMIT " + size));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 高风险数据修改操作前自动备份当前存档。 */
    private void backupBefore() {
        try {
            saveBackupService.createBackup("dev-before");
        } catch (Exception e) {
            // 备份失败不阻断开发者操作，但记录日志
            log.warn("开发者操作前自动备份失败：{}", e.getMessage());
        }
    }

    /** 写入一条开发者操作日志。 */
    private void log(String action, String detail) {
        try {
            PlayerEntity player = playerMapper.selectOne(null);
            DevOperationLogEntity record = new DevOperationLogEntity();
            record.setSaveId(player != null ? player.getSaveId() : null);
            record.setAction(action);
            record.setDetail(detail);
            operationLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("开发者操作日志写入失败：{}", e.getMessage());
        }
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    private BossesConfig.BossConfig requireBoss(String bossId) {
        BossesConfig.BossConfig boss = registry.getBoss(bossId);
        if (boss == null) {
            throw new BusinessException("DEV_BOSS_NOT_FOUND", "Boss 不存在: " + bossId);
        }
        return boss;
    }

    private static int apt(Integer value) {
        if (value == null) {
            return DEFAULT_APTITUDE;
        }
        return Math.max(0, Math.min(100, value));
    }

    /** 添加宠物请求。 */
    @Data
    public static class AddPetRequest {
        private String speciesId;
        private Integer level;
        /** 昵称（可选）。 */
        private String nickname;
        /** 特殊外观 ID（可选）。 */
        private String specialAppearance;
        private Integer hpAptitude;
        private Integer strengthAptitude;
        private Integer spiritAptitude;
        private Integer defenseAptitude;
        private Integer resistanceAptitude;
        private Integer speedAptitude;
        /** 额外稀有技能 ID（可选，仅学习不装备）。 */
        private List<String> rareSkillIds = new ArrayList<>();
    }
}