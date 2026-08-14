package com.petgame.save;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存档文件清单（导出 / 导入校验用）。
 * <p>
 * 三者职责分离：gameVersion 游戏发布版本、saveVersion 存档数据结构版本、
 * configVersion 配置结构版本（本清单不含配置，仅用于版本命名与提示）。
 */
@Data
public class SaveManifest {

    /** 游戏发布版本（gameVersion）。 */
    private String gameVersion;

    /** 存档数据结构版本（saveVersion），导入时校验兼容性。 */
    private int saveVersion;

    /** 导出时间。 */
    private LocalDateTime exportedAt;

    /** 源玩家名称（便于识别备份）。 */
    private String playerName;
}