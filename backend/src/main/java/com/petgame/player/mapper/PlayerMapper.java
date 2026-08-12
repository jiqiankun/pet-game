package com.petgame.player.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.player.entity.PlayerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerMapper extends BaseMapper<PlayerEntity> {
}
