package com.petgame.config;

import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏配置注册中心。
 * <p>
 * 应用启动时加载并校验全部游戏配置，提供统一运行时查询能力。
 * 后续阶段所有内容配置（宠物、技能、Boss、道具等）均在此注册。
 * <p>
 * 查询克制倍率示例：
 * <pre>{@code
 *   double multiplier = registry.getElementAdvantageMultiplier("FIRE", "METAL");
 *   // FIRE 克制 METAL → 返回 1.50
 * }</pre>
 */
@Component
public class GameConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(GameConfigRegistry.class);

    private final GameConfigLoader loader;
    private final GameConfigValidator validator;

    private SystemRuleConfig systemRules;
    private GameElementsConfig elementsConfig;
    private InitialPetsConfig initialPetsConfig;
    private SkillsConfig skillsConfig;
    private StatusesConfig statusesConfig;
    private TestBattleConfig testBattleConfig;
    private ItemsConfig itemsConfig;
    private PetsConfig petsConfig;
    private EncountersConfig encountersConfig;
    private ReleaseGiftsConfig releaseGiftsConfig;
    private MapsConfig mapsConfig;
    private BossesConfig bossesConfig;
    private QuestsConfig questsConfig;
    private ShopConfig shopConfig;
    private RandomEventsConfig randomEventsConfig;
    private BuildRecommendationConfig buildRecommendationsConfig;
    private AchievementsConfig achievementsConfig;
    private BossChallengesConfig bossChallengesConfig;
    private VictoryInteractionConfig victoryInteractionConfig;

    /** 属性 ID → 属性配置 的快速索引。 */
    private Map<String, GameElementConfig> elementIndex;

    /** 克制关系快速索引：key = "ATTACKER|DEFENDER" → true 表示克制。 */
    private Set<String> advantageIndex;

    /** 技能 ID → 技能配置 的快速索引（阶段 3）。 */
    private Map<String, SkillConfig> skillIndex;

    /** 状态 ID → 状态配置 的快速索引（阶段 3）。 */
    private Map<String, StatusEffectConfig> statusIndex;

    /** 被动 ID → 被动配置 的快速索引（阶段 3）。 */
    private Map<String, PassiveSkillConfig> passiveIndex;

    /** 道具 ID → 道具配置 的快速索引（阶段 4）。 */
    private Map<String, ItemConfig> itemIndex;

    /** 种族 ID → 种族配置 的快速索引（阶段 5）。 */
    private Map<String, PetSpeciesConfig> speciesIndex;

    /** 区域 ID → 区域配置 的快速索引（阶段 6）。 */
    private Map<String, MapsConfig.RegionConfig> regionIndex;

    /** 营地 ID → 所属区域配置 的快速索引（阶段 6）。 */
    private Map<String, MapsConfig.RegionConfig> campRegionIndex;

    /** Boss ID → Boss 配置 的快速索引（阶段 7）。 */
    private Map<String, BossesConfig.BossConfig> bossIndex;

    /** 任务 ID → 任务配置 的快速索引（阶段 9）。 */
    private Map<String, QuestsConfig.QuestConfig> questIndex;

    /** NPC ID → NPC 配置 的快速索引（阶段 9）。 */
    private Map<String, QuestsConfig.NpcConfig> npcIndex;

    /** 成就 ID → 成就配置 的快速索引（阶段 11）。 */
    private Map<String, AchievementsConfig.AchievementConfig> achievementIndex;

    /** Boss ID → Boss 挑战目标组配置 的快速索引（阶段 11）。 */
    private Map<String, BossChallengesConfig.BossChallengeGroup> bossChallengeIndex;

    public GameConfigRegistry(GameConfigLoader loader, GameConfigValidator validator) {
        this.loader = loader;
        this.validator = validator;
    }

    /**
     * Bean 初始化后加载并校验全部配置。
     * 校验失败时抛出异常使应用启动失败。
     */
    @PostConstruct
    public void init() {
        log.info("开始加载游戏配置...");

        this.systemRules = loader.loadSystemConfig();
        this.elementsConfig = loader.loadElementsConfig();
        this.initialPetsConfig = loader.loadInitialPetsConfig();
        this.skillsConfig = loader.loadSkillsConfig();
        this.statusesConfig = loader.loadStatusesConfig();
        this.testBattleConfig = loader.loadTestBattleConfig();
        this.itemsConfig = loader.loadItemsConfig();
        this.petsConfig = loader.loadPetsConfig();
        this.encountersConfig = loader.loadEncountersConfig();
        this.releaseGiftsConfig = loader.loadReleaseGiftsConfig();
        this.mapsConfig = loader.loadMapsConfig();
        this.bossesConfig = loader.loadBossesConfig();
        this.questsConfig = loader.loadQuestsConfig();
        this.shopConfig = loader.loadShopConfig();
        this.randomEventsConfig = loader.loadRandomEventsConfig();
        this.buildRecommendationsConfig = loader.loadBuildRecommendationsConfig();
        this.achievementsConfig = loader.loadAchievementsConfig();
        this.bossChallengesConfig = loader.loadBossChallengesConfig();
        this.victoryInteractionConfig = loader.loadVictoryInteractionConfig();

        // 校验
        validator.validate(systemRules, elementsConfig, initialPetsConfig,
                skillsConfig, statusesConfig, testBattleConfig, itemsConfig,
                petsConfig, encountersConfig, releaseGiftsConfig, mapsConfig,
                bossesConfig, questsConfig, shopConfig, randomEventsConfig, buildRecommendationsConfig,
                achievementsConfig, bossChallengesConfig, victoryInteractionConfig);

        // 构建索引
        buildElementIndex();
        buildAdvantageIndex();
        buildSkillIndex();
        buildStatusIndex();
        buildPassiveIndex();
        buildItemIndex();
        buildSpeciesIndex();
        buildRegionIndex();
        buildBossIndex();
        buildQuestIndex();
        buildAchievementIndex();
        buildBossChallengeIndex();

        log.info("游戏配置加载完成：{} 种属性，{} 条克制关系，{} 个初始宠物选项，{} 个技能，{} 个被动，{} 个状态，{} 个道具，{} 个种族，{} 个遭遇组，{} 个区域，{} 个 Boss，{} 个商店商品，{} 个随机事件，{} 个推荐 Build, {} 个成就，{} 组 Boss 挑战, {} 条胜利互动",
                elementsConfig.getElements().size(),
                elementsConfig.getAdvantages() != null ? elementsConfig.getAdvantages().size() : 0,
                initialPetsConfig.getInitialPets().size(),
                skillsConfig.getSkills().size(),
                skillsConfig.getPassives().size(),
                statusesConfig.getStatuses().size(),
                itemsConfig.getItems().size(),
                petsConfig.getSpecies().size(),
                encountersConfig.getEncounterGroups().size(),
                mapsConfig.getRegions().size(),
                bossesConfig.getBosses().size(),
                questsConfig.getQuests().size(),
                shopConfig.getShopItems().size(),
                randomEventsConfig.getRandomEvents() != null ? randomEventsConfig.getRandomEvents().size() : 0,
                buildRecommendationsConfig.getRecommendations() != null ? buildRecommendationsConfig.getRecommendations().size() : 0,
                achievementsConfig.getAchievements() != null ? achievementsConfig.getAchievements().size() : 0,
                bossChallengesConfig.getGroups() != null ? bossChallengesConfig.getGroups().size() : 0,
                victoryInteractionConfig.getInteractions() != null ? victoryInteractionConfig.getInteractions().size() : 0);
    }

    // ---- 查询方法 ----

    /** 获取系统规则配置（只读使用）。 */
    public SystemRuleConfig getSystemRules() {
        return systemRules;
    }

    /** 获取属性体系配置（只读使用）。 */
    public GameElementsConfig getElementsConfig() {
        return elementsConfig;
    }

    /** 获取初始宠物配置（只读使用）。 */
    public InitialPetsConfig getInitialPetsConfig() {
        return initialPetsConfig;
    }

    /** 获取技能配置（只读使用，阶段 3）。 */
    public SkillsConfig getSkillsConfig() {
        return skillsConfig;
    }

    /** 获取状态配置（只读使用，阶段 3）。 */
    public StatusesConfig getStatusesConfig() {
        return statusesConfig;
    }

    /** 获取测试战斗配置（只读使用，阶段 3）。 */
    public TestBattleConfig getTestBattleConfig() {
        return testBattleConfig;
    }

    /** 获取道具配置（只读使用，阶段 4）。 */
    public ItemsConfig getItemsConfig() {
        return itemsConfig;
    }

    /** 获取宠物种族配置（只读使用，阶段 5）。 */
    public PetsConfig getPetsConfig() {
        return petsConfig;
    }

    /** 获取野生遭遇配置（只读使用，阶段 5）。 */
    public EncountersConfig getEncountersConfig() {
        return encountersConfig;
    }

    /** 获取放生礼物配置（只读使用，阶段 5）。 */
    public ReleaseGiftsConfig getReleaseGiftsConfig() {
        return releaseGiftsConfig;
    }

    /** 获取地图与区域配置（只读使用，阶段 6）。 */
    public MapsConfig getMapsConfig() {
        return mapsConfig;
    }

    /** 获取 Boss 配置（只读使用，阶段 7）。 */
    public BossesConfig getBossesConfig() {
        return bossesConfig;
    }

    /** 获取任务配置（只读使用，阶段 9）。 */
    public QuestsConfig getQuestsConfig() {
        return questsConfig;
    }

    /** 获取商店配置（只读使用，阶段 10）。 */
    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    /** 获取随机事件配置（只读使用，阶段 10）。 */
    public RandomEventsConfig getRandomEventsConfig() {
        return randomEventsConfig;
    }

    /** 获取推荐 Build 配置（只读使用，阶段 10）。 */
    public BuildRecommendationConfig getBuildRecommendationsConfig() {
        return buildRecommendationsConfig;
    }

    /** 获取成就配置（只读使用，阶段 11）。 */
    public AchievementsConfig getAchievementsConfig() {
        return achievementsConfig;
    }

    /** 获取 Boss 挑战目标配置（只读使用，阶段 11）。 */
    public BossChallengesConfig getBossChallengesConfig() {
        return bossChallengesConfig;
    }

    /** 获取敌方胜利互动配置（只读使用，阶段 12）。 */
    public VictoryInteractionConfig getVictoryInteractionConfig() {
        return victoryInteractionConfig;
    }

    /** 根据成就 ID 获取成就配置，不存在返回 null（阶段 11）。 */
    public AchievementsConfig.AchievementConfig getAchievement(String achievementId) {
        return achievementId == null ? null : achievementIndex.get(achievementId);
    }

    /** 根据 Boss ID 获取 Boss 挑战目标组配置，不存在返回 null（阶段 11）。 */
    public BossChallengesConfig.BossChallengeGroup getBossChallengeGroup(String bossId) {
        return bossId == null ? null : bossChallengeIndex.get(bossId);
    }

    /** 根据任务 ID 获取任务配置，不存在返回 null（阶段 9）。 */
    public QuestsConfig.QuestConfig getQuest(String questId) {
        return questId == null ? null : questIndex.get(questId);
    }

    /** 根据 NPC ID 获取 NPC 配置，不存在返回 null（阶段 9）。 */
    public QuestsConfig.NpcConfig getNpc(String npcId) {
        return npcId == null ? null : npcIndex.get(npcId);
    }

    /** 获取全部主线任务（按配置顺序，阶段 9）。 */
    public List<QuestsConfig.QuestConfig> getMainQuests() {
        return questsConfig.getQuests().stream()
                .filter(q -> "MAIN".equals(q.getType()))
                .collect(Collectors.toList());
    }

    /** 获取全部支线任务（按配置顺序，阶段 9）。 */
    public List<QuestsConfig.QuestConfig> getSideQuests() {
        return questsConfig.getQuests().stream()
                .filter(q -> "SIDE".equals(q.getType()))
                .collect(Collectors.toList());
    }

    /** 获取全部隐藏任务（按配置顺序，阶段 9）。 */
    public List<QuestsConfig.QuestConfig> getHiddenQuests() {
        return questsConfig.getQuests().stream()
                .filter(q -> "HIDDEN".equals(q.getType()))
                .collect(Collectors.toList());
    }

    /** 获取指定区域的任务（阶段 9）。 */
    public List<QuestsConfig.QuestConfig> getQuestsByRegion(String regionId) {
        return questsConfig.getQuests().stream()
                .filter(q -> regionId.equals(q.getRegionId()))
                .collect(Collectors.toList());
    }

    /** 获取教学步骤配置（按 order 排序，阶段 9）。 */
    public List<QuestsConfig.TutorialStepConfig> getTutorials() {
        return questsConfig.getTutorials().stream()
                .sorted(Comparator.comparingInt(QuestsConfig.TutorialStepConfig::getOrder))
                .collect(Collectors.toList());
    }

    /** 根据 Boss ID 获取 Boss 配置，不存在返回 null（阶段 7）。 */
    public BossesConfig.BossConfig getBoss(String bossId) {
        return bossId == null ? null : bossIndex.get(bossId);
    }

    /** 根据区域 ID 获取区域配置，不存在返回 null（阶段 6）。 */
    public MapsConfig.RegionConfig getRegion(String mapId) {
        return mapId == null ? null : regionIndex.get(mapId);
    }

    /** 根据营地 ID 获取其所属区域配置，不存在返回 null（阶段 6）。 */
    public MapsConfig.RegionConfig getRegionByCamp(String campId) {
        return campId == null ? null : campRegionIndex.get(campId);
    }

    /** 获取全部已实装区域（排除结构预留 planned 区域，按配置顺序，阶段 6）。 */
    public List<MapsConfig.RegionConfig> getImplementedRegions() {
        return mapsConfig.getRegions().stream()
                .filter(r -> !r.isPlanned())
                .toList();
    }

    /** 根据道具 ID 获取道具配置，不存在返回 null。 */
    public ItemConfig getItem(String itemId) {
        return itemId == null ? null : itemIndex.get(itemId);
    }

    /**
     * 根据 speciesId 获取种族配置（阶段 5 起统一来源为 pets/pets.yml）。
     * @return 对应种族配置，不存在返回 null
     */
    public PetSpeciesConfig getSpecies(String speciesId) {
        return speciesId == null ? null : speciesIndex.get(speciesId);
    }

    /** 获取全部种族配置（只读，按配置顺序）。 */
    public List<PetSpeciesConfig> getAllSpecies() {
        return petsConfig.getSpecies();
    }

    /** 根据技能 ID 获取技能配置，不存在返回 null。 */
    public SkillConfig getSkill(String skillId) {
        return skillId == null ? null : skillIndex.get(skillId);
    }

    /** 根据状态 ID 获取状态配置，不存在返回 null。 */
    public StatusEffectConfig getStatus(String statusId) {
        return statusId == null ? null : statusIndex.get(statusId);
    }

    /** 根据被动 ID 获取被动配置，不存在返回 null。 */
    public PassiveSkillConfig getPassive(String passiveId) {
        return passiveId == null ? null : passiveIndex.get(passiveId);
    }

    /** 获取状态索引（供 StatusModifiers 聚合单位状态修正）。 */
    public Map<String, StatusEffectConfig> getStatusIndex() {
        return statusIndex;
    }

    /** 获取状态联动规则列表。 */
    public List<StatusesConfig.StatusSynergyConfig> getSynergies() {
        return statusesConfig.getSynergies();
    }

    /** 获取所有属性 ID 列表。 */
    public List<String> getAllElementIds() {
        return elementsConfig.getElements().stream()
                .map(GameElementConfig::getId)
                .toList();
    }

    /** 根据属性 ID 获取属性配置，不存在返回 null。 */
    public GameElementConfig getElement(String elementId) {
        return elementIndex.get(elementId);
    }

    /**
     * 查询两个属性之间的克制倍率。
     *
     * @param attackerElementId 攻击方属性 ID
     * @param defenderElementId 防御方属性 ID
     * @return 克制 ×1.50 / 被克 ×0.75 / 普通 ×1.00
     */
    public double getElementAdvantageMultiplier(String attackerElementId, String defenderElementId) {
        if (attackerElementId.equals(defenderElementId)) {
            return systemRules.getNeutralMultiplier();
        }
        if (advantageIndex.contains(attackerElementId + "|" + defenderElementId)) {
            return systemRules.getAdvantageMultiplier();
        }
        if (advantageIndex.contains(defenderElementId + "|" + attackerElementId)) {
            return systemRules.getDisadvantageMultiplier();
        }
        return systemRules.getNeutralMultiplier();
    }

    // ---- 内部方法 ----

    private void buildElementIndex() {
        elementIndex = new LinkedHashMap<>();
        for (GameElementConfig elem : elementsConfig.getElements()) {
            elementIndex.put(elem.getId(), elem);
        }
    }

    private void buildAdvantageIndex() {
        advantageIndex = new HashSet<>();
        if (elementsConfig.getAdvantages() != null) {
            for (ElementAdvantageConfig adv : elementsConfig.getAdvantages()) {
                advantageIndex.add(adv.getAttacker() + "|" + adv.getDefender());
            }
        }
    }

    private void buildSkillIndex() {
        skillIndex = new LinkedHashMap<>();
        for (SkillConfig skill : skillsConfig.getSkills()) {
            skillIndex.put(skill.getId(), skill);
        }
    }

    private void buildStatusIndex() {
        statusIndex = new LinkedHashMap<>();
        for (StatusEffectConfig status : statusesConfig.getStatuses()) {
            statusIndex.put(status.getId(), status);
        }
    }

    private void buildPassiveIndex() {
        passiveIndex = new LinkedHashMap<>();
        for (PassiveSkillConfig passive : skillsConfig.getPassives()) {
            passiveIndex.put(passive.getId(), passive);
        }
    }

    private void buildItemIndex() {
        itemIndex = new LinkedHashMap<>();
        if (itemsConfig != null && itemsConfig.getItems() != null) {
            for (ItemConfig item : itemsConfig.getItems()) {
                itemIndex.put(item.getId(), item);
            }
        }
    }

    private void buildSpeciesIndex() {
        speciesIndex = new LinkedHashMap<>();
        for (PetSpeciesConfig species : petsConfig.getSpecies()) {
            speciesIndex.put(species.getId(), species);
        }
    }

    private void buildRegionIndex() {
        regionIndex = new LinkedHashMap<>();
        campRegionIndex = new LinkedHashMap<>();
        for (MapsConfig.RegionConfig region : mapsConfig.getRegions()) {
            regionIndex.put(region.getId(), region);
            for (MapsConfig.CampConfig camp : region.getCamps()) {
                campRegionIndex.put(camp.getCampId(), region);
            }
        }
    }

    private void buildBossIndex() {
        bossIndex = new LinkedHashMap<>();
        if (bossesConfig != null && bossesConfig.getBosses() != null) {
            for (BossesConfig.BossConfig boss : bossesConfig.getBosses()) {
                bossIndex.put(boss.getId(), boss);
            }
        }
    }

    private void buildQuestIndex() {
        questIndex = new LinkedHashMap<>();
        npcIndex = new LinkedHashMap<>();
        if (questsConfig != null) {
            if (questsConfig.getQuests() != null) {
                for (QuestsConfig.QuestConfig quest : questsConfig.getQuests()) {
                    questIndex.put(quest.getId(), quest);
                }
            }
            if (questsConfig.getNpcs() != null) {
                for (QuestsConfig.NpcConfig npc : questsConfig.getNpcs()) {
                    npcIndex.put(npc.getNpcId(), npc);
                }
            }
        }
    }

    private void buildAchievementIndex() {
        achievementIndex = new LinkedHashMap<>();
        if (achievementsConfig != null && achievementsConfig.getAchievements() != null) {
            for (AchievementsConfig.AchievementConfig ach : achievementsConfig.getAchievements()) {
                achievementIndex.put(ach.getId(), ach);
            }
        }
    }

    private void buildBossChallengeIndex() {
        bossChallengeIndex = new LinkedHashMap<>();
        if (bossChallengesConfig != null && bossChallengesConfig.getGroups() != null) {
            for (BossChallengesConfig.BossChallengeGroup group : bossChallengesConfig.getGroups()) {
                bossChallengeIndex.put(group.getBossId(), group);
            }
        }
    }
}
