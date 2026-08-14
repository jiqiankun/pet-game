package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/** 已完成的 Boss 挑战目标记录（阶段 11；复合唯一键 save_id + boss_id + challenge_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_boss_challenge")
public class PlayerBossChallengeEntity {
    private String saveId;
    private String bossId;
    private String challengeId;
    private LocalDateTime completedAt;
}