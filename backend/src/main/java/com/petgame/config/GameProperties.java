package com.petgame.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 游戏配置属性。
 * <p>
 * 绑定 application.yml 中 game.* 配置项。
 * 包含版本号体系与外部配置目录路径。
 */
@Data
@Component
@ConfigurationProperties(prefix = "game")
public class GameProperties {

    /**
     * 游戏发布版本号 (gameVersion)。
     */
    private String version = "1.0.0";

    /**
     * 存档数据结构版本 (saveVersion)。
     */
    private int saveVersion = 1;

    /**
     * 游戏配置结构版本 (configVersion)。
     */
    private int configVersion = 1;

    /**
     * 外部游戏配置目录路径。
     * 启动时读取此目录中的 YAML 文件覆盖 JAR 内默认配置（相同 ID 外部覆盖内部）。
     */
    private String configDir = "./config/game";

    /**
     * 开发者模式开关（默认关闭）。
     */
    private boolean developerMode = false;
}
