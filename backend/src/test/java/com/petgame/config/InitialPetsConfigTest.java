package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 2 初始宠物配置校验测试（阶段 5 适配）。
 * <p>
 * 阶段 5 起 InitialPetOption 瘦身为「speciesId + 资质覆盖」，
 * 种族数据由 pets/pets.yml 提供（引用校验由全量加载测试覆盖）。
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
        for (String speciesId : new String[]{"PET_FIRE_001", "PET_WATER_001", "PET_WOOD_001"}) {
            InitialPetsConfig.InitialPetOption opt = new InitialPetsConfig.InitialPetOption();
            opt.setSpeciesId(speciesId);
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
    void validate_aptitudeBelow80_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        config.getInitialPets().get(0).setAptitudeHp(70); // 低于 80
        assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config));
    }

    @Test
    void validate_speciesReferenceMissing_shouldFail() {
        // 提供 pets 配置但不含被引用的种族 → 引用校验失败
        InitialPetsConfig config = validInitialPets();
        PetsConfig pets = new PetsConfig();
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId("PET_OTHER_999");
        species.setName("其他");
        species.setElement("FIRE");
        pets.setSpecies(List.of(species));

        GameConfigValidator fullValidator = new GameConfigValidator();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                fullValidator.validate(validSystem(), validElements(), config,
                        null, null, null, null, pets, null, null));
        assertTrue(ex.getMessage().contains("引用不存在的种族"),
                "错误信息应包含种族引用缺失提示: " + ex.getMessage());
    }

    @Test
    void validate_initialItemReferenceMissing_shouldFail() {
        InitialPetsConfig config = validInitialPets();
        InitialPetsConfig.InitialItemEntry entry = new InitialPetsConfig.InitialItemEntry();
        entry.setItemId("ITEM_NOT_EXIST");
        entry.setQuantity(1);
        config.setInitialItems(List.of(entry));

        ItemsConfig items = new ItemsConfig();
        ItemConfig ball = new ItemConfig();
        ball.setId("ITEM_CAPTURE_BALL_NORMAL");
        ball.setItemType("CAPTURE_BALL");
        items.setItems(List.of(ball));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                validator.validate(validSystem(), validElements(), config,
                        null, null, null, items, null, null, null));
        assertTrue(ex.getMessage().contains("引用不存在的道具"),
                "错误信息应包含道具引用缺失提示: " + ex.getMessage());
    }

    @Test
    void initialPetsConfig_defaultValues() {
        InitialPetsConfig config = new InitialPetsConfig();
        assertEquals(500, config.getInitialGold());
        assertEquals(0, config.getInitialExpPool());
        assertEquals("MAP_START_VILLAGE", config.getInitialMapId());
        assertNotNull(config.getInitialItems());
    }
}
