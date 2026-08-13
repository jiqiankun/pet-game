-- V7：任务系统表（阶段 9）
-- 任务状态、目标进度、NPC 对话、教学进度、永久地图变更、隐藏任务触发记录

-- 1. 任务状态
CREATE TABLE player_quest (
    save_id VARCHAR(64) NOT NULL,
    quest_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/ACTIVE/COMPLETED',
    current_objective INT NOT NULL DEFAULT 0 COMMENT '当前目标序号',
    accepted_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    PRIMARY KEY (save_id, quest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 任务目标进度
CREATE TABLE player_quest_objective (
    save_id VARCHAR(64) NOT NULL,
    quest_id VARCHAR(64) NOT NULL,
    objective_id VARCHAR(64) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    target_count INT NOT NULL DEFAULT 1,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (save_id, quest_id, objective_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. NPC 对话进度
CREATE TABLE player_dialogue (
    save_id VARCHAR(64) NOT NULL,
    npc_id VARCHAR(64) NOT NULL,
    dialogue_node_id VARCHAR(64) DEFAULT NULL,
    dialogue_count INT NOT NULL DEFAULT 0,
    last_spoken_at DATETIME DEFAULT NULL,
    PRIMARY KEY (save_id, npc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 新手教学进度
CREATE TABLE player_tutorial (
    save_id VARCHAR(64) NOT NULL,
    step_id VARCHAR(64) NOT NULL,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    skipped TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (save_id, step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 永久地图变更
CREATE TABLE player_map_change (
    save_id VARCHAR(64) NOT NULL,
    change_id VARCHAR(64) NOT NULL,
    activated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (save_id, change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 隐藏任务触发记录
CREATE TABLE player_hidden_trigger (
    save_id VARCHAR(64) NOT NULL,
    trigger_key VARCHAR(128) NOT NULL COMMENT '触发键（如 LOCATION:mapId 或 DIALOGUE_COUNT:npcId）',
    trigger_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (save_id, trigger_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 玩家存档表新增通关标记
ALTER TABLE player ADD COLUMN story_completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '第一阶段通关标记';
