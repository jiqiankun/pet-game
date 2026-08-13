package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 任务状态（阶段 9，复合主键 save_id + quest_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_quest")
public class PlayerQuestEntity {
    private String saveId;
    private String questId;
    private String status; // AVAILABLE / ACTIVE / COMPLETED
    private Integer currentObjective;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
}
