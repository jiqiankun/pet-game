package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 隐藏任务触发记录（阶段 9，复合主键 save_id + trigger_key）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_hidden_trigger")
public class PlayerHiddenTriggerEntity {
    private String saveId;
    private String triggerKey;
    private Integer triggerCount;
}
