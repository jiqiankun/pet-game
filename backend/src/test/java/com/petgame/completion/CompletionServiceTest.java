package com.petgame.completion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.boss.entity.BossDefeatCountEntity;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.completion.service.CompletionService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 游戏完成度服务单元测试（阶段 11，需求 §111）。
 * <p>
 * 验证：加权完成度计算、空状态、满状态、区域进度与 clamp 行为。
 */
class CompletionServiceTest {

    private GameConfigRegistry registry;
    private PlayerMapper playerMapper;
    private PlayerRegionUnlockMapper regionUnlockMapper;
    private BossDefeatCountMapper bossDefeatCountMapper;
    private PlayerQuestMapper questMapper;
    private PokedexService pokedexService;
    private CompletionService completionService;

    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);
        playerMapper = mock(PlayerMapper.class);
        regionUnlockMapper = mock(PlayerRegionUnlockMapper.class);
        bossDefeatCountMapper = mock(BossDefeatCountMapper.class);
        questMapper = mock(PlayerQuestMapper.class);
        pokedexService = mock(PokedexService.class);

        player = new PlayerEntity();
        player.setSaveId("SAVE_001");
        player.setStoryCompleted(false);
        when(playerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player);

        // 默认权重：20/10/10/25/10/15/5/5
        when(registry.getSystemRules()).thenReturn(new SystemRuleConfig());

        when(pokedexService.getFullPokedex("SAVE_001")).thenReturn(List.of());
        when(regionUnlockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(bossDefeatCountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(questMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(registry.getQuestsConfig()).thenReturn(null);

        completionService = new CompletionService(registry, playerMapper, regionUnlockMapper,
                bossDefeatCountMapper, questMapper, pokedexService);
    }

    @Test
    void getCompletion_emptyState_shouldBeZero() {
        Map<String, Object> result = completionService.getCompletion("SAVE_001");
        assertEquals(0.0, result.get("overall"));
        @SuppressWarnings("unchecked")
        Map<String, Object> comps = (Map<String, Object>) result.get("components");
        assertEquals(8, comps.size());
    }

    @Test
    void getCompletion_regionProgress_shouldBeWeighted() {
        // 解锁 2/5 区域，其余为 0
        List<PlayerRegionUnlockEntity> regions = new ArrayList<>();
        regions.add(region("MAP_AREA_MEADOW"));
        regions.add(region("MAP_AREA_FOREST"));
        when(regionUnlockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(regions);

        Map<String, Object> result = completionService.getCompletion("SAVE_001");
        // 区域分项贡献 = 10 × (2/5) = 4
        @SuppressWarnings("unchecked")
        Map<String, Object> comps = (Map<String, Object>) result.get("components");
        Map<String, Object> region = (Map<String, Object>) comps.get("region");
        assertEquals(0.4, ((Number) region.get("progress")).doubleValue(), 0.0001);
        assertEquals(4.0, ((Number) region.get("contribution")).doubleValue(), 0.0001);
        assertEquals(4.0, ((Number) result.get("overall")).doubleValue(), 0.0001);
    }

    @Test
    void getCompletion_fullState_shouldApproach100() {
        // 全部区域解锁
        List<PlayerRegionUnlockEntity> regions = new ArrayList<>();
        for (String id : List.of("MAP_AREA_MEADOW", "MAP_AREA_FOREST", "MAP_AREA_WATERS",
                "MAP_AREA_THUNDER", "MAP_AREA_RUINS")) {
            regions.add(region(id));
        }
        when(regionUnlockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(regions);

        // 全部图鉴：seen + caught + 研究 Lv.5
        List<PokedexEntryVo> pokedex = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            PokedexEntryVo entry = new PokedexEntryVo();
            entry.setSpeciesId("SPEC_" + i);
            entry.setSeen(true);
            entry.setCaught(true);
            entry.setResearchLevel(5);
            pokedex.add(entry);
        }
        when(pokedexService.getFullPokedex("SAVE_001")).thenReturn(pokedex);

        // 全部主 Boss + 隐藏 Boss 已击败
        when(bossDefeatCountMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            BossDefeatCountEntity e = new BossDefeatCountEntity();
            e.setDefeatCount(1);
            return e;
        });

        // 主线完成
        player.setStoryCompleted(true);

        // 8 条 SIDE 支线全部完成
        List<QuestsConfig.QuestConfig> questCfg = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            QuestsConfig.QuestConfig q = new QuestsConfig.QuestConfig();
            q.setId("QSIDE_" + i);
            q.setType("SIDE");
            questCfg.add(q);
        }
        QuestsConfig questsConfig = new QuestsConfig();
        questsConfig.setQuests(questCfg);
        when(registry.getQuestsConfig()).thenReturn(questsConfig);
        PlayerQuestEntity completed = new PlayerQuestEntity();
        completed.setStatus("COMPLETED");
        when(questMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(completed);

        Map<String, Object> result = completionService.getCompletion("SAVE_001");
        double overall = ((Number) result.get("overall")).doubleValue();
        assertEquals(100.0, overall, 0.01);
    }

    @Test
    void getCompletion_noPlayer_shouldReturnEmpty() {
        when(playerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        Map<String, Object> result = completionService.getCompletion("SAVE_001");
        assertEquals(0.0, result.get("overall"));
        assertTrue(((Map<?, ?>) result.get("components")).isEmpty());
    }

    private PlayerRegionUnlockEntity region(String id) {
        PlayerRegionUnlockEntity r = new PlayerRegionUnlockEntity();
        r.setSaveId("SAVE_001");
        r.setRegionId(id);
        return r;
    }
}