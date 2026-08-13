package com.petgame.pet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 玩家宠物技能实体（player_pet_skill 表）。
 * <p>
 * 记录玩家某只宠物已学习的技能及装备槽位（需求 §23 等级解锁、§24 装配）。
 * 最多装备 4 个主动技能（slot 1~4，null 表示已学习未装备）。
 * 被动技能不进表，运行时按种族配置自动生效。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_pet_skill")
public class PlayerPetSkillEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属宠物 ID。 */
    private Long petId;

    /** 技能 ID（引用 skills 配置）。 */
    private String skillId;

    /** 来源：LEVEL_UP 等级解锁 / RARE 稀有掉落 / SKILL_BOOK 技能书（阶段 4 仅 LEVEL_UP）。 */
    private String sourceType;

    /** 装备槽位 1~4，null 表示已学习但未装备。 */
    private Integer slot;

    /** 学习时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime learnedAt;
}
