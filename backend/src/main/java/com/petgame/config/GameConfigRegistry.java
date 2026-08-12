package com.petgame.config;

import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

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

    /** 属性 ID → 属性配置 的快速索引。 */
    private Map<String, GameElementConfig> elementIndex;

    /** 克制关系快速索引：key = "ATTACKER|DEFENDER" → true 表示克制。 */
    private Set<String> advantageIndex;

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

        // 校验
        validator.validate(systemRules, elementsConfig, initialPetsConfig);

        // 构建索引
        buildElementIndex();
        buildAdvantageIndex();

        log.info("游戏配置加载完成：{} 种属性，{} 条克制关系，{} 个初始宠物选项",
                elementsConfig.getElements().size(),
                elementsConfig.getAdvantages() != null ? elementsConfig.getAdvantages().size() : 0,
                initialPetsConfig.getInitialPets().size());
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
}
