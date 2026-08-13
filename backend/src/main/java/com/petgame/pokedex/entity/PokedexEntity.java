package com.petgame.pokedex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图鉴种族研究进度（阶段 8，复合主键 save_id + species_id）。
 * <p>
 * 种族共享，非个体。记录研究值、发现/捕获状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_pokedex")
public class PokedexEntity {
    private String saveId;
    private String speciesId;
    private Integer researchPoints;
    private Boolean seen;
    private Boolean caught;
    private LocalDateTime firstSeenAt;
    private LocalDateTime firstCaughtAt;
}
