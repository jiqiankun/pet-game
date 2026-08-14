package com.petgame.achievement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/** 已解锁成就记录（阶段 11；复合唯一键 save_id + achievement_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_achievement")
public class PlayerAchievementEntity {
    private String saveId;
    private String achievementId;
    private LocalDateTime unlockedAt;
}