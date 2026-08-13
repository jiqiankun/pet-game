package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** Boss 幸运值（阶段 7，复合主键 save_id + boss_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_boss_luck")
public class BossLuckEntity {
    private String saveId;
    private String bossId;
    private Integer luckValue;
}
