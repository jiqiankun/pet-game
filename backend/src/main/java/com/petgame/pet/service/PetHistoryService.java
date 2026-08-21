package com.petgame.pet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleUnit;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.statistics.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物个人履历服务（阶段 11，需求 §113）。
 * <p>
 * 在战斗结算时从战斗事件日志聚合每只玩家宠物的伤害/承伤/治疗/击败数，
 * 并更新 player_pet 履历字段与玩家统计。该记录与战斗结算共用同一事务，避免
 * 在结算已锁定宠物行后以独立事务再次更新同一行。
 * 履历仅作记录展示，不增加属性、不反向影响战斗数值。
 */
@Service
public class PetHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PetHistoryService.class);

    private final PlayerPetMapper petMapper;
    private final StatisticsService statisticsService;

    public PetHistoryService(PlayerPetMapper petMapper, StatisticsService statisticsService) {
        this.petMapper = petMapper;
        this.statisticsService = statisticsService;
    }

    /**
     * 战斗结算后聚合宠物履历与玩家统计。
     *
     * @param saveId     存档 ID
     * @param ctx        战斗上下文（含事件日志）
     * @param playerWon  玩家是否获胜
     */
    @Transactional
    public void recordBattleSummary(String saveId, BattleContext ctx, boolean playerWon) {
        try {
            doRecordBattleSummary(saveId, ctx, playerWon);
        } catch (Exception e) {
            log.warn("宠物履历记录异常（不阻断主流程）：saveId={}, battleId={}, error={}",
                    saveId, ctx.getBattleId(), e.getMessage());
        }
    }

    private void doRecordBattleSummary(String saveId, BattleContext ctx, boolean playerWon) {
        boolean isBoss = "BOSS".equals(ctx.getBattleType());
        List<BattleEvent> events = ctx.getEvents();
        if (events == null) {
            return;
        }

        // 每只玩家宠物的聚合：petDbId → 累计伤害/承伤/治疗/击败数
        Map<Long, long[]> petAgg = new HashMap<>();
        // 技能使用：skillId → 次数
        Map<String, Integer> skillUse = new HashMap<>();
        // 玩家宠物最高单次伤害 / 最高暴击伤害
        long maxDamage = 0;
        long maxCrit = 0;

        // 敌方单位击败归属：unitId → 击杀来源 petDbId
        Map<String, Long> killSourceByUnit = new HashMap<>();

        for (BattleEvent ev : events) {
            if (ev.getType() == null) {
                continue;
            }
            Long srcPet = parsePetDbId(ev.getSourceId());
            Long tgtPet = parsePetDbId(ev.getTargetId());
            int value = ev.getValue() == null ? 0 : ev.getValue();

            switch (ev.getType()) {
                case DAMAGE -> {
                    if (srcPet != null) {
                        inc(petAgg, srcPet, 0, value); // 伤害
                        if (value > maxDamage) {
                            maxDamage = value;
                        }
                        boolean crit = Boolean.TRUE.equals(ev.getCritical());
                        if (crit && value > maxCrit) {
                            maxCrit = value;
                        }
                        // 记录敌方单位最近伤害来源（用于击杀归属）
                        if (isEnemyUnit(ev.getTargetId())) {
                            killSourceByUnit.put(ev.getTargetId(), srcPet);
                        }
                    }
                    if (tgtPet != null) {
                        inc(petAgg, tgtPet, 1, value); // 承伤
                    }
                }
                case HEAL, LIFE_STEAL -> {
                    if (srcPet != null) {
                        inc(petAgg, srcPet, 2, value); // 治疗
                    }
                }
                case SKILL_CAST -> {
                    if (ev.getSkillId() != null) {
                        skillUse.merge(ev.getSkillId(), 1, Integer::sum);
                    }
                }
                case PET_DEFEATED -> {
                    // 敌方单位倒下 → 计入击杀来源宠物
                    if (isEnemyUnit(ev.getTargetId()) && killSourceByUnit.containsKey(ev.getTargetId())) {
                        Long killer = killSourceByUnit.get(ev.getTargetId());
                        inc(petAgg, killer, 3, 1); // 击败数
                    }
                }
                default -> {
                    // 忽略
                }
            }
        }

        // 写回宠物履历字段
        for (Map.Entry<Long, long[]> entry : petAgg.entrySet()) {
            PlayerPetEntity pet = petMapper.selectById(entry.getKey());
            if (pet == null) {
                continue;
            }
            long[] agg = entry.getValue();
            pet.setTotalDamage(nzL(pet.getTotalDamage()) + agg[0]);
            pet.setTotalDamageTaken(nzL(pet.getTotalDamageTaken()) + agg[1]);
            pet.setTotalHeal(nzL(pet.getTotalHeal()) + agg[2]);
            pet.setKillCount(nz(pet.getKillCount()) + (int) agg[3]);
            if (isBoss) {
                pet.setBossBattleCount(nz(pet.getBossBattleCount()) + 1);
                if (playerWon) {
                    pet.setBossWinCount(nz(pet.getBossWinCount()) + 1);
                }
            }
            petMapper.updateById(pet);
        }

        // 玩家统计
        statisticsService.increment(saveId, StatisticsService.ST_TOTAL_DAMAGE, aggTotalDamage(petAgg));
        statisticsService.increment(saveId, StatisticsService.ST_TOTAL_HEAL, aggTotalHeal(petAgg));
        statisticsService.setMax(saveId, StatisticsService.ST_MAX_DAMAGE, maxDamage);
        statisticsService.setMax(saveId, StatisticsService.ST_MAX_CRIT_DAMAGE, maxCrit);
        statisticsService.increment(saveId, StatisticsService.ST_TOTAL_KILLS, aggKills(petAgg));
        for (Map.Entry<String, Integer> skill : skillUse.entrySet()) {
            statisticsService.increment(saveId, StatisticsService.ST_USE_SKILL_PREFIX + skill.getKey(), skill.getValue());
        }
        // 出战宠物使用统计
        for (BattleUnit unit : ctx.getPlayerSide().getUnits()) {
            if (unit.getSpeciesId() != null) {
                statisticsService.increment(saveId, StatisticsService.ST_USE_PET_PREFIX + unit.getSpeciesId(), 1);
            }
        }
    }

    private long aggTotalDamage(Map<Long, long[]> agg) {
        long sum = 0;
        for (long[] v : agg.values()) {
            sum += v[0];
        }
        return sum;
    }

    private long aggTotalHeal(Map<Long, long[]> agg) {
        long sum = 0;
        for (long[] v : agg.values()) {
            sum += v[2];
        }
        return sum;
    }

    private long aggKills(Map<Long, long[]> agg) {
        long sum = 0;
        for (long[] v : agg.values()) {
            sum += v[3];
        }
        return sum;
    }

    private void inc(Map<Long, long[]> agg, Long petDbId, int idx, long delta) {
        if (petDbId == null) {
            return;
        }
        agg.computeIfAbsent(petDbId, k -> new long[4])[idx] += delta;
    }

    private Long parsePetDbId(String unitId) {
        if (unitId != null && unitId.startsWith("P_")) {
            try {
                return Long.parseLong(unitId.substring(2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isEnemyUnit(String unitId) {
        return unitId != null && !unitId.startsWith("P_");
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private long nzL(Long v) {
        return v == null ? 0L : v;
    }
}
