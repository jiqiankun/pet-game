-- 阶段 14：新手教学「重置教学提示」能力
-- 增加 reward_granted 标记：奖励（如捕捉教学赠送技能书）仅首次完成发放一次，
-- 重置教学提示后重新完成不重复发放，符合需求「第一次捕捉教学免费赠送」。

ALTER TABLE player_tutorial
    ADD COLUMN reward_granted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '该步骤奖励是否已首次发放';