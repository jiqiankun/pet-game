package com.petgame.capture;

import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.BattleUnit.WildUnitData;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.MapsConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.common.GameRandom;
import com.petgame.developer.DevContext;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 野生遭遇生成服务（阶段 5）。
 * <p>
 * 按刷新组配置生成野生敌方阵容（需求 §43/§44）：
 * <ul>
 *   <li>按权重抽取种族、按等级范围生成等级；</li>
 *   <li>六维资质在 aptitudeMin~aptitudeMax 均匀随机；</li>
 *   <li>六维个体浮动 = 种族基础 × ±baseStatVariance（与玩家宠物同一面板模型）；</li>
 *   <li>低概率（rareSkillChance）从种族稀有技能池随机携带 1 个稀有技能；</li>
 *   <li>极低概率（specialAppearanceChance）标记特殊外观。</li>
 * </ul>
 * 本阶段无精英个体、无隐藏遭遇（后续阶段）。
 */
@Service
public class WildEncounterService {

    /** 阶段 5 简化遭遇入口使用的默认刷新组。 */
    public static final String GENERAL_GROUP_ID = "ENCOUNTER_GENERAL";

    /** 特殊外观标记（落库 specialAppearance 字段值）。 */
    public static final String SPECIAL_APPEARANCE_MARK = "SPECIAL";

    private final GameConfigRegistry registry;
    private final PetGrowthService growthService;
    private final DevContext devContext;

    public WildEncounterService(GameConfigRegistry registry,
                                PetGrowthService growthService,
                                DevContext devContext) {
        this.registry = registry;
        this.growthService = growthService;
        this.devContext = devContext;
    }

    /**
     * 生成一场野生遭遇的敌方阵容。
     *
     * @param groupId 刷新组 ID
     * @param random  随机源（统一 GameRandom，固定种子可复现）
     * @return 野生敌方单位列表（首 standardBattleSlots 只上场）
     */
    public List<BattleUnit> generateEncounter(String groupId, GameRandom random) {
        EncountersConfig.EncounterGroup group = getEncounterGroup(groupId);
        SystemRuleConfig rules = registry.getSystemRules();
        // 开发者工具：强制本次全部野生遭遇为精英（消费后清除）
        boolean forceElite = devContext.consumeForceElite();
        int teamSize = random.nextInt(group.getTeamSizeMin(), group.getTeamSizeMax());

        List<BattleUnit> units = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) {
            EncountersConfig.SpeciesEntry entry = rollSpecies(group.getSpecies(), random);
            int level = random.nextInt(entry.getLevelMin(), entry.getLevelMax());
            boolean elite = forceElite || random.chance(rules.getEliteSpawnChance());
            if (elite) {
                level = Math.min(level + random.nextInt(
                        rules.getEliteLevelBonusMin(), rules.getEliteLevelBonusMax()), rules.getLevelCap());
            }
            units.add(generateWildUnit(entry, rules, random, i, level, elite));
        }
        return units;
    }

    /**
     * 正式地图遭遇入口（阶段 13）：地图、队伍和全局难度共同决定本次临时敌方。
     * 不持久化任何野外成长状态。
     */
    public List<BattleUnit> generateEncounter(String groupId, MapsConfig.RegionConfig region,
                                              List<PlayerPetEntity> teamPets, String difficulty,
                                              GameRandom random) {
        EncountersConfig.EncounterGroup group = getEncounterGroup(groupId);
        SystemRuleConfig rules = registry.getSystemRules();
        SystemRuleConfig.DifficultyProfile profile = requireProfile(difficulty);
        // 开发者工具：强制本次野生遭遇为精英（消费后清除）
        boolean eliteEncounter = devContext.consumeForceElite()
                || random.chance(rules.getEliteSpawnChance());
        int teamSize = resolveTeamSize(group, profile, eliteEncounter, rules.getStandardBattleSlots(), random);
        int referenceLevel = teamReferenceLevel(teamPets);
        List<BattleUnit> units = new ArrayList<>();
        Set<String> selectedSpecies = new HashSet<>();
        Set<String> selectedRoles = new HashSet<>();
        for (int i = 0; i < teamSize; i++) {
            String preferredRole = profile.isRoleSynergy() ? nextPreferredRole(selectedRoles) : null;
            EncountersConfig.SpeciesEntry entry = rollSpecies(group.getSpecies(), random,
                    preferredRole, selectedSpecies);
            boolean elite = (i == 0 && eliteEncounter) || (i > 0 && random.chance(rules.getEliteSpawnChance()));
            int level = resolveLevel(entry, region, profile, referenceLevel, elite, rules, random);
            BattleUnit unit = generateWildUnit(entry, rules, random, i, level, elite);
            units.add(unit);
            selectedSpecies.add(entry.getSpeciesId());
            selectedRoles.add(resolveRole(registry.getSpecies(entry.getSpeciesId())));
        }
        return units;
    }

    /**
     * 获取刷新组配置，不存在抛出业务异常。
     */
    public EncountersConfig.EncounterGroup getEncounterGroup(String groupId) {
        for (EncountersConfig.EncounterGroup group
                : registry.getEncountersConfig().getEncounterGroups()) {
            if (group.getId().equals(groupId)) {
                return group;
            }
        }
        throw new com.petgame.common.BusinessException("ENCOUNTER_GROUP_NOT_FOUND",
                "遭遇组不存在: " + groupId);
    }

    /** 生成单只野生单位（与玩家宠物共用面板公式，保证捕捉后属性一致）。 */
    private BattleUnit generateWildUnit(EncountersConfig.SpeciesEntry entry,
                                        SystemRuleConfig rules, GameRandom random, int index,
                                        int level, boolean isElite) {
        PetSpeciesConfig species = registry.getSpecies(entry.getSpeciesId());
        if (species == null) {
            throw new IllegalStateException("野生遭遇引用的种族配置缺失: " + entry.getSpeciesId());
        }

        // 阶段 10：精英个体判定
        int aptitudeFloor = rules.getAptitudeMin();
        double rareSkillBonus = 0;
        if (isElite) {
            aptitudeFloor = Math.max(aptitudeFloor, rules.getEliteMinAptitudeFloor());
            rareSkillBonus = rules.getEliteRareSkillChanceBonus();
        }

        // 六维资质均匀随机（精英个体使用提高的下限）
        int hpApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());
        int strApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());
        int sprApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());
        int defApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());
        int resApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());
        int spdApt = random.nextInt(aptitudeFloor, rules.getAptitudeMax());

        // 六维个体浮动 = 种族基础 × ±baseStatVariance（捕获时固化）
        double variance = rules.getBaseStatVariance();
        int hpOff = (int) Math.round(species.getBaseHp() * random.nextDouble(-variance, variance));
        int strOff = (int) Math.round(species.getBaseStrength() * random.nextDouble(-variance, variance));
        int sprOff = (int) Math.round(species.getBaseSpirit() * random.nextDouble(-variance, variance));
        int defOff = (int) Math.round(species.getBaseDefense() * random.nextDouble(-variance, variance));
        int resOff = (int) Math.round(species.getBaseResistance() * random.nextDouble(-variance, variance));
        int spdOff = (int) Math.round(species.getBaseSpeed() * random.nextDouble(-variance, variance));

        // 低概率稀有技能 + 精英额外稀有技能概率
        List<String> extraSkills = new ArrayList<>();
        double effectiveRareSkillChance = rules.getRareSkillChance() + rareSkillBonus;
        if (!species.getRareSkills().isEmpty() && random.chance(effectiveRareSkillChance)) {
            extraSkills.add(species.getRareSkills()
                    .get(random.nextInt(0, species.getRareSkills().size() - 1)));
        }

        // 阶段 10：多变体特殊外观（替代原有单一 SPECIAL 标记）
        String specialAppearance = rollSpecialAppearance(rules, random);

        // 统一面板公式：临时实体（不落库）→ 面板快照
        PlayerPetEntity tmp = new PlayerPetEntity();
        tmp.setLevel(level);
        tmp.setHpAptitude(hpApt);
        tmp.setStrengthAptitude(strApt);
        tmp.setSpiritAptitude(sprApt);
        tmp.setDefenseAptitude(defApt);
        tmp.setResistanceAptitude(resApt);
        tmp.setSpeedAptitude(spdApt);
        tmp.setBaseHpOffset(hpOff);
        tmp.setBaseStrengthOffset(strOff);
        tmp.setBaseSpiritOffset(sprOff);
        tmp.setBaseDefenseOffset(defOff);
        tmp.setBaseResistanceOffset(resOff);
        tmp.setBaseSpeedOffset(spdOff);
        // 野生单位无自由加点（面板公式需要非空字段）
        tmp.setFreePointHp(0);
        tmp.setFreePointStrength(0);
        tmp.setFreePointSpirit(0);
        tmp.setFreePointDefense(0);
        tmp.setFreePointResistance(0);
        tmp.setFreePointSpeed(0);
        PetPanelStats stats = growthService.computePanelStats(tmp, species);

        BattleUnit unit = new BattleUnit();
        unit.setUnitId("W_" + (index + 1));
        unit.setSpeciesId(species.getId());
        unit.setName(species.getName());
        unit.setElement(species.getElement());
        unit.setLevel(level);
        unit.setActualLevel(level);
        unit.setEffectiveLevel(level);
        unit.setMaxHp(stats.getMaxHp());
        unit.setStrength(stats.getStrength());
        unit.setSpirit(stats.getSpirit());
        unit.setDefense(stats.getDefense());
        unit.setResistance(stats.getResistance());
        unit.setSpeed(stats.getSpeed());
        unit.setCurrentHp(stats.getMaxHp());
        unit.setActive(index < rules.getStandardBattleSlots());
        unit.setPosition(unit.isActive() ? index : -1);

        // 已解锁种族技能 + 稀有技能
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            if (slot.getUnlockLevel() <= level) {
                unit.getSkillIds().add(slot.getSkillId());
            }
        }
        unit.getSkillIds().addAll(extraSkills);
        // REV-012：野生单位仅携带其等级已解锁的被动
        for (PetSpeciesConfig.SpeciesPassiveSlot passiveSlot : species.getPassives()) {
            if (passiveSlot.getUnlockLevel() > level) {
                continue;
            }
            PassiveSkillConfig passive = registry.getPassive(passiveSlot.getPassiveId());
            if (passive != null) {
                unit.getPassives().add(passive);
            }
        }

        WildUnitData wildData = new WildUnitData();
        wildData.setHpAptitude(hpApt);
        wildData.setStrengthAptitude(strApt);
        wildData.setSpiritAptitude(sprApt);
        wildData.setDefenseAptitude(defApt);
        wildData.setResistanceAptitude(resApt);
        wildData.setSpeedAptitude(spdApt);
        wildData.setBaseHpOffset(hpOff);
        wildData.setBaseStrengthOffset(strOff);
        wildData.setBaseSpiritOffset(sprOff);
        wildData.setBaseDefenseOffset(defOff);
        wildData.setBaseResistanceOffset(resOff);
        wildData.setBaseSpeedOffset(spdOff);
        wildData.setExtraSkillIds(extraSkills);
        wildData.setSpecialAppearance(specialAppearance);
        wildData.setElite(isElite);
        unit.setWildData(wildData);
        return unit;
    }

    /**
     * 多变体特殊外观抽取（阶段 10）。
     * <p>
     * 从 specialAppearanceVariants 中按独立概率抽取；
     * 若未配置变体则回退到原有单一 SPECIAL 标记。
     */
    private String rollSpecialAppearance(SystemRuleConfig rules, GameRandom random) {
        List<SystemRuleConfig.AppearanceVariantConfig> variants = rules.getSpecialAppearanceVariants();
        if (variants != null && !variants.isEmpty()) {
            for (SystemRuleConfig.AppearanceVariantConfig variant : variants) {
                if (random.chance(variant.getChance())) {
                    return variant.getId();
                }
            }
            return null;
        }
        // 回退到原有单一标记
        return random.chance(rules.getSpecialAppearanceChance())
                ? SPECIAL_APPEARANCE_MARK : null;
    }

    /** 按权重抽取种族条目。 */
    private EncountersConfig.SpeciesEntry rollSpecies(
            List<EncountersConfig.SpeciesEntry> entries, GameRandom random) {
        return rollWeighted(entries, random);
    }

    /** 高难协同时优先补角色，但不按玩家属性定制反制。 */
    private EncountersConfig.SpeciesEntry rollSpecies(List<EncountersConfig.SpeciesEntry> entries,
                                                       GameRandom random, String preferredRole,
                                                       Set<String> selectedSpecies) {
        List<EncountersConfig.SpeciesEntry> candidates = new ArrayList<>();
        if (preferredRole != null) {
            for (EncountersConfig.SpeciesEntry entry : entries) {
                if (!selectedSpecies.contains(entry.getSpeciesId())
                        && preferredRole.equals(resolveRole(registry.getSpecies(entry.getSpeciesId())))) {
                    candidates.add(entry);
                }
            }
        }
        if (candidates.isEmpty()) {
            for (EncountersConfig.SpeciesEntry entry : entries) {
                if (!selectedSpecies.contains(entry.getSpeciesId())) {
                    candidates.add(entry);
                }
            }
        }
        return rollWeighted(candidates.isEmpty() ? entries : candidates, random);
    }

    private EncountersConfig.SpeciesEntry rollWeighted(
            List<EncountersConfig.SpeciesEntry> entries, GameRandom random) {
        int totalWeight = entries.stream().mapToInt(EncountersConfig.SpeciesEntry::getWeight).sum();
        if (totalWeight <= 0) {
            throw new IllegalStateException("刷新组种族池权重总和必须大于 0");
        }
        int roll = random.nextInt(1, totalWeight);
        int cumulative = 0;
        for (EncountersConfig.SpeciesEntry entry : entries) {
            cumulative += entry.getWeight();
            if (roll <= cumulative) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private SystemRuleConfig.DifficultyProfile requireProfile(String difficulty) {
        String key = difficulty == null ? null : difficulty.toUpperCase();
        SystemRuleConfig.GameDifficultyConfig config = registry.getSystemRules().getGameDifficulty();
        SystemRuleConfig.DifficultyProfile profile = config != null && key != null
                ? config.getProfiles().get(key) : null;
        if (profile == null) {
            throw new com.petgame.common.BusinessException("INVALID_GAME_DIFFICULTY", "未知全局难度: " + difficulty);
        }
        return profile;
    }

    private int resolveTeamSize(EncountersConfig.EncounterGroup group,
                                SystemRuleConfig.DifficultyProfile profile, boolean eliteEncounter,
                                int battleSlots, GameRandom random) {
        int difficultyMin = eliteEncounter ? profile.getEliteMinTeamSize() : profile.getWildMinTeamSize();
        int difficultyMax = eliteEncounter ? profile.getEliteMaxTeamSize() : profile.getWildMaxTeamSize();
        int min = Math.max(group.getTeamSizeMin(), difficultyMin);
        int max = Math.min(Math.min(group.getTeamSizeMax(), difficultyMax), battleSlots);
        if (min > max) {
            throw new IllegalStateException("遭遇队伍数量边界无交集: " + group.getId());
        }
        return random.nextInt(min, max);
    }

    private int resolveLevel(EncountersConfig.SpeciesEntry entry, MapsConfig.RegionConfig region,
                             SystemRuleConfig.DifficultyProfile profile, int referenceLevel,
                             boolean elite, SystemRuleConfig rules, GameRandom random) {
        int recommended = region.getRecommendedEnemyLevel();
        int randomOffset = random.nextInt(entry.getLevelMin(), entry.getLevelMax()) - recommended;
        int dynamicOffset = clamp(referenceLevel - recommended,
                profile.getWildPlayerAdjustmentMin(), profile.getWildPlayerAdjustmentMax());
        int baseOffset = elite ? profile.getEliteLevelOffset() : profile.getWildLevelOffset();
        int minOffset = elite ? profile.getEliteMinLevelOffset() : profile.getWildMinLevelOffset();
        int maxOffset = elite ? profile.getEliteMaxLevelOffset() : profile.getWildMaxLevelOffset();
        int eliteBonus = elite ? random.nextInt(rules.getEliteLevelBonusMin(), rules.getEliteLevelBonusMax()) : 0;
        int min = Math.max(region.getMinEnemyLevel(), recommended + minOffset);
        int max = Math.min(Math.min(region.getMaxEnemyLevel(), recommended + maxOffset), rules.getLevelCap());
        if (min > max) {
            throw new IllegalStateException("遭遇等级边界无交集: " + region.getId());
        }
        return clamp(recommended + baseOffset + dynamicOffset + randomOffset + eliteBonus, min, max);
    }

    private static int teamReferenceLevel(List<PlayerPetEntity> teamPets) {
        if (teamPets == null || teamPets.isEmpty()) {
            return 1;
        }
        return teamPets.stream()
                .map(PlayerPetEntity::getLevel)
                .filter(level -> level != null && level > 0)
                .sorted(java.util.Comparator.reverseOrder())
                .limit(3)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .mapToInt(value -> (int) Math.round(value))
                .findFirst()
                .orElse(1);
    }

    private String nextPreferredRole(Set<String> roles) {
        if (!roles.contains("SUPPORT")) return "SUPPORT";
        if (!roles.contains("CONTROL")) return "CONTROL";
        if (!roles.contains("TANK")) return "TANK";
        return "DAMAGE";
    }

    /** 沿用既有 role 配置；未标注的种族以面板倾向作最小推断。 */
    private String resolveRole(PetSpeciesConfig species) {
        if (species == null) return "DAMAGE";
        if (species.getRole() != null && !species.getRole().isBlank()) {
            return species.getRole().toUpperCase();
        }
        boolean hasHeal = species.getSkills().stream().anyMatch(slot -> {
            var skill = registry.getSkill(slot.getSkillId());
            return skill != null && "HEAL".equalsIgnoreCase(skill.getEffectType());
        });
        if (hasHeal) return "SUPPORT";
        double bulk = species.getBaseHp() + species.getBaseDefense() + species.getBaseResistance();
        double offense = species.getBaseStrength() + species.getBaseSpirit();
        return bulk > offense * 1.7 ? "TANK" : "DAMAGE";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
