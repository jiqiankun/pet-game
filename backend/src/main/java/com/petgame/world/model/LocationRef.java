package com.petgame.world.model;

import com.petgame.common.BusinessException;
import lombok.Data;

/**
 * LocationRef：世界位置引用（阶段 2）。
 * <p>
 * 统一表达任务 / NPC / Boss / 地图变化 / 导航所使用的世界位置，
 * 由 regionId + mapId + 可选 anchor（出生/入口对象）+ 可选 landmark 组成。
 * <p>
 * 兼容说明（需求阶段 2 §9.3.5）：第一阶段各区域与地图一一对应（regionId == mapId），
 * 旧配置中的 raw ID（如 MAP_AREA_MEADOW）即区域 ID，也是唯一地图 ID，
 * {@link #fromRaw(String)} 会将旧 ID 同时映射为区域与地图 ID，保证旧存档可读；
 * 后续阶段拆分多图时，再以显式 LocationRef 表达 region+map 的层级。
 */
@Data
public class LocationRef {

    /** 区域 ID（必填，顶层归属）。 */
    private final String regionId;

    /** 地图 ID（必填；兼容期默认与 regionId 相同）。 */
    private final String mapId;

    /** 可选：地图内锚点 / 出入口对象 ID（对应 Tiled 对象）。 */
    private String anchorId;

    /** 可选：地标 ID。 */
    private String landmarkId;

    /** 可选：功能对象 ID（NPC / Boss / 采集等，供旧 raw object 引用）。 */
    private String objectId;

    public LocationRef(String regionId, String mapId) {
        this.regionId = regionId;
        this.mapId = mapId;
    }

    public static LocationRef of(String regionId, String mapId) {
        return new LocationRef(regionId, mapId);
    }

    public static LocationRef of(String regionId, String mapId, String anchorId) {
        LocationRef ref = new LocationRef(regionId, mapId);
        ref.setAnchorId(anchorId);
        return ref;
    }

    /**
     * 由旧 raw ID（区域 ID == 地图 ID）构造兼容 LocationRef。
     * 空值抛出 INVALID_LOCATION 稳定错误码。
     */
    public static LocationRef fromRaw(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException("INVALID_LOCATION", "位置引用不能为空");
        }
        return new LocationRef(rawId, rawId);
    }

    /** 解析出处支持限定节点的合法 LocationRef，否则抛出稳定错误码。 */
    public static LocationRef fromRawOrThrow(String rawId, String errorCode) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException(errorCode, "位置引用不能为空");
        }
        return new LocationRef(rawId, rawId);
    }

    /** 兼容期地图 ID：mapId 为空时回退到 regionId。 */
    public String effectiveMapId() {
        return mapId != null && !mapId.isBlank() ? mapId : regionId;
    }

    @Override
    public String toString() {
        return effectiveMapId() + (anchorId != null ? "@" + anchorId : "");
    }
}