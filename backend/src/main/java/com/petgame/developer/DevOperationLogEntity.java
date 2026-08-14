package com.petgame.developer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开发者操作日志（dev_operation_log 表，V12 建表）。
 * <p>
 * 记录开发者数据操作类高风险操作的执行记录，便于审计与回查。
 * 玩家存档数据分离：本表不属于玩家存档快照（阶段 14）。
 */
@Data
@TableName("dev_operation_log")
public class DevOperationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 存档 ID（单存档游戏，可为空）。 */
    private String saveId;

    /** 操作类型（如 dev.grantGold / dev.addPet / dev.resetPet）。 */
    private String action;

    /** 操作详情（JSON 或可读文本）。 */
    private String detail;

    /** 操作时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}