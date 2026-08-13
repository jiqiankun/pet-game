package com.petgame.boss.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.boss.entity.BossLuckEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BossLuckMapper extends BaseMapper<BossLuckEntity> {
    @Update("UPDATE player_boss_luck SET luck_value = luck_value + #{delta} " +
            "WHERE save_id = #{saveId} AND boss_id = #{bossId}")
    int incrementLuck(@Param("saveId") String saveId, @Param("bossId") String bossId,
                      @Param("delta") int delta);
}
