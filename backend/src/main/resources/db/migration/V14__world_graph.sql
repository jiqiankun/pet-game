-- ============================================================
-- 宠物精灵游戏 - 阶段 2：WorldGraph 玩家知识与世界状态
-- ============================================================
-- 内容配置（世界图谱拓扑）在 maps.yml 派生，本迁移只存玩家侧引用与动态状态。
-- 迁移必须兼容既有存档与重复部署：新表有默认/空语义，旧存档运行时由
-- player.current_map_id 推导兼容位置，坐标无效时落到配置出生锚点。
-- ============================================================

-- 玩家世界状态（每存档一行）：
-- 当前地图/区域 + 精确坐标 + 朝向 + 可选相机锚点 + 最近有效安全点 + 世界状态版本
CREATE TABLE player_world_state (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    current_map_id VARCHAR(64) NOT NULL COMMENT '当前地图 ID（兼容期 == 区域 ID）',
    current_region_id VARCHAR(64) NOT NULL COMMENT '当前区域 ID',
    pos_x DOUBLE NULL COMMENT '精确 X 坐标（空=未精确定位，落到出生锚点）',
    pos_y DOUBLE NULL COMMENT '精确 Y 坐标',
    facing VARCHAR(16) NULL COMMENT '朝向 UP/DOWN/LEFT/RIGHT',
    camera_anchor_x DOUBLE NULL COMMENT '可选相机锚点 X',
    camera_anchor_y DOUBLE NULL COMMENT '可选相机锚点 Y',
    nearest_safe_point VARCHAR(64) NULL COMMENT '最近有效安全点锚点 ID',
    world_version INT NOT NULL DEFAULT 1 COMMENT '世界状态版本（图谱变更时递增）',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (save_id),
    KEY idx_world_state_map (current_map_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家世界状态';

-- 玩家知识（已发现/已解锁的世界节点）
-- locationType: REGION / MAP / CONNECTION / LANDMARK / SHORTCUT
CREATE TABLE player_known_location (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    location_type VARCHAR(16) NOT NULL COMMENT '节点类型',
    location_id VARCHAR(64) NOT NULL COMMENT '节点 ID',
    known_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (save_id, location_type, location_id),
    KEY idx_known_location (save_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家知识（已发现/解锁）';