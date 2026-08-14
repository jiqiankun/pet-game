package com.petgame.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.statistics.entity.PlayerStatisticEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlayerStatisticMapper extends BaseMapper<PlayerStatisticEntity> {

    /** 统计键值增量（存在则自增，不存在由上层先插入后调用）。 */
    @Update("UPDATE player_statistic SET stat_value = stat_value + #{delta}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE save_id = #{saveId} AND stat_key = #{statKey}")
    int incrementValue(@Param("saveId") String saveId, @Param("statKey") String statKey,
                       @Param("delta") long delta);
}