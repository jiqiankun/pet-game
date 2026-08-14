-- ============================================================
-- V10：阶段 11 新增表（成就 + 玩家统计 + Boss 挑战目标）与宠物履历字段
-- ============================================================
-- player_achievement：已解锁成就（save_id + achievement_id）
-- player_statistic：玩家统计键值（save_id + stat_key + stat_value）
-- player_boss_challenge：已完成的 Boss 挑战目标（save_id + boss_id + challenge_id）
-- player_pet 新增个人履历字段（需求 §113）
-- ============================================================

-- 已解锁成就记录
CREATE TABLE IF NOT EXISTS player_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    achievement_id VARCHAR(64) NOT NULL,
    unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_achievement (save_id, achievement_id),
    INDEX idx_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 玩家统计键值（需求 §112）
CREATE TABLE IF NOT EXISTS player_statistic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    stat_key VARCHAR(128) NOT NULL,
    stat_value BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_stat (save_id, stat_key),
    INDEX idx_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 已完成的 Boss 挑战目标记录
CREATE TABLE IF NOT EXISTS player_boss_challenge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    boss_id VARCHAR(64) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_boss_challenge (save_id, boss_id, challenge_id),
    INDEX idx_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- player_pet 新增个人履历字段（需求 §113）
ALTER TABLE player_pet
    ADD COLUMN kill_count INT NOT NULL DEFAULT 0 COMMENT '累计击败数量',
    ADD COLUMN boss_battle_count INT NOT NULL DEFAULT 0 COMMENT 'Boss 参与次数',
    ADD COLUMN boss_win_count INT NOT NULL DEFAULT 0 COMMENT 'Boss 胜利次数',
    ADD COLUMN total_damage BIGINT NOT NULL DEFAULT 0 COMMENT '累计造成伤害',
    ADD COLUMN total_damage_taken BIGINT NOT NULL DEFAULT 0 COMMENT '累计承受伤害',
    ADD COLUMN total_heal BIGINT NOT NULL DEFAULT 0 COMMENT '累计治疗量',
    ADD COLUMN capture_assist_count INT NOT NULL DEFAULT 0 COMMENT '捕捉辅助次数';