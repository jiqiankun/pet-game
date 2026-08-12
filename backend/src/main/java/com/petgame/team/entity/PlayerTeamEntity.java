package com.petgame.team.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_team")
public class PlayerTeamEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private String name;
    private Integer slot;
    private Boolean isActive;
}
