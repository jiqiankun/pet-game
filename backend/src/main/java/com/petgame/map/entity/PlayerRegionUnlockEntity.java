package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 区域解锁状态（阶段 6，复合主键 save_id + region_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_region_unlock")
public class PlayerRegionUnlockEntity {
    private String saveId;
    private String regionId;
    private LocalDateTime unlockedAt;
}
