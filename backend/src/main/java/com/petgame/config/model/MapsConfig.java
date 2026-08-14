package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图与区域配置（阶段 6）。
 * <p>
 * 数据来源 game-config/maps/maps.yml。只存游戏内容配置：
 * 区域元数据、出口连接、营地、采集点、宝箱与奖励；
 * 玩家侧状态（解锁、营地激活、宝箱消耗、本次访问记录）全部存 MySQL。
 * <p>
 * 地图差异通过 Tiled JSON（前端表现）+ 本配置（业务内容）解决，
 * 不为每张地图创建独立场景类（规划阶段 6 核心业务规则）。
 */
@Data
public class MapsConfig {

    private int configVersion = 1;

    /** 探索全局规则（战败提示等）。 */
    private ExplorationConfig exploration = new ExplorationConfig();

    /** 区域列表（含起始据点与主要区域；planned=true 为结构预留）。 */
    private List<RegionConfig> regions = new ArrayList<>();

    /** 探索全局规则。 */
    @Data
    public static class ExplorationConfig {
        /** 战败轻度嘲讽式提示池（需求 §44，随机取一条）。 */
        private List<String> defeatMessages = new ArrayList<>();
    }

    /** 区域配置。 */
    @Data
    public static class RegionConfig {
        /** 区域 ID（同 player.current_map_id，如 MAP_AREA_MEADOW）。 */
        private String id;
        /** 区域名称。 */
        private String name;
        /** 区域类型：BASE 起始据点 / AREA 主要区域。 */
        private String type;
        /** 推荐等级描述（大地图展示，如 3~8）。 */
        private String recommendedLevel;
        /** 敌方数值推荐等级（阶段 13，野外缩放基准）。 */
        private int recommendedEnemyLevel = 1;
        /** 敌方数值最低等级（阶段 13，野外缩放硬下界）。 */
        private int minEnemyLevel = 1;
        /** 敌方数值最高等级（阶段 13，野外缩放硬上界）。 */
        private int maxEnemyLevel = 1;
        /**
         * 解锁方式：AUTO 默认解锁 / BOSS 击败区域 Boss 解锁（阶段 7）/
         * QUEST 主线任务解锁（阶段 9）。
         */
        private String unlockType;
        /** 结构预留标记（true = 分支/最终区域占位，阶段 9 开放，本阶段不实装）。 */
        private boolean planned;
        /** Tiled 地图资源文件名（前端 public/assets/maps/{mapFile}.json）。 */
        private String mapFile;
        /** 默认出生点对象 ID（对应 Tiled 对象层 spawn 对象）。 */
        private String spawnObjectId;
        /** 本区域允许发起野生遭遇的刷新组（地图对象声明的 groupId 必须在此列表中）。 */
        private List<String> encounterGroups = new ArrayList<>();
        /** 区域出口列表。 */
        private List<ExitConfig> exits = new ArrayList<>();
        /** 区域营地列表。 */
        private List<CampConfig> camps = new ArrayList<>();
        /** 普通采集点列表（离开区域重进后刷新）。 */
        private List<GatherPointConfig> gathers = new ArrayList<>();
        /** 隐藏宝箱列表（一次性）。 */
        private List<ChestConfig> chests = new ArrayList<>();
        /** 埋伏点列表（阶段 10，需求 §57）。 */
        private List<AmbushSpotConfig> ambushSpots = new ArrayList<>();
    }

    /** 埋伏点配置（阶段 10，需求 §57）。 */
    @Data
    public static class AmbushSpotConfig {
        /** 埋伏 ID（唯一标识，用于一次性记录）。 */
        private String ambushId;
        /** 使用的遭遇组 ID。 */
        private String encounterGroupId;
        /** 触发概率。 */
        private double chance = 0.10;
        /** 是否一次性（触发后记录到数据库，不再重复）。 */
        private boolean oneTime = false;
    }

    /** 出口配置（区域间移动，需求 §69）。 */
    @Data
    public static class ExitConfig {
        /** 出口 ID（对应 Tiled 对象层 exit 对象）。 */
        private String exitId;
        /** 出口名称。 */
        private String name;
        /** 目标区域 ID。 */
        private String targetMapId;
        /** 到达目标区域后的入口对象 ID（Tiled 对象）。 */
        private String entryObjectId;
    }

    /** 营地配置（需求 §75：免费恢复 + 激活后可传送）。 */
    @Data
    public static class CampConfig {
        /** 营地 ID（对应 Tiled 对象层 camp 对象）。 */
        private String campId;
        /** 营地名称。 */
        private String name;
        /** 新游戏自动激活（起始据点营地）。 */
        private boolean autoActivate;
    }

    /** 普通采集点配置（需求 §73：离开并重新进入区域后刷新）。 */
    @Data
    public static class GatherPointConfig {
        /** 采集点 ID（对应 Tiled 对象层 gather 对象）。 */
        private String gatherId;
        /** 采集点名称。 */
        private String name;
        /** 道具奖励列表（数量在 qtyMin~qtyMax 间随机）。 */
        private List<RewardEntry> rewards = new ArrayList<>();
        /** 金币奖励下限。 */
        private int goldMin;
        /** 金币奖励上限。 */
        private int goldMax;
    }

    /** 隐藏宝箱配置（需求 §73：一次性）。 */
    @Data
    public static class ChestConfig {
        /** 宝箱 ID（对应 Tiled 对象层 chest 对象）。 */
        private String chestId;
        /** 宝箱名称。 */
        private String name;
        /** 道具奖励列表。 */
        private List<RewardEntry> rewards = new ArrayList<>();
        /** 金币奖励下限。 */
        private int goldMin;
        /** 金币奖励上限。 */
        private int goldMax;
    }

    /** 单条道具奖励条目。 */
    @Data
    public static class RewardEntry {
        /** 道具 ID（引用 items.yml）。 */
        private String itemId;
        /** 数量下限。 */
        private int qtyMin = 1;
        /** 数量上限。 */
        private int qtyMax = 1;
    }
}
