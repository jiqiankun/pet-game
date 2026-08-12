package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameConfigValidator 配置校验测试。
 */
class GameConfigValidatorTest {

    private final GameConfigValidator validator = new GameConfigValidator();

    private SystemRuleConfig validSystem() {
        return new SystemRuleConfig();
    }

    private GameElementsConfig validElements() {
        GameElementsConfig config = new GameElementsConfig();
        GameElementConfig fire = new GameElementConfig();
        fire.setId("FIRE");
        fire.setName("火");
        GameElementConfig water = new GameElementConfig();
        water.setId("WATER");
        water.setName("水");
        config.setElements(List.of(fire, water));

        ElementAdvantageConfig adv = new ElementAdvantageConfig();
        adv.setAttacker("WATER");
        adv.setDefender("FIRE");
        config.setAdvantages(List.of(adv));
        return config;
    }

    @Test
    void validate_validConfig_shouldPass() {
        assertDoesNotThrow(() -> validator.validate(validSystem(), validElements()));
    }

    @Test
    void validate_duplicateElementId_shouldFail() {
        GameElementsConfig config = validElements();
        GameElementConfig dup = new GameElementConfig();
        dup.setId("FIRE");
        dup.setName("火重复");
        List<GameElementConfig> list = new ArrayList<>(config.getElements());
        list.add(dup);
        config.setElements(list);

        assertThrows(IllegalStateException.class, () -> validator.validate(validSystem(), config));
    }

    @Test
    void validate_advantageReferenceNonExistent_shouldFail() {
        GameElementsConfig config = validElements();
        ElementAdvantageConfig bad = new ElementAdvantageConfig();
        bad.setAttacker("DRAGON");
        bad.setDefender("FIRE");
        List<ElementAdvantageConfig> list = new ArrayList<>(config.getAdvantages());
        list.add(bad);
        config.setAdvantages(list);

        assertThrows(IllegalStateException.class, () -> validator.validate(validSystem(), config));
    }

    @Test
    void validate_invalidCritRate_shouldFail() {
        SystemRuleConfig system = validSystem();
        system.setCritRate(1.5);
        assertThrows(IllegalStateException.class, () -> validator.validate(system, validElements()));
    }

    @Test
    void validate_critMinGreaterThanMax_shouldFail() {
        SystemRuleConfig system = validSystem();
        system.setCritMultiplierMin(2.5);
        system.setCritMultiplierMax(1.5);
        assertThrows(IllegalStateException.class, () -> validator.validate(system, validElements()));
    }

    @Test
    void validate_invalidLevelCap_shouldFail() {
        SystemRuleConfig system = validSystem();
        system.setLevelCap(0);
        assertThrows(IllegalStateException.class, () -> validator.validate(system, validElements()));
    }

    @Test
    void validate_battleSlotsExceedCarry_shouldFail() {
        SystemRuleConfig system = validSystem();
        system.setStandardBattleSlots(5);
        system.setMaxCarryPets(3);
        assertThrows(IllegalStateException.class, () -> validator.validate(system, validElements()));
    }

    @Test
    void validate_emptyElements_shouldFail() {
        GameElementsConfig config = new GameElementsConfig();
        config.setElements(List.of());
        assertThrows(IllegalStateException.class, () -> validator.validate(validSystem(), config));
    }

    @Test
    void validate_negativeAdvantageMultiplier_shouldFail() {
        SystemRuleConfig system = validSystem();
        system.setAdvantageMultiplier(-1.0);
        assertThrows(IllegalStateException.class, () -> validator.validate(system, validElements()));
    }
}
