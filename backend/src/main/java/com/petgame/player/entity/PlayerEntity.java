package com.petgame.player.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player")
public class PlayerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private Integer saveVersion;
    private String gameVersion;
    private String playerName;
    private String avatarId;
    private Integer gold;
    private Integer expPool;
    private String currentMapId;
    private String mainQuestId;
    private Long playTimeSeconds;
    /** 第一阶段通关标记（阶段 9，V7 迁移）。 */
    private Boolean storyCompleted;
    /** 全局游戏难度：NORMAL/ELITE/NIGHTMARE/HELL（阶段 13）。 */
    private String gameDifficulty;
    // ---- 自动战斗偏好（阶段 10，V9 迁移；自动开关本身按战斗存在 BattleContext）----
    /** 自动战斗策略：BALANCED/AGGRESSIVE/DEFENSIVE/CAPTURE（默认 BALANCED）。 */
    private String autoStrategy;
    /** 自动换宠开关（默认开）。 */
    private Boolean autoSwitch;
    /** 自动换宠 HP 阈值百分比（默认 25）。 */
    private Integer autoSwitchHpThreshold;
    /** 自动使用恢复道具开关（默认关，不静默消耗玩家资源）。 */
    private Boolean autoUseRecoveryItem;
    /** 自动恢复道具 HP 阈值百分比（默认 35）。 */
    private Integer autoRecoveryHpThreshold;
    /** 自动复苏开关（默认关）。 */
    private Boolean autoRevive;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
