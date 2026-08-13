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
        validate(system, elements, initialPets, skills, statuses, testBattle, null);
    }

    /**
     * 校验全部配置（阶段 4：含道具配置），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets,
                         SkillsConfig skills, StatusesConfig statuses, TestBattleConfig testBattle,
                         ItemsConfig items) {
        validate(system, elements, initialPets, skills, statuses, testBattle, items, null, null, null);
    }

    /**
     * 校验全部配置（阶段 5：含宠物种族、野生遭遇、放生礼物），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets,
                         SkillsConfig skills, StatusesConfig statuses, TestBattleConfig testBattle,
                         ItemsConfig items, PetsConfig pets, EncountersConfig encounters,
                         ReleaseGiftsConfig releaseGifts) {
        validate(system, elements, initialPets, skills, statuses, testBattle, items,
                pets, encounters, releaseGifts, null);
    }

    /**
     * 校验全部配置（阶段 6：含地图与区域），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets,
                         SkillsConfig skills, StatusesConfig statuses, TestBattleConfig testBattle,
                         ItemsConfig items, PetsConfig pets, EncountersConfig encounters,
                         ReleaseGiftsConfig releaseGifts, MapsConfig maps) {
        validate(system, elements, initialPets, skills, statuses, testBattle, items,
                pets, encounters, releaseGifts, maps, null);
    }

    /**
     * 校验全部配置（阶段 7：含 Boss 配置），发现严重错误时抛出 {@link IllegalStateException}。
     */
    public void validate(SystemRuleConfig system, GameElementsConfig elements, InitialPetsConfig initialPets,
                         SkillsConfig skills, StatusesConfig statuses, TestBattleConfig testBattle,
                         ItemsConfig items, PetsConfig pets, EncountersConfig encounters,
                         ReleaseGiftsConfig releaseGifts, MapsConfig maps, BossesConfig bosses) {
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
            validateInitialPets(initialPets, pets, items, skills, errors);
        }
        if (pets != null) {
            validatePets(pets, skills, elements, system, errors);
        }
        if (encounters != null) {
            validateEncounters(encounters, pets, errors);
        }
        if (releaseGifts != null) {
            validateReleaseGifts(releaseGifts, items, errors);
        }
        if (maps != null) {
            validateMaps(maps, encounters, items, initialPets, errors);
        }
        if (bosses != null) {
            validateBosses(bosses, elements, skills, items, maps, errors);
        }
        if (testBattle != null) {
            validateTestBattle(testBattle, skills, elements, errors);
        }
        if (items != null) {
            validateItems(items, errors);
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

        // 阶段 4：养成系统配置
        if (system.getFreePointsPerLevel() < 0) {
            errors.add("freePointsPerLevel 必须 >= 0，当前值: " + system.getFreePointsPerLevel());
        }
        if (system.getExpBase() <= 0) {
            errors.add("expBase 必须 > 0，当前值: " + system.getExpBase());
        }
        if (system.getExpGrowthFactor() < 1.0) {
            errors.add("expGrowthFactor 必须 >= 1.0，当前值: " + system.getExpGrowthFactor());
        }
        if (system.getStatPointCost() <= 0 || system.getHpPointCost() <= 0 || system.getSpeedPointCost() <= 0) {
            errors.add("statPointCost/hpPointCost/speedPointCost 必须 > 0");
        }
        if (system.getSpeedPointCost() < system.getStatPointCost()) {
            errors.add("speedPointCost(" + system.getSpeedPointCost()
                    + ") 不应小于 statPointCost(" + system.getStatPointCost() + ")");
        }

        // 阶段 5：捕捉与放生礼物
        if (system.getCaptureHpFactor() < 0 || system.getCaptureHpFactor() > 1) {
            errors.add("captureHpFactor 必须在 [0, 1] 范围内，当前值: " + system.getCaptureHpFactor());
        }
        if (system.getStatusCaptureBonus() < 0) {
            errors.add("statusCaptureBonus 必须 >= 0，当前值: " + system.getStatusCaptureBonus());
        }
        if (system.getCaptureStatusMaxCount() < 0) {
            errors.add("captureStatusMaxCount 必须 >= 0，当前值: " + system.getCaptureStatusMaxCount());
        }
        if (system.getEliteCaptureMultiplier() <= 0) {
            errors.add("eliteCaptureMultiplier 必须 > 0，当前值: " + system.getEliteCaptureMultiplier());
        }
        if (system.getFleeSuccessRate() < 0 || system.getFleeSuccessRate() > 1) {
            errors.add("fleeSuccessRate 必须在 [0, 1] 范围内，当前值: " + system.getFleeSuccessRate());
        }
        if (system.getRareSkillChance() < 0 || system.getRareSkillChance() > 1) {
            errors.add("rareSkillChance 必须在 [0, 1] 范围内，当前值: " + system.getRareSkillChance());
        }
        if (system.getSpecialAppearanceChance() < 0 || system.getSpecialAppearanceChance() > 1) {
            errors.add("specialAppearanceChance 必须在 [0, 1] 范围内，当前值: "
                    + system.getSpecialAppearanceChance());
        }
        validateRarityIntMap("releaseGiftBaseValue", system.getReleaseGiftBaseValue(), errors);
        if (system.getReleaseLevelFactorPerLevel() < 0) {
            errors.add("releaseLevelFactorPerLevel 必须 >= 0，当前值: " + system.getReleaseLevelFactorPerLevel());
        }
        if (system.getReleaseLevelFactorCap() < 1.0) {
            errors.add("releaseLevelFactorCap 必须 >= 1.0，当前值: " + system.getReleaseLevelFactorCap());
        }
        if (system.getReleaseCultivationFactorMax() < 1.0) {
            errors.add("releaseCultivationFactorMax 必须 >= 1.0，当前值: " + system.getReleaseCultivationFactorMax());
        }
        if (system.getReleaseCultivationPointsCap() <= 0) {
            errors.add("releaseCultivationPointsCap 必须 > 0，当前值: " + system.getReleaseCultivationPointsCap());
        }
        if (system.getReleaseWarningAptitudeThreshold() < 0
                || system.getReleaseWarningAptitudeThreshold() > system.getAptitudeMax()) {
            errors.add("releaseWarningAptitudeThreshold 必须在 [0, aptitudeMax] 范围内，当前值: "
                    + system.getReleaseWarningAptitudeThreshold());
        }
        validateRarityDoubleMap("wildRewardRarityMultiplier", system.getWildRewardRarityMultiplier(), errors);

        // 阶段 8：图鉴研究值配置
        validatePokedexRules(system.getPokedex(), errors);
    }

    /** 校验图鉴研究值配置（阶段 8）：等级门槛严格递增、分值非负、资质预估合法。 */
    private void validatePokedexRules(SystemRuleConfig.PokedexRuleConfig pokedex, List<String> errors) {
        if (pokedex == null) {
            return;
        }
        // 等级门槛严格递增
        if (pokedex.getLevelThresholds() != null && !pokedex.getLevelThresholds().isEmpty()) {
            int prevValue = 0;
            for (Map.Entry<String, Integer> entry : pokedex.getLevelThresholds().entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 0) {
                    errors.add("pokedex.levelThresholds[" + entry.getKey() + "] 必须 >= 0");
                } else if (entry.getValue() <= prevValue) {
                    errors.add("pokedex.levelThresholds 必须严格递增，等级 " + entry.getKey()
                            + " 门槛 " + entry.getValue() + " 不高于前一等级 " + prevValue);
                } else {
                    prevValue = entry.getValue();
                }
            }
        }
        // 分值非负
        if (pokedex.getFirstDiscoveryPoints() < 0) {
            errors.add("pokedex.firstDiscoveryPoints 必须 >= 0");
        }
        if (pokedex.getFirstCapturePoints() < 0) {
            errors.add("pokedex.firstCapturePoints 必须 >= 0");
        }
        if (pokedex.getSubsequentCapturePoints() < 0) {
            errors.add("pokedex.subsequentCapturePoints 必须 >= 0");
        }
        if (pokedex.getBattleParticipationPoints() < 0) {
            errors.add("pokedex.battleParticipationPoints 必须 >= 0");
        }
        if (pokedex.getBattleWinPoints() < 0) {
            errors.add("pokedex.battleWinPoints 必须 >= 0");
        }
        if (pokedex.getSkillUnlockPoints() < 0) {
            errors.add("pokedex.skillUnlockPoints 必须 >= 0");
        }
        if (pokedex.getHighAptitude80Points() < 0) {
            errors.add("pokedex.highAptitude80Points 必须 >= 0");
        }
        if (pokedex.getHighAptitude90Points() < 0) {
            errors.add("pokedex.highAptitude90Points 必须 >= 0");
        }
        if (pokedex.getRareSkillDiscoveryPoints() < 0) {
            errors.add("pokedex.rareSkillDiscoveryPoints 必须 >= 0");
        }
        if (pokedex.getSpecialAppearancePoints() < 0) {
            errors.add("pokedex.specialAppearancePoints 必须 >= 0");
        }
        if (pokedex.getEliteCapturePoints() < 0) {
            errors.add("pokedex.eliteCapturePoints 必须 >= 0");
        }
        // 资质预估等级标签合法
        if (pokedex.getAptitudeGrades() != null) {
            Set<String> validGrades = Set.of("S", "A", "B", "C", "D");
            for (Map.Entry<String, Integer> entry : pokedex.getAptitudeGrades().entrySet()) {
                if (!validGrades.contains(entry.getKey())) {
                    errors.add("pokedex.aptitudeGrades 非法标签: " + entry.getKey());
                }
                if (entry.getValue() == null || entry.getValue() < 0) {
                    errors.add("pokedex.aptitudeGrades[" + entry.getKey() + "] 必须 >= 0");
                }
            }
        }
    }

    /** 稀有度 → 整数映射的键与数值校验（如放生礼物基础点数）。 */
    private void validateRarityIntMap(String fieldName, Map<String, Integer> map, List<String> errors) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!VALID_RARITIES.contains(entry.getKey())) {
                errors.add(fieldName + " 稀有度键非法: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                errors.add(fieldName + "[" + entry.getKey() + "] 必须 >= 0");
            }
        }
    }

    /** 稀有度 → 浮点映射的键与数值校验（如野生奖励系数）。 */
    private void validateRarityDoubleMap(String fieldName, Map<String, Double> map, List<String> errors) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (!VALID_RARITIES.contains(entry.getKey())) {
                errors.add(fieldName + " 稀有度键非法: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() <= 0) {
                errors.add(fieldName + "[" + entry.getKey() + "] 必须 > 0");
            }
        }
    }

    private void validateInitialPets(InitialPetsConfig config, PetsConfig pets, ItemsConfig items,
                                     SkillsConfig skills, List<String> errors) {
        if (config.getInitialPets() == null || config.getInitialPets().isEmpty()) {
            errors.add("initialPets 列表不能为空");
            return;
        }
        if (config.getInitialPets().size() != 3) {
            errors.add("initialPets 必须恰好 3 个选项，当前: " + config.getInitialPets().size());
        }
        // REV-014：新游戏赠送技能引用校验
        if (config.getGrantSkills() != null && skills != null) {
            Set<String> validSkillIds = new HashSet<>();
            for (SkillConfig skill : skills.getSkills()) {
                validSkillIds.add(skill.getId());
            }
            for (String grantSkillId : config.getGrantSkills()) {
                if (grantSkillId == null || !validSkillIds.contains(grantSkillId)) {
                    errors.add("grantSkills 引用不存在的技能: " + grantSkillId);
                }
            }
        }
        Set<String> speciesIds = new HashSet<>();
        Set<String> validSpeciesIds = new HashSet<>();
        if (pets != null) {
            for (PetSpeciesConfig species : pets.getSpecies()) {
                validSpeciesIds.add(species.getId());
            }
        }
        for (InitialPetsConfig.InitialPetOption pet : config.getInitialPets()) {
            if (pet.getSpeciesId() == null || pet.getSpeciesId().isBlank()) {
                errors.add("initialPet 缺少 speciesId");
            } else if (!validSpeciesIds.isEmpty() && !validSpeciesIds.contains(pet.getSpeciesId())) {
                errors.add("initialPet 引用不存在的种族: " + pet.getSpeciesId());
            }
            if (pet.getSpeciesId() != null && !speciesIds.add(pet.getSpeciesId())) {
                errors.add("initialPet speciesId 重复: " + pet.getSpeciesId());
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
        // 初始道具引用校验（阶段 5）
        Set<String> validItemIds = new HashSet<>();
        if (items != null && items.getItems() != null) {
            for (ItemConfig item : items.getItems()) {
                validItemIds.add(item.getId());
            }
        }
        if (config.getInitialItems() != null) {
            for (InitialPetsConfig.InitialItemEntry entry : config.getInitialItems()) {
                if (entry.getItemId() == null
                        || (!validItemIds.isEmpty() && !validItemIds.contains(entry.getItemId()))) {
                    errors.add("initialItem 引用不存在的道具: " + entry.getItemId());
                }
                if (entry.getQuantity() <= 0) {
                    errors.add("initialItem " + entry.getItemId() + " quantity 必须 > 0");
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
    /** Effect 组合框架效果类型（REV-001/REV-006，技术方案 §76）。 */
    private static final Set<String> VALID_EFFECT_ITEM_TYPES = Set.of(
            "APPLY_STATUS", "DAMAGE", "HEAL", "SHIELD",
            "LIFE_STEAL", "LEAVE_AT_ONE_HP", "REMOVE_STATUS", "DISPEL", "STEAL_BUFF",
            "HP_PERCENT_EXCHANGE", "SWITCH_PET", "CHANGE_ACTION_ORDER", "MODIFY_COOLDOWN",
            "DELAYED", "STACK", "LIFE_COST", "PROTECT_FROM_DEFEAT");
    /** 状态五类模型（REV-002，需求 §148.2/技术方案 §77）。 */
    private static final Set<String> VALID_STATUS_CATEGORIES =
            Set.of("CONTINUOUS", "BUFF", "DEBUFF", "SPECIAL_CONTROL", "MARK");
    /** 技能来源（REV-001，需求 §23）。 */
    private static final Set<String> VALID_SKILL_SOURCES =
            Set.of("INNATE", "BOOK", "EVOLUTION", "SPECIAL");
    /** 技能类型（REV-001，需求 §23）。 */
    private static final Set<String> VALID_SKILL_TYPES = Set.of("ACTIVE", "PASSIVE");
    /** AI 语义标签（REV-001，需求 §67，阶段 10 消费）。 */
    private static final Set<String> VALID_AI_TAGS = Set.of(
            "DAMAGE", "HEAL", "CONTROL", "CAPTURE_ASSIST", "SURVIVAL", "FINISHER",
            "SHIELD_BREAK", "DISPEL", "SWITCH", "ACTION_ORDER", "LIFE_STEAL");
    /** 被动触发时机（REV-009，技术方案 §78 + ON_KILL 击败敌方时机）。 */
    private static final Set<String> VALID_PASSIVE_TRIGGERS = Set.of(
            "BATTLE_START", "TURN_START", "BEFORE_ACTION", "BEFORE_DAMAGE", "AFTER_DAMAGE",
            "AFTER_TAKE_DAMAGE", "BEFORE_TAKE_DAMAGE", "AFTER_HEAL", "AFTER_SKILL", "ON_CRITICAL", "ON_STATUS_APPLIED",
            "ON_ENTER", "ON_EXIT", "ON_DEFEAT", "ON_KILL", "ON_ALLY_DEFEAT", "TURN_END", "BATTLE_END");
    private static final Set<String> VALID_PASSIVE_EFFECTS = Set.of("SURVIVE_LETHAL", "APPLY_STATUS_ALLY_ALL",
            "APPLY_STATUS_SELF", "DAMAGE_ENEMY_RANDOM", "HEAL_SELF", "REDUCE_PHYSICAL_DAMAGE");
    // 阶段 4：道具配置枚举
    private static final Set<String> VALID_ITEM_CATEGORIES =
            Set.of("CAPTURE", "RECOVERY", "MATERIAL", "SKILL_BOOK", "KEY_ITEM");
    private static final Set<String> VALID_ITEM_TYPES =
            Set.of("HEAL_HP", "REVIVE", "CAPTURE_BALL", "MATERIAL", "SKILL_BOOK", "KEY_ITEM");
    private static final Set<String> VALID_RARITIES = Set.of("COMMON", "RARE", "EPIC", "LEGENDARY");
    private static final Set<String> VALID_GIFT_TYPES = Set.of("GOLD", "EXP", "ITEM");

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
            // REV-002：叠层与新机制字段校验
            if (status.isStack() && status.getMaxStack() < 2) {
                errors.add("status " + status.getId() + " 可叠层状态 maxStack 必须 >= 2");
            }
            if (status.getMaxStack() < 1) {
                errors.add("status " + status.getId() + " maxStack 必须 >= 1");
            }
            if (status.getStackTrigger() != null
                    && !Set.of("NONE", "DAMAGE").contains(status.getStackTrigger())) {
                errors.add("status " + status.getId() + " stackTrigger 非法: " + status.getStackTrigger());
            }
            if (status.getCounterRate() < 0 || status.getCounterRate() > 1) {
                errors.add("status " + status.getId() + " counterRate 必须在 [0, 1] 范围内");
            }
            if (status.getHealPercent() < 0 || status.getHealPercent() > 1) {
                errors.add("status " + status.getId() + " healPercent 必须在 [0, 1] 范围内");
            }
            if (status.getDotPercent() < 0 || status.getDotPercent() > 1) {
                errors.add("status " + status.getId() + " dotPercent 必须在 [0, 1] 范围内");
            }
            if (status.isCaptureStun() && status.isCaptureBonus()) {
                errors.add("status " + status.getId() + " 震慑状态不得计入捕捉加成（需求 §142）");
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
            // REV-001：来源/类型两维度、AI 标签、次数限制、maxOf
            if (!VALID_SKILL_SOURCES.contains(skill.getSource())) {
                errors.add("skill " + skill.getId() + " source 非法: " + skill.getSource());
            }
            if (!VALID_SKILL_TYPES.contains(skill.getSkillType())) {
                errors.add("skill " + skill.getId() + " skillType 非法: " + skill.getSkillType());
            }
            if (skill.getMaxUsesPerBattle() < 0) {
                errors.add("skill " + skill.getId() + " maxUsesPerBattle 必须 >= 0");
            }
            if (skill.getTags() != null) {
                for (String tag : skill.getTags()) {
                    if (tag == null || !VALID_AI_TAGS.contains(tag.toUpperCase())) {
                        errors.add("skill " + skill.getId() + " AI 标签非法: " + tag);
                    }
                }
            }
            if (skill.getMaxOf() != null) {
                for (String statKey : skill.getMaxOf()) {
                    if (statKey == null || !VALID_STAT_KEYS.contains(statKey.toUpperCase())) {
                        errors.add("skill " + skill.getId() + " maxOf 引用非法属性维度: " + statKey);
                    }
                }
            }
            if (skill.getMaxOfCoefficient() < 0) {
                errors.add("skill " + skill.getId() + " maxOfCoefficient 必须 >= 0");
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
                String effectType = effect.getType() != null ? effect.getType().toUpperCase() : "";
                if (("APPLY_STATUS".equals(effectType) || "STACK".equals(effectType))
                        && (effect.getStatusId() == null || !statusIds.contains(effect.getStatusId()))) {
                    errors.add("skill " + skill.getId() + " 附加效果引用不存在的状态: " + effect.getStatusId());
                }
                // REMOVE_STATUS：指定 statusId 时校验存在性；dotOnly/categories 模式可不指定 statusId
                if ("REMOVE_STATUS".equals(effectType) && effect.getStatusId() != null
                        && !statusIds.contains(effect.getStatusId())) {
                    errors.add("skill " + skill.getId() + " 附加效果引用不存在的状态: " + effect.getStatusId());
                }
                if (effect.getChance() < 0 || effect.getChance() > 1) {
                    errors.add("skill " + skill.getId() + " 附加效果 chance 必须在 [0, 1] 范围内");
                }
                if (("LIFE_STEAL".equals(effectType) || "HP_PERCENT_EXCHANGE".equals(effectType)
                        || "LIFE_COST".equals(effectType))
                        && (effect.getPercent() <= 0 || effect.getPercent() > 1)) {
                    errors.add("skill " + skill.getId() + " 附加效果 percent 必须在 (0, 1] 范围内");
                }
                if (effect.getDelayRounds() < 0) {
                    errors.add("skill " + skill.getId() + " 附加效果 delayRounds 必须 >= 0");
                }
                if (effect.getCategories() != null) {
                    for (String category : effect.getCategories()) {
                        if (category == null || !VALID_STATUS_CATEGORIES.contains(category.toUpperCase())) {
                            errors.add("skill " + skill.getId() + " 附加效果 categories 非法: " + category);
                        }
                    }
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

    /** 校验初始宠物的技能/被动引用存在且槽位不重复（阶段 4 起废弃：种族数据统一来源为 pets 配置）。 */
    private void validatePets(PetsConfig pets, SkillsConfig skills, GameElementsConfig elements,
                              SystemRuleConfig system, List<String> errors) {
        if (pets.getSpecies() == null || pets.getSpecies().isEmpty()) {
            errors.add("pets species 列表不能为空");
            return;
        }
        Set<String> validElementIds = new HashSet<>();
        for (GameElementConfig elem : elements.getElements()) {
            validElementIds.add(elem.getId());
        }
        Set<String> skillIds = new HashSet<>();
        if (skills != null) {
            for (SkillConfig skill : skills.getSkills()) {
                skillIds.add(skill.getId());
            }
        }
        Set<String> passiveIds = new HashSet<>();
        if (skills != null) {
            for (PassiveSkillConfig passive : skills.getPassives()) {
                passiveIds.add(passive.getId());
            }
        }
        Set<String> speciesIds = new HashSet<>();
        Map<String, Integer> rarityCounts = new HashMap<>();
        for (PetSpeciesConfig species : pets.getSpecies()) {
            if (species.getId() == null || species.getId().isBlank()) {
                errors.add("pet species 缺少 id 字段");
                continue;
            }
            if (!speciesIds.add(species.getId())) {
                errors.add("pet species ID 重复: " + species.getId());
            }
            if (!validElementIds.contains(species.getElement())) {
                errors.add("pet species " + species.getId() + " 引用不存在的属性: " + species.getElement());
            }
            if (species.getRarity() == null || !VALID_RARITIES.contains(species.getRarity())) {
                errors.add("pet species " + species.getId() + " rarity 非法: " + species.getRarity());
            } else {
                rarityCounts.merge(species.getRarity(), 1, Integer::sum);
            }
            if (species.getCaptureRate() <= 0 || species.getCaptureRate() > 1) {
                errors.add("pet species " + species.getId()
                        + " captureRate 必须在 (0, 1] 范围内，当前值: " + species.getCaptureRate());
            }
            if (species.getBaseHp() <= 0) {
                errors.add("pet species " + species.getId() + " baseHp 必须 > 0");
            }
            if (species.getBaseStrength() < 0 || species.getBaseSpirit() < 0 || species.getBaseDefense() < 0
                    || species.getBaseResistance() < 0 || species.getBaseSpeed() < 0) {
                errors.add("pet species " + species.getId() + " 六维基础值必须 >= 0");
            }
            if (species.getSkills() == null || species.getSkills().isEmpty()) {
                errors.add("pet species " + species.getId() + " 未配置种族技能");
            } else {
                Set<Integer> slots = new HashSet<>();
                Set<String> speciesSkillIds = new HashSet<>();
                for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
                    if (slot.getSkillId() == null || !skillIds.contains(slot.getSkillId())) {
                        errors.add("pet species " + species.getId()
                                + " 引用不存在的技能: " + slot.getSkillId());
                    }
                    // REV-004：同种族内技能引用不得重复（技能共享是跨种族概念）
                    if (slot.getSkillId() != null && !speciesSkillIds.add(slot.getSkillId())) {
                        errors.add("pet species " + species.getId()
                                + " 技能引用重复: " + slot.getSkillId());
                    }
                    if (slot.getSlot() != null && !slots.add(slot.getSlot())) {
                        errors.add("pet species " + species.getId() + " 技能槽位重复: " + slot.getSlot());
                    }
                    if (slot.getUnlockLevel() < 1) {
                        errors.add("pet species " + species.getId() + " 技能 " + slot.getSkillId()
                                + " unlockLevel 必须 >= 1");
                    }
                    if (slot.getUnlockLevel() > system.getLevelCap()) {
                        errors.add("pet species " + species.getId() + " 技能 " + slot.getSkillId()
                                + " unlockLevel 超过 levelCap");
                    }
                }
            }
            for (String skillId : species.getRareSkills()) {
                if (!skillIds.contains(skillId)) {
                    errors.add("pet species " + species.getId() + " rareSkills 引用不存在的技能: " + skillId);
                }
            }
            // REV-003：被动含解锁等级，校验引用与等级范围
            Set<String> speciesPassiveIds = new HashSet<>();
            for (PetSpeciesConfig.SpeciesPassiveSlot passiveSlot : species.getPassives()) {
                if (passiveSlot.getPassiveId() == null || !passiveIds.contains(passiveSlot.getPassiveId())) {
                    errors.add("pet species " + species.getId()
                            + " 引用不存在的被动: " + passiveSlot.getPassiveId());
                }
                if (passiveSlot.getPassiveId() != null
                        && !speciesPassiveIds.add(passiveSlot.getPassiveId())) {
                    errors.add("pet species " + species.getId()
                            + " 被动引用重复: " + passiveSlot.getPassiveId());
                }
                if (passiveSlot.getUnlockLevel() < 1 || passiveSlot.getUnlockLevel() > system.getLevelCap()) {
                    errors.add("pet species " + species.getId() + " 被动 " + passiveSlot.getPassiveId()
                            + " unlockLevel 必须在 [1, levelCap] 范围内");
                }
            }
        }
        // 规模与稀有度分布（需求 §59/§60：27 种 = 普通 12 / 稀有 9 / 珍稀 5 / 传说 1）
        if (speciesIds.size() != 27) {
            errors.add("pets 种族数量必须为 27（需求 §59），当前: " + speciesIds.size());
        }
        String[] rarities = {"COMMON", "RARE", "EPIC", "LEGENDARY"};
        int[] expected = {12, 9, 5, 1};
        for (int i = 0; i < rarities.length; i++) {
            int actual = rarityCounts.getOrDefault(rarities[i], 0);
            if (actual != expected[i]) {
                errors.add("pets 稀有度 " + rarities[i] + " 数量应为 " + expected[i]
                        + "（需求 §60），当前: " + actual);
            }
        }
    }

    /** 校验野生遭遇配置（阶段 5）：组 ID 唯一、阵容范围合法、种族引用与权重合法。 */
    private void validateEncounters(EncountersConfig encounters, PetsConfig pets, List<String> errors) {
        if (encounters.getEncounterGroups() == null || encounters.getEncounterGroups().isEmpty()) {
            errors.add("encounterGroups 列表不能为空");
            return;
        }
        Set<String> validSpeciesIds = new HashSet<>();
        if (pets != null) {
            for (PetSpeciesConfig species : pets.getSpecies()) {
                validSpeciesIds.add(species.getId());
            }
        }
        Set<String> groupIds = new HashSet<>();
        for (EncountersConfig.EncounterGroup group : encounters.getEncounterGroups()) {
            if (group.getId() == null || group.getId().isBlank()) {
                errors.add("encounterGroup 缺少 id 字段");
                continue;
            }
            if (!groupIds.add(group.getId())) {
                errors.add("encounterGroup ID 重复: " + group.getId());
            }
            if (group.getTeamSizeMin() < 1 || group.getTeamSizeMax() < group.getTeamSizeMin()) {
                errors.add("encounterGroup " + group.getId() + " teamSize 范围非法");
            }
            if (group.getExpPerLevel() < 0 || group.getGoldPerLevel() < 0) {
                errors.add("encounterGroup " + group.getId() + " expPerLevel/goldPerLevel 必须 >= 0");
            }
            if (group.getSpecies() == null || group.getSpecies().isEmpty()) {
                errors.add("encounterGroup " + group.getId() + " species 列表不能为空");
            } else {
                for (EncountersConfig.SpeciesEntry entry : group.getSpecies()) {
                    if (entry.getSpeciesId() == null || !validSpeciesIds.contains(entry.getSpeciesId())) {
                        errors.add("encounterGroup " + group.getId()
                                + " 引用不存在的种族: " + entry.getSpeciesId());
                    }
                    if (entry.getWeight() <= 0) {
                        errors.add("encounterGroup " + group.getId() + " 种族 " + entry.getSpeciesId()
                                + " weight 必须 > 0");
                    }
                    if (entry.getLevelMin() < 1 || entry.getLevelMax() < entry.getLevelMin()) {
                        errors.add("encounterGroup " + group.getId() + " 种族 " + entry.getSpeciesId()
                                + " 等级范围非法");
                    }
                }
            }
        }
    }

    /** 校验放生礼物配置（阶段 5）：礼物池非空、类型与数值合法、道具引用存在。 */
    private void validateReleaseGifts(ReleaseGiftsConfig releaseGifts, ItemsConfig items, List<String> errors) {
        if (releaseGifts.getGifts() == null || releaseGifts.getGifts().isEmpty()) {
            errors.add("releaseGifts gifts 列表不能为空");
            return;
        }
        Set<String> validItemIds = new HashSet<>();
        if (items != null && items.getItems() != null) {
            for (ItemConfig item : items.getItems()) {
                validItemIds.add(item.getId());
            }
        }
        for (ReleaseGiftsConfig.GiftEntry gift : releaseGifts.getGifts()) {
            if (gift.getType() == null || !VALID_GIFT_TYPES.contains(gift.getType())) {
                errors.add("releaseGift type 非法: " + gift.getType());
            }
            if (gift.getUnitValue() <= 0) {
                errors.add("releaseGift unitValue 必须 > 0");
            }
            if (gift.getWeight() <= 0) {
                errors.add("releaseGift weight 必须 > 0");
            }
            if ("ITEM".equals(gift.getType())
                    && (gift.getItemId() == null || !validItemIds.contains(gift.getItemId()))) {
                errors.add("releaseGift 引用不存在的道具: " + gift.getItemId());
            }
            if ("ITEM".equals(gift.getType()) && gift.getQuantity() <= 0) {
                errors.add("releaseGift 道具 quantity 必须 > 0");
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

    /**
     * 校验道具配置（阶段 4）：ID 唯一、分类与类型合法、使用场景与效果数值合理。
     */
    private void validateItems(ItemsConfig items, List<String> errors) {
        if (items.getItems() == null || items.getItems().isEmpty()) {
            // 阶段 4 允许空道具配置（后续阶段补入）
            return;
        }
        Set<String> itemIds = new HashSet<>();
        for (ItemConfig item : items.getItems()) {
            if (item.getId() == null || item.getId().isBlank()) {
                errors.add("item 缺少 id 字段");
                continue;
            }
            if (!itemIds.add(item.getId())) {
                errors.add("item ID 重复: " + item.getId());
            }
            if (item.getCategory() == null || !VALID_ITEM_CATEGORIES.contains(item.getCategory())) {
                errors.add("item " + item.getId() + " category 非法: " + item.getCategory());
            }
            if (item.getItemType() == null || !VALID_ITEM_TYPES.contains(item.getItemType())) {
                errors.add("item " + item.getId() + " itemType 非法: " + item.getItemType());
            }
            // 恢复类道具数值应 > 0
            if (("HEAL_HP".equals(item.getItemType()) || "REVIVE".equals(item.getItemType()))
                    && item.getValue() <= 0) {
                errors.add("item " + item.getId() + " 恢复类道具 value 必须 > 0");
            }
        }
    }

    /** 合法的解锁方式（阶段 6：AUTO/BOSS/QUEST，后两者随阶段 7/9 启用）。 */
    private static final Set<String> VALID_UNLOCK_TYPES = Set.of("AUTO", "BOSS", "QUEST");

    /**
     * 校验地图与区域配置（阶段 6）：
     * ID 唯一、出口目标存在且非预留、刷新组引用存在、奖励道具引用存在且数量合法、
     * 初始地图必须存在且已实装。
     */
    private void validateMaps(MapsConfig maps, EncountersConfig encounters, ItemsConfig items,
                              InitialPetsConfig initialPets, List<String> errors) {
        if (maps.getRegions() == null || maps.getRegions().isEmpty()) {
            errors.add("maps regions 列表不能为空");
            return;
        }

        Set<String> regionIds = new HashSet<>();
        Set<String> campIds = new HashSet<>();
        Set<String> gatherIds = new HashSet<>();
        Set<String> chestIds = new HashSet<>();
        Set<String> encounterGroupIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        if (encounters != null && encounters.getEncounterGroups() != null) {
            for (EncountersConfig.EncounterGroup g : encounters.getEncounterGroups()) {
                encounterGroupIds.add(g.getId());
            }
        }
        if (items != null && items.getItems() != null) {
            for (ItemConfig item : items.getItems()) {
                itemIds.add(item.getId());
            }
        }

        Map<String, MapsConfig.RegionConfig> regionIndex = new HashMap<>();
        for (MapsConfig.RegionConfig region : maps.getRegions()) {
            if (region.getId() == null || region.getId().isBlank()) {
                errors.add("region 缺少 id 字段");
                continue;
            }
            if (!regionIds.add(region.getId())) {
                errors.add("region ID 重复: " + region.getId());
            }
            regionIndex.put(region.getId(), region);

            if (region.isPlanned()) {
                // 结构预留区域：仅校验解锁方式，不要求内容完整（阶段 9 开放）
                if (region.getUnlockType() != null && !VALID_UNLOCK_TYPES.contains(region.getUnlockType())) {
                    errors.add("region " + region.getId() + " unlockType 非法: " + region.getUnlockType());
                }
                continue;
            }

            if (region.getUnlockType() == null || !VALID_UNLOCK_TYPES.contains(region.getUnlockType())) {
                errors.add("region " + region.getId() + " unlockType 非法: " + region.getUnlockType());
            }
            if (region.getMapFile() == null || region.getMapFile().isBlank()) {
                errors.add("region " + region.getId() + " 缺少 mapFile（Tiled 地图资源名）");
            }

            // 刷新组引用
            for (String groupId : region.getEncounterGroups()) {
                if (!encounterGroupIds.contains(groupId)) {
                    errors.add("region " + region.getId() + " 引用不存在的刷新组: " + groupId);
                }
            }

            // 营地 ID 全局唯一
            for (MapsConfig.CampConfig camp : region.getCamps()) {
                if (camp.getCampId() == null || camp.getCampId().isBlank()) {
                    errors.add("region " + region.getId() + " 存在缺少 campId 的营地");
                    continue;
                }
                if (!campIds.add(camp.getCampId())) {
                    errors.add("营地 ID 重复: " + camp.getCampId());
                }
            }

            // 采集点 ID 全局唯一 + 奖励合法
            for (MapsConfig.GatherPointConfig gather : region.getGathers()) {
                if (gather.getGatherId() == null || gather.getGatherId().isBlank()) {
                    errors.add("region " + region.getId() + " 存在缺少 gatherId 的采集点");
                    continue;
                }
                if (!gatherIds.add(gather.getGatherId())) {
                    errors.add("采集点 ID 重复: " + gather.getGatherId());
                }
                validateMapRewards(region.getId(), gather.getGatherId(), gather.getRewards(),
                        gather.getGoldMin(), gather.getGoldMax(), itemIds, errors);
            }

            // 宝箱 ID 全局唯一 + 奖励合法（宝箱至少应有奖励）
            for (MapsConfig.ChestConfig chest : region.getChests()) {
                if (chest.getChestId() == null || chest.getChestId().isBlank()) {
                    errors.add("region " + region.getId() + " 存在缺少 chestId 的宝箱");
                    continue;
                }
                if (!chestIds.add(chest.getChestId())) {
                    errors.add("宝箱 ID 重复: " + chest.getChestId());
                }
                validateMapRewards(region.getId(), chest.getChestId(), chest.getRewards(),
                        chest.getGoldMin(), chest.getGoldMax(), itemIds, errors);
                if (chest.getRewards().isEmpty() && chest.getGoldMax() <= 0) {
                    errors.add("宝箱 " + chest.getChestId() + " 没有任何奖励");
                }
            }
        }

        // 出口目标必须存在且非预留区域
        for (MapsConfig.RegionConfig region : maps.getRegions()) {
            if (region.isPlanned()) {
                continue;
            }
            for (MapsConfig.ExitConfig exit : region.getExits()) {
                if (exit.getExitId() == null || exit.getExitId().isBlank()) {
                    errors.add("region " + region.getId() + " 存在缺少 exitId 的出口");
                    continue;
                }
                MapsConfig.RegionConfig target = regionIndex.get(exit.getTargetMapId());
                if (target == null) {
                    errors.add("出口 " + exit.getExitId() + " 目标区域不存在: " + exit.getTargetMapId());
                } else if (target.isPlanned()) {
                    errors.add("出口 " + exit.getExitId() + " 指向结构预留区域（本阶段不可达）: "
                            + exit.getTargetMapId());
                }
            }
        }

        // 初始地图必须存在且已实装
        if (initialPets != null && initialPets.getInitialMapId() != null) {
            MapsConfig.RegionConfig initialRegion = regionIndex.get(initialPets.getInitialMapId());
            if (initialRegion == null) {
                errors.add("initialMapId 对应区域不存在: " + initialPets.getInitialMapId());
            } else if (initialRegion.isPlanned()) {
                errors.add("initialMapId 不可指向结构预留区域: " + initialPets.getInitialMapId());
            }
        }
    }

    /** 校验地图对象奖励条目：道具引用存在、数量区间合法、金币区间合法。 */
    private void validateMapRewards(String regionId, String objectId,
                                    List<MapsConfig.RewardEntry> rewards,
                                    int goldMin, int goldMax,
                                    Set<String> itemIds, List<String> errors) {
        for (MapsConfig.RewardEntry reward : rewards) {
            if (reward.getItemId() == null || !itemIds.contains(reward.getItemId())) {
                errors.add(regionId + " 对象 " + objectId + " 引用不存在的道具: " + reward.getItemId());
            }
            if (reward.getQtyMin() < 1 || reward.getQtyMax() < reward.getQtyMin()) {
                errors.add(regionId + " 对象 " + objectId + " 奖励数量区间非法: "
                        + reward.getItemId() + " [" + reward.getQtyMin() + ", " + reward.getQtyMax() + "]");
            }
        }
        if (goldMin < 0 || goldMax < goldMin) {
            errors.add(regionId + " 对象 " + objectId + " 金币区间非法: [" + goldMin + ", " + goldMax + "]");
        }
    }

    // ---- 阶段 7：Boss 配置校验 ----

    /**
     * 校验 Boss 配置：引用完整性（元素/技能/被动/道具/区域存在性）、
     * HP 阈值合法性、掉落概率合法性。
     */
    private void validateBosses(BossesConfig bosses, GameElementsConfig elements,
                                SkillsConfig skills, ItemsConfig items,
                                MapsConfig maps, List<String> errors) {
        if (bosses.getBosses() == null || bosses.getBosses().isEmpty()) {
            return; // Boss 配置可选
        }

        Set<String> elementIds = elements.getElements().stream()
                .map(GameElementConfig::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> skillIds = skills.getSkills().stream()
                .map(SkillConfig::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> passiveIds = skills.getPassives().stream()
                .map(PassiveSkillConfig::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> itemIds = items.getItems().stream()
                .map(ItemConfig::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> regionIds = maps != null
                ? maps.getRegions().stream().map(MapsConfig.RegionConfig::getId).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        Set<String> bossIds = new HashSet<>();
        for (BossesConfig.BossConfig boss : bosses.getBosses()) {
            if (boss.getId() == null || boss.getId().isBlank()) {
                errors.add("Boss ID 不可为空");
                continue;
            }
            if (!bossIds.add(boss.getId())) {
                errors.add("Boss ID 重复: " + boss.getId());
            }
            if (boss.getElement() != null && !elementIds.contains(boss.getElement())) {
                errors.add("Boss " + boss.getId() + " 引用不存在的属性: " + boss.getElement());
            }
            if (boss.getMapId() != null && !regionIds.contains(boss.getMapId())) {
                errors.add("Boss " + boss.getId() + " 引用不存在的区域: " + boss.getMapId());
            }

            if (boss.getDifficulties() != null) {
                for (Map.Entry<String, BossesConfig.DifficultyConfig> entry : boss.getDifficulties().entrySet()) {
                    String diffKey = entry.getKey();
                    BossesConfig.DifficultyConfig diff = entry.getValue();
                    String prefix = "Boss " + boss.getId() + " 难度 " + diffKey;

                    // 技能引用
                    for (String skillId : diff.getSkills()) {
                        if (!skillIds.contains(skillId)) {
                            errors.add(prefix + " 引用不存在的技能: " + skillId);
                        }
                    }
                    // 被动引用
                    for (String passiveId : diff.getPassives()) {
                        if (!passiveIds.contains(passiveId)) {
                            errors.add(prefix + " 引用不存在的被动: " + passiveId);
                        }
                    }
                    // 掉落校验
                    if (diff.getDrops() != null) {
                        for (Map.Entry<String, List<BossesConfig.DropEntry>> dropEntry : diff.getDrops().entrySet()) {
                            for (BossesConfig.DropEntry drop : dropEntry.getValue()) {
                                if (drop.getItemId() == null || !itemIds.contains(drop.getItemId())) {
                                    errors.add(prefix + " 掉落引用不存在的道具: " + drop.getItemId());
                                }
                                if (drop.getChance() < 0 || drop.getChance() > 1.0) {
                                    errors.add(prefix + " 掉落概率非法: " + drop.getItemId() + " chance=" + drop.getChance());
                                }
                                if (drop.getQty() < 1) {
                                    errors.add(prefix + " 掉落数量非法: " + drop.getItemId() + " qty=" + drop.getQty());
                                }
                            }
                        }
                    }
                    // 阶段触发器校验
                    if (diff.getPhases() != null) {
                        for (BossesConfig.PhaseTrigger phase : diff.getPhases()) {
                            if (phase.getHpPercent() <= 0 || phase.getHpPercent() >= 1.0) {
                                errors.add(prefix + " HP 阈值非法: " + phase.getHpPercent() + "（应在 0~1 之间）");
                            }
                            for (BossesConfig.PhaseEffect effect : phase.getEffects()) {
                                if ("ADD_SKILL".equals(effect.getType()) && effect.getSkillId() != null
                                        && !skillIds.contains(effect.getSkillId())) {
                                    errors.add(prefix + " 阶段技能引用不存在: " + effect.getSkillId());
                                }
                                if ("BUFF_SELF".equals(effect.getType()) && effect.getStatusId() != null) {
                                    // 状态引用校验已在其他地方处理，此处仅记录
                                }
                            }
                        }
                    }
                    // stats 合法性
                    BossesConfig.StatsConfig stats = diff.getStats();
                    if (stats.getMaxHp() <= 0) {
                        errors.add(prefix + " maxHp 必须 > 0");
                    }
                    if (diff.getLuckGain() < 0) {
                        errors.add(prefix + " luckGain 不可为负数");
                    }
                }
            }
        }
    }
}
