package com.petgame.config.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.petgame.config.GameProperties;
import com.petgame.config.model.AchievementsConfig;
import com.petgame.config.model.BossChallengesConfig;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.GameElementsConfig;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.ItemsConfig;
import com.petgame.config.model.MapsConfig;
import com.petgame.config.model.PetsConfig;
import com.petgame.config.model.QuestsConfig;
import com.petgame.config.model.RandomEventsConfig;
import com.petgame.config.model.BuildRecommendationConfig;
import com.petgame.config.model.ReleaseGiftsConfig;
import com.petgame.config.model.ShopConfig;
import com.petgame.config.model.SkillsConfig;
import com.petgame.config.model.StatusesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.config.model.TestBattleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 游戏配置加载器。
 * <p>
 * 加载流程：读取 JAR 内部默认配置 → 读取外部配置目录 → 相同文件外部覆盖内部。
 * 不做热更新，修改配置需重启。
 */
@Component
public class GameConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(GameConfigLoader.class);

    private static final String INTERNAL_CONFIG_PREFIX = "game-config/";
    private static final String SYSTEM_YML = "system.yml";
    private static final String ELEMENTS_YML = "elements.yml";
    private static final String INITIAL_PETS_YML = "initial-pets.yml";
    private static final String SKILLS_YML = "skills/skills.yml";
    private static final String STATUSES_YML = "statuses/statuses.yml";
    private static final String TEST_BATTLE_YML = "test-battle.yml";
    private static final String ITEMS_YML = "items/items.yml";
    private static final String PETS_YML = "pets/pets.yml";
    private static final String ENCOUNTERS_YML = "encounters/encounters.yml";
    private static final String RELEASE_GIFTS_YML = "drops/release-gifts.yml";
    private static final String MAPS_YML = "maps/maps.yml";
    private static final String BOSSES_YML = "bosses/bosses.yml";
    private static final String QUESTS_YML = "quests/quests.yml";
    private static final String SHOP_YML = "shop/shop.yml";
    private static final String RANDOM_EVENTS_YML = "events/random-events.yml";
    private static final String BUILD_RECOMMENDATIONS_YML = "builds/build-recommendations.yml";
    private static final String ACHIEVEMENTS_YML = "achievements/achievements.yml";
    private static final String BOSS_CHALLENGES_YML = "bosses/boss-challenges.yml";

    private final GameProperties gameProperties;
    private final ObjectMapper yamlMapper;

    public GameConfigLoader(GameProperties gameProperties) {
        this.gameProperties = gameProperties;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 加载系统规则配置。
     */
    public SystemRuleConfig loadSystemConfig() {
        SystemRuleConfig config = loadInternalYaml(SYSTEM_YML, SystemRuleConfig.class);
        if (config == null) {
            log.warn("内部 system.yml 未找到，使用默认值");
            config = new SystemRuleConfig();
        }
        SystemRuleConfig external = loadExternalYaml(SYSTEM_YML, SystemRuleConfig.class);
        if (external != null) {
            log.info("已加载外部 system.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载属性体系配置。
     */
    public GameElementsConfig loadElementsConfig() {
        GameElementsConfig config = loadInternalYaml(ELEMENTS_YML, GameElementsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 elements.yml 未找到，无法启动");
        }
        GameElementsConfig external = loadExternalYaml(ELEMENTS_YML, GameElementsConfig.class);
        if (external != null) {
            log.info("已加载外部 elements.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载初始宠物配置。
     */
    public InitialPetsConfig loadInitialPetsConfig() {
        InitialPetsConfig config = loadInternalYaml(INITIAL_PETS_YML, InitialPetsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 initial-pets.yml 未找到，无法启动");
        }
        InitialPetsConfig external = loadExternalYaml(INITIAL_PETS_YML, InitialPetsConfig.class);
        if (external != null) {
            log.info("已加载外部 initial-pets.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载技能配置（含被动技能）。
     */
    public SkillsConfig loadSkillsConfig() {
        SkillsConfig config = loadInternalYaml(SKILLS_YML, SkillsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 skills/skills.yml 未找到，无法启动");
        }
        SkillsConfig external = loadExternalYaml(SKILLS_YML, SkillsConfig.class);
        if (external != null) {
            log.info("已加载外部 skills/skills.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载状态体系配置。
     */
    public StatusesConfig loadStatusesConfig() {
        StatusesConfig config = loadInternalYaml(STATUSES_YML, StatusesConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 statuses/statuses.yml 未找到，无法启动");
        }
        StatusesConfig external = loadExternalYaml(STATUSES_YML, StatusesConfig.class);
        if (external != null) {
            log.info("已加载外部 statuses/statuses.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载测试战斗配置（阶段 3 验收用固定敌方阵容）。
     */
    public TestBattleConfig loadTestBattleConfig() {
        TestBattleConfig config = loadInternalYaml(TEST_BATTLE_YML, TestBattleConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 test-battle.yml 未找到，无法启动");
        }
        TestBattleConfig external = loadExternalYaml(TEST_BATTLE_YML, TestBattleConfig.class);
        if (external != null) {
            log.info("已加载外部 test-battle.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载道具配置（阶段 4 恢复道具基础，阶段 5 补入捕捉球）。
     */
    public ItemsConfig loadItemsConfig() {
        ItemsConfig config = loadInternalYaml(ITEMS_YML, ItemsConfig.class);
        if (config == null) {
            // 阶段 4 起道具配置为必需
            config = new ItemsConfig();
            log.warn("内部 items/items.yml 未找到，使用空道具配置");
        }
        ItemsConfig external = loadExternalYaml(ITEMS_YML, ItemsConfig.class);
        if (external != null) {
            log.info("已加载外部 items/items.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载宠物种族配置（阶段 5：27 种基础宠物的唯一数据来源）。
     */
    public PetsConfig loadPetsConfig() {
        PetsConfig config = loadInternalYaml(PETS_YML, PetsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 pets/pets.yml 未找到，无法启动");
        }
        PetsConfig external = loadExternalYaml(PETS_YML, PetsConfig.class);
        if (external != null) {
            log.info("已加载外部 pets/pets.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载野生遭遇配置（阶段 5：按权重生成野生阵容）。
     */
    public EncountersConfig loadEncountersConfig() {
        EncountersConfig config = loadInternalYaml(ENCOUNTERS_YML, EncountersConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 encounters/encounters.yml 未找到，无法启动");
        }
        EncountersConfig external = loadExternalYaml(ENCOUNTERS_YML, EncountersConfig.class);
        if (external != null) {
            log.info("已加载外部 encounters/encounters.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载放生礼物配置（阶段 5：按价值点数从礼物池抽取）。
     */
    public ReleaseGiftsConfig loadReleaseGiftsConfig() {
        ReleaseGiftsConfig config = loadInternalYaml(RELEASE_GIFTS_YML, ReleaseGiftsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 drops/release-gifts.yml 未找到，无法启动");
        }
        ReleaseGiftsConfig external = loadExternalYaml(RELEASE_GIFTS_YML, ReleaseGiftsConfig.class);
        if (external != null) {
            log.info("已加载外部 drops/release-gifts.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载地图与区域配置（阶段 6：地图探索与区域系统）。
     */
    public MapsConfig loadMapsConfig() {
        MapsConfig config = loadInternalYaml(MAPS_YML, MapsConfig.class);
        if (config == null) {
            throw new IllegalStateException("内部 maps/maps.yml 未找到，无法启动");
        }
        MapsConfig external = loadExternalYaml(MAPS_YML, MapsConfig.class);
        if (external != null) {
            log.info("已加载外部 maps/maps.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载 Boss 配置（阶段 7：Boss 系统与重复挑战）。
     */
    public BossesConfig loadBossesConfig() {
        BossesConfig config = loadInternalYaml(BOSSES_YML, BossesConfig.class);
        if (config == null) {
            log.warn("内部 bosses/bosses.yml 未找到，使用空配置");
            config = new BossesConfig();
        }
        BossesConfig external = loadExternalYaml(BOSSES_YML, BossesConfig.class);
        if (external != null) {
            log.info("已加载外部 bosses/bosses.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载任务配置（阶段 9：主线/支线/隐藏/NPC对话/教学）。
     */
    public QuestsConfig loadQuestsConfig() {
        QuestsConfig config = loadInternalYaml(QUESTS_YML, QuestsConfig.class);
        if (config == null) {
            log.warn("内部 quests/quests.yml 未找到，使用空配置");
            config = new QuestsConfig();
        }
        QuestsConfig external = loadExternalYaml(QUESTS_YML, QuestsConfig.class);
        if (external != null) {
            log.info("已加载外部 quests/quests.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载商店配置（阶段 10）。
     */
    public ShopConfig loadShopConfig() {
        ShopConfig config = loadInternalYaml(SHOP_YML, ShopConfig.class);
        if (config == null) {
            config = new ShopConfig();
            log.warn("内部 shop/shop.yml 未找到，使用空商店配置");
        }
        ShopConfig external = loadExternalYaml(SHOP_YML, ShopConfig.class);
        if (external != null) {
            log.info("已加载外部 shop/shop.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载随机事件配置（阶段 10）。
     */
    public RandomEventsConfig loadRandomEventsConfig() {
        RandomEventsConfig config = loadInternalYaml(RANDOM_EVENTS_YML, RandomEventsConfig.class);
        if (config == null) {
            config = new RandomEventsConfig();
            log.warn("内部 events/random-events.yml 未找到，使用空随机事件配置");
        }
        RandomEventsConfig external = loadExternalYaml(RANDOM_EVENTS_YML, RandomEventsConfig.class);
        if (external != null) {
            log.info("已加载外部 events/random-events.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载推荐 Build 配置（阶段 10）。
     */
    public BuildRecommendationConfig loadBuildRecommendationsConfig() {
        BuildRecommendationConfig config = loadInternalYaml(BUILD_RECOMMENDATIONS_YML, BuildRecommendationConfig.class);
        if (config == null) {
            config = new BuildRecommendationConfig();
            log.warn("内部 builds/build-recommendations.yml 未找到，使用空推荐 Build 配置");
        }
        BuildRecommendationConfig external = loadExternalYaml(BUILD_RECOMMENDATIONS_YML, BuildRecommendationConfig.class);
        if (external != null) {
            log.info("已加载外部 builds/build-recommendations.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载成就配置（阶段 11）。
     */
    public AchievementsConfig loadAchievementsConfig() {
        AchievementsConfig config = loadInternalYaml(ACHIEVEMENTS_YML, AchievementsConfig.class);
        if (config == null) {
            config = new AchievementsConfig();
            log.warn("内部 achievements/achievements.yml 未找到，使用空成就配置");
        }
        AchievementsConfig external = loadExternalYaml(ACHIEVEMENTS_YML, AchievementsConfig.class);
        if (external != null) {
            log.info("已加载外部 achievements/achievements.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 加载 Boss 挑战目标配置（阶段 11）。
     */
    public BossChallengesConfig loadBossChallengesConfig() {
        BossChallengesConfig config = loadInternalYaml(BOSS_CHALLENGES_YML, BossChallengesConfig.class);
        if (config == null) {
            config = new BossChallengesConfig();
            log.warn("内部 bosses/boss-challenges.yml 未找到，使用空 Boss 挑战配置");
        }
        BossChallengesConfig external = loadExternalYaml(BOSS_CHALLENGES_YML, BossChallengesConfig.class);
        if (external != null) {
            log.info("已加载外部 bosses/boss-challenges.yml 覆盖内部配置");
            config = external;
        }
        return config;
    }

    /**
     * 从 JAR 内部 classpath:game-config/ 加载 YAML。
     */
    private <T> T loadInternalYaml(String fileName, Class<T> type) {
        String resourcePath = INTERNAL_CONFIG_PREFIX + fileName;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream is = resource.getInputStream()) {
                return yamlMapper.readValue(is, type);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取内部配置失败: " + resourcePath, e);
        }
    }

    /**
     * 从外部配置目录加载 YAML（相同文件名覆盖内部）。
     */
    private <T> T loadExternalYaml(String fileName, Class<T> type) {
        String configDir = gameProperties.getConfigDir();
        if (configDir == null || configDir.isBlank()) {
            return null;
        }
        Path externalPath = Path.of(configDir, fileName);
        if (!Files.exists(externalPath)) {
            return null;
        }
        try (InputStream is = Files.newInputStream(externalPath)) {
            return yamlMapper.readValue(is, type);
        } catch (IOException e) {
            throw new IllegalStateException("读取外部配置失败: " + externalPath, e);
        }
    }
}
