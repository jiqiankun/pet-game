package com.petgame.save.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 游戏设置（game_setting 表，V2 建表）。
 * <p>
 * 阶段 14 存档备份纳入全量快照；当前业务未写此表，导入时按快照恢复。
 */
@Data
@TableName("game_setting")
public class GameSettingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String saveId;
    private String settingKey;
    private String settingValue;
}