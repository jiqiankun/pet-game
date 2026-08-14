package com.petgame.pokedex;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.MapsConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pokedex.entity.PokedexEntity;
import com.petgame.pokedex.entity.PokedexHistoryEntity;
import com.petgame.pokedex.mapper.PokedexHistoryMapper;
import com.petgame.pokedex.mapper.PokedexMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.pokedex.vo.PokedexDetailVo;
import com.petgame.pokedex.vo.PokedexEntryVo;
import com.petgame.pokedex.vo.WildIdentificationVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PokedexService 单元测试（阶段 8 验收标准）。
 * <p>
 * 覆盖：研究等级计算、首次发现/后续发现、首次/后续捕获、战斗参与/获胜、
 * 技能解锁、高资质/稀有技能/特殊外观/精英、历史记录累加、
 * caught 保底 Lv.2、Lv.5 野外识别。
 */
@ExtendWith(MockitoExtension.class)
class PokedexServiceTest {

    private static final String SAVE_ID = "test-save-id";
    private static final String SPECIES_ID = "PET_FIRE_001";

    @Mock
    private PokedexMapper pokedexMapper;
    @Mock
    private PokedexHistoryMapper historyMapper;

    private GameConfigRegistry registry;
    private PokedexService pokedexService;

    @BeforeEach
    void setUp() throws Exception {
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId(SPECIES_ID);
        species.setName("烬牙兽");
        species.setElement("FIRE");
        species.setRarity("COMMON");
        species.setCaptureRate(0.5);
        species.setDescription("火属性种族描述");
        species.setBaseHp(100);
        species.setBaseStrength(20);
        species.setBaseSpirit(20);
        species.setBaseDefense(20);
        species.setBaseResistance(20);
        species.setBaseSpeed(20);

        registry = buildRegistry(List.of(species));
        pokedexService = new PokedexService(registry, pokedexMapper, historyMapper);
    }

    // ==================== 研究等级计算 ====================

    @Test
    void computeResearchLevel_zeroPoints_notSeen_returnsLv0() {
        assertEquals(0, pokedexService.computeResearchLevel(0, false, false));
    }

    @Test
    void computeResearchLevel_seenAtLeastLv1() {
        assertEquals(1, pokedexService.computeResearchLevel(5, true, false));
    }

    @Test
    void computeResearchLevel_caughtAtLeastLv2() {
        assertEquals(2, pokedexService.computeResearchLevel(15, true, true));
    }

    @Test
    void computeResearchLevel_thresholds() {
        // Lv.1=10, Lv.2=30, Lv.3=60, Lv.4=100, Lv.5=150
        assertEquals(1, pokedexService.computeResearchLevel(10, true, false));
        assertEquals(2, pokedexService.computeResearchLevel(30, true, false));
        assertEquals(3, pokedexService.computeResearchLevel(60, true, false));
        assertEquals(4, pokedexService.computeResearchLevel(100, true, false));
        assertEquals(5, pokedexService.computeResearchLevel(150, true, false));
    }

    @Test
    void computeResearchLevel_caughtGuaranteesLv2() {
        // caught=true 但研究值仅 15（不到 Lv.2 门槛 30）
        assertEquals(2, pokedexService.computeResearchLevel(15, true, true));
    }

    // ==================== 发现记录 ====================

    @Test
    void recordDiscovery_firstDiscovery_addsPointsAndMarksSeen() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);

        pokedexService.recordDiscovery(SAVE_ID, SPECIES_ID);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        PokedexEntity saved = captor.getValue();
        assertTrue(saved.getSeen());
        assertEquals(5, saved.getResearchPoints()); // firstDiscoveryPoints
        assertNotNull(saved.getFirstSeenAt());
    }

    @Test
    void recordDiscovery_alreadySeen_doesNotAddPointsAgain() {
        PokedexEntity existing = newEntity(SAVE_ID, SPECIES_ID, 5, true, false);
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        pokedexService.recordDiscovery(SAVE_ID, SPECIES_ID);

        // 不应执行任何 insert 或 update
        verify(pokedexMapper, never()).insert(any(PokedexEntity.class));
        verify(pokedexMapper, never()).update(any(), any(LambdaQueryWrapper.class));
    }

    // ==================== 捕获记录 ====================

    @Test
    void recordCapture_firstCapture_adds10PointsAndMarksCaught() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.insert(any(PokedexHistoryEntity.class))).thenReturn(1);

        int[] apts = {80, 80, 80, 80, 80, 80};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts, List.of(), false, null);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        PokedexEntity saved = captor.getValue();
        assertTrue(saved.getCaught());
        assertTrue(saved.getSeen());
        // firstCapture(10) + highAptitude80(5) = 15
        assertEquals(15, saved.getResearchPoints());
    }

    @Test
    void recordCapture_subsequentCapture_adds2Points() {
        PokedexEntity existing = newEntity(SAVE_ID, SPECIES_ID, 15, true, true);
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(pokedexMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(1);
        PokedexHistoryEntity hist = newHistory(SAVE_ID, SPECIES_ID, 1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(hist);
        when(historyMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(1);

        int[] apts = {50, 50, 50, 50, 50, 50};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts, List.of(), false, null);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).update(captor.capture(), any(LambdaQueryWrapper.class));
        PokedexEntity saved = captor.getValue();
        // 15 + subsequentCapture(2) = 17
        assertEquals(17, saved.getResearchPoints());
    }

    @Test
    void recordCapture_highAptitude90_adds8Points() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.insert(any(PokedexHistoryEntity.class))).thenReturn(1);

        int[] apts = {92, 92, 92, 92, 92, 92};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts, List.of(), false, null);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        // firstCapture(10) + highAptitude90(8) = 18
        assertEquals(18, captor.getValue().getResearchPoints());
    }

    @Test
    void recordCapture_rareSkillDiscovery_adds5Points() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.insert(any(PokedexHistoryEntity.class))).thenReturn(1);

        int[] apts = {50, 50, 50, 50, 50, 50};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts,
                List.of("RARE_SKILL_1"), false, null);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        // firstCapture(10) + rareSkill(5) = 15
        assertEquals(15, captor.getValue().getResearchPoints());
    }

    @Test
    void recordCapture_specialAppearance_adds10Points() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.insert(any(PokedexHistoryEntity.class))).thenReturn(1);

        int[] apts = {50, 50, 50, 50, 50, 50};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts,
                List.of(), false, "SPECIAL");

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        // firstCapture(10) + specialAppearance(10) = 20
        assertEquals(20, captor.getValue().getResearchPoints());
    }

    @Test
    void recordCapture_eliteCapture_adds8Points() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.insert(any(PokedexHistoryEntity.class))).thenReturn(1);

        int[] apts = {50, 50, 50, 50, 50, 50};
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts,
                List.of(), true, null);

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        // firstCapture(10) + elite(8) = 18
        assertEquals(18, captor.getValue().getResearchPoints());
    }

    // ==================== 战斗记录 ====================

    @Test
    void recordBattleParticipation_adds1PointPerSpecies() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);

        pokedexService.recordBattleParticipation(SAVE_ID, List.of(SPECIES_ID));

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getResearchPoints());
    }

    @Test
    void recordBattleWins_adds1PointPerSpecies() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);

        pokedexService.recordBattleWins(SAVE_ID, List.of(SPECIES_ID));

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getResearchPoints());
    }

    // ==================== 技能解锁 ====================

    @Test
    void recordSkillUnlock_adds2Points() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(pokedexMapper.insert(any(PokedexEntity.class))).thenReturn(1);

        pokedexService.recordSkillUnlock(SAVE_ID, SPECIES_ID, "SKILL_A");

        ArgumentCaptor<PokedexEntity> captor = ArgumentCaptor.forClass(PokedexEntity.class);
        verify(pokedexMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getResearchPoints());
    }

    // ==================== 历史记录累加 ====================

    @Test
    void recordCapture_historyAccumulates() {
        PokedexEntity existing = newEntity(SAVE_ID, SPECIES_ID, 15, true, true);
        PokedexHistoryEntity hist = newHistory(SAVE_ID, SPECIES_ID, 2);
        hist.setBestHp(85);
        hist.setBestCombinedAptitude(480);

        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(pokedexMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(1);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(hist);
        when(historyMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(1);

        int[] apts = {90, 80, 70, 60, 50, 40}; // combined = 390, avg = 65
        pokedexService.recordCapture(SAVE_ID, SPECIES_ID, apts, List.of(), false, null);

        ArgumentCaptor<PokedexHistoryEntity> histCaptor = ArgumentCaptor.forClass(PokedexHistoryEntity.class);
        verify(historyMapper).update(histCaptor.capture(), any(LambdaQueryWrapper.class));
        PokedexHistoryEntity updated = histCaptor.getValue();
        assertEquals(3, updated.getTotalCaptures()); // 2 + 1
        assertEquals(90, updated.getBestHp()); // max(85, 90)
        assertEquals(480, updated.getBestCombinedAptitude()); // max(480, 390)
    }

    // ==================== Lv.5 野外识别 ====================

    @Test
    void getWildIdentification_level5_returnsGradeLabel() {
        PokedexEntity entity = newEntity(SAVE_ID, SPECIES_ID, 150, true, true);
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        int[] apts = {92, 92, 92, 92, 92, 92}; // avg = 92 → S grade
        WildIdentificationVo result = pokedexService.getWildIdentification(SAVE_ID, SPECIES_ID, apts);

        assertNotNull(result);
        assertEquals("S", result.getGradeLabel());
    }

    @Test
    void getWildIdentification_levelBelow5_returnsNull() {
        PokedexEntity entity = newEntity(SAVE_ID, SPECIES_ID, 50, true, true);
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        int[] apts = {92, 92, 92, 92, 92, 92};
        WildIdentificationVo result = pokedexService.getWildIdentification(SAVE_ID, SPECIES_ID, apts);

        assertNull(result);
    }

    @Test
    void getWildIdentification_averageAptitude_returnsBGrade() {
        PokedexEntity entity = newEntity(SAVE_ID, SPECIES_ID, 150, true, true);
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        int[] apts = {70, 68, 72, 65, 70, 65}; // avg = 68 → B grade (65-79)
        WildIdentificationVo result = pokedexService.getWildIdentification(SAVE_ID, SPECIES_ID, apts);

        assertNotNull(result);
        assertEquals("B", result.getGradeLabel());
    }

    // ==================== 查询 ====================

    @Test
    void getFullPokedex_returnsAllSpecies() {
        PokedexEntity entity = newEntity(SAVE_ID, SPECIES_ID, 15, true, true);
        when(pokedexMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<PokedexEntryVo> entries = pokedexService.getFullPokedex(SAVE_ID);

        assertEquals(1, entries.size());
        PokedexEntryVo entry = entries.get(0);
        assertEquals(SPECIES_ID, entry.getSpeciesId());
        assertTrue(entry.isSeen());
        assertTrue(entry.isCaught());
        assertEquals(15, entry.getResearchPoints());
    }

    @Test
    void getSpeciesEntry_level0_returnsUnknown() {
        when(pokedexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(historyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        PokedexDetailVo detail = pokedexService.getSpeciesEntry(SAVE_ID, SPECIES_ID);

        assertEquals(0, detail.getResearchLevel());
        assertNull(detail.getName());
        assertNull(detail.getDescription());
    }

    // ==================== 辅助方法 ====================

    private PokedexEntity newEntity(String saveId, String speciesId, int points,
                                     boolean seen, boolean caught) {
        PokedexEntity e = new PokedexEntity();
        e.setSaveId(saveId);
        e.setSpeciesId(speciesId);
        e.setResearchPoints(points);
        e.setSeen(seen);
        e.setCaught(caught);
        return e;
    }

    private PokedexHistoryEntity newHistory(String saveId, String speciesId, int totalCaptures) {
        PokedexHistoryEntity h = new PokedexHistoryEntity();
        h.setSaveId(saveId);
        h.setSpeciesId(speciesId);
        h.setTotalCaptures(totalCaptures);
        h.setTotalDefeats(0);
        h.setEliteEncounters(0);
        h.setSpecialAppearances(0);
        h.setBestCombinedAptitude(0);
        h.setBestHp(0);
        h.setBestStrength(0);
        h.setBestSpirit(0);
        h.setBestDefense(0);
        h.setBestResistance(0);
        h.setBestSpeed(0);
        return h;
    }

    /** 构建最小化测试用 GameConfigRegistry。 */
    private static GameConfigRegistry buildRegistry(List<PetSpeciesConfig> speciesList) throws Exception {
        SystemRuleConfig system = new SystemRuleConfig();
        // pokedex 配置
        SystemRuleConfig.PokedexRuleConfig pokedex = new SystemRuleConfig.PokedexRuleConfig();
        Map<String, Integer> thresholds = new LinkedHashMap<>();
        thresholds.put("1", 10);
        thresholds.put("2", 30);
        thresholds.put("3", 60);
        thresholds.put("4", 100);
        thresholds.put("5", 150);
        pokedex.setLevelThresholds(thresholds);
        pokedex.setFirstDiscoveryPoints(5);
        pokedex.setFirstCapturePoints(10);
        pokedex.setSubsequentCapturePoints(2);
        pokedex.setBattleParticipationPoints(1);
        pokedex.setBattleWinPoints(1);
        pokedex.setSkillUnlockPoints(2);
        pokedex.setHighAptitude80Points(5);
        pokedex.setHighAptitude90Points(8);
        pokedex.setRareSkillDiscoveryPoints(5);
        pokedex.setSpecialAppearancePoints(10);
        pokedex.setEliteCapturePoints(8);
        Map<String, Integer> grades = new LinkedHashMap<>();
        grades.put("S", 90);
        grades.put("A", 80);
        grades.put("B", 65);
        grades.put("C", 50);
        pokedex.setAptitudeGrades(grades);
        system.setPokedex(pokedex);

        com.petgame.config.model.PetsConfig petsConfig = new com.petgame.config.model.PetsConfig();
        petsConfig.setSpecies(speciesList);

        GameConfigRegistry registry = new GameConfigRegistry(null, null);
        setField(registry, "systemRules", system);
        setField(registry, "petsConfig", petsConfig);

        LinkedHashMap<String, PetSpeciesConfig> speciesIndex = new LinkedHashMap<>();
        for (PetSpeciesConfig s : speciesList) {
            speciesIndex.put(s.getId(), s);
        }
        setField(registry, "speciesIndex", speciesIndex);
        setField(registry, "skillIndex", new LinkedHashMap<>());
        setField(registry, "itemIndex", new LinkedHashMap<>());
        setField(registry, "statusIndex", new LinkedHashMap<>());
        setField(registry, "passiveIndex", new LinkedHashMap<>());

        // 空的遭遇和地图配置（Lv.4 区域反查需要）
        EncountersConfig encountersConfig = new EncountersConfig();
        setField(registry, "encountersConfig", encountersConfig);
        MapsConfig mapsConfig = new MapsConfig();
        setField(registry, "mapsConfig", mapsConfig);

        return registry;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
