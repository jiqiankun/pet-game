package com.petgame.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.statistics.entity.PlayerStatisticEntity;
import com.petgame.statistics.mapper.PlayerStatisticMapper;
import com.petgame.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 玩家统计服务单元测试（阶段 11，需求 §112）。
 * <p>
 * 验证：增量写入（存在/不存在）、最高值写入、读取、使用最多项计算。
 */
class StatisticsServiceTest {

    private PlayerStatisticMapper statisticMapper;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticMapper = mock(PlayerStatisticMapper.class);
        statisticsService = new StatisticsService(statisticMapper);
    }

    @Test
    void increment_existingRow_shouldCallIncrementValue() {
        when(statisticMapper.incrementValue("SAVE_001", StatisticsService.ST_BATTLES_TOTAL, 1L))
                .thenReturn(1);

        statisticsService.increment("SAVE_001", StatisticsService.ST_BATTLES_TOTAL, 1);

        verify(statisticMapper).incrementValue("SAVE_001", StatisticsService.ST_BATTLES_TOTAL, 1L);
        verify(statisticMapper, never()).insert(any(PlayerStatisticEntity.class));
    }

    @Test
    void increment_missingRow_shouldInsert() {
        when(statisticMapper.incrementValue(anyString(), anyString(), anyLong())).thenReturn(0);

        statisticsService.increment("SAVE_001", StatisticsService.ST_BATTLES_WON, 3);

        verify(statisticMapper).incrementValue("SAVE_001", StatisticsService.ST_BATTLES_WON, 3L);
        verify(statisticMapper).insert(org.mockito.ArgumentMatchers.<PlayerStatisticEntity>argThat(e ->
                "SAVE_001".equals(e.getSaveId())
                        && StatisticsService.ST_BATTLES_WON.equals(e.getStatKey())
                        && Long.valueOf(3).equals(e.getStatValue())));
    }

    @Test
    void increment_zeroDelta_shouldDoNothing() {
        statisticsService.increment("SAVE_001", StatisticsService.ST_BATTLES_WON, 0);

        verify(statisticMapper, never()).incrementValue(anyString(), anyString(), anyLong());
        verify(statisticMapper, never()).insert(any(PlayerStatisticEntity.class));
    }

    @Test
    void setMax_existingHigher_shouldNotUpdate() {
        PlayerStatisticEntity exists = new PlayerStatisticEntity();
        exists.setSaveId("SAVE_001");
        exists.setStatKey(StatisticsService.ST_MAX_DAMAGE);
        exists.setStatValue(500L);
        when(statisticMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exists);

        statisticsService.setMax("SAVE_001", StatisticsService.ST_MAX_DAMAGE, 300);

        verify(statisticMapper, never()).updateById(any(PlayerStatisticEntity.class));
        verify(statisticMapper, never()).insert(any(PlayerStatisticEntity.class));
        assertEquals(500L, exists.getStatValue());
    }

    @Test
    void setMax_existingLower_shouldUpdate() {
        PlayerStatisticEntity exists = new PlayerStatisticEntity();
        exists.setSaveId("SAVE_001");
        exists.setStatKey(StatisticsService.ST_MAX_DAMAGE);
        exists.setStatValue(500L);
        when(statisticMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exists);

        statisticsService.setMax("SAVE_001", StatisticsService.ST_MAX_DAMAGE, 800);

        verify(statisticMapper).updateById(exists);
        assertEquals(800L, exists.getStatValue());
    }

    @Test
    void setMax_missing_shouldInsert() {
        when(statisticMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        statisticsService.setMax("SAVE_001", StatisticsService.ST_MAX_PET_LEVEL, 50);

        verify(statisticMapper).insert(org.mockito.ArgumentMatchers.<PlayerStatisticEntity>argThat(e ->
                "SAVE_001".equals(e.getSaveId())
                        && StatisticsService.ST_MAX_PET_LEVEL.equals(e.getStatKey())
                        && Long.valueOf(50).equals(e.getStatValue())));
    }

    @Test
    void getStat_missing_shouldReturnZero() {
        when(statisticMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertEquals(0L, statisticsService.getStat("SAVE_001", StatisticsService.ST_BATTLES_TOTAL));
    }

    @Test
    void getAllStats_shouldReturnMap() {
        PlayerStatisticEntity a = new PlayerStatisticEntity();
        a.setSaveId("SAVE_001");
        a.setStatKey(StatisticsService.ST_BATTLES_TOTAL);
        a.setStatValue(10L);
        PlayerStatisticEntity b = new PlayerStatisticEntity();
        b.setSaveId("SAVE_001");
        b.setStatKey(StatisticsService.ST_BATTLES_WON);
        b.setStatValue(7L);
        when(statisticMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a, b));

        Map<String, Long> all = statisticsService.getAllStats("SAVE_001");

        assertEquals(2, all.size());
        assertEquals(10L, all.get(StatisticsService.ST_BATTLES_TOTAL));
        assertEquals(7L, all.get(StatisticsService.ST_BATTLES_WON));
    }

    @Test
    void computeMostUsed_shouldPickHighest() {
        PlayerStatisticEntity petA = stat(StatisticsService.ST_USE_PET_PREFIX + "SPEC_A", 5);
        PlayerStatisticEntity petB = stat(StatisticsService.ST_USE_PET_PREFIX + "SPEC_B", 9);
        PlayerStatisticEntity skill = stat(StatisticsService.ST_USE_SKILL_PREFIX + "SKILL_X", 12);
        when(statisticMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(petA, petB, skill));

        Map<String, String> most = statisticsService.computeMostUsed("SAVE_001");

        assertEquals("SPEC_B", most.get("mostUsedPet"));
        assertEquals("9", most.get("mostUsedPetCount"));
        assertEquals("SKILL_X", most.get("mostUsedSkill"));
        assertEquals("12", most.get("mostUsedSkillCount"));
    }

    @Test
    void computeMostUsed_noData_shouldBeNull() {
        when(statisticMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        Map<String, String> most = statisticsService.computeMostUsed("SAVE_001");
        assertNull(most.get("mostUsedPet"));
        assertNull(most.get("mostUsedSkill"));
        assertEquals("0", most.get("mostUsedPetCount"));
    }

    private PlayerStatisticEntity stat(String key, long value) {
        PlayerStatisticEntity e = new PlayerStatisticEntity();
        e.setSaveId("SAVE_001");
        e.setStatKey(key);
        e.setStatValue(value);
        return e;
    }
}