package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 采集点本次访问消耗记录（阶段 6，复合主键 save_id + gather_id + session_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_gather_used")
public class PlayerGatherUsedEntity {
    private String saveId;
    private String gatherId;
    private String sessionId;
    private LocalDateTime gatheredAt;
}
