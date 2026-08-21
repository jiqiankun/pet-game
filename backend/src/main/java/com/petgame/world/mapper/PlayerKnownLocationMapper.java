package com.petgame.world.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.world.entity.PlayerKnownLocationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerKnownLocationMapper extends BaseMapper<PlayerKnownLocationEntity> {
}