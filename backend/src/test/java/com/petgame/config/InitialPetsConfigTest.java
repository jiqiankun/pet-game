package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 2：初始宠物配置校验测试。
 */
class InitialPetsConfigTest {

    private final GameConfigValidator validator = new GameConfigValidator();

    private SystemRuleConfig validSystem() {
        return new SystemRuleConfig();
    }

    private GameElementsConfig validElements() {
        GameElementsConfig config = new GameElementsConfig();
        String[][] elems = {
                {"METAL", "金"}, {"WOOD", "木"}, {"WATER", "水"}, {"FIRE", "火"},
                {"EARTH", "土"}, {"WIND", "风"}, {"THUNDER", "雷"}, {"LIGHT", "光"}, {"DARK", "暗"}
        };
        List<GameElementConfig> elemList = new ArrayList<>();
        for (String[] e : elems) {
            GameElementConfig ge = new GameElementConfig();
            ge.setId(e[0]);
            ge.setName(e[1]);
            elemList.add(ge);
        }
        config.setElements(elemList);
        config.setAdvantages(new ArrayList<>());
        return config;
    }

    private InitialPetsConfig validInitialPets() {
        InitialPetsConfig config = new InitialPetsConfig();
        List<InitialPetsConfig.InitialPetOption> pets = new ArrayList<>();
        String[][] petData = {
                {"PET_FIRE_001", "烬牙兽", "FIRE"},
                {"PET_WATER_001", "汐月灵", "WATER"},
                {"PET_WOOD_001", "藤梦鹿", "WOOD"}
        };
        for (String[] p : petData) {
            InitialPetsConfig.InitialPetOption opt = new InitialPetsConfig.InitialPetOption();
            opt.setSpeciesId(p[0]);
            opt.setName(p[1]);
            opt.setElement(p[2]);
            opt.setBaseHp(85);
            opt.setBaseStrength(80);
            opt.setBaseSpirit(80);
            opt.setBaseDefense(80);
            opt.setBaseResistance(80);
            opt.setBaseSpeed(80);
            opt.setAptitudeHp(85);
            opt.setAptitudeStrength(85);
            opt.setAptitudeSpirit(85);
            opt.setAptitudeDefense(85);
            opt.setAptitudeResistance(85);
            opt.setAptitudeSpeed(85);
            pets.add(opt);
        }
        config.setInitialPets(pets);
        return config;
    }

    @Test
    void validate_validInitialPets_shouldPass() {
        assertDoesNotThrow(() ->
                validator.validate(validSystem(), validElements(), validInitialPets()));
    }

    @Test
    void validate_emptyInitialPets_shouldFail() {
        InitialPetsConfig config = new InitialPetsConfig();
        config.setInitialPets(List.of());
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void validate_wrongNumberOfPets_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        config.setInitialPets(config.getInitialPets().subList(0, 2)); // 只有 2 个
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void validate_duplicateSpeciesId_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        config.getInitialPets().get(1).setSpeciesId("PET_FIRE_001"); // 重复
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void validate_invalidElementReference_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        config.getInitialPets().get(0).setElement("DRAGON"); // 不存在
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void validate_aptitudeBelow80_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        config.getInitialPets().get(0).setAptitudeHp(70); // 低于 80
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void initialPetsConfig_defaultValues() {
        InitialPetsConfig config = new InitialPetsConfig();
        assertEquals(500, config.getInitialGold());
        assertEquals(0, config.getInitialExpPool());
        assertEquals("MAP_START_VILLAGE", config.getInitialMapId());
    }
}
