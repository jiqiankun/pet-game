package com.petgame.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerInventoryMapper extends BaseMapper<PlayerInventoryEntity> {
}
