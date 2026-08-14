-- ============================================================
-- V11：阶段 13 全局难度与 Boss 遭遇快照
-- ============================================================

ALTER TABLE player
    ADD COLUMN game_difficulty VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '全局难度';

CREATE TABLE IF NOT EXISTS player_boss_encounter_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    boss_id VARCHAR(64) NOT NULL,
    boss_difficulty VARCHAR(32) NOT NULL,
    game_difficulty VARCHAR(16) NOT NULL,
    generated_level INT NOT NULL,
    player_level_cap INT NOT NULL,
    boss_ai_level INT NOT NULL DEFAULT 1,
    random_seed BIGINT NOT NULL,
    snapshot_version INT NOT NULL DEFAULT 1,
    roster_json MEDIUMTEXT NOT NULL,
    locked TINYINT(1) NOT NULL DEFAULT 1,
    defeated TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_boss_difficulty (save_id, boss_id, boss_difficulty),
    INDEX idx_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
