package com.petgame.developer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petgame.developer.DevOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开发者操作日志 Mapper（阶段 14）。
 * <p>
 * 注意：必须位于 *.mapper 子包，与其它 Mapper 一致，
 * 否则不会被 @MapperScan("com.petgame.**.mapper") 扫描注册。
 */
@Mapper
public interface DevOperationLogMapper extends BaseMapper<DevOperationLogEntity> {
}
