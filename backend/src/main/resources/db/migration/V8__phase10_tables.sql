-- ============================================================
-- V8：阶段 10 新增表（埋伏记录 + 随机事件去重）
-- ============================================================
-- player_ambush_triggered：一次性埋伏触发记录
-- player_random_event_used：每会话事件不重复记录
-- ============================================================

-- 一次性埋伏触发记录（save_id + ambush_id，埋伏触发后写入，不再重复）
CREATE TABLE IF NOT EXISTS player_ambush_triggered (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    ambush_id VARCHAR(64) NOT NULL,
    triggered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_ambush (save_id, ambush_id),
    INDEX idx_save_id (save_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 随机事件已使用记录（save_id + event_id + session_id，同会话不重复触发同一事件）
CREATE TABLE IF NOT EXISTS player_random_event_used (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_save_event_session (save_id, event_id, session_id),
    INDEX idx_save_session (save_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
