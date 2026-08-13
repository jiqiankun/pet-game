package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 隐藏宝箱消耗记录（阶段 6，复合主键 save_id + chest_id，一次性）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_chest_loot")
public class PlayerChestLootEntity {
    private String saveId;
    private String chestId;
    private LocalDateTime lootedAt;
}
