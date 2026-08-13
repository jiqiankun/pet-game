package com.petgame.capture;

import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.BattleUnit.WildUnitData;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.common.GameRandom;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public WildEncounterService(GameConfigRegistry registry, PetGrowthService growthService) {
        this.registry = registry;
        this.growthService = growthService;
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
        int teamSize = random.nextInt(group.getTeamSizeMin(), group.getTeamSizeMax());

        List<BattleUnit> units = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) {
            units.add(generateWildUnit(group, rules, random, i));
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
    private BattleUnit generateWildUnit(EncountersConfig.EncounterGroup group,
                                        SystemRuleConfig rules, GameRandom random, int index) {
        EncountersConfig.SpeciesEntry entry = rollSpecies(group.getSpecies(), random);
        PetSpeciesConfig species = registry.getSpecies(entry.getSpeciesId());
        if (species == null) {
            throw new IllegalStateException("野生遭遇引用的种族配置缺失: " + entry.getSpeciesId());
        }
        int level = random.nextInt(entry.getLevelMin(), entry.getLevelMax());

        // 六维资质均匀随机
        int hpApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());
        int strApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());
        int sprApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());
        int defApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());
        int resApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());
        int spdApt = random.nextInt(rules.getAptitudeMin(), rules.getAptitudeMax());

        // 六维个体浮动 = 种族基础 × ±baseStatVariance（捕获时固化）
        double variance = rules.getBaseStatVariance();
        int hpOff = (int) Math.round(species.getBaseHp() * random.nextDouble(-variance, variance));
        int strOff = (int) Math.round(species.getBaseStrength() * random.nextDouble(-variance, variance));
        int sprOff = (int) Math.round(species.getBaseSpirit() * random.nextDouble(-variance, variance));
        int defOff = (int) Math.round(species.getBaseDefense() * random.nextDouble(-variance, variance));
        int resOff = (int) Math.round(species.getBaseResistance() * random.nextDouble(-variance, variance));
        int spdOff = (int) Math.round(species.getBaseSpeed() * random.nextDouble(-variance, variance));

        // 低概率稀有技能 + 极低概率特殊外观
        List<String> extraSkills = new ArrayList<>();
        if (!species.getRareSkills().isEmpty() && random.chance(rules.getRareSkillChance())) {
            extraSkills.add(species.getRareSkills()
                    .get(random.nextInt(0, species.getRareSkills().size() - 1)));
        }
        String specialAppearance = random.chance(rules.getSpecialAppearanceChance())
                ? SPECIAL_APPEARANCE_MARK : null;

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
        unit.setWildData(wildData);
        return unit;
    }

    /** 按权重抽取种族条目。 */
    private EncountersConfig.SpeciesEntry rollSpecies(
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
}
