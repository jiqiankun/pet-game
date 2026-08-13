package com.petgame.config.loader;

import com.petgame.config.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 游戏配置启动校验器。
 * <p>
 * 应用启动时执行校验，发现严重错误时抛出异常使启动失败。
 * 校验规则随配置类型增加逐步补充。
 */
@Component
public class GameConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(GameConfigValidator.class);

    /**
     * 校验全部配置，发现严重错误时抛出 {@link IllegalStateException}。
     *
     * @param system   系统规则配置
     * @param elements 属性体系配置
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements) {
        validate(system, elements, null);
    }

    /**
     * 校验全部配置（含初始宠物），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets) {
        validate(system, elements, initialPets, null, null, null);
    }

    /**
     * 校验全部配置（阶段 3：含技能、状态、测试战斗），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets,
                         SkillsConfig skills, StatusesConfig statuses, TestBattleConfig testBattle) {
        List<String> errors = new ArrayList<>();

        validateSystemRules(system, errors);
        validateElements(elements, errors);
        if (statuses != null) {
            validateStatuses(statuses, errors);
        }
        if (skills != null) {
            validateSkills(skills, elements, statuses, errors);
        }
        if (initialPets != null) {
            validateInitialPets(initialPets, elements, errors);
            if (skills != null) {
                validateInitialPetSkillRefs(initialPets, skills, errors);
            }
        }
        if (testBattle != null) {
            validateTestBattle(testBattle, skills, elements, errors);
        }

        if (!errors.isEmpty()) {
            String msg = "游戏配置校验失败（" + errors.size() + " 个错误）:\n"
                    + String.join("\n", errors.stream().map(e -> "  - " + e).toList());
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("游戏配置校验通过");
    }

    private void validateSystemRules(SystemRuleConfig system, List<String> errors) {
        // 倍率合法性
        if (system.getAdvantageMultiplier() <= 0) {
            errors.add("advantageMultiplier 必须 > 0，当前值: " + system.getAdvantageMultiplier());
        }
        if (system.getNeutralMultiplier() <= 0) {
            errors.add("neutralMultiplier 必须 > 0，当前值: " + system.getNeutralMultiplier());
        }
        if (system.getDisadvantageMultiplier() <= 0) {
            errors.add("disadvantageMultiplier 必须 > 0，当前值: " + system.getDisadvantageMultiplier());
        }
        if (system.getSameElementBonus() <= 0) {
            errors.add("sameElementBonus 必须 > 0，当前值: " + system.getSameElementBonus());
        }

        // 暴击
        if (system.getCritRate() < 0 || system.getCritRate() > 1) {
            errors.add("critRate 必须在 [0, 1] 范围内，当前值: " + system.getCritRate());
        }
        if (system.getCritMultiplierMin() > system.getCritMultiplierMax()) {
            errors.add("critMultiplierMin(" + system.getCritMultiplierMin()
                    + ") 不能大于 critMultiplierMax(" + system.getCritMultiplierMax() + ")");
        }
        if (system.getCritMultiplierMin() < 1.0) {
            errors.add("critMultiplierMin 必须 >= 1.0，当前值: " + system.getCritMultiplierMin());
        }

        // 等级上限
        if (system.getLevelCap() <= 0) {
            errors.add("levelCap 必须 > 0，当前值: " + system.getLevelCap());
        }

        // 队伍数量
        if (system.getMaxCarryPets() <= 0) {
            errors.add("maxCarryPets 必须 > 0，当前值: " + system.getMaxCarryPets());
        }
        if (system.getStandardBattleSlots() <= 0) {
            errors.add("standardBattleSlots 必须 > 0，当前值: " + system.getStandardBattleSlots());
        }
        if (system.getStandardBattleSlots() > system.getMaxCarryPets()) {
            errors.add("standardBattleSlots(" + system.getStandardBattleSlots()
                    + ") 不能大于 maxCarryPets(" + system.getMaxCarryPets() + ")");
        }

        // 资质范围
        if (system.getAptitudeMin() < 0) {
            errors.add("aptitudeMin 必须 >= 0，当前值: " + system.getAptitudeMin());
        }
        if (system.getAptitudeMax() <= system.getAptitudeMin()) {
            errors.add("aptitudeMax(" + system.getAptitudeMax()
                    + ") 必须大于 aptitudeMin(" + system.getAptitudeMin() + ")");
        }

        // 浮动比例
        if (system.getBaseStatVariance() < 0 || system.getBaseStatVariance() > 1) {
            errors.add("baseStatVariance 必须在 [0, 1] 范围内，当前值: " + system.getBaseStatVariance());
        }

        // 放生加成上限
        if (system.getReleaseBonusMax() < 0 || system.getReleaseBonusMax() > 1) {
            errors.add("releaseBonusMax 必须在 [0, 1] 范围内，当前值: " + system.getReleaseBonusMax());
        }

        // Boss 幸运值
        if (system.getLuckyExchangeCost() <= 0) {
            errors.add("luckyExchangeCost 必须 > 0，当前值: " + system.getLuckyExchangeCost());
        }

        // 战斗结算（阶段 3）
        if (system.getDefenseMitigationConstant() <= 0) {
            errors.add("defenseMitigationConstant 必须 > 0，当前值: " + system.getDefenseMitigationConstant());
        }
        if (system.getDefendDamageReduction() <= 0 || system.getDefendDamageReduction() > 1) {
            errors.add("defendDamageReduction 必须在 (0, 1] 范围内，当前值: " + system.getDefendDamageReduction());
        }
        if (system.getMinDamage() < 1) {
            errors.add("minDamage 必须 >= 1，当前值: " + system.getMinDamage());
        }
        if (system.getFreePointStatValue() < 0 || system.getFreePointHpValue() < 0) {
            errors.add("freePointStatValue/freePointHpValue 必须 >= 0");
        }
        if (system.getLevelStatGrowth() < 0 || system.getLevelHpGrowth() < 0) {
            errors.add("levelStatGrowth/levelHpGrowth 必须 >= 0");
        }
    }

    private void validateInitialPets(InitialPetsConfig config, GameElementsConfig elements, List<String> errors) {
        if (config.getInitialPets() == null || config.getInitialPets().isEmpty()) {
            errors.add("initialPets 列表不能为空");
            return;
        }
        if (config.getInitialPets().size() != 3) {
            errors.add("initialPets 必须恰好 3 个选项，当前: " + config.getInitialPets().size());
        }
        Set<String> speciesIds = new HashSet<>();
        Set<String> validElementIds = new HashSet<>();
        for (GameElementConfig elem : elements.getElements()) {
            validElementIds.add(elem.getId());
        }
        for (InitialPetsConfig.InitialPetOption pet : config.getInitialPets()) {
            if (pet.getSpeciesId() == null || pet.getSpeciesId().isBlank()) {
                errors.add("initialPet 缺少 speciesId");
            }
            if (!speciesIds.add(pet.getSpeciesId())) {
                errors.add("initialPet speciesId 重复: " + pet.getSpeciesId());
            }
            if (pet.getElement() != null && !validElementIds.contains(pet.getElement())) {
                errors.add("initialPet 引用不存在的属性: " + pet.getElement());
            }
            // 初始宠物资质至少 A（各项 >= 80）
            int[] apts = {pet.getAptitudeHp(), pet.getAptitudeStrength(), pet.getAptitudeSpirit(),
                    pet.getAptitudeDefense(), pet.getAptitudeResistance(), pet.getAptitudeSpeed()};
            for (int apt : apts) {
                if (apt < 80) {
                    errors.add("initialPet " + pet.getSpeciesId() + " 资质必须 >= 80，当前值: " + apt);
                    break;
                }
            }
        }
    }

    private void validateElements(GameElementsConfig elements, List<String> errors) {
        List<GameElementConfig> elementList = elements.getElements();
        if (elementList == null || elementList.isEmpty()) {
            errors.add("elements 列表不能为空");
            return;
        }


        // ID 重复检查
        Set<String> elementIds = new HashSet<>();
        for (GameElementConfig elem : elementList) {
            if (elem.getId() == null || elem.getId().isBlank()) {
                errors.add("element 缺少 id 字段");
                continue;
            }
            if (!elementIds.add(elem.getId())) {
                errors.add("element ID 重复: " + elem.getId());
            }
        }

        // 克制关系引用检查
        List<ElementAdvantageConfig> advantages = elements.getAdvantages();
        if (advantages != null) {
            for (ElementAdvantageConfig adv : advantages) {
                if (!elementIds.contains(adv.getAttacker())) {
                    errors.add("advantage 引用不存在的 attacker 属性: " + adv.getAttacker());
                }
                if (!elementIds.contains(adv.getDefender())) {
                    errors.add("advantage 引用不存在的 defender 属性: " + adv.getDefender());
                }
            }
        }
    }

    // ---- 阶段 3：状态 / 技能 / 测试战斗校验 ----

    private static final Set<String> VALID_STAT_KEYS =
            Set.of("HP", "STRENGTH", "SPIRIT", "DEFENSE", "RESISTANCE", "SPEED");
    private static final Set<String> VALID_SKILL_TARGETS =
            Set.of("ENEMY_SINGLE", "ENEMY_ALL", "ALLY_SINGLE", "ALLY_ALL", "SELF");
    private static final Set<String> VALID_DAMAGE_TYPES = Set.of("PHYSICAL", "MAGICAL", "NONE");
    private static final Set<String> VALID_SKILL_EFFECT_TYPES = Set.of("DAMAGE", "HEAL", "SHIELD", "NONE");
    private static final Set<String> VALID_EFFECT_ITEM_TYPES = Set.of("APPLY_STATUS", "DAMAGE", "HEAL", "SHIELD");
    private static final Set<String> VALID_STATUS_CATEGORIES = Set.of("DOT", "CONTROL", "DEBUFF", "BUFF");
    private static final Set<String> VALID_PASSIVE_TRIGGERS = Set.of("ON_ENTER", "ON_EXIT", "ON_HIT_TAKEN",
            "ON_ATTACK", "ON_CRIT", "ON_KILL", "ON_DEATH", "ON_ROUND_START", "ON_ROUND_END");
    private static final Set<String> VALID_PASSIVE_EFFECTS = Set.of("SURVIVE_LETHAL", "APPLY_STATUS_ALLY_ALL",
            "APPLY_STATUS_SELF", "DAMAGE_ENEMY_RANDOM", "HEAL_SELF");

    /** 校验状态配置：ID 唯一、类别合法、数值字段范围、联动引用合法。 */
    private void validateStatuses(StatusesConfig statuses, List<String> errors) {
        Set<String> statusIds = new HashSet<>();
        for (StatusEffectConfig status : statuses.getStatuses()) {
            if (status.getId() == null || status.getId().isBlank()) {
                errors.add("status 缺少 id 字段");
                continue;
            }
            if (!statusIds.add(status.getId())) {
                errors.add("status ID 重复: " + status.getId());
            }
            if (!VALID_STATUS_CATEGORIES.contains(status.getCategory())) {
                errors.add("status " + status.getId() + " category 非法: " + status.getCategory());
            }
            if (status.getDefaultDuration() < 1) {
                errors.add("status " + status.getId() + " defaultDuration 必须 >= 1，当前值: "
                        + status.getDefaultDuration());
            }
            if (status.getSkipActionChance() < 0 || status.getSkipActionChance() > 1) {
                errors.add("status " + status.getId() + " skipActionChance 必须在 [0, 1] 范围内");
            }
            if (status.getGuardTransferPercent() < 0 || status.getGuardTransferPercent() > 1) {
                errors.add("status " + status.getId() + " guardTransferPercent 必须在 [0, 1] 范围内");
            }
        }
        for (StatusesConfig.StatusSynergyConfig synergy : statuses.getSynergies()) {
            if (!statusIds.contains(synergy.getRequiredStatus())) {
                errors.add("synergy 引用不存在的状态: " + synergy.getRequiredStatus());
            }
            if (synergy.getDamageMultiplier() <= 0) {
                errors.add("synergy " + synergy.getRequiredStatus() + "+" + synergy.getSkillElement()
                        + " damageMultiplier 必须 > 0");
            }
        }
    }

    /** 校验技能与被动配置：ID 唯一、枚举合法、数值范围、状态引用合法。 */
    private void validateSkills(SkillsConfig skills, GameElementsConfig elements,
                                StatusesConfig statuses, List<String> errors) {
        Set<String> validElementIds = new HashSet<>();
        for (GameElementConfig elem : elements.getElements()) {
            validElementIds.add(elem.getId());
        }
        Set<String> statusIds = new HashSet<>();
        if (statuses != null) {
            for (StatusEffectConfig status : statuses.getStatuses()) {
                statusIds.add(status.getId());
            }
        }

        Set<String> skillIds = new HashSet<>();
        for (SkillConfig skill : skills.getSkills()) {
            if (skill.getId() == null || skill.getId().isBlank()) {
                errors.add("skill 缺少 id 字段");
                continue;
            }
            if (!skillIds.add(skill.getId())) {
                errors.add("skill ID 重复: " + skill.getId());
            }
            if (skill.getElement() != null && !"NONE".equalsIgnoreCase(skill.getElement())
                    && !validElementIds.contains(skill.getElement())) {
                errors.add("skill " + skill.getId() + " 引用不存在的属性: " + skill.getElement());
            }
            if (!VALID_SKILL_TARGETS.contains(skill.getTarget())) {
                errors.add("skill " + skill.getId() + " target 非法: " + skill.getTarget());
            }
            if (!VALID_DAMAGE_TYPES.contains(skill.getDamageType())) {
                errors.add("skill " + skill.getId() + " damageType 非法: " + skill.getDamageType());
            }
            if (!VALID_SKILL_EFFECT_TYPES.contains(skill.getEffectType())) {
                errors.add("skill " + skill.getId() + " effectType 非法: " + skill.getEffectType());
            }
            if (skill.getAccuracy() < 0 || skill.getAccuracy() > 1) {
                errors.add("skill " + skill.getId() + " accuracy 必须在 [0, 1] 范围内");
            }
            if (skill.getCooldown() < 0) {
                errors.add("skill " + skill.getId() + " cooldown 必须 >= 0");
            }
            if (skill.getChargeTurns() < 0) {
                errors.add("skill " + skill.getId() + " chargeTurns 必须 >= 0");
            }
            validateScalingKeys(skill.getId(), skill.getScaling(), errors);
            for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
                if (!VALID_EFFECT_ITEM_TYPES.contains(effect.getType())) {
                    errors.add("skill " + skill.getId() + " 附加效果 type 非法: " + effect.getType());
                }
                if ("APPLY_STATUS".equalsIgnoreCase(effect.getType()) && !statusIds.contains(effect.getStatusId())) {
                    errors.add("skill " + skill.getId() + " 附加效果引用不存在的状态: " + effect.getStatusId());
                }
                if (effect.getChance() < 0 || effect.getChance() > 1) {
                    errors.add("skill " + skill.getId() + " 附加效果 chance 必须在 [0, 1] 范围内");
                }
                validateScalingKeys(skill.getId(), effect.getScaling(), errors);
            }
        }

        Set<String> passiveIds = new HashSet<>();
        for (PassiveSkillConfig passive : skills.getPassives()) {
            if (passive.getId() == null || passive.getId().isBlank()) {
                errors.add("passive 缺少 id 字段");
                continue;
            }
            if (!passiveIds.add(passive.getId())) {
                errors.add("passive ID 重复: " + passive.getId());
            }
            if (!VALID_PASSIVE_TRIGGERS.contains(passive.getTrigger())) {
                errors.add("passive " + passive.getId() + " trigger 非法: " + passive.getTrigger());
            }
            if (!VALID_PASSIVE_EFFECTS.contains(passive.getEffectType())) {
                errors.add("passive " + passive.getId() + " effectType 非法: " + passive.getEffectType());
            }
            if (passive.getEffectType() != null && passive.getEffectType().startsWith("APPLY_STATUS")
                    && !statusIds.contains(passive.getStatusId())) {
                errors.add("passive " + passive.getId() + " 引用不存在的状态: " + passive.getStatusId());
            }
            if (passive.getElement() != null && !"NONE".equalsIgnoreCase(passive.getElement())
                    && !validElementIds.contains(passive.getElement())) {
                errors.add("passive " + passive.getId() + " 引用不存在的属性: " + passive.getElement());
            }
            if (passive.getMaxTriggerPerBattle() < 0) {
                errors.add("passive " + passive.getId() + " maxTriggerPerBattle 必须 >= 0");
            }
        }
    }

    private void validateScalingKeys(String ownerId, Map<String, Double> scaling, List<String> errors) {
        if (scaling == null) {
            return;
        }
        for (String statKey : scaling.keySet()) {
            if (statKey == null || !VALID_STAT_KEYS.contains(statKey.toUpperCase())) {
                errors.add(ownerId + " scaling 引用非法属性维度: " + statKey);
            }
        }
    }

    /** 校验初始宠物的技能/被动引用存在且槽位不重复。 */
    private void validateInitialPetSkillRefs(InitialPetsConfig initialPets, SkillsConfig skills, List<String> errors) {
        Set<String> skillIds = new HashSet<>();
        for (SkillConfig skill : skills.getSkills()) {
            skillIds.add(skill.getId());
        }
        Set<String> passiveIds = new HashSet<>();
        for (PassiveSkillConfig passive : skills.getPassives()) {
            passiveIds.add(passive.getId());
        }
        for (InitialPetsConfig.InitialPetOption pet : initialPets.getInitialPets()) {
            if (pet.getSkills() == null || pet.getSkills().isEmpty()) {
                errors.add("initialPet " + pet.getSpeciesId() + " 未配置初始技能");
                continue;
            }
            Set<Integer> slots = new HashSet<>();
            for (InitialPetsConfig.InitialSkillSlot slot : pet.getSkills()) {
                if (slot.getSkillId() == null || !skillIds.contains(slot.getSkillId())) {
                    errors.add("initialPet " + pet.getSpeciesId() + " 引用不存在的技能: " + slot.getSkillId());
                }
                if (slot.getSlot() != null && !slots.add(slot.getSlot())) {
                    errors.add("initialPet " + pet.getSpeciesId() + " 技能槽位重复: " + slot.getSlot());
                }
            }
            for (String passiveId : pet.getPassives()) {
                if (!passiveIds.contains(passiveId)) {
                    errors.add("initialPet " + pet.getSpeciesId() + " 引用不存在的被动: " + passiveId);
                }
            }
        }
    }

    /** 校验测试战斗敌方阵容：unitId 唯一、属性合法、数值合法、技能/被动引用存在。 */
    private void validateTestBattle(TestBattleConfig testBattle, SkillsConfig skills,
                                    GameElementsConfig elements, List<String> errors) {
        if (testBattle.getEnemies() == null || testBattle.getEnemies().isEmpty()) {
            errors.add("testBattle enemies 列表不能为空");
            return;
        }
        Set<String> validElementIds = new HashSet<>();
        for (GameElementConfig elem : elements.getElements()) {
            validElementIds.add(elem.getId());
        }
        Set<String> skillIds = new HashSet<>();
        for (SkillConfig skill : skills.getSkills()) {
            skillIds.add(skill.getId());
        }
        Set<String> passiveIds = new HashSet<>();
        for (PassiveSkillConfig passive : skills.getPassives()) {
            passiveIds.add(passive.getId());
        }
        Set<String> unitIds = new HashSet<>();
        for (TestBattleConfig.TestEnemyUnit enemy : testBattle.getEnemies()) {
            if (enemy.getUnitId() == null || enemy.getUnitId().isBlank()) {
                errors.add("testBattle 敌方单位缺少 unitId");
                continue;
            }
            if (!unitIds.add(enemy.getUnitId())) {
                errors.add("testBattle 敌方 unitId 重复: " + enemy.getUnitId());
            }
            if (enemy.getElement() == null || !validElementIds.contains(enemy.getElement())) {
                errors.add("testBattle " + enemy.getUnitId() + " 引用不存在的属性: " + enemy.getElement());
            }
            if (enemy.getLevel() < 1) {
                errors.add("testBattle " + enemy.getUnitId() + " level 必须 >= 1");
            }
            if (enemy.getMaxHp() <= 0) {
                errors.add("testBattle " + enemy.getUnitId() + " maxHp 必须 > 0");
            }
            if (enemy.getSkillIds() == null || enemy.getSkillIds().isEmpty()) {
                errors.add("testBattle " + enemy.getUnitId() + " 未配置技能");
            } else {
                for (String skillId : enemy.getSkillIds()) {
                    if (!skillIds.contains(skillId)) {
                        errors.add("testBattle " + enemy.getUnitId() + " 引用不存在的技能: " + skillId);
                    }
                }
            }
            for (String passiveId : enemy.getPassiveIds()) {
                if (!passiveIds.contains(passiveId)) {
                    errors.add("testBattle " + enemy.getUnitId() + " 引用不存在的被动: " + passiveId);
                }
            }
        }
    }
}
