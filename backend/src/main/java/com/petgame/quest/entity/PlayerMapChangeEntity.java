package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 永久地图变更（阶段 9，复合主键 save_id + change_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_map_change")
public class PlayerMapChangeEntity {
    private String saveId;
    private String changeId;
    private LocalDateTime activatedAt;
}
