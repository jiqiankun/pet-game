package com.petgame.battle.service;

import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Boss 战有效等级解析器（阶段 13）。
 * 只创建临时投影，绝不修改真实宠物实体。
 */
@Component
public class BattleLevelResolver {

    private final PetGrowthService growthService;

    public BattleLevelResolver(PetGrowthService growthService) {
        this.growthService = growthService;
    }

    /** 按有效等级重算面板，并在必要时投影自由属性点。 */
    public ResolvedPet resolve(PlayerPetEntity pet, PetSpeciesConfig species, Integer playerLevelCap) {
        int actualLevel = Math.max(1, nz(pet.getLevel()));
        int effectiveLevel = playerLevelCap == null
                ? actualLevel
                : Math.max(1, Math.min(actualLevel, playerLevelCap));
        PlayerPetEntity projected = copyForBattle(pet);
        int effectiveBudget = growthService.freePointsEarned(effectiveLevel, species.getRarity());
        projectFreePoints(pet, projected, effectiveBudget);
        PetPanelStats stats = growthService.computePanelStatsAtLevel(projected, species, effectiveLevel);
        return new ResolvedPet(actualLevel, effectiveLevel, effectiveBudget, projected, stats);
    }

    /**
     * 按真实已投入比例投影自由点。先取整，再以当前最大余数补点；
     * 速度等成本继续沿用 PetGrowthService 的既有规则。
     */
    private void projectFreePoints(PlayerPetEntity source, PlayerPetEntity target, int budget) {
        int spent = growthService.consumedFreePoints(source);
        if (spent <= budget) {
            return;
        }
        List<PointShare> shares = new ArrayList<>(List.of(
                new PointShare("HP", nz(source.getFreePointHp()), cost("HP")),
                new PointShare("STRENGTH", nz(source.getFreePointStrength()), cost("STRENGTH")),
                new PointShare("SPIRIT", nz(source.getFreePointSpirit()), cost("SPIRIT")),
                new PointShare("DEFENSE", nz(source.getFreePointDefense()), cost("DEFENSE")),
                new PointShare("RESISTANCE", nz(source.getFreePointResistance()), cost("RESISTANCE")),
                new PointShare("SPEED", nz(source.getFreePointSpeed()), cost("SPEED"))));
        int used = 0;
        for (PointShare share : shares) {
            share.projected = Math.min(share.original,
                    (int) Math.floor((double) share.original * budget / spent));
            share.remainder = (double) share.original * budget / spent - share.projected;
            used += share.projected * share.cost;
        }
        while (true) {
            int remaining = budget - used;
            PointShare next = shares.stream()
                    .filter(s -> s.projected < s.original && s.cost <= remaining)
                    .max(Comparator.comparingDouble((PointShare s) -> s.remainder)
                            .thenComparingInt(s -> -s.cost)
                            .thenComparing(s -> s.key))
                    .orElse(null);
            if (next == null) {
                break;
            }
            next.projected++;
            next.remainder--;
            used += next.cost;
        }
        for (PointShare share : shares) {
            apply(target, share.key, share.projected);
        }
    }

    private int cost(String key) {
        return growthService.pointCostForStat(key, 1);
    }

    private static void apply(PlayerPetEntity pet, String key, int value) {
        switch (key) {
            case "HP" -> pet.setFreePointHp(value);
            case "STRENGTH" -> pet.setFreePointStrength(value);
            case "SPIRIT" -> pet.setFreePointSpirit(value);
            case "DEFENSE" -> pet.setFreePointDefense(value);
            case "RESISTANCE" -> pet.setFreePointResistance(value);
            case "SPEED" -> pet.setFreePointSpeed(value);
            default -> throw new IllegalArgumentException("未知自由点维度: " + key);
        }
    }

    private static PlayerPetEntity copyForBattle(PlayerPetEntity source) {
        PlayerPetEntity copy = new PlayerPetEntity();
        copy.setLevel(source.getLevel());
        copy.setHpAptitude(source.getHpAptitude());
        copy.setStrengthAptitude(source.getStrengthAptitude());
        copy.setSpiritAptitude(source.getSpiritAptitude());
        copy.setDefenseAptitude(source.getDefenseAptitude());
        copy.setResistanceAptitude(source.getResistanceAptitude());
        copy.setSpeedAptitude(source.getSpeedAptitude());
        copy.setBaseHpOffset(source.getBaseHpOffset());
        copy.setBaseStrengthOffset(source.getBaseStrengthOffset());
        copy.setBaseSpiritOffset(source.getBaseSpiritOffset());
        copy.setBaseDefenseOffset(source.getBaseDefenseOffset());
        copy.setBaseResistanceOffset(source.getBaseResistanceOffset());
        copy.setBaseSpeedOffset(source.getBaseSpeedOffset());
        copy.setFreePointHp(nz(source.getFreePointHp()));
        copy.setFreePointStrength(nz(source.getFreePointStrength()));
        copy.setFreePointSpirit(nz(source.getFreePointSpirit()));
        copy.setFreePointDefense(nz(source.getFreePointDefense()));
        copy.setFreePointResistance(nz(source.getFreePointResistance()));
        copy.setFreePointSpeed(nz(source.getFreePointSpeed()));
        return copy;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    @Data
    @AllArgsConstructor
    public static class ResolvedPet {
        private int actualLevel;
        private int effectiveLevel;
        private int effectivePointBudget;
        private PlayerPetEntity projectedPet;
        private PetPanelStats stats;
    }

    private static class PointShare {
        private final String key;
        private final int original;
        private final int cost;
        private int projected;
        private double remainder;

        private PointShare(String key, int original, int cost) {
            this.key = key;
            this.original = original;
            this.cost = cost;
        }
    }
}
