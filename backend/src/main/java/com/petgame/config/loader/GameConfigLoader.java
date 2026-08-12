package com.petgame.config.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.petgame.config.GameProperties;
import com.petgame.config.model.GameElementsConfig;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.SystemRuleConfig;
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
