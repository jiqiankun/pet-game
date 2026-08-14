package com.petgame.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.statistics.entity.PlayerStatisticEntity;
import com.petgame.statistics.mapper.PlayerStatisticMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家统计服务（阶段 11，需求 §112）。
 * <p>
 * 以「键值」形式记录玩家长期统计项，由既有结算点事件驱动增量写入
 * （REQUIRES_NEW 传播，失败不阻断主流程）。不重复采集，不构建规则引擎。
 * 统计项仅作展示与成就/完成度依据，不反向影响战斗数值。
 */
@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    /** 统计键：游戏时长（秒）。 */
    public static final String ST_PLAY_TIME = "PLAY_TIME_SECONDS";
    /** 统计键：战斗总次数。 */
    public static final String ST_BATTLES_TOTAL = "BATTLES_TOTAL";
    /** 统计键：战斗胜利次数。 */
    public static final String ST_BATTLES_WON = "BATTLES_WON";
    /** 统计键：战斗失败次数。 */
    public static final String ST_BATTLES_LOST = "BATTLES_LOST";
    /** 统计键：逃跑次数。 */
    public static final String ST_FLED = "FLED_COUNT";
    /** 统计键：捕获成功次数。 */
    public static final String ST_CAPTURES_SUCCESS = "CAPTURES_SUCCESS";
    /** 统计键：捕获失败次数。 */
    public static final String ST_CAPTURES_FAILED = "CAPTURES_FAILED";
    /** 统计键：放生宠物数量。 */
    public static final String ST_RELEASED_PETS = "RELEASED_PETS";
    /** 统计键：累计击败宠物数量。 */
    public static final String ST_TOTAL_KILLS = "TOTAL_KILLS";
    /** 统计键：Boss 击败次数。 */
    public static final String ST_BOSS_DEFEATED = "BOSS_DEFEATED";
    /** 统计键：最高单次伤害。 */
    public static final String ST_MAX_DAMAGE = "MAX_DAMAGE";
    /** 统计键：最高暴击伤害。 */
    public static final String ST_MAX_CRIT_DAMAGE = "MAX_CRIT_DAMAGE";
    /** 统计键：捕捉球总消耗。 */
    public static final String ST_CAPTURE_BALLS_USED = "CAPTURE_BALLS_USED";
    /** 统计键：累计获得金币。 */
    public static final String ST_GOLD_EARNED = "GOLD_EARNED";
    /** 统计键：累计获得经验。 */
    public static final String ST_EXP_EARNED = "EXP_EARNED";
    /** 统计键：最高宠物等级。 */
    public static final String ST_MAX_PET_LEVEL = "MAX_PET_LEVEL";
    /** 统计键：满级宠物数量。 */
    public static final String ST_MAX_LEVEL_PETS = "MAX_LEVEL_PETS";
    /** 统计键：累计造成伤害。 */
    public static final String ST_TOTAL_DAMAGE = "TOTAL_DAMAGE";
    /** 统计键：累计治疗量。 */
    public static final String ST_TOTAL_HEAL = "TOTAL_HEAL";
    /** 统计键：研究等级 Lv.5 的种族数量。 */
    public static final String ST_POKEDEX_RESEARCHED_5 = "POKEDEX_RESEARCHED_5";
    /** 统计键：累计捕获精英个体数量。 */
    public static final String ST_ELITE_CAPTURED = "ELITE_CAPTURED";
    /** 统计键：累计捕获特殊外观个体数量。 */
    public static final String ST_SPECIAL_APPEARANCE_CAPTURED = "SPECIAL_APPEARANCE_CAPTURED";
    /** 统计键：累计完成的 Boss 挑战目标数量。 */
    public static final String ST_BOSS_CHALLENGES = "BOSS_CHALLENGES";
    /** 统计键前缀：宠物出战次数（USE_PET_<speciesId>）。 */
    public static final String ST_USE_PET_PREFIX = "USE_PET_";
    /** 统计键前缀：技能使用次数（USE_SKILL_<skillId>）。 */
    public static final String ST_USE_SKILL_PREFIX = "USE_SKILL_";

    private final PlayerStatisticMapper statisticMapper;

    public StatisticsService(PlayerStatisticMapper statisticMapper) {
        this.statisticMapper = statisticMapper;
    }

    // ==================== 写入（事件钩子）====================

    /** 统计键值增量（不存在则插入，REQUIRES_NEW，失败不阻断主流程）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(String saveId, String statKey, long delta) {
        try {
            doIncrement(saveId, statKey, delta);
        } catch (Exception e) {
            log.warn("统计写入异常（不阻断主流程）：saveId={}, key={}, delta={}, error={}",
                    saveId, statKey, delta, e.getMessage());
        }
    }

    private void doIncrement(String saveId, String statKey, long delta) {
        if (delta == 0 || statKey == null) {
            return;
        }
        int updated = statisticMapper.incrementValue(saveId, statKey, delta);
        if (updated == 0) {
            // 不存在则插入
            PlayerStatisticEntity entity = new PlayerStatisticEntity();
            entity.setSaveId(saveId);
            entity.setStatKey(statKey);
            entity.setStatValue(delta);
            statisticMapper.insert(entity);
        }
    }

    /** 设置统计键为指定值（用于最高值类，REQUIRES_NEW）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setMax(String saveId, String statKey, long value) {
        try {
            PlayerStatisticEntity exists = statisticMapper.selectOne(
                    new LambdaQueryWrapper<PlayerStatisticEntity>()
                            .eq(PlayerStatisticEntity::getSaveId, saveId)
                            .eq(PlayerStatisticEntity::getStatKey, statKey));
            if (exists != null && exists.getStatValue() >= value) {
                return;
            }
            if (exists != null) {
                exists.setStatValue(value);
                statisticMapper.updateById(exists);
            } else {
                exists = new PlayerStatisticEntity();
                exists.setSaveId(saveId);
                exists.setStatKey(statKey);
                exists.setStatValue(value);
                statisticMapper.insert(exists);
            }
        } catch (Exception e) {
            log.warn("统计最高值写入异常（不阻断主流程）：saveId={}, key={}, value={}, error={}",
                    saveId, statKey, value, e.getMessage());
        }
    }

    /** 设置统计键为指定值（用于可重置的计数，REQUIRES_NEW）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void set(String saveId, String statKey, long value) {
        try {
            PlayerStatisticEntity exists = statisticMapper.selectOne(
                    new LambdaQueryWrapper<PlayerStatisticEntity>()
                            .eq(PlayerStatisticEntity::getSaveId, saveId)
                            .eq(PlayerStatisticEntity::getStatKey, statKey));
            if (exists != null) {
                exists.setStatValue(value);
                statisticMapper.updateById(exists);
            } else {
                exists = new PlayerStatisticEntity();
                exists.setSaveId(saveId);
                exists.setStatKey(statKey);
                exists.setStatValue(value);
                statisticMapper.insert(exists);
            }
        } catch (Exception e) {
            log.warn("统计值写入异常（不阻断主流程）：saveId={}, key={}, value={}, error={}",
                    saveId, statKey, value, e.getMessage());
        }
    }

    // ==================== 查询 ====================

    /** 读取单个统计值（不存在返回 0）。 */
    public long getStat(String saveId, String statKey) {
        PlayerStatisticEntity exists = statisticMapper.selectOne(
                new LambdaQueryWrapper<PlayerStatisticEntity>()
                        .eq(PlayerStatisticEntity::getSaveId, saveId)
                        .eq(PlayerStatisticEntity::getStatKey, statKey));
        return exists == null ? 0L : exists.getStatValue();
    }

    /** 读取指定存档全部统计键值。 */
    public Map<String, Long> getAllStats(String saveId) {
        List<PlayerStatisticEntity> rows = statisticMapper.selectList(
                new LambdaQueryWrapper<PlayerStatisticEntity>()
                        .eq(PlayerStatisticEntity::getSaveId, saveId));
        Map<String, Long> map = new HashMap<>();
        for (PlayerStatisticEntity row : rows) {
            map.put(row.getStatKey(), row.getStatValue());
        }
        return map;
    }

    /** 计算「使用最多宠物 / 使用最多技能」显示项（基于 USE_* 统计键扫描）。 */
    public Map<String, String> computeMostUsed(String saveId) {
        Map<String, Long> all = getAllStats(saveId);
        String mostPet = null;
        long mostPetCount = 0;
        String mostSkill = null;
        long mostSkillCount = 0;
        for (Map.Entry<String, Long> e : all.entrySet()) {
            if (e.getKey().startsWith(ST_USE_PET_PREFIX)) {
                if (e.getValue() > mostPetCount) {
                    mostPetCount = e.getValue();
                    mostPet = e.getKey().substring(ST_USE_PET_PREFIX.length());
                }
            } else if (e.getKey().startsWith(ST_USE_SKILL_PREFIX)) {
                if (e.getValue() > mostSkillCount) {
                    mostSkillCount = e.getValue();
                    mostSkill = e.getKey().substring(ST_USE_SKILL_PREFIX.length());
                }
            }
        }
        Map<String, String> result = new HashMap<>();
        result.put("mostUsedPet", mostPet);
        result.put("mostUsedPetCount", String.valueOf(mostPetCount));
        result.put("mostUsedSkill", mostSkill);
        result.put("mostUsedSkillCount", String.valueOf(mostSkillCount));
        return result;
    }
}