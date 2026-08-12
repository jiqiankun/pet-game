package com.petgame.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_inventory")
public class PlayerInventoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private String itemId;
    private Integer quantity;
}
