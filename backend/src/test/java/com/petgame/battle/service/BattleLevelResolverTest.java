package com.petgame.battle.service;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.pet.PetGrowthTestFixtures;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 阶段 13：有效等级只投影战斗临时数据。 */
class BattleLevelResolverTest {

    @Test
    void resolve_capsLevelAndProjectsPointsWithoutMutatingSource() {
        PetSpeciesConfig species = PetGrowthTestFixtures.species("SPEC_TEST", "RARE", 50, List.of());
        GameConfigRegistry registry = PetGrowthTestFixtures.buildRegistry(List.of(species));
        PetGrowthService growth = new PetGrowthService(registry);
        BattleLevelResolver resolver = new BattleLevelResolver(growth);
        PlayerPetEntity pet = petAtLevel50();

        BattleLevelResolver.ResolvedPet result = resolver.resolve(pet, species, 20);

        assertEquals(50, result.getActualLevel());
        assertEquals(20, result.getEffectiveLevel());
        assertEquals(50, pet.getLevel(), "真实等级不得被战斗压制改写");
        assertEquals(60, pet.getFreePointHp(), "真实自由点不得被投影改写");
        assertEquals(20, pet.getFreePointSpeed(), "真实速度点不得被投影改写");
        assertTrue(growth.consumedFreePoints(result.getProjectedPet()) <= result.getEffectivePointBudget());
        assertEquals(result.getStats(), growth.computePanelStatsAtLevel(
                result.getProjectedPet(), species, result.getEffectiveLevel()));
    }

    private static PlayerPetEntity petAtLevel50() {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setLevel(50);
        pet.setHpAptitude(50);
        pet.setStrengthAptitude(50);
        pet.setSpiritAptitude(50);
        pet.setDefenseAptitude(50);
        pet.setResistanceAptitude(50);
        pet.setSpeedAptitude(50);
        pet.setFreePointHp(60);
        pet.setFreePointStrength(30);
        pet.setFreePointSpirit(20);
        pet.setFreePointDefense(10);
        pet.setFreePointResistance(10);
        pet.setFreePointSpeed(20);
        return pet;
    }
}
