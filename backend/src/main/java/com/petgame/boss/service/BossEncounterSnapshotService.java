package com.petgame.boss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petgame.boss.entity.BossEncounterSnapshotEntity;
import com.petgame.boss.mapper.BossEncounterSnapshotMapper;
import com.petgame.common.BusinessException;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boss 遭遇快照服务（阶段 13）。
 * 首次挑战生成，之后只读取；重置仅允许跨全局难度显式确认。
 */
@Service
public class BossEncounterSnapshotService {

    private final GameConfigRegistry registry;
    private final PetGrowthService growthService;
    private final BossEncounterSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public BossEncounterSnapshotService(GameConfigRegistry registry, PetGrowthService growthService,
                                        BossEncounterSnapshotMapper snapshotMapper, ObjectMapper objectMapper) {
        this.registry = registry;
        this.growthService = growthService;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    /** 获取已有快照；不存在时按当前难度创建。 */
    @Transactional
    public EncounterData getOrCreate(String saveId, BossesConfig.BossConfig boss,
                                     BossesConfig.DifficultyConfig bossDifficulty,
                                     String bossDifficultyKey, String gameDifficulty,
                                     List<PlayerPetEntity> teamPets, Long requestedSeed) {
        BossEncounterSnapshotEntity existing = find(saveId, boss.getId(), bossDifficultyKey);
        if (existing != null) {
            return decode(existing);
        }
        long seed = requestedSeed != null ? requestedSeed : System.nanoTime();
        EncounterData data = generate(boss, bossDifficulty, bossDifficultyKey, gameDifficulty, teamPets, seed);
        BossEncounterSnapshotEntity entity = toEntity(saveId, boss.getId(), bossDifficultyKey, data, 1, false);
        try {
            snapshotMapper.insert(entity);
            data.setSnapshotId(entity.getId());
            return data;
        } catch (DuplicateKeyException ignored) {
            // 并发点击时以最先写入的快照为准，绝不生成第二套遭遇。
            BossEncounterSnapshotEntity concurrent = find(saveId, boss.getId(), bossDifficultyKey);
            if (concurrent == null) {
                throw ignored;
            }
            return decode(concurrent);
        }
    }

    /** 查询快照摘要；未创建时返回 null。 */
    public SnapshotView getView(String saveId, String bossId, String bossDifficulty, String currentGameDifficulty) {
        BossEncounterSnapshotEntity entity = find(saveId, bossId, bossDifficulty);
        return entity == null ? null : toView(entity, decode(entity), currentGameDifficulty);
    }

    /** 跨全局难度确认后重置；相同难度不能重掷。 */
    @Transactional
    public SnapshotView resetForCurrentDifficulty(String saveId, BossesConfig.BossConfig boss,
                                                  BossesConfig.DifficultyConfig bossDifficulty,
                                                  String bossDifficultyKey, String currentGameDifficulty,
                                                  List<PlayerPetEntity> teamPets) {
        BossEncounterSnapshotEntity entity = find(saveId, boss.getId(), bossDifficultyKey);
        if (entity == null) {
            throw new BusinessException("BOSS_SNAPSHOT_NOT_FOUND", "尚未生成该 Boss 难度的遭遇快照");
        }
        if (currentGameDifficulty.equalsIgnoreCase(entity.getGameDifficulty())) {
            throw new BusinessException("BOSS_SNAPSHOT_RESET_NOT_ALLOWED", "当前难度与快照难度相同，不能重掷 Boss 遭遇");
        }
        EncounterData data = generate(boss, bossDifficulty, bossDifficultyKey, currentGameDifficulty,
                teamPets, System.nanoTime());
        int version = nz(entity.getSnapshotVersion()) + 1;
        BossEncounterSnapshotEntity replacement = toEntity(saveId, boss.getId(), bossDifficultyKey,
                data, version, false);
        replacement.setId(entity.getId());
        snapshotMapper.updateById(replacement);
        data.setSnapshotId(entity.getId());
        return toView(replacement, data, currentGameDifficulty);
    }

    /** Boss 获胜后记录首次击败状态，不影响重复挑战时的固定阵容。 */
    public void markDefeated(Long snapshotId) {
        if (snapshotId == null) {
            return;
        }
        BossEncounterSnapshotEntity entity = snapshotMapper.selectById(snapshotId);
        if (entity != null && !Boolean.TRUE.equals(entity.getDefeated())) {
            entity.setDefeated(true);
            snapshotMapper.updateById(entity);
        }
    }

    private EncounterData generate(BossesConfig.BossConfig boss, BossesConfig.DifficultyConfig bossDifficulty,
                                   String bossDifficultyKey, String gameDifficulty,
                                   List<PlayerPetEntity> teamPets, long seed) {
        SystemRuleConfig rules = registry.getSystemRules();
        SystemRuleConfig.DifficultyProfile profile = requireProfile(gameDifficulty);
        int baseLevel = boss.getRecommendedLevel() + profile.getBossLevelOffset();
        int teamReference = teamReferenceLevel(teamPets);
        int upward = Math.min(profile.getBossPlayerLevelUpwardLimit(), Math.max(0, teamReference - baseLevel));
        int generatedLevel = Math.min(rules.getLevelCap(), Math.max(1, baseLevel + upward));
        int playerLevelCap = profile.isEffectiveLevelCapEnabled()
                ? Math.min(rules.getLevelCap(), generatedLevel + profile.getPlayerLevelCapOffset())
                : rules.getLevelCap();

        EncounterData data = new EncounterData();
        data.setGameDifficulty(gameDifficulty.toUpperCase());
        data.setBossDifficulty(bossDifficultyKey);
        data.setBossId(boss.getId());
        data.setGeneratedLevel(generatedLevel);
        data.setPlayerLevelCap(playerLevelCap);
        data.setBossAiLevel(profile.getBossAiLevel());
        data.setRandomSeed(seed);
        data.getUnits().add(buildCoreUnit(boss, bossDifficulty, bossDifficultyKey, generatedLevel, profile));

        int maxTeamSize = Math.min(boss.getMaxTeamSize(), rules.getStandardBattleSlots());
        int minimumOptional = Math.max(0, boss.getMinTeamSize() - 1);
        int optionalCount = Math.min(Math.max(0, maxTeamSize - 1),
                Math.max(minimumOptional, profile.getBossOptionalSlotCount()));
        GameRandom random = new GameRandom(seed);
        Set<String> chosenSpecies = new HashSet<>();
        Set<String> chosenRoles = new HashSet<>();
        TeamProfile teamProfile = analyzeTeam(teamPets);
        for (int index = 0; index < optionalCount; index++) {
            PetSpeciesConfig species = chooseOptionalSpecies(boss, random, chosenSpecies, chosenRoles, teamProfile);
            if (species == null) {
                break;
            }
            data.getUnits().add(buildOptionalUnit(species, generatedLevel, index + 1));
            chosenSpecies.add(species.getId());
            chosenRoles.add(roleOf(species));
        }
        return data;
    }

    private UnitData buildCoreUnit(BossesConfig.BossConfig boss, BossesConfig.DifficultyConfig bossDifficulty,
                                   String bossDifficultyKey, int level,
                                   SystemRuleConfig.DifficultyProfile profile) {
        BossesConfig.StatsConfig stats = bossDifficulty.getStats();
        UnitData unit = new UnitData();
        unit.setUnitId("BOSS_" + boss.getId() + "_" + bossDifficultyKey + "_CORE");
        unit.setName(boss.getName() + " (" + bossDifficultyKey + ")");
        unit.setElement(boss.getElement());
        unit.setRole("CORE");
        unit.setLevel(level);
        unit.setMaxHp(scale(stats.getMaxHp(), profile.getBossHpMultiplier()));
        unit.setStrength(scale(stats.getStrength(), profile.getBossAttackMultiplier()));
        unit.setSpirit(scale(stats.getSpirit(), profile.getBossAttackMultiplier()));
        unit.setDefense(scale(stats.getDefense(), profile.getBossDefenseMultiplier()));
        unit.setResistance(scale(stats.getResistance(), profile.getBossDefenseMultiplier()));
        unit.setSpeed(scale(stats.getSpeed(), profile.getBossSpeedMultiplier()));
        unit.setPosition(0);
        unit.getSkillIds().addAll(bossDifficulty.getSkills());
        unit.getPassiveIds().addAll(bossDifficulty.getPassives());
        if (bossDifficulty.getPhases() != null) {
            unit.getPhases().addAll(bossDifficulty.getPhases());
        }
        unit.setControlResistance(registry.getSystemRules().getControlResistance().getOrDefault("BOSS", 0.6));
        return unit;
    }

    private UnitData buildOptionalUnit(PetSpeciesConfig species, int level, int position) {
        PlayerPetEntity temp = new PlayerPetEntity();
        temp.setLevel(level);
        temp.setHpAptitude(50);
        temp.setStrengthAptitude(50);
        temp.setSpiritAptitude(50);
        temp.setDefenseAptitude(50);
        temp.setResistanceAptitude(50);
        temp.setSpeedAptitude(50);
        temp.setFreePointHp(0);
        temp.setFreePointStrength(0);
        temp.setFreePointSpirit(0);
        temp.setFreePointDefense(0);
        temp.setFreePointResistance(0);
        temp.setFreePointSpeed(0);
        PetPanelStats stats = growthService.computePanelStatsAtLevel(temp, species, level);

        UnitData unit = new UnitData();
        unit.setUnitId("BOSS_SUPPORT_" + position + "_" + species.getId());
        unit.setSpeciesId(species.getId());
        unit.setName(species.getName());
        unit.setElement(species.getElement());
        unit.setRole(roleOf(species));
        unit.setLevel(level);
        unit.setMaxHp(stats.getMaxHp());
        unit.setStrength(stats.getStrength());
        unit.setSpirit(stats.getSpirit());
        unit.setDefense(stats.getDefense());
        unit.setResistance(stats.getResistance());
        unit.setSpeed(stats.getSpeed());
        unit.setPosition(position);
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            if (slot.getUnlockLevel() <= level) {
                unit.getSkillIds().add(slot.getSkillId());
            }
        }
        for (PetSpeciesConfig.SpeciesPassiveSlot slot : species.getPassives()) {
            if (slot.getUnlockLevel() <= level) {
                unit.getPassiveIds().add(slot.getPassiveId());
            }
        }
        unit.setControlResistance(registry.getSystemRules().getControlResistance().getOrDefault("NORMAL", 1.0));
        return unit;
    }

    private PetSpeciesConfig chooseOptionalSpecies(BossesConfig.BossConfig boss, GameRandom random,
                                                   Set<String> chosenSpecies, Set<String> chosenRoles,
                                                   TeamProfile teamProfile) {
        List<PetSpeciesConfig> candidates = new ArrayList<>();
        for (String speciesId : boss.getOptionalSpeciesIds()) {
            if (!chosenSpecies.contains(speciesId)) {
                PetSpeciesConfig species = registry.getSpecies(speciesId);
                if (species != null) candidates.add(species);
            }
        }
        if (candidates.isEmpty()) return null;
        String preferredRole = preferredRole(chosenRoles, teamProfile);
        List<PetSpeciesConfig> preferred = candidates.stream()
                .filter(species -> preferredRole.equals(roleOf(species))).toList();
        List<PetSpeciesConfig> pool = preferred.isEmpty() ? candidates : preferred;
        return pool.get(random.nextInt(0, pool.size() - 1));
    }

    /**
     * 玩家队伍仅低权重影响可选位的角色优先级；不会按属性生成完美反制。
     * 模板候选池、候选数量与核心单位始终由 Boss 配置决定。
     */
    private static String preferredRole(Set<String> roles, TeamProfile player) {
        if (roles.isEmpty() && player.damage() >= 2) return "SUPPORT";
        if (!roles.contains("CONTROL") && player.support() > 0) return "CONTROL";
        if (!roles.contains("SUPPORT")) return "SUPPORT";
        if (!roles.contains("CONTROL")) return "CONTROL";
        if (!roles.contains("TANK")) return "TANK";
        return "DAMAGE";
    }

    /** 仅分析当前激活队伍的种族定位，不读取仓库与未装备技能。 */
    private TeamProfile analyzeTeam(List<PlayerPetEntity> teamPets) {
        int damage = 0, tank = 0, support = 0, control = 0;
        if (teamPets != null) {
            for (PlayerPetEntity pet : teamPets) {
                PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
                switch (roleOf(species)) {
                    case "TANK" -> tank++;
                    case "SUPPORT" -> support++;
                    case "CONTROL" -> control++;
                    default -> damage++;
                }
            }
        }
        return new TeamProfile(damage, tank, support, control);
    }

    private static String roleOf(PetSpeciesConfig species) {
        if (species == null) {
            return "DAMAGE";
        }
        if (species.getRole() != null && !species.getRole().isBlank()) {
            return species.getRole().toUpperCase();
        }
        double bulk = species.getBaseHp() + species.getBaseDefense() + species.getBaseResistance();
        double offense = species.getBaseStrength() + species.getBaseSpirit();
        return bulk > offense * 1.7 ? "TANK" : "DAMAGE";
    }

    private SystemRuleConfig.DifficultyProfile requireProfile(String gameDifficulty) {
        SystemRuleConfig.GameDifficultyConfig config = registry.getSystemRules().getGameDifficulty();
        SystemRuleConfig.DifficultyProfile profile = config.getProfiles().get(gameDifficulty.toUpperCase());
        if (profile == null) {
            throw new BusinessException("INVALID_GAME_DIFFICULTY", "未知全局难度: " + gameDifficulty);
        }
        return profile;
    }

    private BossEncounterSnapshotEntity find(String saveId, String bossId, String bossDifficulty) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<BossEncounterSnapshotEntity>()
                .eq(BossEncounterSnapshotEntity::getSaveId, saveId)
                .eq(BossEncounterSnapshotEntity::getBossId, bossId)
                .eq(BossEncounterSnapshotEntity::getBossDifficulty, bossDifficulty));
    }

    private BossEncounterSnapshotEntity toEntity(String saveId, String bossId, String bossDifficulty,
                                                 EncounterData data, int version, boolean defeated) {
        BossEncounterSnapshotEntity entity = new BossEncounterSnapshotEntity();
        entity.setSaveId(saveId);
        entity.setBossId(bossId);
        entity.setBossDifficulty(bossDifficulty);
        entity.setGameDifficulty(data.getGameDifficulty());
        entity.setGeneratedLevel(data.getGeneratedLevel());
        entity.setPlayerLevelCap(data.getPlayerLevelCap());
        entity.setBossAiLevel(data.getBossAiLevel());
        entity.setRandomSeed(data.getRandomSeed());
        entity.setSnapshotVersion(version);
        entity.setRosterJson(encode(data));
        entity.setLocked(true);
        entity.setDefeated(defeated);
        return entity;
    }

    private EncounterData decode(BossEncounterSnapshotEntity entity) {
        try {
            EncounterData data = objectMapper.readValue(entity.getRosterJson(), EncounterData.class);
            data.setSnapshotId(entity.getId());
            return data;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Boss 遭遇快照无法解析: " + entity.getId(), e);
        }
    }

    private String encode(EncounterData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Boss 遭遇快照无法序列化", e);
        }
    }

    private SnapshotView toView(BossEncounterSnapshotEntity entity, EncounterData data,
                                String currentGameDifficulty) {
        SnapshotView view = new SnapshotView();
        view.setSnapshotId(entity.getId());
        view.setGameDifficulty(entity.getGameDifficulty());
        view.setCurrentGameDifficulty(currentGameDifficulty);
        view.setDifficultyMismatch(!entity.getGameDifficulty().equalsIgnoreCase(currentGameDifficulty));
        view.setCanReset(!entity.getGameDifficulty().equalsIgnoreCase(currentGameDifficulty));
        view.setGeneratedLevel(entity.getGeneratedLevel());
        view.setPlayerLevelCap(entity.getPlayerLevelCap());
        view.setBossAiLevel(entity.getBossAiLevel());
        view.setSnapshotVersion(entity.getSnapshotVersion());
        view.setLocked(Boolean.TRUE.equals(entity.getLocked()));
        view.setDefeated(Boolean.TRUE.equals(entity.getDefeated()));
        view.setUnits(data.getUnits());
        return view;
    }

    private static int teamReferenceLevel(List<PlayerPetEntity> teamPets) {
        if (teamPets == null || teamPets.isEmpty()) return 1;
        List<Integer> levels = teamPets.stream().map(PlayerPetEntity::getLevel)
                .filter(level -> level != null && level > 0)
                .sorted(Comparator.reverseOrder()).limit(3).toList();
        return levels.isEmpty() ? 1 : (int) Math.round(levels.stream().mapToInt(Integer::intValue).average().orElse(1));
    }

    private static int scale(int value, double multiplier) {
        return Math.max(1, (int) Math.round(value * multiplier));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private record TeamProfile(int damage, int tank, int support, int control) {
    }

    @Data
    public static class EncounterData {
        private Long snapshotId;
        /** Boss ID（阶段 14 美术验收：Boss 核心单位展示立绘用）。 */
        private String bossId;
        private String gameDifficulty;
        private String bossDifficulty;
        private int generatedLevel;
        private int playerLevelCap;
        private int bossAiLevel;
        private long randomSeed;
        private List<UnitData> units = new ArrayList<>();
    }

    @Data
    public static class UnitData {
        private String unitId;
        private String speciesId;
        private String name;
        private String element;
        private String role;
        private int level;
        private int maxHp;
        private int strength;
        private int spirit;
        private int defense;
        private int resistance;
        private int speed;
        private int position;
        private double controlResistance;
        private List<String> skillIds = new ArrayList<>();
        private List<String> passiveIds = new ArrayList<>();
        private List<BossesConfig.PhaseTrigger> phases = new ArrayList<>();
    }

    @Data
    public static class SnapshotView {
        private Long snapshotId;
        private String gameDifficulty;
        private String currentGameDifficulty;
        private boolean difficultyMismatch;
        private boolean canReset;
        private Integer generatedLevel;
        private Integer playerLevelCap;
        private Integer bossAiLevel;
        private Integer snapshotVersion;
        private boolean locked;
        private boolean defeated;
        private List<UnitData> units = new ArrayList<>();
    }
}
