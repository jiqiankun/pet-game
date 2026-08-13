-- ============================================================
-- V5：Boss 系统进度表（阶段 7）
-- ============================================================

-- Boss 击败次数（按 boss + 难度分别统计）
CREATE TABLE player_boss_defeat_count (
  save_id VARCHAR(64) NOT NULL,
  boss_id VARCHAR(64) NOT NULL,
  difficulty VARCHAR(16) NOT NULL,
  defeat_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (save_id, boss_id, difficulty)
);

-- Boss 难度解锁（首通后解锁下一难度）
CREATE TABLE player_boss_difficulty_unlock (
  save_id VARCHAR(64) NOT NULL,
  boss_id VARCHAR(64) NOT NULL,
  difficulty VARCHAR(16) NOT NULL,
  unlocked_at DATETIME NOT NULL,
  PRIMARY KEY (save_id, boss_id, difficulty)
);

-- Boss 幸运值（每 Boss 独立，不同难度共享）
CREATE TABLE player_boss_luck (
  save_id VARCHAR(64) NOT NULL,
  boss_id VARCHAR(64) NOT NULL,
  luck_value INT NOT NULL DEFAULT 0,
  PRIMARY KEY (save_id, boss_id)
);

-- Boss 掉落情报解锁（击败次数达到阈值时自动解锁，或提前获得隐藏物品时立即解锁）
CREATE TABLE player_boss_drop_unlock (
  save_id VARCHAR(64) NOT NULL,
  boss_id VARCHAR(64) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  unlocked_at DATETIME NOT NULL,
  PRIMARY KEY (save_id, boss_id, rarity)
);

-- Boss 手动通关记录（自动战斗解锁校验：只有手动击败过才允许自动）
CREATE TABLE player_boss_manual_clear (
  save_id VARCHAR(64) NOT NULL,
  boss_id VARCHAR(64) NOT NULL,
  difficulty VARCHAR(16) NOT NULL,
  cleared_at DATETIME NOT NULL,
  PRIMARY KEY (save_id, boss_id, difficulty)
);
