-- ============================================================
-- 宠物精灵游戏 - 阶段 2：玩家存档基础表
-- ============================================================
-- 包含：玩家主表、玩家宠物、宠物技能、队伍、队伍成员、背包、游戏设置
-- ============================================================

-- 玩家主表
CREATE TABLE player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL COMMENT '存档唯一标识',
    save_version INT NOT NULL DEFAULT 1 COMMENT '存档数据结构版本',
    game_version VARCHAR(32) NOT NULL DEFAULT '1.0.0' COMMENT '创建时游戏版本',
    player_name VARCHAR(32) NOT NULL COMMENT '玩家名称',
    avatar_id VARCHAR(32) NOT NULL DEFAULT 'AVATAR_DEFAULT' COMMENT '预设头像/形象 ID',
    gold INT NOT NULL DEFAULT 0 COMMENT '金币',
    exp_pool INT NOT NULL DEFAULT 0 COMMENT '公共经验池',
    current_map_id VARCHAR(64) NOT NULL DEFAULT 'MAP_START_VILLAGE' COMMENT '当前地图 ID',
    main_quest_id VARCHAR(64) COMMENT '当前主线任务 ID',
    play_time_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '累计游玩时间（秒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_player_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家存档主表';

-- 玩家宠物
CREATE TABLE player_pet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL COMMENT '所属存档',
    species_id VARCHAR(64) NOT NULL COMMENT '种族 ID（引用配置）',
    nickname VARCHAR(32) COMMENT '昵称（null 则显示种族名）',
    level INT NOT NULL DEFAULT 1,
    hp_aptitude INT NOT NULL COMMENT '生命资质 0~100',
    strength_aptitude INT NOT NULL COMMENT '力量资质',
    spirit_aptitude INT NOT NULL COMMENT '灵力资质',
    defense_aptitude INT NOT NULL COMMENT '防御资质',
    resistance_aptitude INT NOT NULL COMMENT '抗性资质',
    speed_aptitude INT NOT NULL COMMENT '速度资质',
    free_point_hp INT NOT NULL DEFAULT 0 COMMENT '自由点-生命',
    free_point_strength INT NOT NULL DEFAULT 0 COMMENT '自由点-力量',
    free_point_spirit INT NOT NULL DEFAULT 0 COMMENT '自由点-灵力',
    free_point_defense INT NOT NULL DEFAULT 0 COMMENT '自由点-防御',
    free_point_resistance INT NOT NULL DEFAULT 0 COMMENT '自由点-抗性',
    free_point_speed INT NOT NULL DEFAULT 0 COMMENT '自由点-速度',
    current_hp INT NOT NULL COMMENT '当前 HP',
    is_starter BOOLEAN NOT NULL DEFAULT FALSE COMMENT '初始伙伴纪念标记',
    special_appearance VARCHAR(32) COMMENT '特殊外观 ID',
    locked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '锁定（防误放生）',
    favorite BOOLEAN NOT NULL DEFAULT FALSE COMMENT '收藏',
    captured_map_id VARCHAR(64) COMMENT '捕获地点',
    captured_at DATETIME COMMENT '捕获时间',
    battle_count INT NOT NULL DEFAULT 0,
    win_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pet_save_id (save_id),
    INDEX idx_pet_species_id (species_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家宠物';

-- 玩家宠物技能
CREATE TABLE player_pet_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pet_id BIGINT NOT NULL COMMENT '所属宠物',
    skill_id VARCHAR(64) NOT NULL COMMENT '技能 ID（引用配置）',
    source_type VARCHAR(32) NOT NULL DEFAULT 'LEVEL_UP' COMMENT '来源：LEVEL_UP/RARE/SKILL_BOOK',
    slot INT COMMENT '装备槽位 1~4，null 表示未装备',
    learned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_pet_id (pet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家宠物技能';

-- 玩家队伍
CREATE TABLE player_team (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    name VARCHAR(32) NOT NULL COMMENT '队伍名称',
    slot INT NOT NULL COMMENT '预设编号 1~5',
    is_active BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否当前激活',
    INDEX idx_team_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家队伍';

-- 队伍成员
CREATE TABLE player_team_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    pet_id BIGINT NOT NULL,
    position INT NOT NULL COMMENT '位置 1~6（1-3 首发，4-6 候补）',
    INDEX idx_member_team_id (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队伍成员';

-- 玩家背包
CREATE TABLE player_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NOT NULL COMMENT '道具 ID（引用配置）',
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE INDEX idx_inv_save_item (save_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家背包';

-- 游戏设置
CREATE TABLE game_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    UNIQUE INDEX idx_setting_save_key (save_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏设置';
