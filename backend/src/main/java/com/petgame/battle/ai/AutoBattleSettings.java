package com.petgame.battle.ai;

import lombok.Data;

/**
 * 战斗级自动战斗设置（阶段 10，内存态，随战斗创建与销毁）。
 * <p>
 * 玩家偏好（策略/开关/阈值）持久化在 player 表；开战或开启自动时
 * 从偏好载入本对象。enabled=false 或对象为 null 时完全走手动链路。
 */
@Data
public class AutoBattleSettings {

    /** 本战斗是否启用自动决策。 */
    private boolean enabled;

    /** 策略：BALANCED / AGGRESSIVE / DEFENSIVE / CAPTURE。 */
    private String strategy = "BALANCED";

    /** 自动换宠开关。 */
    private boolean autoSwitch = true;

    /** 自动换宠 HP 阈值（0~1）。 */
    private double autoSwitchHpThreshold = 0.25;

    /** 自动使用恢复道具开关（默认关闭）。 */
    private boolean autoUseRecoveryItem = false;

    /** 自动恢复道具 HP 阈值（0~1）。 */
    private double autoRecoveryHpThreshold = 0.35;

    /** 自动复苏开关（默认关闭）。 */
    private boolean autoRevive = false;

    /** CAPTURE 策略指定的捕捉目标单位 ID（null = 自动选择最低 HP 可捕捉敌人）。 */
    private String captureTargetId;
}
