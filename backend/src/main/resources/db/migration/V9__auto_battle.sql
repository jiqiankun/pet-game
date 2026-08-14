-- ============================================================
-- V9：阶段 10 自动战斗策略系统 —— player 表自动战斗偏好字段
-- ============================================================
-- 自动战斗开关本身按战斗存在 BattleContext（内存）；
-- 玩家偏好（策略/开关/阈值）持久化在 player 表。
-- 恢复道具/复苏道具自动使用默认关闭（不静默消耗玩家资源）。
-- ============================================================

ALTER TABLE player
    ADD COLUMN auto_strategy VARCHAR(16) NOT NULL DEFAULT 'BALANCED' COMMENT '自动战斗策略：BALANCED/AGGRESSIVE/DEFENSIVE/CAPTURE',
    ADD COLUMN auto_switch TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自动换宠开关（默认开）',
    ADD COLUMN auto_switch_hp_threshold INT NOT NULL DEFAULT 25 COMMENT '自动换宠 HP 阈值百分比（默认 25）',
    ADD COLUMN auto_use_recovery_item TINYINT(1) NOT NULL DEFAULT 0 COMMENT '自动使用恢复道具开关（默认关）',
    ADD COLUMN auto_recovery_hp_threshold INT NOT NULL DEFAULT 35 COMMENT '自动恢复道具 HP 阈值百分比（默认 35）',
    ADD COLUMN auto_revive TINYINT(1) NOT NULL DEFAULT 0 COMMENT '自动复苏开关（默认关）';
