package com.petgame.pet;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.PetsConfig;
import com.petgame.config.model.SystemRuleConfig;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物成长领域测试夹具（阶段 4）。
 * <p>
 * 以程序化配置构建 GameConfigRegistry（不依赖 Spring 与 YAML），
 * 便于对面板属性公式、升级经验、自由点数、技能解锁做确定性断言。
 * <p>
 * 默认配置与 system.yml 一致：
 * <ul>
 *   <li>等级上限 50；每级 3 自由点；稀有度每 10 级额外 0/2/4/6。</li>
 *   <li>expBase=100，expGrowthFactor=1.15。</li>
 *   <li>statPointCost=1，hpPointCost=1，speedPointCost=2。</li>
 *   <li>levelStatGrowth=2.0，levelHpGrowth=8.0；freePointStatValue=1.0，freePointHpValue=5.0。</li>
 * </ul>
 */
public final class PetGrowthTestFixtures {

    /** 稀有度额外点数表（需求 §19：COMMON/RARE/EPIC/LEGENDARY = 0/2/4/6）。 */
    public static final Map<String, Integer> RARITY_EXTRA = Map.of(
            "COMMON", 0, "RARE", 2, "EPIC", 4, "LEGENDARY", 6);

    private PetGrowthTestFixtures() {
    }

    /**
     * 构建测试用配置注册中心。
     * <p>
     * 阶段 5 起种族配置使用 PetSpeciesConfig（pets/pets.yml 模型）。
     *
     * @param speciesList 种族配置列表
     */
    public static GameConfigRegistry buildRegistry(List<PetSpeciesConfig> speciesList) {
        SystemRuleConfig system = defaultSystemRules();

        PetsConfig petsConfig = new PetsConfig();
        petsConfig.setSpecies(speciesList);

        try {
            GameConfigRegistry registry = new GameConfigRegistry(null, null);
            setField(registry, "systemRules", system);
            setField(registry, "petsConfig", petsConfig);
            // 种族索引（getSpecies 依赖）
            LinkedHashMap<String, PetSpeciesConfig> speciesIndex = new LinkedHashMap<>();
            for (PetSpeciesConfig species : speciesList) {
                speciesIndex.put(species.getId(), species);
            }
            setField(registry, "speciesIndex", speciesIndex);
            // 初始化空索引，避免 PetService.getSkill/getItem 等查询触发 NPE
            setField(registry, "skillIndex", new LinkedHashMap<>());
            setField(registry, "itemIndex", new LinkedHashMap<>());
            setField(registry, "statusIndex", new LinkedHashMap<>());
            setField(registry, "passiveIndex", new LinkedHashMap<>());
            return registry;
        } catch (Exception e) {
            throw new IllegalStateException("PetGrowth 测试夹具构建失败", e);
        }
    }

    /** 默认系统规则（与 system.yml 默认值一致）。 */
    public static SystemRuleConfig defaultSystemRules() {
        SystemRuleConfig system = new SystemRuleConfig();
        system.setLevelCap(50);
        system.setFreePointsPerLevel(3);
        system.setRarityExtraPointsPer10Levels(new HashMap<>(RARITY_EXTRA));
        system.setExpBase(100);
        system.setExpGrowthFactor(1.15);
        system.setStatPointCost(1);
        system.setHpPointCost(1);
        system.setSpeedPointCost(2);
        system.setLevelStatGrowth(2.0);
        system.setLevelHpGrowth(8.0);
        system.setFreePointStatValue(1.0);
        system.setFreePointHpValue(5.0);
        return system;
    }

    /**
     * 构建测试用种族配置（阶段 5：PetSpeciesConfig 模型）。
     *
     * @param speciesId  种族 ID
     * @param rarity     稀有度
     * @param aptitude   资质参数（阶段 5 资质属个体属性，此处保留签名兼容，不再写入种族配置）
     * @param skillSlots 技能槽（含 unlockLevel）
     */
    public static PetSpeciesConfig species(
            String speciesId, String rarity, int aptitude,
            List<PetSpeciesConfig.SpeciesSkillSlot> skillSlots) {
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId(speciesId);
        species.setName(speciesId);
        species.setElement("WATER");
        species.setRarity(rarity);
        species.setBaseHp(100);
        species.setBaseStrength(20);
        species.setBaseSpirit(20);
        species.setBaseDefense(20);
        species.setBaseResistance(20);
        species.setBaseSpeed(20);
        species.setSkills(skillSlots);
        return species;
    }

    /** 构建技能槽。 */
    public static PetSpeciesConfig.SpeciesSkillSlot skillSlot(String skillId, int unlockLevel) {
        PetSpeciesConfig.SpeciesSkillSlot slot = new PetSpeciesConfig.SpeciesSkillSlot();
        slot.setSkillId(skillId);
        slot.setUnlockLevel(unlockLevel);
        return slot;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
