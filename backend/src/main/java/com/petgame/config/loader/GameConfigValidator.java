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
        List<String> errors = new ArrayList<>();

        validateSystemRules(system, errors);
        validateElements(elements, errors);
        if (initialPets != null) {
            validateInitialPets(initialPets, elements, errors);
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
}
