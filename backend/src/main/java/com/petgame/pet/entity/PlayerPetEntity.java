package com.petgame.pet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_pet")
public class PlayerPetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private String speciesId;
    private String nickname;
    private Integer level;
    private Integer hpAptitude;
    private Integer strengthAptitude;
    private Integer spiritAptitude;
    private Integer defenseAptitude;
    private Integer resistanceAptitude;
    private Integer speedAptitude;
    private Integer freePointHp;
    private Integer freePointStrength;
    private Integer freePointSpirit;
    private Integer freePointDefense;
    private Integer freePointResistance;
    private Integer freePointSpeed;
    private Integer currentHp;
    private Boolean isStarter;
    private String specialAppearance;
    private Boolean locked;
    private Boolean favorite;
    private String capturedMapId;
    private LocalDateTime capturedAt;
    private Integer battleCount;
    private Integer winCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
