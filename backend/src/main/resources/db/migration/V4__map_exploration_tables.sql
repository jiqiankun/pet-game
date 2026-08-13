-- ============================================================
-- 宠物精灵游戏 - 阶段 6：地图探索与区域系统玩家状态表
-- ============================================================
-- 内容配置（区域/营地/采集/宝箱）在 YAML，本表只存玩家侧状态引用（ID）。
-- ============================================================

-- 区域解锁状态（需求 §69：主线顺序基本固定；AUTO 解锁懒写入）
CREATE TABLE player_region_unlock (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    region_id VARCHAR(64) NOT NULL COMMENT '区域 ID（引用配置）',
    unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
    PRIMARY KEY (save_id, region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域解锁状态';

-- 营地激活状态（需求 §75：激活后可在已激活营地间免费传送）
CREATE TABLE player_camp_activation (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    camp_id VARCHAR(64) NOT NULL COMMENT '营地 ID（引用配置）',
    activated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '激活时间',
    PRIMARY KEY (save_id, camp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营地激活状态';

-- 隐藏宝箱消耗记录（需求 §73：隐藏宝箱一次性）
CREATE TABLE player_chest_loot (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    chest_id VARCHAR(64) NOT NULL COMMENT '宝箱 ID（引用配置）',
    looted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开启时间',
    PRIMARY KEY (save_id, chest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隐藏宝箱消耗记录';

-- 地图访问会话（需求 §76：离开区域重新进入/营地休息触发刷新；不使用现实时间）
-- 每次进入区域生成新 session_id，采集记录随会话失效即视为刷新。
CREATE TABLE player_map_session (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    map_id VARCHAR(64) NOT NULL COMMENT '区域 ID（引用配置）',
    session_id VARCHAR(64) NOT NULL COMMENT '本次访问会话 ID',
    entered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '进入时间',
    PRIMARY KEY (save_id, map_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地图访问会话';

-- 普通采集点本次访问消耗记录（需求 §73：重进区域刷新，单次访问内一次性）
CREATE TABLE player_gather_used (
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    gather_id VARCHAR(64) NOT NULL COMMENT '采集点 ID（引用配置）',
    session_id VARCHAR(64) NOT NULL COMMENT '所属访问会话',
    gathered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    PRIMARY KEY (save_id, gather_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集点本次访问消耗记录';
