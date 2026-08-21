package com.petgame.world.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 玩家世界状态（阶段 2）：每存档一行，保存当前精确探索位置与相机/朝向等动态状态。
 * <p>
 * 只存玩家引用与动态状态，不复制地图名称/描述/推荐等级等配置内容（架构边界）。
 * 存量迁移：旧存档由 {@code player.current_map_id} 推导，坐标无效时运行时落到配置出生锚点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_world_state")
public class PlayerWorldStateEntity {

    /** 所属存档（主键，业务字符串，MyBatis-Plus selectById/updateById 依赖此标记）。 */
    @TableId(type = IdType.INPUT)
    private String saveId;

    /** 当前地图 ID（兼容期 == 区域 ID）。 */
    private String currentMapId;

    /** 当前所在区域 ID。 */
    private String currentRegionId;

    /** 精确 X 坐标（地图坐标；为 null 表示未精确定位，落到出生锚点）。 */
    private Double posX;

    /** 精确 Y 坐标。 */
    private Double posY;

    /** 朝向：UP/DOWN/LEFT/RIGHT。 */
    private String facing;

    /** 可选相机锚点 X。 */
    private Double cameraAnchorX;

    /** 可选相机锚点 Y。 */
    private Double cameraAnchorY;

    /** 最近有效安全点锚点 ID（营地 / 出生点）。 */
    private String nearestSafePoint;

    /** 世界状态版本（内容图谱变更时递增；用于前端判断是否需要刷新投影）。 */
    private Integer worldVersion;

    private LocalDateTime updatedAt;
}