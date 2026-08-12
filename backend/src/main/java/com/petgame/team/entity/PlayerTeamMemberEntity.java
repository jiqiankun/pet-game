package com.petgame.team.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_team_member")
public class PlayerTeamMemberEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long petId;
    private Integer position;
}
