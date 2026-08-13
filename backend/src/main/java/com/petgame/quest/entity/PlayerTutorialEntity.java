package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 新手教学进度（阶段 9，复合主键 save_id + step_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_tutorial")
public class PlayerTutorialEntity {
    private String saveId;
    private String stepId;
    private Boolean completed;
    private Boolean skipped;
}
