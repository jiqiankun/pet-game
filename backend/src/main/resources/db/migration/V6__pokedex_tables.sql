-- ============================================================
-- V6：图鉴系统进度表（阶段 8）
-- ============================================================

-- 种族研究进度（种族共享，非个体）
CREATE TABLE player_pokedex (
  save_id VARCHAR(64) NOT NULL,
  species_id VARCHAR(64) NOT NULL,
  research_points INT NOT NULL DEFAULT 0,
  seen BOOLEAN NOT NULL DEFAULT FALSE,
  caught BOOLEAN NOT NULL DEFAULT FALSE,
  first_seen_at DATETIME,
  first_caught_at DATETIME,
  PRIMARY KEY (save_id, species_id)
);

-- 种族历史记录（放生不清除）
CREATE TABLE player_pokedex_history (
  save_id VARCHAR(64) NOT NULL,
  species_id VARCHAR(64) NOT NULL,
  total_captures INT NOT NULL DEFAULT 0,
  total_defeats INT NOT NULL DEFAULT 0,
  elite_encounters INT NOT NULL DEFAULT 0,
  special_appearances INT NOT NULL DEFAULT 0,
  best_combined_aptitude INT NOT NULL DEFAULT 0,
  best_hp INT NOT NULL DEFAULT 0,
  best_strength INT NOT NULL DEFAULT 0,
  best_spirit INT NOT NULL DEFAULT 0,
  best_defense INT NOT NULL DEFAULT 0,
  best_resistance INT NOT NULL DEFAULT 0,
  best_speed INT NOT NULL DEFAULT 0,
  discovered_rare_skills TEXT,
  PRIMARY KEY (save_id, species_id)
);
