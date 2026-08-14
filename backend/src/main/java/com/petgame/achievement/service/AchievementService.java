package com.petgame.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.achievement.entity.PlayerAchievementEntity;
import com.petgame.achievement.mapper.PlayerAchievementMapper;
import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.AchievementsConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.pokedex.vo.PokedexEntryVo;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.mapper.PlayerQuestMapper;
import com.petgame.statistics.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成就服务（阶段 11，需求 §110）。
 * <p>
 * 事件记录驱动：由既有结算点调用 {@link #checkAchievements(String)} 触发，
 * 从玩家状态快照重新评估全部配置成就，满足条件即解锁并发放一次性奖励。
 * 不构建成就依赖树/规则引擎；成就仅记录与奖励，不反向影响战斗数值。
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    /** 主 Boss ID（普通难度首通即视为达成，隐藏/精英不计入）。 */
    private static final Set<String> MAIN_BOSS_IDS = Set.of(
            "BOSS_MEADOW_GUARDIAN", "BOSS_FOREST_KING", "BOSS_WATERS_GUARDIAN",
            "BOSS_THUNDER_GUARDIAN", "BOSS_RUINS_GUARDIAN");

    private final GameConfigRegistry registry;
    private final PlayerAchievementMapper achievementMapper;
    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper inventoryMapper;
    private final PlayerPetMapper petMapper;
    private final PlayerQuestMapper questMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final BossDefeatCountMapper bossDefeatCountMapper;
    private final StatisticsService statisticsService;
    private final PokedexService pokedexService;

    public AchievementService(GameConfigRegistry registry,
                              PlayerAchievementMapper achievementMapper,
                              PlayerMapper playerMapper,
                              PlayerInventoryMapper inventoryMapper,
                              PlayerPetMapper petMapper,
                              PlayerQuestMapper questMapper,
                              PlayerRegionUnlockMapper regionUnlockMapper,
                              BossDefeatCountMapper bossDefeatCountMapper,
                              StatisticsService statisticsService,
                              PokedexService pokedexService) {
        this.registry = registry;
        this.achievementMapper = achievementMapper;
        this.playerMapper = playerMapper;
        this.inventoryMapper = inventoryMapper;
        this.petMapper = petMapper;
        this.questMapper = questMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.bossDefeatCountMapper = bossDefeatCountMapper;
        this.statisticsService = statisticsService;
        this.pokedexService = pokedexService;
    }

    // ==================== 查询 ====================

    /** 全部成就列表（含解锁状态；隐藏成就未解锁时不暴露）。 */
    public List<Map<String, Object>> listAchievements(String saveId) {
        Set<String> unlocked = loadUnlocked(saveId);
        List<Map<String, Object>> result = new ArrayList<>();
        AchievementsConfig cfg = registry.getAchievementsConfig();
        if (cfg == null || cfg.getAchievements() == null) {
            return result;
        }
        for (AchievementsConfig.AchievementConfig ach : cfg.getAchievements()) {
            boolean isUnlocked = unlocked.contains(ach.getId());
            if (ach.isHidden() && !isUnlocked) {
                continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", ach.getId());
            vo.put("name", ach.getName());
            vo.put("description", ach.getDescription());
            vo.put("category", ach.getCategory());
            vo.put("hidden", ach.isHidden());
            vo.put("unlocked", isUnlocked);
            vo.put("titleId", ach.getTitleId());
            vo.put("avatarId", ach.getAvatarId());
            if (isUnlocked) {
                PlayerAchievementEntity ent = achievementMapper.selectOne(
                        new LambdaQueryWrapper<PlayerAchievementEntity>()
                                .eq(PlayerAchievementEntity::getSaveId, saveId)
                                .eq(PlayerAchievementEntity::getAchievementId, ach.getId()));
                vo.put("unlockedAt", ent == null ? null : ent.getUnlockedAt());
            }
            result.add(vo);
        }
        return result;
    }

    /** 已解锁成就 ID 集合。 */
    public Set<String> loadUnlocked(String saveId) {
        Set<String> set = new LinkedHashSet<>();
        List<PlayerAchievementEntity> rows = achievementMapper.selectList(
                new LambdaQueryWrapper<PlayerAchievementEntity>()
                        .eq(PlayerAchievementEntity::getSaveId, saveId));
        for (PlayerAchievementEntity row : rows) {
            set.add(row.getAchievementId());
        }
        return set;
    }

    // ==================== 事件钩子 ====================

    /**
     * 事件驱动成就算法入口（REQUIRES_NEW，失败不阻断主流程）。
     * 在任意结算点事件后调用，重新评估全部成就并解锁新达成项。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAchievements(String saveId) {
        try {
            doCheckAchievements(saveId);
        } catch (Exception e) {
            log.warn("成就检查异常（不阻断主流程）：saveId={}, error={}", saveId, e.getMessage());
        }
    }

    private void doCheckAchievements(String saveId) {
        PlayerEntity player = playerMapper.selectOne(
                new LambdaQueryWrapper<PlayerEntity>().eq(PlayerEntity::getSaveId, saveId));
        if (player == null) {
            return;
        }
        PlayerState state = buildState(saveId, player);
        Set<String> unlocked = loadUnlocked(saveId);

        AchievementsConfig cfg = registry.getAchievementsConfig();
        if (cfg == null || cfg.getAchievements() == null) {
            return;
        }
        for (AchievementsConfig.AchievementConfig ach : cfg.getAchievements()) {
            if (ach.getId() == null || unlocked.contains(ach.getId())) {
                continue;
            }
            if (evaluateCondition(ach, state)) {
                unlockAndReward(saveId, ach, player);
            }
        }
    }

    /** 解锁成就并发放一次性奖励。 */
    private void unlockAndReward(String saveId, AchievementsConfig.AchievementConfig ach, PlayerEntity player) {
        PlayerAchievementEntity ent = new PlayerAchievementEntity();
        ent.setSaveId(saveId);
        ent.setAchievementId(ach.getId());
        ent.setUnlockedAt(LocalDateTime.now());
        try {
            achievementMapper.insert(ent);
        } catch (Exception e) {
            // 并发/重复插入保护
            log.warn("成就解锁重复插入（忽略）：saveId={}, achievementId={}", saveId, ach.getId());
            return;
        }
        grantRewards(saveId, ach.getRewards(), player);
        log.info("成就解锁：saveId={}, achievementId={}, name={}", saveId, ach.getId(), ach.getName());
    }

    /** 发放奖励（金币/经验直接加到玩家，道具加入背包）。 */
    private void grantRewards(String saveId, List<AchievementsConfig.RewardEntry> rewards, PlayerEntity player) {
        if (rewards == null) {
            return;
        }
        for (AchievementsConfig.RewardEntry r : rewards) {
            if (r.getType() == null) {
                continue;
            }
            switch (r.getType()) {
                case "GOLD" -> {
                    player.setGold(player.getGold() + r.getQuantity());
                    statisticsService.increment(saveId, StatisticsService.ST_GOLD_EARNED, r.getQuantity());
                }
                case "EXP" -> {
                    player.setExpPool(player.getExpPool() + r.getQuantity());
                    statisticsService.increment(saveId, StatisticsService.ST_EXP_EARNED, r.getQuantity());
                }
                case "ITEM" -> addInventoryItem(saveId, r.getItemId(), r.getQuantity());
                default -> log.warn("成就奖励类型未知：{}", r.getType());
            }
        }
        if (player.getId() != null) {
            playerMapper.updateById(player);
        }
    }

    private void addInventoryItem(String saveId, String itemId, int quantity) {
        PlayerInventoryEntity existing = inventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, saveId)
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            inventoryMapper.updateById(existing);
        } else {
            PlayerInventoryEntity inv = new PlayerInventoryEntity();
            inv.setSaveId(saveId);
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            inventoryMapper.insert(inv);
        }
    }

    // ==================== 条件评估 ====================

    /** 玩家状态快照（单次评估内共享，避免重复查询）。 */
    private static class PlayerState {
        Map<String, Long> stats = new HashMap<>();
        long gold;
        long expPool;
        boolean storyCompleted;
        int discoveredCount;
        int capturedCount;
        Set<String> discoveredSpecies = new HashSet<>();
        Set<String> capturedSpecies = new HashSet<>();
        Map<String, Integer> researchLevels = new HashMap<>();
        Set<String> unlockedRegions = new HashSet<>();
        Set<String> mainBossDefeated = new HashSet<>();
        int completedQuestCount;
        int maxPetLevel;
        int maxLevelPets;
        boolean eliteCaptured;
        boolean specialAppearanceCaptured;
    }

    private PlayerState buildState(String saveId, PlayerEntity player) {
        PlayerState s = new PlayerState();
        s.stats = statisticsService.getAllStats(saveId);
        s.gold = player.getGold() == null ? 0 : player.getGold();
        s.expPool = player.getExpPool() == null ? 0 : player.getExpPool();
        s.storyCompleted = Boolean.TRUE.equals(player.getStoryCompleted());

        // 图鉴：发现/捕获种数 + 研究等级
        List<PokedexEntryVo> pokedex = pokedexService.getFullPokedex(saveId);
        for (PokedexEntryVo entry : pokedex) {
            if (entry.isSeen()) {
                s.discoveredCount++;
                s.discoveredSpecies.add(entry.getSpeciesId());
            }
            if (entry.isCaught()) {
                s.capturedCount++;
                s.capturedSpecies.add(entry.getSpeciesId());
                if (entry.getResearchLevel() >= 5) {
                    s.researchLevels.put(entry.getSpeciesId(), entry.getResearchLevel());
                }
            }
        }

        // 区域解锁
        List<PlayerRegionUnlockEntity> regions = regionUnlockMapper.selectList(
                new LambdaQueryWrapper<PlayerRegionUnlockEntity>()
                        .eq(PlayerRegionUnlockEntity::getSaveId, saveId));
        for (PlayerRegionUnlockEntity r : regions) {
            s.unlockedRegions.add(r.getRegionId());
        }

        // 主 Boss 普通难度首通
        for (String bossId : MAIN_BOSS_IDS) {
            BossDefeatCountEntity row = bossDefeatCountMapper.selectOne(
                    new LambdaQueryWrapper<BossDefeatCountEntity>()
                            .eq(BossDefeatCountEntity::getSaveId, saveId)
                            .eq(BossDefeatCountEntity::getBossId, bossId)
                            .eq(BossDefeatCountEntity::getDifficulty, "NORMAL"));
            if (row != null && row.getDefeatCount() != null && row.getDefeatCount() > 0) {
                s.mainBossDefeated.add(bossId);
            }
        }

        // 已完成任务数
        List<PlayerQuestEntity> quests = questMapper.selectList(
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, saveId)
                        .eq(PlayerQuestEntity::getStatus, "COMPLETED"));
        s.completedQuestCount = quests.size();

        // 宠物等级
        List<PlayerPetEntity> pets = petMapper.selectList(
                new LambdaQueryWrapper<PlayerPetEntity>().eq(PlayerPetEntity::getSaveId, saveId));
        for (PlayerPetEntity p : pets) {
            int lv = p.getLevel() == null ? 1 : p.getLevel();
            if (lv > s.maxPetLevel) {
                s.maxPetLevel = lv;
            }
            if (lv >= 50) {
                s.maxLevelPets++;
            }
        }

        s.eliteCaptured = s.stats.getOrDefault(StatisticsService.ST_ELITE_CAPTURED, 0L) > 0;
        s.specialAppearanceCaptured = s.stats.getOrDefault(StatisticsService.ST_SPECIAL_APPEARANCE_CAPTURED, 0L) > 0;
        return s;
    }

    private boolean evaluateCondition(AchievementsConfig.AchievementConfig ach, PlayerState s) {
        String type = ach.getConditionType();
        String value = ach.getConditionValue();
        int count = ach.getConditionCount();
        return switch (type) {
            case "STAT_GE" -> s.stats.getOrDefault(value, 0L) >= count;
            case "CAPTURE_SPECIES_COUNT" -> s.capturedCount >= count;
            case "DISCOVER_SPECIES_COUNT" -> s.discoveredCount >= count;
            case "CAPTURE_SPECIFIC" -> s.capturedSpecies.contains(value);
            case "DISCOVER_SPECIFIC" -> s.discoveredSpecies.contains(value);
            case "REGION_UNLOCK_COUNT" -> s.unlockedRegions.size() >= count;
            case "REGION_UNLOCK_SPECIFIC" -> s.unlockedRegions.contains(value);
            case "BOSS_DEFEAT_COUNT" -> s.stats.getOrDefault(StatisticsService.ST_BOSS_DEFEATED, 0L) >= count;
            case "BOSS_DEFEAT_ALL_MAIN" -> s.mainBossDefeated.containsAll(MAIN_BOSS_IDS);
            case "BOSS_CHALLENGE_COUNT" -> s.stats.getOrDefault(StatisticsService.ST_BOSS_CHALLENGES, 0L) >= count;
            case "POKEDEX_RESEARCH_LEVEL" -> s.researchLevels.getOrDefault(value, 0) >= count;
            case "QUEST_COMPLETE_COUNT" -> s.completedQuestCount >= count;
            case "MAIN_QUEST_COMPLETE" -> s.storyCompleted;
            case "PET_LEVEL_MAX" -> s.maxPetLevel >= 50;
            case "PET_LEVEL_SPECIFIC" -> s.maxPetLevel >= count;
            case "MAX_LEVEL_PETS_COUNT" -> s.maxLevelPets >= count;
            case "POKEDEX_RESEARCHED_5_COUNT" -> s.researchLevels.size() >= count;
            case "SPECIAL_APPEARANCE_CAPTURE" -> s.specialAppearanceCaptured;
            case "ELITE_CAPTURE" -> s.eliteCaptured;
            case "GOLD_GE" -> s.gold >= count;
            case "EXP_POOL_GE" -> s.expPool >= count;
            default -> false;
        };
    }
}