package com.petgame.boss.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Boss 首次遭遇快照（阶段 13）。 */
@Data
@TableName("player_boss_encounter_snapshot")
public class BossEncounterSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private String bossId;
    private String bossDifficulty;
    private String gameDifficulty;
    private Integer generatedLevel;
    private Integer playerLevelCap;
    private Integer bossAiLevel;
    private Long randomSeed;
    private Integer snapshotVersion;
    private String rosterJson;
    private Boolean locked;
    private Boolean defeated;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
