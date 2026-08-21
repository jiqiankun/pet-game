package com.petgame.world.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 玩家知识（PlayerKnowledge，阶段 2）：已发现 / 已解锁的世界节点记录。
 * <p>
 * locationType 取值：REGION / MAP / CONNECTION / LANDMARK / SHORTCUT。
 * <ul>
 *   <li>REGION / MAP：已发现的地图（普通地图进入即发现）。</li>
 *   <li>CONNECTION：已发现的连接（隐藏路线发现后才写入，普通连接进入即发现）。</li>
 *   <li>LANDMARK：已发现的地标（阶段 4 追踪目标基础）。</li>
 *   <li>SHORTCUT：已解锁的捷径（OPEN_SHORTCUT 地图变化触发），区别于「已发现」。</li>
 * </ul>
 * 服务端只依据本表过滤图谱响应，隐藏连接未发现前不会下发。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("player_known_location")
public class PlayerKnownLocationEntity {

    /** 所属存档。 */
    private String saveId;

    /** 节点类型：REGION/MAP/CONNECTION/LANDMARK/SHORTCUT。 */
    private String locationType;

    /** 节点 ID（区域/地图/连接/地标/快捷连接 ID）。 */
    private String locationId;

    /** 记录时间。 */
    private LocalDateTime knownAt;
}