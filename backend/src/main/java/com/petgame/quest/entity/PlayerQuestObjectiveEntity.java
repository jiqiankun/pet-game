package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 任务目标进度（阶段 9，复合主键 save_id + quest_id + objective_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_quest_objective")
public class PlayerQuestObjectiveEntity {
    private String saveId;
    private String questId;
    private String objectiveId;
    private Integer progress;
    private Integer targetCount;
    private Boolean completed;
}
