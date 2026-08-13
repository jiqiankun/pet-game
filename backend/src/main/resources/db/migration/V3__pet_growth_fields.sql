-- ============================================================
-- 宠物精灵游戏 - 阶段 4：养成与队伍闭环 - 字段补全
-- ============================================================
-- 补建技术方案 §35 要求但阶段 2 漏建的字段：
--   1. captured_level：捕获等级（放生礼物、履历展示依赖，初始宠物 = 1）
--   2. base_*_offset：六维个体基础浮动固化值（捕获时围绕种族标准 ±5% 生成，
--      初始宠物不浮动，全部 = 0）
-- 阶段 4 成长系统正式化面板属性公式（需求 §9/§10/§12）依赖个体浮动可固化。
-- ============================================================

ALTER TABLE player_pet
    ADD COLUMN captured_level INT NOT NULL DEFAULT 1 COMMENT '捕获等级' AFTER level,
    ADD COLUMN base_hp_offset INT NOT NULL DEFAULT 0 COMMENT '生命个体浮动（捕获时固化）' AFTER captured_at,
    ADD COLUMN base_strength_offset INT NOT NULL DEFAULT 0 COMMENT '力量个体浮动' AFTER base_hp_offset,
    ADD COLUMN base_spirit_offset INT NOT NULL DEFAULT 0 COMMENT '灵力个体浮动' AFTER base_strength_offset,
    ADD COLUMN base_defense_offset INT NOT NULL DEFAULT 0 COMMENT '防御个体浮动' AFTER base_spirit_offset,
    ADD COLUMN base_resistance_offset INT NOT NULL DEFAULT 0 COMMENT '抗性个体浮动' AFTER base_defense_offset,
    ADD COLUMN base_speed_offset INT NOT NULL DEFAULT 0 COMMENT '速度个体浮动' AFTER base_resistance_offset;

-- 已存在的初始宠物 captured_level 回填为 1（DEFAULT 已覆盖，此处显式声明语义）
UPDATE player_pet SET captured_level = 1 WHERE captured_level IS NULL;
