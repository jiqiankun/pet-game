package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 一次性埋伏触发记录（阶段 10，save_id + ambush_id 唯一）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_ambush_triggered")
public class PlayerAmbushTriggeredEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String saveId;
    private String ambushId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime triggeredAt;
}
