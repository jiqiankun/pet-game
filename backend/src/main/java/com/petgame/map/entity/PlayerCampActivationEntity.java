package com.petgame.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/** 营地激活状态（阶段 6，复合主键 save_id + camp_id）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_camp_activation")
public class PlayerCampActivationEntity {
    private String saveId;
    private String campId;
    private LocalDateTime activatedAt;
}
