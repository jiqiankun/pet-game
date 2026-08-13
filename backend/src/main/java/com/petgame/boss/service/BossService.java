package com.petgame.boss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.battle.ai.BossDecisionProvider;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.service.BattleService;
import com.petgame.boss.entity.*;
import com.petgame.boss.mapper.*;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.mapper.PlayerMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Boss 服务（阶段 7：Boss 系统与重复挑战）。
 * <p>
 * 核心功能：Boss 开战/结算/自动挑战/幸运兑换/情报查询。
 */
@Service
public class BossService {

    private static final Logger log = LoggerFactory.getLogger(BossService.class);

    /** 情报解锁阈值：首次公开 COMMON、3 次解锁 RARE、6 次解锁 EPIC、10 次解锁 LEGENDARY。 */
    private static final Map<String, Integer> DROP_UNLOCK_THRESHOLDS = Map.of(
            "COMMON", 1, "RARE", 3, "EPIC", 6, "LEGENDARY", 10);

    /** 难度顺序。 */
    private static final List<String> DIFFICULTY_ORDER = List.of("NORMAL", "HARD", "NIGHTMARE");

    private final GameConfigRegistry registry;
    private final BossDecisionProvider bossDecisionProvider;
    private final WildEnemyDecisionProvider wildEnemyDecisionProvider;
    private final BattleService battleService;
    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final BossDefeatCountMapper defeatCountMapper;
    private final BossDifficultyUnlockMapper difficultyUnlockMapper;
    private final BossLuckMapper luckMapper;
    private final BossDropUnlockMapper dropUnlockMapper;
    private final BossManualClearMapper manualClearMapper;

    public BossService(GameConfigRegistry registry,
                       BossDecisionProvider bossDecisionProvider,
                       WildEnemyDecisionProvider wildEnemyDecisionProvider,
                       BattleService battleService,
                       PlayerMapper playerMapper,
                       PlayerInventoryMapper playerInventoryMapper,
                       BossDefeatCountMapper defeatCountMapper,
                       BossDifficultyUnlockMapper difficultyUnlockMapper,
                       BossLuckMapper luckMapper,
                       BossDropUnlockMapper dropUnlockMapper,
                       BossManualClearMapper manualClearMapper) {
        this.registry = registry;
        this.bossDecisionProvider = bossDecisionProvider;
        this.wildEnemyDecisionProvider = wildEnemyDecisionProvider;
        this.battleService = battleService;
        this.playerMapper = playerMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.defeatCountMapper = defeatCountMapper;
        this.difficultyUnlockMapper = difficultyUnlockMapper;
        this.luckMapper = luckMapper;
        this.dropUnlockMapper = dropUnlockMapper;
        this.manualClearMapper = manualClearMapper;
    }

    // ---- 情报查询 ----

    /** 获取全部 Boss 列表（含进度/幸运/情报）。 */
    public List<BossInfoDTO> getAllBossInfo(String saveId) {
        List<BossInfoDTO> result = new ArrayList<>();
        for (BossesConfig.BossConfig boss : registry.getBossesConfig().getBosses()) {
            result.add(buildBossInfoDTO(saveId, boss));
        }
        return result;
    }

    /** 获取单个 Boss 详情。 */
    public BossInfoDTO getBossInfo(String saveId, String bossId) {
        BossesConfig.BossConfig boss = registry.getBoss(bossId);
        if (boss == null) {
            throw new BusinessException("BOSS_NOT_FOUND", "Boss 不存在: " + bossId);
        }
        return buildBossInfoDTO(saveId, boss);
    }

    // ---- Boss 战斗 ----

    /**
     * 开始 Boss 战斗。
     * <p>
     * 校验 Boss 存在、难度已解锁、队伍有可战斗宠物。
     * 创建 BattleContext（battleType=BOSS，uncapturable=true）。
     * 委托给 BattleService 进行实际战斗管理。
     */
    @Transactional
    public String startBossBattle(String saveId, String bossId, String difficulty, Long seed) {
        BossesConfig.BossConfig boss = registry.getBoss(bossId);
        if (boss == null) {
            throw new BusinessException("BOSS_NOT_FOUND", "Boss 不存在: " + bossId);
        }
        BossesConfig.DifficultyConfig diffConfig = boss.getDifficulties().get(difficulty);
        if (diffConfig == null) {
            throw new BusinessException("INVALID_DIFFICULTY", "Boss 难度配置不存在: " + difficulty);
        }
        // 校验难度已解锁（NORMAL 始终解锁）
        if (!"NORMAL".equals(difficulty)) {
            BossDifficultyUnlockEntity unlock = difficultyUnlockMapper.selectOne(
                    new LambdaQueryWrapper<BossDifficultyUnlockEntity>()
                            .eq(BossDifficultyUnlockEntity::getSaveId, saveId)
                            .eq(BossDifficultyUnlockEntity::getBossId, bossId)
                            .eq(BossDifficultyUnlockEntity::getDifficulty, difficulty));
            if (unlock == null) {
                throw new BusinessException("DIFFICULTY_LOCKED", "Boss 难度未解锁: " + difficulty);
            }
        }

        // 委托给 BattleService 进行 Boss 战斗
        return battleService.startBossBattle(saveId, boss, diffConfig, bossId, difficulty, seed);
    }

    // ---- 自动挑战 ----

    /**
     * 自动挑战 Boss。
     * <p>
     * 模式：ONCE/FIVE/TEN/UNTIL_FAIL/UNTIL_LUCKY。
     * 前提：该 Boss+难度已手动击败过。
     */
    @Transactional
    public AutoChallengeResultDTO autoChallenge(String saveId, String bossId, String difficulty, String mode) {
        BossesConfig.BossConfig boss = registry.getBoss(bossId);
        if (boss == null) {
            throw new BusinessException("BOSS_NOT_FOUND", "Boss 不存在: " + bossId);
        }
        BossesConfig.DifficultyConfig diffConfig = boss.getDifficulties().get(difficulty);
        if (diffConfig == null) {
            throw new BusinessException("INVALID_DIFFICULTY", "Boss 难度配置不存在: " + difficulty);
        }
        // 校验已手动击败过
        BossManualClearEntity manualClear = manualClearMapper.selectOne(
                new LambdaQueryWrapper<BossManualClearEntity>()
                        .eq(BossManualClearEntity::getSaveId, saveId)
                        .eq(BossManualClearEntity::getBossId, bossId)
                        .eq(BossManualClearEntity::getDifficulty, difficulty));
        if (manualClear == null) {
            throw new BusinessException("AUTO_LOCKED", "该难度尚未手动击败，不允许自动挑战");
        }

        int maxBattles = switch (mode) {
            case "ONCE" -> 1;
            case "FIVE" -> 5;
            case "TEN" -> 10;
            case "UNTIL_FAIL" -> Integer.MAX_VALUE;
            case "UNTIL_LUCKY" -> Integer.MAX_VALUE;
            default -> throw new BusinessException("INVALID_MODE", "未知的自动挑战模式: " + mode);
        };

        int exchangeCost = registry.getSystemRules().getLuckyExchangeCost();
        int wins = 0, losses = 0, totalExp = 0, totalGold = 0;
        List<Map<String, Object>> totalDrops = new ArrayList<>();

        for (int i = 0; i < maxBattles; i++) {
            // 恢复全队 HP
            battleService.healTeamFully(saveId);

            // 执行战斗（createBossBattle 不 startBattle，runFullBattle 内部统一开战，避免登场被动重复触发）
            String battleId = battleService.createBossBattle(saveId, boss, diffConfig, bossId, difficulty, null);
            BattleContext ctx = battleService.getBattleContext(battleId);

            // 创建独立引擎跑完整战斗（敌方 = BossDecisionProvider，玩家方 = 自动 AI）
            BattleEngine autoEngine = new BattleEngine(registry, bossDecisionProvider);
            autoEngine.runFullBattle(ctx, wildEnemyDecisionProvider);

            // 结算
            boolean playerWon = "PLAYER".equals(ctx.getWinner());
            if (playerWon) {
                wins++;
                // 累计击败次数
                ensureDefeatCount(saveId, bossId, difficulty);
                defeatCountMapper.incrementDefeatCount(saveId, bossId, difficulty, 1);
                // 累计幸运值
                ensureLuck(saveId, bossId);
                luckMapper.incrementLuck(saveId, bossId, diffConfig.getLuckGain());
                // 掉落
                List<Map<String, Object>> battleDrops = rollDrops(saveId, diffConfig);
                totalDrops.addAll(battleDrops);
                // 经验/金币
                int exp = computeBossExp(difficulty, boss.getRecommendedLevel());
                int gold = computeBossGold(difficulty, boss.getRecommendedLevel());
                totalExp += exp;
                totalGold += gold;
                battleService.addExpAndGold(saveId, exp, gold);
                // 情报解锁检查
                checkDropUnlocks(saveId, bossId);
            } else {
                losses++;
            }

            // 停止条件
            if (!playerWon && "UNTIL_FAIL".equals(mode)) break;
            if ("UNTIL_LUCKY".equals(mode)) {
                BossLuckEntity luck = luckMapper.selectOne(
                        new LambdaQueryWrapper<BossLuckEntity>()
                                .eq(BossLuckEntity::getSaveId, saveId)
                                .eq(BossLuckEntity::getBossId, bossId));
                if (luck != null && luck.getLuckValue() >= exchangeCost) break;
            }
        }

        AutoChallengeResultDTO result = new AutoChallengeResultDTO();
        result.setTotalBattles(wins + losses);
        result.setWins(wins);
        result.setLosses(losses);
        result.setTotalExp(totalExp);
        result.setTotalGold(totalGold);
        result.setTotalDrops(totalDrops);
        // 获取最终幸运值
        BossLuckEntity finalLuck = luckMapper.selectOne(
                new LambdaQueryWrapper<BossLuckEntity>()
                        .eq(BossLuckEntity::getSaveId, saveId)
                        .eq(BossLuckEntity::getBossId, bossId));
        result.setFinalLuck(finalLuck != null ? finalLuck.getLuckValue() : 0);
        return result;
    }

    // ---- 幸运兑换 ----

    /**
     * 幸运兑换道具。
     */
    @Transactional
    public void exchangeLuck(String saveId, String bossId, String dropItemId) {
        BossesConfig.BossConfig boss = registry.getBoss(bossId);
        if (boss == null) {
            throw new BusinessException("BOSS_NOT_FOUND", "Boss 不存在: " + bossId);
        }
        int exchangeCost = registry.getSystemRules().getLuckyExchangeCost();

        BossLuckEntity luck = luckMapper.selectOne(
                new LambdaQueryWrapper<BossLuckEntity>()
                        .eq(BossLuckEntity::getSaveId, saveId)
                        .eq(BossLuckEntity::getBossId, bossId));
        if (luck == null || luck.getLuckValue() < exchangeCost) {
            throw new BusinessException("LUCK_INSUFFICIENT",
                    "幸运值不足: 需要 " + exchangeCost + "，当前 " + (luck != null ? luck.getLuckValue() : 0));
        }

        // 查找道具在 Boss 掉落池中的配置
        BossesConfig.DropEntry dropEntry = findDropEntry(boss, dropItemId);
        if (dropEntry == null) {
            throw new BusinessException("DROP_NOT_FOUND",
                    "道具不在 Boss 掉落池中: " + dropItemId);
        }
        // 检查情报是否已解锁
        checkDropUnlockedForExchange(saveId, bossId, boss, dropItemId);

        // 扣除幸运值
        luck.setLuckValue(luck.getLuckValue() - exchangeCost);
        luckMapper.updateById(luck);

        // 发放物品
        addInventoryItem(saveId, dropItemId, dropEntry.getExchangeQty());
    }

    // ---- 内部方法 ----

    private BossInfoDTO buildBossInfoDTO(String saveId, BossesConfig.BossConfig boss) {
        BossInfoDTO dto = new BossInfoDTO();
        dto.setBossId(boss.getId());
        dto.setName(boss.getName());
        dto.setMapId(boss.getMapId());
        dto.setElement(boss.getElement());
        dto.setRecommendedLevel(boss.getRecommendedLevel());

        List<DifficultyInfoDTO> diffInfos = new ArrayList<>();
        int totalDefeatCount = 0;

        for (String diffKey : DIFFICULTY_ORDER) {
            BossesConfig.DifficultyConfig diffConfig = boss.getDifficulties().get(diffKey);
            if (diffConfig == null) continue;

            DifficultyInfoDTO diffDto = new DifficultyInfoDTO();
            diffDto.setDifficulty(diffKey);

            // 解锁状态
            if ("NORMAL".equals(diffKey)) {
                diffDto.setUnlocked(true);
            } else {
                BossDifficultyUnlockEntity unlock = difficultyUnlockMapper.selectOne(
                        new LambdaQueryWrapper<BossDifficultyUnlockEntity>()
                                .eq(BossDifficultyUnlockEntity::getSaveId, saveId)
                                .eq(BossDifficultyUnlockEntity::getBossId, boss.getId())
                                .eq(BossDifficultyUnlockEntity::getDifficulty, diffKey));
                diffDto.setUnlocked(unlock != null);
            }

            // 击败次数
            BossDefeatCountEntity defeatCount = defeatCountMapper.selectOne(
                    new LambdaQueryWrapper<BossDefeatCountEntity>()
                            .eq(BossDefeatCountEntity::getSaveId, saveId)
                            .eq(BossDefeatCountEntity::getBossId, boss.getId())
                            .eq(BossDefeatCountEntity::getDifficulty, diffKey));
            int count = defeatCount != null ? defeatCount.getDefeatCount() : 0;
            diffDto.setDefeatCount(count);
            totalDefeatCount += count;

            // 掉落情报
            List<DropTierInfoDTO> dropInfos = new ArrayList<>();
            if (diffConfig.getDrops() != null) {
                for (Map.Entry<String, List<BossesConfig.DropEntry>> entry : diffConfig.getDrops().entrySet()) {
                    DropTierInfoDTO tierDto = new DropTierInfoDTO();
                    tierDto.setRarity(entry.getKey());
                    // 情报解锁状态
                    int threshold = DROP_UNLOCK_THRESHOLDS.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
                    boolean unlocked = totalDefeatCount >= threshold;
                    // 检查数据库中的解锁记录
                    BossDropUnlockEntity dropUnlock = dropUnlockMapper.selectOne(
                            new LambdaQueryWrapper<BossDropUnlockEntity>()
                                    .eq(BossDropUnlockEntity::getSaveId, saveId)
                                    .eq(BossDropUnlockEntity::getBossId, boss.getId())
                                    .eq(BossDropUnlockEntity::getRarity, entry.getKey()));
                    tierDto.setUnlocked(unlocked || dropUnlock != null);
                    tierDto.setItems(entry.getValue().stream().map(d -> {
                        DropItemInfoDTO itemDto = new DropItemInfoDTO();
                        itemDto.setItemId(d.getItemId());
                        itemDto.setQty(d.getQty());
                        itemDto.setChance(d.getChance());
                        itemDto.setExchangeQty(d.getExchangeQty());
                        return itemDto;
                    }).toList());
                    dropInfos.add(tierDto);
                }
            }
            diffDto.setDropInfo(dropInfos);
            diffInfos.add(diffDto);
        }
        dto.setDifficulties(diffInfos);

        // 幸运值
        BossLuckEntity luck = luckMapper.selectOne(
                new LambdaQueryWrapper<BossLuckEntity>()
                        .eq(BossLuckEntity::getSaveId, saveId)
                        .eq(BossLuckEntity::getBossId, boss.getId()));
        dto.setLuckValue(luck != null ? luck.getLuckValue() : 0);

        return dto;
    }

    private void ensureDefeatCount(String saveId, String bossId, String difficulty) {
        BossDefeatCountEntity existing = defeatCountMapper.selectOne(
                new LambdaQueryWrapper<BossDefeatCountEntity>()
                        .eq(BossDefeatCountEntity::getSaveId, saveId)
                        .eq(BossDefeatCountEntity::getBossId, bossId)
                        .eq(BossDefeatCountEntity::getDifficulty, difficulty));
        if (existing == null) {
            defeatCountMapper.insert(new BossDefeatCountEntity(saveId, bossId, difficulty, 0));
        }
    }

    private void ensureLuck(String saveId, String bossId) {
        BossLuckEntity existing = luckMapper.selectOne(
                new LambdaQueryWrapper<BossLuckEntity>()
                        .eq(BossLuckEntity::getSaveId, saveId)
                        .eq(BossLuckEntity::getBossId, bossId));
        if (existing == null) {
            luckMapper.insert(new BossLuckEntity(saveId, bossId, 0));
        }
    }

    private List<Map<String, Object>> rollDrops(String saveId, BossesConfig.DifficultyConfig diffConfig) {
        List<Map<String, Object>> drops = new ArrayList<>();
        if (diffConfig.getDrops() == null) return drops;
        Random random = new Random();
        for (Map.Entry<String, List<BossesConfig.DropEntry>> entry : diffConfig.getDrops().entrySet()) {
            for (BossesConfig.DropEntry drop : entry.getValue()) {
                if (random.nextDouble() <= drop.getChance()) {
                    addInventoryItem(saveId, drop.getItemId(), drop.getQty());
                    Map<String, Object> dropMap = new HashMap<>();
                    dropMap.put("itemId", drop.getItemId());
                    dropMap.put("qty", drop.getQty());
                    dropMap.put("rarity", entry.getKey());
                    drops.add(dropMap);
                }
            }
        }
        return drops;
    }

    private void checkDropUnlocks(String saveId, String bossId) {
        int totalDefeatCount = getTotalDefeatCount(saveId, bossId);
        for (Map.Entry<String, Integer> entry : DROP_UNLOCK_THRESHOLDS.entrySet()) {
            if (totalDefeatCount >= entry.getValue()) {
                BossDropUnlockEntity existing = dropUnlockMapper.selectOne(
                        new LambdaQueryWrapper<BossDropUnlockEntity>()
                                .eq(BossDropUnlockEntity::getSaveId, saveId)
                                .eq(BossDropUnlockEntity::getBossId, bossId)
                                .eq(BossDropUnlockEntity::getRarity, entry.getKey()));
                if (existing == null) {
                    dropUnlockMapper.insert(new BossDropUnlockEntity(saveId, bossId, entry.getKey(), LocalDateTime.now()));
                }
            }
        }
    }

    private int getTotalDefeatCount(String saveId, String bossId) {
        List<BossDefeatCountEntity> counts = defeatCountMapper.selectList(
                new LambdaQueryWrapper<BossDefeatCountEntity>()
                        .eq(BossDefeatCountEntity::getSaveId, saveId)
                        .eq(BossDefeatCountEntity::getBossId, bossId));
        return counts.stream().mapToInt(BossDefeatCountEntity::getDefeatCount).sum();
    }

    private BossesConfig.DropEntry findDropEntry(BossesConfig.BossConfig boss, String itemId) {
        for (BossesConfig.DifficultyConfig diff : boss.getDifficulties().values()) {
            if (diff.getDrops() != null) {
                for (List<BossesConfig.DropEntry> entries : diff.getDrops().values()) {
                    for (BossesConfig.DropEntry entry : entries) {
                        if (itemId.equals(entry.getItemId())) {
                            return entry;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void checkDropUnlockedForExchange(String saveId, String bossId,
                                               BossesConfig.BossConfig boss, String itemId) {
        // 查找道具所属稀有度
        for (BossesConfig.DifficultyConfig diff : boss.getDifficulties().values()) {
            if (diff.getDrops() != null) {
                for (Map.Entry<String, List<BossesConfig.DropEntry>> entry : diff.getDrops().entrySet()) {
                    for (BossesConfig.DropEntry drop : entry.getValue()) {
                        if (itemId.equals(drop.getItemId())) {
                            // 检查情报解锁
                            BossDropUnlockEntity unlock = dropUnlockMapper.selectOne(
                                    new LambdaQueryWrapper<BossDropUnlockEntity>()
                                            .eq(BossDropUnlockEntity::getSaveId, saveId)
                                            .eq(BossDropUnlockEntity::getBossId, bossId)
                                            .eq(BossDropUnlockEntity::getRarity, entry.getKey()));
                            int totalDefeat = getTotalDefeatCount(saveId, bossId);
                            int threshold = DROP_UNLOCK_THRESHOLDS.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
                            if (unlock == null && totalDefeat < threshold) {
                                throw new BusinessException("DROP_LOCKED",
                                        "该道具的情报尚未解锁: " + entry.getKey());
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private void addInventoryItem(String saveId, String itemId, int quantity) {
        PlayerInventoryEntity existing = playerInventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, saveId)
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            playerInventoryMapper.updateById(existing);
        } else {
            PlayerInventoryEntity inv = new PlayerInventoryEntity();
            inv.setSaveId(saveId);
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            playerInventoryMapper.insert(inv);
        }
    }

    private int computeBossExp(String difficulty, int recommendedLevel) {
        return switch (difficulty) {
            case "NORMAL" -> recommendedLevel * 50;
            case "HARD" -> recommendedLevel * 80;
            case "NIGHTMARE" -> recommendedLevel * 120;
            default -> recommendedLevel * 50;
        };
    }

    private int computeBossGold(String difficulty, int recommendedLevel) {
        return switch (difficulty) {
            case "NORMAL" -> recommendedLevel * 30;
            case "HARD" -> recommendedLevel * 50;
            case "NIGHTMARE" -> recommendedLevel * 80;
            default -> recommendedLevel * 30;
        };
    }

    // ---- DTO ----

    @Data
    public static class BossInfoDTO {
        private String bossId;
        private String name;
        private String mapId;
        private String element;
        private int recommendedLevel;
        private int luckValue;
        private List<DifficultyInfoDTO> difficulties = new ArrayList<>();
    }

    @Data
    public static class DifficultyInfoDTO {
        private String difficulty;
        private boolean unlocked;
        private int defeatCount;
        private List<DropTierInfoDTO> dropInfo = new ArrayList<>();
    }

    @Data
    public static class DropTierInfoDTO {
        private String rarity;
        private boolean unlocked;
        private List<DropItemInfoDTO> items = new ArrayList<>();
    }

    @Data
    public static class DropItemInfoDTO {
        private String itemId;
        private int qty;
        private double chance;
        private int exchangeQty;
    }

    @Data
    public static class AutoChallengeResultDTO {
        private int totalBattles;
        private int wins;
        private int losses;
        private int totalExp;
        private int totalGold;
        private List<Map<String, Object>> totalDrops = new ArrayList<>();
        private int finalLuck;
    }
}
