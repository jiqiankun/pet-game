package com.petgame.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.map.entity.PlayerMapSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerMapSessionMapper extends BaseMapper<PlayerMapSessionEntity> {
}
