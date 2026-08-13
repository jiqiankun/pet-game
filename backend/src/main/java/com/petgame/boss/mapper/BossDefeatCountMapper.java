package com.petgame.boss.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.boss.entity.BossDefeatCountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BossDefeatCountMapper extends BaseMapper<BossDefeatCountEntity> {
    @Update("UPDATE player_boss_defeat_count SET defeat_count = defeat_count + #{delta} " +
            "WHERE save_id = #{saveId} AND boss_id = #{bossId} AND difficulty = #{difficulty}")
    int incrementDefeatCount(@Param("saveId") String saveId, @Param("bossId") String bossId,
                             @Param("difficulty") String difficulty, @Param("delta") int delta);
}
