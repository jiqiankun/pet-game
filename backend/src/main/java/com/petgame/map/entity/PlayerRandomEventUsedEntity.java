package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 随机事件已使用记录（阶段 10，save_id + event_id + session_id 唯一）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_random_event_used")
public class PlayerRandomEventUsedEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String saveId;
    private String eventId;
    private String sessionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime usedAt;
}
