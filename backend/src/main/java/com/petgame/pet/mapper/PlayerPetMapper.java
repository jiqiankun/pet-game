package com.petgame.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.pet.entity.PlayerPetEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerPetMapper extends BaseMapper<PlayerPetEntity> {
}
