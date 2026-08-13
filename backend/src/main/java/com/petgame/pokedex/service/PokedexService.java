package com.petgame.pokedex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.MapsConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pokedex.entity.PokedexEntity;
import com.petgame.pokedex.entity.PokedexHistoryEntity;
import com.petgame.pokedex.mapper.PokedexHistoryMapper;
import com.petgame.pokedex.mapper.PokedexMapper;
import com.petgame.pokedex.vo.PokedexDetailVo;
import com.petgame.pokedex.vo.PokedexEntryVo;
import com.petgame.pokedex.vo.PokedexHistoryVo;
import com.petgame.pokedex.vo.WildIdentificationVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图鉴核心服务（阶段 8）。
 * <p>
 * 管理种族研究进度、研究等级计算、信息解锁层级、历史记录与野外识别。
 * 研究等级 Lv.0~5，种族共享（非个体），研究值来源配置化。
 */
@Service
public class PokedexService {

    private static final Logger log = LoggerFactory.getLogger(PokedexService.class);

    private final GameConfigRegistry registry;
    private final PokedexMapper pokedexMapper;
    private final PokedexHistoryMapper historyMapper;

    /** 种族 ID → 出现区域列表（懒加载缓存，配置不变）。 */
    private Map<String, List<String>> speciesRegionIndex;

    public PokedexService(GameConfigRegistry registry,
                          PokedexMapper pokedexMapper,
                          PokedexHistoryMapper historyMapper) {
        this.registry = registry;
        this.pokedexMapper = pokedexMapper;
        this.historyMapper = historyMapper;
    }

    // ==================== 查询 ====================

    /**
     * 返回全量种族图鉴列表（27 种），含研究等级 + 已解锁信息摘要。
     */
    public List<PokedexEntryVo> getFullPokedex(String saveId) {
        Map<String, PokedexEntity> entityMap = loadPokedexMap(saveId);
        List<PokedexEntryVo> entries = new ArrayList<>();
        for (PetSpeciesConfig species : registry.getAllSpecies()) {
            PokedexEntity entity = entityMap.get(species.getId());
            entries.add(toEntryVo(species, entity));
        }
        return entries;
    }

    /**
     * 返回单个种族详情（研究等级、已解锁信息、历史记录）。
     */
    public PokedexDetailVo getSpeciesEntry(String saveId, String speciesId) {
        PetSpeciesConfig species = registry.getSpecies(speciesId);
        if (species == null) {
            throw new com.petgame.common.BusinessException("SPECIES_NOT_FOUND",
                    "种族不存在: " + speciesId);
        }
        PokedexEntity entity = loadPokedex(saveId, speciesId);
        PokedexHistoryEntity history = loadHistory(saveId, speciesId);
        int level = computeResearchLevel(entity);
        return toDetailVo(species, entity, history, level);
    }

    // ==================== 研究值记录 ====================

    /**
     * 首次发现 +研究值，标记 seen。重复发现不重复加分。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDiscovery(String saveId, String speciesId) {
        if (registry.getSpecies(speciesId) == null) {
            return;
        }
        PokedexEntity entity = loadOrCreate(saveId, speciesId);
        if (Boolean.TRUE.equals(entity.getSeen())) {
            return; // 已发现，不重复加分
        }
        int points = registry.getSystemRules().getPokedex().getFirstDiscoveryPoints();
        entity.setSeen(true);
        entity.setFirstSeenAt(LocalDateTime.now());
        entity.setResearchPoints(nz(entity.getResearchPoints()) + points);
        savePokedex(entity);
        log.debug("图鉴发现：saveId={}, species={}, +{} 研究值", saveId, speciesId, points);
    }

    /**
     * 捕获记录：首次/后续捕获 + 历史记录更新。
     *
     * @param aptitudes        六维资质数组 [hp, str, spr, def, res, spd]
     * @param rareSkillIds     本次捕获携带的稀有技能 ID 列表
     * @param isElite          是否精英个体
     * @param specialAppearance 特殊外观标记（非 null 表示有特殊外观）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCapture(String saveId, String speciesId, int[] aptitudes,
                              List<String> rareSkillIds, boolean isElite,
                              String specialAppearance) {
        if (registry.getSpecies(speciesId) == null) {
            return;
        }
        SystemRuleConfig.PokedexRuleConfig cfg = registry.getSystemRules().getPokedex();
        PokedexEntity entity = loadOrCreate(saveId, speciesId);
        int addedPoints = 0;

        boolean wasCaught = Boolean.TRUE.equals(entity.getCaught());

        // 首次/后续捕获研究值
        if (!wasCaught) {
            addedPoints += cfg.getFirstCapturePoints();
            entity.setCaught(true);
            entity.setFirstCaughtAt(LocalDateTime.now());
            if (!Boolean.TRUE.equals(entity.getSeen())) {
                entity.setSeen(true);
                entity.setFirstSeenAt(LocalDateTime.now());
            }
        } else {
            addedPoints += cfg.getSubsequentCapturePoints();
        }

        // 高资质研究值
        if (aptitudes != null && aptitudes.length == 6) {
            int combined = 0;
            for (int apt : aptitudes) {
                combined += apt;
            }
            int avg = combined / 6;
            if (avg >= 90) {
                addedPoints += cfg.getHighAptitude90Points();
            } else if (avg >= 80) {
                addedPoints += cfg.getHighAptitude80Points();
            }
        }

        // 稀有技能发现
        if (rareSkillIds != null && !rareSkillIds.isEmpty()) {
            addedPoints += cfg.getRareSkillDiscoveryPoints() * rareSkillIds.size();
        }

        // 特殊外观
        if (specialAppearance != null && !specialAppearance.isBlank()) {
            addedPoints += cfg.getSpecialAppearancePoints();
        }

        // 精英捕获
        if (isElite) {
            addedPoints += cfg.getEliteCapturePoints();
        }

        entity.setResearchPoints(nz(entity.getResearchPoints()) + addedPoints);
        savePokedex(entity);

        // 更新历史记录
        updateHistory(saveId, speciesId, aptitudes, rareSkillIds, isElite, specialAppearance);

        log.debug("图鉴捕获：saveId={}, species={}, +{} 研究值, 首次={}",
                saveId, speciesId, addedPoints, !wasCaught);
    }

    /**
     * 使用该宠物战斗 +研究值。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBattleParticipation(String saveId, Collection<String> speciesIds) {
        int points = registry.getSystemRules().getPokedex().getBattleParticipationPoints();
        for (String speciesId : speciesIds) {
            if (registry.getSpecies(speciesId) == null) {
                continue;
            }
            PokedexEntity entity = loadOrCreate(saveId, speciesId);
            entity.setResearchPoints(nz(entity.getResearchPoints()) + points);
            savePokedex(entity);
        }
    }

    /**
     * 使用该宠物获胜 +研究值。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBattleWins(String saveId, Collection<String> speciesIds) {
        int points = registry.getSystemRules().getPokedex().getBattleWinPoints();
        for (String speciesId : speciesIds) {
            if (registry.getSpecies(speciesId) == null) {
                continue;
            }
            PokedexEntity entity = loadOrCreate(saveId, speciesId);
            entity.setResearchPoints(nz(entity.getResearchPoints()) + points);
            savePokedex(entity);
        }
    }

    /**
     * 等级解锁新种族技能 +研究值。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSkillUnlock(String saveId, String speciesId, String skillId) {
        if (registry.getSpecies(speciesId) == null) {
            return;
        }
        int points = registry.getSystemRules().getPokedex().getSkillUnlockPoints();
        PokedexEntity entity = loadOrCreate(saveId, speciesId);
        entity.setResearchPoints(nz(entity.getResearchPoints()) + points);
        savePokedex(entity);
        log.debug("图鉴技能解锁：saveId={}, species={}, skill={}, +{} 研究值",
                saveId, speciesId, skillId, points);
    }

    // ==================== 研究等级计算 ====================

    /**
     * 根据研究进度实体计算研究等级（含 seen/caught 保底规则）。
     */
    public int computeResearchLevel(PokedexEntity entity) {
        if (entity == null) {
            return 0;
        }
        return computeResearchLevel(nz(entity.getResearchPoints()),
                Boolean.TRUE.equals(entity.getSeen()),
                Boolean.TRUE.equals(entity.getCaught()));
    }

    /**
     * 根据研究值计算等级。
     * <ul>
     *   <li>0 点且未见 = Lv.0</li>
     *   <li>seen=true → 至少 Lv.1</li>
     *   <li>caught=true → 至少 Lv.2</li>
     *   <li>其余按研究值门槛升级</li>
     * </ul>
     */
    public int computeResearchLevel(int researchPoints, boolean seen, boolean caught) {
        SystemRuleConfig.PokedexRuleConfig cfg = registry.getSystemRules().getPokedex();
        Map<String, Integer> thresholds = cfg.getLevelThresholds();

        int level = 0;
        // 按等级从高到低匹配
        for (int lv = 5; lv >= 1; lv--) {
            Integer threshold = thresholds.get(String.valueOf(lv));
            if (threshold != null && researchPoints >= threshold) {
                level = lv;
                break;
            }
        }

        // 保底规则
        if (caught && level < 2) {
            level = 2;
        }
        if (seen && level < 1) {
            level = 1;
        }
        return level;
    }

    // ==================== Lv.5 野外识别 ====================

    /**
     * Lv.5 野外识别：返回资质预估等级标签。
     * <p>
     * 只在研究等级 ≥ 5 时返回非 null 结果。
     *
     * @param aptitudes 六维资质数组 [hp, str, spr, def, res, spd]
     */
    public WildIdentificationVo getWildIdentification(String saveId, String speciesId,
                                                      int[] aptitudes) {
        PokedexEntity entity = loadPokedex(saveId, speciesId);
        int level = computeResearchLevel(entity);
        if (level < 5) {
            return null;
        }

        int combined = 0;
        for (int apt : aptitudes) {
            combined += apt;
        }
        int avg = combined / 6;

        SystemRuleConfig.PokedexRuleConfig cfg = registry.getSystemRules().getPokedex();
        Map<String, Integer> grades = cfg.getAptitudeGrades();

        // 按等级从高到低匹配
        String gradeLabel = "D";
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(grades.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue()); // 降序
        for (Map.Entry<String, Integer> entry : sorted) {
            if (avg >= entry.getValue()) {
                gradeLabel = entry.getKey();
                break;
            }
        }

        WildIdentificationVo vo = new WildIdentificationVo();
        vo.setSpeciesId(speciesId);
        vo.setGradeLabel(gradeLabel);
        return vo;
    }

    // ==================== 内部方法 ====================

    private PokedexEntity loadOrCreate(String saveId, String speciesId) {
        PokedexEntity entity = loadPokedex(saveId, speciesId);
        if (entity == null) {
            entity = new PokedexEntity();
            entity.setSaveId(saveId);
            entity.setSpeciesId(speciesId);
            entity.setResearchPoints(0);
            entity.setSeen(false);
            entity.setCaught(false);
        }
        return entity;
    }

    private PokedexEntity loadPokedex(String saveId, String speciesId) {
        return pokedexMapper.selectOne(
                new LambdaQueryWrapper<PokedexEntity>()
                        .eq(PokedexEntity::getSaveId, saveId)
                        .eq(PokedexEntity::getSpeciesId, speciesId));
    }

    private PokedexHistoryEntity loadHistory(String saveId, String speciesId) {
        return historyMapper.selectOne(
                new LambdaQueryWrapper<PokedexHistoryEntity>()
                        .eq(PokedexHistoryEntity::getSaveId, saveId)
                        .eq(PokedexHistoryEntity::getSpeciesId, speciesId));
    }

    private Map<String, PokedexEntity> loadPokedexMap(String saveId) {
        List<PokedexEntity> entities = pokedexMapper.selectList(
                new LambdaQueryWrapper<PokedexEntity>()
                        .eq(PokedexEntity::getSaveId, saveId));
        Map<String, PokedexEntity> map = new HashMap<>();
        for (PokedexEntity e : entities) {
            map.put(e.getSpeciesId(), e);
        }
        return map;
    }

    private void savePokedex(PokedexEntity entity) {
        PokedexEntity existing = loadPokedex(entity.getSaveId(), entity.getSpeciesId());
        if (existing != null) {
            pokedexMapper.update(entity,
                    new LambdaQueryWrapper<PokedexEntity>()
                            .eq(PokedexEntity::getSaveId, entity.getSaveId())
                            .eq(PokedexEntity::getSpeciesId, entity.getSpeciesId()));
        } else {
            pokedexMapper.insert(entity);
        }
    }

    private void updateHistory(String saveId, String speciesId, int[] aptitudes,
                               List<String> rareSkillIds, boolean isElite,
                               String specialAppearance) {
        PokedexHistoryEntity history = loadHistory(saveId, speciesId);
        if (history == null) {
            history = new PokedexHistoryEntity();
            history.setSaveId(saveId);
            history.setSpeciesId(speciesId);
            history.setTotalCaptures(0);
            history.setTotalDefeats(0);
            history.setEliteEncounters(0);
            history.setSpecialAppearances(0);
            history.setBestCombinedAptitude(0);
            history.setBestHp(0);
            history.setBestStrength(0);
            history.setBestSpirit(0);
            history.setBestDefense(0);
            history.setBestResistance(0);
            history.setBestSpeed(0);
        }

        history.setTotalCaptures(nz(history.getTotalCaptures()) + 1);

        if (isElite) {
            history.setEliteEncounters(nz(history.getEliteEncounters()) + 1);
        }
        if (specialAppearance != null && !specialAppearance.isBlank()) {
            history.setSpecialAppearances(nz(history.getSpecialAppearances()) + 1);
        }

        // 更新六维最高资质
        if (aptitudes != null && aptitudes.length == 6) {
            history.setBestHp(Math.max(nz(history.getBestHp()), aptitudes[0]));
            history.setBestStrength(Math.max(nz(history.getBestStrength()), aptitudes[1]));
            history.setBestSpirit(Math.max(nz(history.getBestSpirit()), aptitudes[2]));
            history.setBestDefense(Math.max(nz(history.getBestDefense()), aptitudes[3]));
            history.setBestResistance(Math.max(nz(history.getBestResistance()), aptitudes[4]));
            history.setBestSpeed(Math.max(nz(history.getBestSpeed()), aptitudes[5]));
            int combined = aptitudes[0] + aptitudes[1] + aptitudes[2]
                    + aptitudes[3] + aptitudes[4] + aptitudes[5];
            history.setBestCombinedAptitude(Math.max(nz(history.getBestCombinedAptitude()), combined));
        }

        // 合并稀有技能
        if (rareSkillIds != null && !rareSkillIds.isEmpty()) {
            Set<String> existing = new LinkedHashSet<>();
            if (history.getDiscoveredRareSkills() != null && !history.getDiscoveredRareSkills().isBlank()) {
                existing.addAll(Arrays.asList(history.getDiscoveredRareSkills().split(",")));
            }
            existing.addAll(rareSkillIds);
            history.setDiscoveredRareSkills(String.join(",", existing));
        }

        // 保存
        PokedexHistoryEntity existingEntity = loadHistory(saveId, speciesId);
        if (existingEntity != null) {
            historyMapper.update(history,
                    new LambdaQueryWrapper<PokedexHistoryEntity>()
                            .eq(PokedexHistoryEntity::getSaveId, saveId)
                            .eq(PokedexHistoryEntity::getSpeciesId, speciesId));
        } else {
            historyMapper.insert(history);
        }
    }

    // ==================== VO 转换 ====================

    private PokedexEntryVo toEntryVo(PetSpeciesConfig species, PokedexEntity entity) {
        int level = entity != null ? computeResearchLevel(entity) : 0;
        PokedexEntryVo vo = new PokedexEntryVo();
        vo.setSpeciesId(species.getId());
        vo.setResearchLevel(level);
        vo.setResearchPoints(entity != null ? nz(entity.getResearchPoints()) : 0);
        vo.setSeen(entity != null && Boolean.TRUE.equals(entity.getSeen()));
        vo.setCaught(entity != null && Boolean.TRUE.equals(entity.getCaught()));

        // Lv.0: 仅 ???
        if (level >= 1) {
            vo.setName(species.getName());
            vo.setElement(species.getElement());
        }
        // Lv.2+: 稀有度可见
        if (level >= 2) {
            vo.setRarity(species.getRarity());
        }
        return vo;
    }

    private PokedexDetailVo toDetailVo(PetSpeciesConfig species, PokedexEntity entity,
                                       PokedexHistoryEntity history, int level) {
        PokedexDetailVo vo = new PokedexDetailVo();
        vo.setSpeciesId(species.getId());
        vo.setResearchLevel(level);
        vo.setResearchPoints(entity != null ? nz(entity.getResearchPoints()) : 0);
        vo.setSeen(entity != null && Boolean.TRUE.equals(entity.getSeen()));
        vo.setCaught(entity != null && Boolean.TRUE.equals(entity.getCaught()));

        // Lv.1+: 名称、属性、描述
        if (level >= 1) {
            vo.setName(species.getName());
            vo.setElement(species.getElement());
            vo.setDescription(species.getDescription());
        }

        // Lv.2+: 稀有度、基础捕获率
        if (level >= 2) {
            vo.setRarity(species.getRarity());
            vo.setCaptureRate(species.getCaptureRate());
        }

        // Lv.3+: 技能列表、被动列表、六维基础值
        if (level >= 3) {
            List<PokedexDetailVo.SkillInfoVo> skills = new ArrayList<>();
            if (species.getSkills() != null) {
                for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
                    SkillConfig skill = registry.getSkill(slot.getSkillId());
                    if (skill == null) continue;
                    PokedexDetailVo.SkillInfoVo info = new PokedexDetailVo.SkillInfoVo();
                    info.setSkillId(skill.getId());
                    info.setSkillName(skill.getName());
                    info.setUnlockLevel(slot.getUnlockLevel());
                    info.setSignature(slot.isSignature());
                    skills.add(info);
                }
            }
            vo.setSkills(skills);

            List<PokedexDetailVo.PassiveInfoVo> passives = new ArrayList<>();
            if (species.getPassives() != null) {
                for (PetSpeciesConfig.SpeciesPassiveSlot slot : species.getPassives()) {
                    PassiveSkillConfig passive = registry.getPassive(slot.getPassiveId());
                    if (passive == null) continue;
                    PokedexDetailVo.PassiveInfoVo info = new PokedexDetailVo.PassiveInfoVo();
                    info.setPassiveId(passive.getId());
                    info.setPassiveName(passive.getName());
                    info.setUnlockLevel(slot.getUnlockLevel());
                    info.setSignature(slot.isSignature());
                    passives.add(info);
                }
            }
            vo.setPassives(passives);

            Map<String, Integer> baseStats = new LinkedHashMap<>();
            baseStats.put("hp", species.getBaseHp());
            baseStats.put("strength", species.getBaseStrength());
            baseStats.put("spirit", species.getBaseSpirit());
            baseStats.put("defense", species.getBaseDefense());
            baseStats.put("resistance", species.getBaseResistance());
            baseStats.put("speed", species.getBaseSpeed());
            vo.setBaseStats(baseStats);
        }

        // Lv.4+: 稀有技能池、出现区域
        if (level >= 4) {
            List<String> rareSkills = new ArrayList<>();
            if (species.getRareSkills() != null) {
                for (String rsId : species.getRareSkills()) {
                    SkillConfig rs = registry.getSkill(rsId);
                    rareSkills.add(rs != null ? rs.getName() : rsId);
                }
            }
            vo.setRareSkills(rareSkills);
            vo.setEncounterRegions(getEncounterRegions(species.getId()));
        }

        // Lv.5+: 历史记录、特殊外观、进化占位
        if (level >= 5) {
            if (history != null) {
                vo.setHistory(toHistoryVo(history));
                vo.setSpecialAppearanceCount(nz(history.getSpecialAppearances()));
            }
            vo.setEvolutionPlaceholder("进化资料尚未公开（后续阶段实装）");
        }

        return vo;
    }

    private PokedexHistoryVo toHistoryVo(PokedexHistoryEntity entity) {
        PokedexHistoryVo vo = new PokedexHistoryVo();
        vo.setTotalCaptures(nz(entity.getTotalCaptures()));
        vo.setTotalDefeats(nz(entity.getTotalDefeats()));
        vo.setEliteEncounters(nz(entity.getEliteEncounters()));
        vo.setSpecialAppearances(nz(entity.getSpecialAppearances()));
        vo.setBestCombinedAptitude(nz(entity.getBestCombinedAptitude()));
        vo.setBestHp(nz(entity.getBestHp()));
        vo.setBestStrength(nz(entity.getBestStrength()));
        vo.setBestSpirit(nz(entity.getBestSpirit()));
        vo.setBestDefense(nz(entity.getBestDefense()));
        vo.setBestResistance(nz(entity.getBestResistance()));
        vo.setBestSpeed(nz(entity.getBestSpeed()));
        if (entity.getDiscoveredRareSkills() != null && !entity.getDiscoveredRareSkills().isBlank()) {
            vo.setDiscoveredRareSkills(Arrays.asList(entity.getDiscoveredRareSkills().split(",")));
        } else {
            vo.setDiscoveredRareSkills(List.of());
        }
        return vo;
    }

    // ==================== 遭遇区域反查 ====================

    /**
     * 获取种族可出现的区域列表（反查 encounters + maps 配置）。
     */
    private List<String> getEncounterRegions(String speciesId) {
        if (speciesRegionIndex == null) {
            buildSpeciesRegionIndex();
        }
        return speciesRegionIndex.getOrDefault(speciesId, List.of());
    }

    /** 构建种族 → 出现区域的反向索引。 */
    private void buildSpeciesRegionIndex() {
        Map<String, List<String>> index = new LinkedHashMap<>();
        for (PetSpeciesConfig species : registry.getAllSpecies()) {
            index.put(species.getId(), new ArrayList<>());
        }

        // 构建 encounterGroupId → 包含的 speciesId 列表
        Map<String, Set<String>> groupSpecies = new HashMap<>();
        for (EncountersConfig.EncounterGroup group
                : registry.getEncountersConfig().getEncounterGroups()) {
            Set<String> ids = new HashSet<>();
            for (EncountersConfig.SpeciesEntry entry : group.getSpecies()) {
                ids.add(entry.getSpeciesId());
            }
            groupSpecies.put(group.getId(), ids);
        }

        // 遍历区域，将区域名映射到种族
        for (MapsConfig.RegionConfig region : registry.getMapsConfig().getRegions()) {
            if (region.isPlanned()) continue;
            for (String groupId : region.getEncounterGroups()) {
                Set<String> speciesIds = groupSpecies.get(groupId);
                if (speciesIds == null) continue;
                for (String sid : speciesIds) {
                    index.computeIfAbsent(sid, k -> new ArrayList<>());
                    if (!index.get(sid).contains(region.getName())) {
                        index.get(sid).add(region.getName());
                    }
                }
            }
        }
        this.speciesRegionIndex = index;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
