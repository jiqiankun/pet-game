package com.petgame.player.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player")
public class PlayerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private Integer saveVersion;
    private String gameVersion;
    private String playerName;
    private String avatarId;
    private Integer gold;
    private Integer expPool;
    private String currentMapId;
    private String mainQuestId;
    private Long playTimeSeconds;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
