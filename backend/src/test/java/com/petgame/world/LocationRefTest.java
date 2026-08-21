package com.petgame.world;

import com.petgame.common.BusinessException;
import com.petgame.world.model.LocationRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocationRef 测试（阶段 2）。
 * <p>
 * 验证旧 raw ID（区域 ID == 地图 ID）适配、限定来源与空值拒绝。
 */
class LocationRefTest {

    @Test
    void fromRaw_mapsRegionToMapCompatible() {
        LocationRef ref = LocationRef.fromRaw("MAP_AREA_MEADOW");
        assertEquals("MAP_AREA_MEADOW", ref.getRegionId());
        assertEquals("MAP_AREA_MEADOW", ref.getMapId());
        assertEquals("MAP_AREA_MEADOW", ref.effectiveMapId());
    }

    @Test
    void of_withAnchor() {
        LocationRef ref = LocationRef.of("MAP_AREA_MEADOW", "MAP_AREA_MEADOW", "SPAWN_MEADOW");
        assertEquals("SPAWN_MEADOW", ref.getAnchorId());
        assertEquals("MAP_AREA_MEADOW@SPAWN_MEADOW", ref.toString());
    }

    @Test
    void fromRaw_blankThrowsStableCode() {
        BusinessException ex = assertThrows(BusinessException.class, () -> LocationRef.fromRaw(null));
        assertEquals("INVALID_LOCATION", ex.getErrorCode());
        assertThrows(BusinessException.class, () -> LocationRef.fromRaw(""));
    }

    @Test
    void effectiveMapId_fallsBackToRegion() {
        LocationRef ref = new LocationRef("R_1", null);
        assertEquals("R_1", ref.effectiveMapId());
    }
}