package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** Boss 击败次数（阶段 7，复合主键 save_id + boss_id + difficulty）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_boss_defeat_count")
public class BossDefeatCountEntity {
    private String saveId;
    private String bossId;
    private String difficulty;
    private Integer defeatCount;
}
