-- ============================================================
-- V12：阶段 14 开发者工具
-- 新增开发者操作日志表（记录开发者高风险数据操作，便于审计与回查）
-- ============================================================

CREATE TABLE IF NOT EXISTS dev_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    save_id VARCHAR(64) DEFAULT NULL COMMENT '存档 ID（单存档游戏，可为空）',
    action VARCHAR(64) NOT NULL COMMENT '操作类型（如 dev.addGold / dev.addPet）',
    detail VARCHAR(512) DEFAULT NULL COMMENT '操作详情（JSON 或可读文本）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_save_id (save_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;