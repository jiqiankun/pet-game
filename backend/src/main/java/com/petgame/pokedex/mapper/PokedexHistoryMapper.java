package com.petgame.pokedex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.pokedex.entity.PokedexHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/** 图鉴种族历史记录 Mapper（阶段 8）。 */
@Mapper
public interface PokedexHistoryMapper extends BaseMapper<PokedexHistoryEntity> {
}
