package com.petgame.quest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.quest.entity.PlayerQuestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerQuestMapper extends BaseMapper<PlayerQuestEntity> {
}
