package com.petgame.quest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** NPC 对话进度（阶段 9，复合主键 save_id + npc_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_dialogue")
public class PlayerDialogueEntity {
    private String saveId;
    private String npcId;
    private String dialogueNodeId;
    private Integer dialogueCount;
    private LocalDateTime lastSpokenAt;
}
