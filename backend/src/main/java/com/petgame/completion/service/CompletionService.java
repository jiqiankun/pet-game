package com.petgame.completion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.pokedex.vo.PokedexEntryVo;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.mapper.PlayerQuestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏完成度服务（阶段 11，需求 §111）。
 * <p>
 * 由主线、区域、宠物发现/捕获、图鉴研究、Boss、隐藏区域、主要支线按权重加权计算。
 * 100% 不要求 S 资质、特殊外观、稀有技能或 Boss 噩梦全通（§138 口径）。
 * <p>
 * 推断口径（与配置/既有数据对齐，交付说明中标注）：
 * - 区域：5 个可探索实施区域解锁比例；
 * - 隐藏区域：无独立「隐藏区域」数据，以 3 个隐藏/精英 Boss 击败比例近似；
 * - 主要支线：SIDE 支线任务完成比例。
 */
@Service
public class CompletionService {

    private static final Logger log = LoggerFactory.getLogger(CompletionService.class);

    /** 可探索的实施区域（不含据点，共 5 个）。 */
    private static final List<String> EXPLORABLE_REGIONS = List.of(
            "MAP_AREA_MEADOW", "MAP_AREA_FOREST", "MAP_AREA_WATERS",
            "MAP_AREA_THUNDER", "MAP_AREA_RUINS");

    /** 隐藏/精英 Boss（用于「隐藏区域」分项近似）。 */
    private static final List<String> HIDDEN_BOSSES = List.of(
            "BOSS_HIDDEN_SHADOW", "BOSS_ELITE_FOREST", "BOSS_HIDDEN_RUINS");

    private final GameConfigRegistry registry;
    private final PlayerMapper playerMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final BossDefeatCountMapper bossDefeatCountMapper;
    private final PlayerQuestMapper questMapper;
    private final PokedexService pokedexService;

    public CompletionService(GameConfigRegistry registry,
                             PlayerMapper playerMapper,
                             PlayerRegionUnlockMapper regionUnlockMapper,
                             BossDefeatCountMapper bossDefeatCountMapper,
                             PlayerQuestMapper questMapper,
                             PokedexService pokedexService) {
        this.registry = registry;
        this.playerMapper = playerMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.bossDefeatCountMapper = bossDefeatCountMapper;
        this.questMapper = questMapper;
        this.pokedexService = pokedexService;
    }

    /** 计算游戏完成度（总体 + 各分项进度与权重）。 */
    public Map<String, Object> getCompletion(String saveId) {
        PlayerEntity player = playerMapper.selectOne(
                new LambdaQueryWrapper<PlayerEntity>().eq(PlayerEntity::getSaveId, saveId));
        if (player == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("overall", 0.0);
            empty.put("components", new LinkedHashMap<String, Object>());
            return empty;
        }

        SystemRuleConfig.CompletionConfig w = registry.getSystemRules().getCompletion();

        // 主线
        double mainProgress = Boolean.TRUE.equals(player.getStoryCompleted()) ? 1.0 : 0.0;

        // 区域
        int unlockedRegions = countUnlockedRegions(saveId);
        double regionProgress = clamp01((double) unlockedRegions / EXPLORABLE_REGIONS.size());

        // 宠物发现 / 捕获 / 图鉴研究
        int discovered = 0;
        int captured = 0;
        double researchSum = 0;
        int speciesCount = 0;
        List<PokedexEntryVo> pokedex = pokedexService.getFullPokedex(saveId);
        for (PokedexEntryVo entry : pokedex) {
            speciesCount++;
            if (entry.isSeen()) {
                discovered++;
            }
            if (entry.isCaught()) {
                captured++;
            }
            researchSum += entry.getResearchLevel();
        }
        double discoveryProgress = speciesCount == 0 ? 0.0 : clamp01((double) discovered / speciesCount);
        double captureProgress = speciesCount == 0 ? 0.0 : clamp01((double) captured / speciesCount);
        double researchProgress = speciesCount == 0 ? 0.0 : clamp01(researchSum / (speciesCount * 5.0));

        // Boss（主 Boss 普通难度首通）
        int mainDefeated = countMainBossDefeated(saveId);
        double bossProgress = clamp01((double) mainDefeated / 5.0);

        // 隐藏区域（隐藏/精英 Boss 击败比例近似）
        int hiddenDefeated = countHiddenBossDefeated(saveId);
        double hiddenRegionProgress = clamp01((double) hiddenDefeated / HIDDEN_BOSSES.size());

        // 主要支线（SIDE 支线完成比例）
        double sideQuestProgress = countSideQuestProgress(saveId);

        double overall = w.getMainQuestWeight() * mainProgress
                + w.getRegionWeight() * regionProgress
                + w.getDiscoveryWeight() * discoveryProgress
                + w.getCaptureWeight() * captureProgress
                + w.getResearchWeight() * researchProgress
                + w.getBossWeight() * bossProgress
                + w.getHiddenRegionWeight() * hiddenRegionProgress
                + w.getSideQuestWeight() * sideQuestProgress;
        // 权重以百分比计（各分项权重之和为 100），总体完成度范围 [0, 100]
        overall = Math.max(0.0, Math.min(100.0, overall));

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("main", component(w.getMainQuestWeight(), mainProgress));
        components.put("region", component(w.getRegionWeight(), regionProgress));
        components.put("discovery", component(w.getDiscoveryWeight(), discoveryProgress));
        components.put("capture", component(w.getCaptureWeight(), captureProgress));
        components.put("research", component(w.getResearchWeight(), researchProgress));
        components.put("boss", component(w.getBossWeight(), bossProgress));
        components.put("hiddenRegion", component(w.getHiddenRegionWeight(), hiddenRegionProgress));
        components.put("sideQuest", component(w.getSideQuestWeight(), sideQuestProgress));

        Map<String, Object> result = new HashMap<>();
        result.put("overall", Math.round(overall * 100.0) / 100.0);
        result.put("components", components);
        return result;
    }

    private Map<String, Object> component(double weight, double progress) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weight", weight);
        m.put("progress", Math.round(progress * 10000.0) / 10000.0);
        m.put("contribution", Math.round(weight * progress * 100.0) / 100.0);
        return m;
    }

    private int countUnlockedRegions(String saveId) {
        List<PlayerRegionUnlockEntity> rows = regionUnlockMapper.selectList(
                new LambdaQueryWrapper<PlayerRegionUnlockEntity>()
                        .eq(PlayerRegionUnlockEntity::getSaveId, saveId));
        int count = 0;
        for (PlayerRegionUnlockEntity row : rows) {
            if (EXPLORABLE_REGIONS.contains(row.getRegionId())) {
                count++;
            }
        }
        return count;
    }

    private int countMainBossDefeated(String saveId) {
        int count = 0;
        for (String bossId : List.of("BOSS_MEADOW_GUARDIAN", "BOSS_FOREST_KING", "BOSS_WATERS_GUARDIAN",
                "BOSS_THUNDER_GUARDIAN", "BOSS_RUINS_GUARDIAN")) {
            BossDefeatCountEntity row = bossDefeatCountMapper.selectOne(
                    new LambdaQueryWrapper<BossDefeatCountEntity>()
                            .eq(BossDefeatCountEntity::getSaveId, saveId)
                            .eq(BossDefeatCountEntity::getBossId, bossId)
                            .eq(BossDefeatCountEntity::getDifficulty, "NORMAL"));
            if (row != null && row.getDefeatCount() != null && row.getDefeatCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private int countHiddenBossDefeated(String saveId) {
        int count = 0;
        for (String bossId : HIDDEN_BOSSES) {
            BossDefeatCountEntity row = bossDefeatCountMapper.selectOne(
                    new LambdaQueryWrapper<BossDefeatCountEntity>()
                            .eq(BossDefeatCountEntity::getSaveId, saveId)
                            .eq(BossDefeatCountEntity::getBossId, bossId)
                            .eq(BossDefeatCountEntity::getDifficulty, "NORMAL"));
            if (row != null && row.getDefeatCount() != null && row.getDefeatCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private double countSideQuestProgress(String saveId) {
        List<QuestsConfig.QuestConfig> quests = registry.getQuestsConfig() == null
                ? List.of() : registry.getQuestsConfig().getQuests();
        if (quests == null) {
            return 0.0;
        }
        int totalSide = 0;
        int completedSide = 0;
        for (QuestsConfig.QuestConfig q : quests) {
            if ("SIDE".equals(q.getType())) {
                totalSide++;
                PlayerQuestEntity state = questMapper.selectOne(
                        new LambdaQueryWrapper<PlayerQuestEntity>()
                                .eq(PlayerQuestEntity::getSaveId, saveId)
                                .eq(PlayerQuestEntity::getQuestId, q.getId()));
                if (state != null && "COMPLETED".equals(state.getStatus())) {
                    completedSide++;
                }
            }
        }
        return totalSide == 0 ? 0.0 : clamp01((double) completedSide / totalSide);
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}