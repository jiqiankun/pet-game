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
    /** 捕获等级（初始宠物 = 1）。 */
    private Integer capturedLevel;
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
    /** 六维个体基础浮动（捕获时固化，初始宠物 = 0）。 */
    private Integer baseHpOffset;
    private Integer baseStrengthOffset;
    private Integer baseSpiritOffset;
    private Integer baseDefenseOffset;
    private Integer baseResistanceOffset;
    private Integer baseSpeedOffset;
    private Integer battleCount;
    private Integer winCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
