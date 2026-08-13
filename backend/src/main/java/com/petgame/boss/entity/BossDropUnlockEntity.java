package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** Boss 掉落情报解锁（阶段 7，复合主键 save_id + boss_id + rarity）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_boss_drop_unlock")
public class BossDropUnlockEntity {
    private String saveId;
    private String bossId;
    private String rarity;
    private LocalDateTime unlockedAt;
}
