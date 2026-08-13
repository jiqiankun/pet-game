package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** Boss 难度解锁（阶段 7，复合主键 save_id + boss_id + difficulty）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_boss_difficulty_unlock")
public class BossDifficultyUnlockEntity {
    private String saveId;
    private String bossId;
    private String difficulty;
    private LocalDateTime unlockedAt;
}
