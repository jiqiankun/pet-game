package com.petgame.statistics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/** 玩家统计键值（阶段 11，需求 §112；复合唯一键 save_id + stat_key）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_statistic")
public class PlayerStatisticEntity {
    private Long id;
    private String saveId;
    private String statKey;
    private Long statValue;
    private LocalDateTime updatedAt;
}