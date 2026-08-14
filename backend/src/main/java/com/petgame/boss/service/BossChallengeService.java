package com.petgame.boss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.achievement.service.AchievementService;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleUnit;
import com.petgame.boss.entity.PlayerBossChallengeEntity;
import com.petgame.boss.mapper.PlayerBossChallengeMapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossChallengesConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
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
 * Boss 挑战目标服务（阶段 11，规划决策四）。
 * <p>
 * 每个主 Boss 设 4 个挑战目标，仅在击败场次中判定，任意难度均可计入。
 * 单目标首次完成发放一次性实用奖励，并联动成就系统；集齐某 Boss 全部目标授予专属称号。
 * 判定在战斗结算点触发（REQUIRES_NEW，失败不阻断主流程）。
 */
@Service
public class BossChallengeService {

    private static final Logger log = LoggerFactory.getLogger(BossChallengeService.class);

    private final GameConfigRegistry registry;
    private final PlayerBossChallengeMapper challengeMapper;
    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper inventoryMapper;
    private final StatisticsService statisticsService;
    private final AchievementService achievementService;

    public BossChallengeService(GameConfigRegistry registry,
                                PlayerBossChallengeMapper challengeMapper,
                                PlayerMapper playerMapper,
                                PlayerInventoryMapper inventoryMapper,
                                StatisticsService statisticsService,
                                AchievementService achievementService) {
        this.registry = registry;
        this.challengeMapper = challengeMapper;
        this.playerMapper = playerMapper;
        this.inventoryMapper = inventoryMapper;
        this.statisticsService = statisticsService;
        this.achievementService = achievementService;
    }

    // ==================== 判定入口（战斗结算调用）====================

    /**
     * Boss 战斗结算后判定挑战目标（仅玩家获胜时评估）。
     *
     * @param saveId    存档 ID
     * @param ctx       战斗上下文
     * @param playerWon 玩家是否获胜
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBossBattle(String saveId, BattleContext ctx, boolean playerWon) {
        try {
            if (!playerWon || !"BOSS".equals(ctx.getBattleType()) || ctx.getBossId() == null) {
                return;
            }
            doJudge(saveId, ctx);
        } catch (Exception e) {
            log.warn("Boss 挑战判定异常（不阻断主流程）：saveId={}, battleId={}, error={}",
                    saveId, ctx.getBattleId(), e.getMessage());
        }
    }

    private void doJudge(String saveId, BattleContext ctx) {
        BossChallengesConfig.BossChallengeGroup group = registry.getBossChallengeGroup(ctx.getBossId());
        if (group == null || group.getChallenges() == null) {
            return;
        }
        Set<String> completed = loadCompleted(saveId, ctx.getBossId());
        int newlyCompleted = 0;
        for (BossChallengesConfig.ChallengeConfig ch : group.getChallenges()) {
            if (completed.contains(ch.getChallengeId())) {
                continue;
            }
            if (judgeChallenge(ch, ctx)) {
                completeChallenge(saveId, ctx.getBossId(), ch);
                newlyCompleted++;
            }
        }
        if (newlyCompleted > 0) {
            // 统计 + 联动成就（成就系统按统计值重新评估）
            statisticsService.increment(saveId, StatisticsService.ST_BOSS_CHALLENGES, newlyCompleted);
            achievementService.checkAchievements(saveId);
        }
    }

    private boolean judgeChallenge(BossChallengesConfig.ChallengeConfig ch, BattleContext ctx) {
        return switch (ch.getType()) {
            case "TURN_LIMIT" -> ctx.getCurrentRound() <= ch.getValue();
            case "NO_RECOVERY_ITEM" -> ctx.getConsumedRecoveryItems() == null
                    || ctx.getConsumedRecoveryItems().isEmpty();
            case "NO_PET_FAINTED" -> !anyPlayerPetFainted(ctx);
            case "MULTI_ELEMENT" -> distinctPlayerElements(ctx) >= ch.getValue();
            default -> false;
        };
    }

    private boolean anyPlayerPetFainted(BattleContext ctx) {
        for (BattleEvent ev : ctx.getEvents()) {
            if (ev.getType() == BattleEventType.PET_DEFEATED
                    && ev.getTargetId() != null && ev.getTargetId().startsWith("P_")) {
                return true;
            }
        }
        return false;
    }

    private int distinctPlayerElements(BattleContext ctx) {
        Set<String> elements = new HashSet<>();
        for (BattleUnit unit : ctx.getPlayerSide().getUnits()) {
            if (unit.getElement() != null && unit.getSpeciesId() != null) {
                elements.add(unit.getElement());
            }
        }
        return elements.size();
    }

    private void completeChallenge(String saveId, String bossId, BossChallengesConfig.ChallengeConfig ch) {
        PlayerBossChallengeEntity ent = new PlayerBossChallengeEntity();
        ent.setSaveId(saveId);
        ent.setBossId(bossId);
        ent.setChallengeId(ch.getChallengeId());
        ent.setCompletedAt(LocalDateTime.now());
        try {
            challengeMapper.insert(ent);
        } catch (Exception e) {
            log.warn("Boss 挑战重复写入（忽略）：saveId={}, bossId={}, challengeId={}",
                    saveId, bossId, ch.getChallengeId());
            return;
        }
        grantRewards(saveId, ch.getRewards());
        log.info("Boss 挑战完成：saveId={}, bossId={}, challengeId={}", saveId, bossId, ch.getChallengeId());
    }

    private void grantRewards(String saveId, List<BossChallengesConfig.RewardEntry> rewards) {
        if (rewards == null) {
            return;
        }
        PlayerEntity player = playerMapper.selectOne(
                new LambdaQueryWrapper<PlayerEntity>().eq(PlayerEntity::getSaveId, saveId));
        if (player == null) {
            return;
        }
        for (BossChallengesConfig.RewardEntry r : rewards) {
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
                default -> log.warn("Boss 挑战奖励类型未知：{}", r.getType());
            }
        }
        playerMapper.updateById(player);
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

    // ==================== 查询 ====================

    private Set<String> loadCompleted(String saveId, String bossId) {
        Set<String> set = new LinkedHashSet<>();
        List<PlayerBossChallengeEntity> rows = challengeMapper.selectList(
                new LambdaQueryWrapper<PlayerBossChallengeEntity>()
                        .eq(PlayerBossChallengeEntity::getSaveId, saveId)
                        .eq(PlayerBossChallengeEntity::getBossId, bossId));
        for (PlayerBossChallengeEntity row : rows) {
            set.add(row.getChallengeId());
        }
        return set;
    }

    /** 全部 Boss 挑战目标列表（含完成状态与集齐称号判断）。 */
    public List<Map<String, Object>> listChallenges(String saveId) {
        List<Map<String, Object>> result = new ArrayList<>();
        BossChallengesConfig cfg = registry.getBossChallengesConfig();
        if (cfg == null || cfg.getGroups() == null) {
            return result;
        }
        for (BossChallengesConfig.BossChallengeGroup group : cfg.getGroups()) {
            if (group.getBossId() == null || group.getChallenges() == null) {
                continue;
            }
            Set<String> completed = loadCompleted(saveId, group.getBossId());
            List<Map<String, Object>> challList = new ArrayList<>();
            for (BossChallengesConfig.ChallengeConfig ch : group.getChallenges()) {
                Map<String, Object> vo = new HashMap<>();
                vo.put("challengeId", ch.getChallengeId());
                vo.put("type", ch.getType());
                vo.put("name", ch.getName());
                vo.put("description", ch.getDescription());
                vo.put("value", ch.getValue());
                vo.put("completed", completed.contains(ch.getChallengeId()));
                vo.put("achievementId", ch.getAchievementId());
                challList.add(vo);
            }
            Map<String, Object> groupVo = new HashMap<>();
            groupVo.put("bossId", group.getBossId());
            groupVo.put("completionTitleId", group.getCompletionTitleId());
            groupVo.put("allCompleted", completed.containsAll(
                    group.getChallenges().stream()
                            .map(BossChallengesConfig.ChallengeConfig::getChallengeId).toList()));
            groupVo.put("challenges", challList);
            result.add(groupVo);
        }
        return result;
    }
}