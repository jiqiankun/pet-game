package com.petgame.pet.domain;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.entity.PlayerPetEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物成长领域服务（阶段 4 正式化）。
 * <p>
 * 承载面板属性公式（需求 §9/§12）、升级经验、自由点数、加点消耗、技能解锁等核心规则。
 * 阶段 3 的临时面板公式（资质作用于基础值）在此正式化为资质作用于等级成长，
 * 使资质差异随等级逐渐体现、Lv.1 时资质无影响（符合需求 §12）。
 * <p>
 * 本服务无状态，仅读取配置，不直接操作数据库；持久化由 PetService 编排。
 */
@Component
public class PetGrowthService {

    private final GameConfigRegistry registry;

    public PetGrowthService(GameConfigRegistry registry) {
        this.registry = registry;
    }

    // ==================== 面板属性公式 ====================

    /**
     * 计算宠物当前面板属性（需求 §9）。
     * 最终属性 = 种族基础（含个体浮动） + 等级固定成长 + 资质成长修正 + 自由属性点。
     */
    public PetPanelStats computePanelStats(PlayerPetEntity pet, PetSpeciesConfig species) {
        return computePanelStatsAtLevel(pet, species, pet.getLevel());
    }

    /**
     * 计算宠物在指定等级的面板属性（用于升级预览，自由点数不变）。
     */
    public PetPanelStats computePanelStatsAtLevel(PlayerPetEntity pet,
                                                   PetSpeciesConfig species, int level) {
        SystemRuleConfig rules = registry.getSystemRules();
        int levelBonus = Math.max(0, level - 1);

        PetPanelStats stats = new PetPanelStats();

        // HP 维度
        int hpBase = species.getBaseHp() + nz(pet.getBaseHpOffset());
        PetPanelStats.Breakdown hpBd = computeHpBreakdown(
                hpBase, pet.getHpAptitude(), pet.getFreePointHp(), levelBonus, rules);
        stats.setMaxHp(hpBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.HP, hpBd);

        // 非HP五维
        int strBase = species.getBaseStrength() + nz(pet.getBaseStrengthOffset());
        PetPanelStats.Breakdown strBd = computeStatBreakdown(
                strBase, pet.getStrengthAptitude(), pet.getFreePointStrength(), levelBonus, rules);
        stats.setStrength(strBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.STRENGTH, strBd);

        int sprBase = species.getBaseSpirit() + nz(pet.getBaseSpiritOffset());
        PetPanelStats.Breakdown sprBd = computeStatBreakdown(
                sprBase, pet.getSpiritAptitude(), pet.getFreePointSpirit(), levelBonus, rules);
        stats.setSpirit(sprBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.SPIRIT, sprBd);

        int defBase = species.getBaseDefense() + nz(pet.getBaseDefenseOffset());
        PetPanelStats.Breakdown defBd = computeStatBreakdown(
                defBase, pet.getDefenseAptitude(), pet.getFreePointDefense(), levelBonus, rules);
        stats.setDefense(defBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.DEFENSE, defBd);

        int resBase = species.getBaseResistance() + nz(pet.getBaseResistanceOffset());
        PetPanelStats.Breakdown resBd = computeStatBreakdown(
                resBase, pet.getResistanceAptitude(), pet.getFreePointResistance(), levelBonus, rules);
        stats.setResistance(resBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.RESISTANCE, resBd);

        int spdBase = species.getBaseSpeed() + nz(pet.getBaseSpeedOffset());
        PetPanelStats.Breakdown spdBd = computeStatBreakdown(
                spdBase, pet.getSpeedAptitude(), pet.getFreePointSpeed(), levelBonus, rules);
        stats.setSpeed(spdBd.getTotal());
        stats.getBreakdowns().put(PetPanelStats.SPEED, spdBd);

        return stats;
    }

    /**
     * 非 HP 维度分解：资质成长修正作用于「等级成长部分」（需求 §12）。
     * growth = levelStatGrowth * levelBonus
     * aptBonus = growth * (aptitude - 50) / 100
     * freeBonus = freePoints * freePointStatValue
     * total = base + growth + aptBonus + freeBonus
     */
    private PetPanelStats.Breakdown computeStatBreakdown(int base, int aptitude, int freePoints,
                                                          int levelBonus, SystemRuleConfig rules) {
        double growth = rules.getLevelStatGrowth() * levelBonus;
        double aptBonus = growth * (aptitude - 50) / 100.0;
        double freeBonus = freePoints * rules.getFreePointStatValue();
        int total = (int) Math.round(base + growth + aptBonus + freeBonus);
        return new PetPanelStats.Breakdown(base, (int) Math.round(growth),
                (int) Math.round(aptBonus), (int) Math.round(freeBonus), total);
    }

    /**
     * HP 维度分解（独立成长与自由点系数）。
     */
    private PetPanelStats.Breakdown computeHpBreakdown(int baseHp, int aptitude, int freePoints,
                                                        int levelBonus, SystemRuleConfig rules) {
        double growth = rules.getLevelHpGrowth() * levelBonus;
        double aptBonus = growth * (aptitude - 50) / 100.0;
        double freeBonus = freePoints * rules.getFreePointHpValue();
        int total = (int) Math.round(baseHp + growth + aptBonus + freeBonus);
        return new PetPanelStats.Breakdown(baseHp, (int) Math.round(growth),
                (int) Math.round(aptBonus), (int) Math.round(freeBonus), total);
    }

    // ==================== 升级经验 ====================

    /**
     * 从 level 升到 level+1 所需经验（需求 §17）。
     * exp = expBase * expGrowthFactor^(level-1)；达到上限返回 0。
     */
    public int expToNextLevel(int level) {
        SystemRuleConfig rules = registry.getSystemRules();
        if (level >= rules.getLevelCap()) {
            return 0;
        }
        return (int) Math.round(rules.getExpBase() * Math.pow(rules.getExpGrowthFactor(), level - 1));
    }

    /**
     * 从 fromLevel 升到 toLevel 累计所需经验（fromLevel < toLevel）。
     */
    public int totalExpToReach(int fromLevel, int toLevel) {
        if (toLevel <= fromLevel) {
            return 0;
        }
        int total = 0;
        for (int lv = fromLevel; lv < toLevel; lv++) {
            total += expToNextLevel(lv);
        }
        return total;
    }

    // ==================== 自由属性点 ====================

    /**
     * 截止 level 累计获得的自由点数（需求 §19）。
     * 每级 freePointsPerLevel（默认 3）+ 稀有度每 10 级额外。
     * Lv.1 = 0；Lv.10 首次获得稀有度额外点数。
     */
    public int freePointsEarned(int level, String rarity) {
        SystemRuleConfig rules = registry.getSystemRules();
        int perLevel = rules.getFreePointsPerLevel() * Math.max(0, level - 1);
        int rarityExtra = getRarityExtraPoints(rarity);
        int milestoneBonus = rarityExtra * (level / 10);
        return perLevel + milestoneBonus;
    }

    /** 获取稀有度每 10 级额外点数（COMMON 0 / RARE 2 / EPIC 4 / LEGENDARY 6）。 */
    public int getRarityExtraPoints(String rarity) {
        if (rarity == null) {
            return 0;
        }
        return registry.getSystemRules().getRarityExtraPointsPer10Levels()
                .getOrDefault(rarity, 0);
    }

    /**
     * 计算宠物剩余可分配自由点数 = 已获得 - 已消耗（按需求 §20 转换表折算，速度每点次消耗 2）。
     */
    public int freePointsAvailable(PlayerPetEntity pet, PetSpeciesConfig species) {
        int earned = freePointsEarned(pet.getLevel(), species.getRarity());
        return earned - consumedFreePoints(pet);
    }

    /** 已分配自由点数的点次总和（不折算速度双倍消耗，仅作参考）。 */
    public int allocatedFreePoints(PlayerPetEntity pet) {
        return nz(pet.getFreePointHp()) + nz(pet.getFreePointStrength()) + nz(pet.getFreePointSpirit())
                + nz(pet.getFreePointDefense()) + nz(pet.getFreePointResistance()) + nz(pet.getFreePointSpeed());
    }

    /**
     * 按需求 §20 转换表折算已消耗的自由点数：
     * 生命每点次消耗 hpPointCost（默认 1）、力量/灵力/防御/抗性每点次消耗 statPointCost（默认 1）、
     * 速度每点次消耗 speedPointCost（默认 2）。
     */
    public int consumedFreePoints(PlayerPetEntity pet) {
        SystemRuleConfig rules = registry.getSystemRules();
        return nz(pet.getFreePointHp()) * rules.getHpPointCost()
                + (nz(pet.getFreePointStrength()) + nz(pet.getFreePointSpirit())
                + nz(pet.getFreePointDefense()) + nz(pet.getFreePointResistance())) * rules.getStatPointCost()
                + nz(pet.getFreePointSpeed()) * rules.getSpeedPointCost();
    }

    /**
     * 计算增加 points 点该维度属性消耗的自由点数（需求 §20）。
     *
     * @param statKey 维度键（PetPanelStats.HP/STRENGTH/.../SPEED）
     */
    public int pointCostForStat(String statKey, int points) {
        if (points <= 0) {
            return 0;
        }
        SystemRuleConfig rules = registry.getSystemRules();
        int costPerPoint;
        switch (statKey) {
            case PetPanelStats.HP:
                costPerPoint = rules.getHpPointCost();
                break;
            case PetPanelStats.SPEED:
                costPerPoint = rules.getSpeedPointCost();
                break;
            default:
                costPerPoint = rules.getStatPointCost();
                break;
        }
        return costPerPoint * points;
    }

    /**
     * 洗点后返还的自由点数 = 已消耗自由点数（免费洗点，需求 §21）。
     */
    public int freePointsRefundOnReset(PlayerPetEntity pet) {
        return consumedFreePoints(pet);
    }

    // ==================== 技能解锁 ====================

    /**
     * 返回 (fromLevel, toLevel] 区间新解锁的技能（需求 §23 等级解锁）。
     */
    public List<UnlockedSkill> skillsUnlockedBetween(PetSpeciesConfig species,
                                                      int fromLevel, int toLevel) {
        List<UnlockedSkill> unlocked = new ArrayList<>();
        if (species.getSkills() == null) {
            return unlocked;
        }
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            int ul = slot.getUnlockLevel();
            if (ul > fromLevel && ul <= toLevel) {
                unlocked.add(new UnlockedSkill(slot.getSkillId(), ul));
            }
        }
        return unlocked;
    }

    /** 已解锁的全部技能（unlockLevel <= level），含等级解锁的种族技能。 */
    public List<PetSpeciesConfig.SpeciesSkillSlot> learnedSpeciesSkills(
            PetSpeciesConfig species, int level) {
        List<PetSpeciesConfig.SpeciesSkillSlot> learned = new ArrayList<>();
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            if (slot.getUnlockLevel() <= level) {
                learned.add(slot);
            }
        }
        return learned;
    }

    // ==================== 升级预览 ====================

    /**
     * 升级预览（需求 §17：所需经验、升级后等级、属性变化、获得点数、即将解锁技能）。
     *
     * @param pet         宠物存档
     * @param species     种族配置
     * @param targetLevel 目标等级（必须 > 当前等级且 <= levelCap）
     */
    public LevelUpPreview previewLevelUp(PlayerPetEntity pet,
                                           PetSpeciesConfig species, int targetLevel) {
        SystemRuleConfig rules = registry.getSystemRules();
        int currentLevel = pet.getLevel();
        if (targetLevel <= currentLevel) {
            throw new IllegalArgumentException("目标等级必须大于当前等级");
        }
        if (targetLevel > rules.getLevelCap()) {
            throw new IllegalArgumentException("目标等级超过等级上限 " + rules.getLevelCap());
        }

        LevelUpPreview preview = new LevelUpPreview();
        preview.setFromLevel(currentLevel);
        preview.setToLevel(targetLevel);
        preview.setExpRequired(totalExpToReach(currentLevel, targetLevel));
        preview.setPointsGained(freePointsEarned(targetLevel, species.getRarity())
                - freePointsEarned(currentLevel, species.getRarity()));
        preview.setSkillsUnlocked(skillsUnlockedBetween(species, currentLevel, targetLevel));
        preview.setBeforeStats(computePanelStats(pet, species));
        preview.setAfterStats(computePanelStatsAtLevel(pet, species, targetLevel));
        return preview;
    }

    // ==================== 工具 ====================

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 已解锁技能记录。 */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UnlockedSkill {
        private String skillId;
        private int unlockLevel;
    }

    /** 升级预览结果。 */
    @lombok.Data
    public static class LevelUpPreview {
        private int fromLevel;
        private int toLevel;
        private int expRequired;
        private int pointsGained;
        private List<UnlockedSkill> skillsUnlocked;
        private PetPanelStats beforeStats;
        private PetPanelStats afterStats;
        /** 当前玩家公共经验池可用值（由 PetService 回填）。 */
        private Integer expPoolAvailable;
        /** 经验池是否足够（由 PetService 回填）。 */
        private Boolean expPoolSufficient;
    }
}
