package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 地图访问会话（阶段 6，复合主键 save_id + map_id；每次进入区域更新 session_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_map_session")
public class PlayerMapSessionEntity {
    private String saveId;
    private String mapId;
    private String sessionId;
    private LocalDateTime enteredAt;
}
