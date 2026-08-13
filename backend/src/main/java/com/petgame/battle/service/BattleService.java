package com.petgame.battle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.engine.TurnResult;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.config.model.TestBattleConfig;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗服务（阶段 3）。
 * <p>
 * 管理内存中的战斗上下文（技术方案 §20-§21）：战斗临时数据只存服务器内存，
 * 战斗过程零数据库写入，服务重启后未完成战斗直接丢弃。
 * <p>
 * 阶段 3 仅提供测试战斗入口（固定敌方阵容），玩家队伍取自当前激活队伍。
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final GameConfigRegistry registry;
    private final BattleEngine engine;
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;

    /** 战斗上下文内存池：battleId → BattleContext。不落库。 */
    private final Map<String, BattleContext> battles = new ConcurrentHashMap<>();

    public BattleService(GameConfigRegistry registry,
                         WildEnemyDecisionProvider enemyDecisionProvider,
                         PlayerMapper playerMapper,
                         PlayerPetMapper playerPetMapper,
                         PlayerTeamMapper playerTeamMapper,
                         PlayerTeamMemberMapper playerTeamMemberMapper) {
        this.registry = registry;
        this.engine = new BattleEngine(registry, enemyDecisionProvider);
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
    }

    /**
     * 开始测试战斗：当前激活队伍 VS test-battle.yml 固定敌方阵容。
     *
     * @param seed 随机种子（null = 随机），固定种子可复现完全一致的战斗
     */
    public BattleSnapshot startTestBattle(Long seed) {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }

        List<PlayerPetEntity> teamPets = loadActiveTeamPets(player.getSaveId());
        if (teamPets.isEmpty()) {
            throw new BusinessException("NO_BATTLE_UNITS", "当前激活队伍没有可参战宠物");
        }

        long battleSeed = seed != null ? seed : System.nanoTime();
        String battleId = UUID.randomUUID().toString();
        BattleContext ctx = new BattleContext(battleId, battleSeed);

        ctx.setPlayerSide(buildPlayerSide(teamPets));
        ctx.setEnemySide(buildEnemySide());

        engine.startBattle(ctx);
        battles.put(battleId, ctx);

        log.info("测试战斗开始：battleId={}, seed={}, 玩家单位={}, 敌方单位={}",
                battleId, battleSeed, ctx.getPlayerSide().getUnits().size(),
                ctx.getEnemySide().getUnits().size());

        // 开战事件（登场被动等）一并返回
        return toSnapshot(ctx, new ArrayList<>(ctx.getEvents()));
    }

    /**
     * 提交玩家行动意图，结算一整个回合。
     */
    public BattleSnapshot submitActions(String battleId, List<BattleAction> actions) {
        BattleContext ctx = requireBattle(battleId);
        TurnResult result = engine.playTurn(ctx, actions);
        return toSnapshot(ctx, result.getEvents());
    }

    /**
     * 查询战斗当前状态。
     */
    public BattleSnapshot getBattle(String battleId) {
        BattleContext ctx = requireBattle(battleId);
        return toSnapshot(ctx, List.of());
    }

    private BattleContext requireBattle(String battleId) {
        BattleContext ctx = battles.get(battleId);
        if (ctx == null) {
            throw new BusinessException("BATTLE_NOT_FOUND", "战斗不存在或已结束清理: " + battleId);
        }
        return ctx;
    }

    // ---- 队伍构建 ----

    /** 加载当前激活队伍的宠物（按队伍位置升序）。 */
    private List<PlayerPetEntity> loadActiveTeamPets(String saveId) {
        PlayerTeamEntity team = playerTeamMapper.selectOne(
                new LambdaQueryWrapper<PlayerTeamEntity>()
                        .eq(PlayerTeamEntity::getSaveId, saveId)
                        .eq(PlayerTeamEntity::getIsActive, true)
                        .last("LIMIT 1"));
        if (team == null) {
            return List.of();
        }
        List<PlayerTeamMemberEntity> members = playerTeamMemberMapper.selectList(
                new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                        .eq(PlayerTeamMemberEntity::getTeamId, team.getId())
                        .orderByAsc(PlayerTeamMemberEntity::getPosition));
        List<PlayerPetEntity> pets = new ArrayList<>();
        for (PlayerTeamMemberEntity member : members) {
            PlayerPetEntity pet = playerPetMapper.selectById(member.getPetId());
            if (pet != null) {
                pets.add(pet);
            }
        }
        return pets;
    }

    private BattleSide buildPlayerSide(List<PlayerPetEntity> pets) {
        SystemRuleConfig rules = registry.getSystemRules();
        int activeSlots = Math.min(rules.getStandardBattleSlots(), pets.size());

        BattleSide side = new BattleSide("PLAYER");
        int index = 0;
        for (PlayerPetEntity pet : pets) {
            if (index >= rules.getMaxCarryPets()) {
                break;
            }
            InitialPetsConfig.InitialPetOption option = findSpeciesOption(pet.getSpeciesId());
            if (option == null) {
                throw new BusinessException("SPECIES_CONFIG_MISSING",
                        "宠物种族配置缺失: " + pet.getSpeciesId());
            }
            side.getUnits().add(buildPlayerUnit(pet, option, index < activeSlots ? index : -1));
            index++;
        }
        return side;
    }

    private InitialPetsConfig.InitialPetOption findSpeciesOption(String speciesId) {
        return registry.getInitialPetsConfig().getInitialPets().stream()
                .filter(p -> p.getSpeciesId().equals(speciesId))
                .findFirst().orElse(null);
    }

    /**
     * 玩家宠物 → 战斗单位。
     * <p>
     * 面板属性链路（需求 §9）：种族基础 + 等级固定成长 + 资质成长修正 + 自由属性点。
     * 个体浮动在新游戏/捕获时已固化到存档，战斗内不再浮动。
     * 战斗 Buff/Debuff 由引擎状态体系在运行时叠加，不进面板。
     */
    private BattleUnit buildPlayerUnit(PlayerPetEntity pet, InitialPetsConfig.InitialPetOption option,
                                       int position) {
        SystemRuleConfig rules = registry.getSystemRules();
        int levelBonus = Math.max(0, pet.getLevel() - 1);

        BattleUnit unit = new BattleUnit();
        unit.setUnitId("P_" + pet.getId());
        unit.setPetDbId(pet.getId());
        unit.setName(pet.getNickname() != null && !pet.getNickname().isBlank()
                ? pet.getNickname() : option.getName());
        unit.setElement(option.getElement());
        unit.setLevel(pet.getLevel());

        unit.setMaxHp(computeHp(option.getBaseHp(), pet.getHpAptitude(),
                pet.getFreePointHp(), levelBonus, rules));
        unit.setStrength(computeStat(option.getBaseStrength(), pet.getStrengthAptitude(),
                pet.getFreePointStrength(), levelBonus, rules));
        unit.setSpirit(computeStat(option.getBaseSpirit(), pet.getSpiritAptitude(),
                pet.getFreePointSpirit(), levelBonus, rules));
        unit.setDefense(computeStat(option.getBaseDefense(), pet.getDefenseAptitude(),
                pet.getFreePointDefense(), levelBonus, rules));
        unit.setResistance(computeStat(option.getBaseResistance(), pet.getResistanceAptitude(),
                pet.getFreePointResistance(), levelBonus, rules));
        unit.setSpeed(computeStat(option.getBaseSpeed(), pet.getSpeedAptitude(),
                pet.getFreePointSpeed(), levelBonus, rules));

        // HP 跨战斗保留：取存档当前 HP，非法值回满
        int currentHp = pet.getCurrentHp() != null ? pet.getCurrentHp() : unit.getMaxHp();
        unit.setCurrentHp(Math.min(Math.max(currentHp, 1), unit.getMaxHp()));

        unit.setActive(position >= 0);
        unit.setPosition(position);

        for (InitialPetsConfig.InitialSkillSlot slot : option.getSkills()) {
            if (registry.getSkill(slot.getSkillId()) != null) {
                unit.getSkillIds().add(slot.getSkillId());
            }
        }
        for (String passiveId : option.getPassives()) {
            PassiveSkillConfig passive = registry.getPassive(passiveId);
            if (passive != null) {
                unit.getPassives().add(passive);
            }
        }
        return unit;
    }

    /** 非 HP 面板 = 基础 + 等级成长 + 资质修正（资质 50 为中性）+ 自由点。 */
    private int computeStat(int base, int aptitude, int freePoints, int levelBonus, SystemRuleConfig rules) {
        return (int) Math.round(base
                + rules.getLevelStatGrowth() * levelBonus
                + base * (aptitude - 50) / 100.0
                + freePoints * rules.getFreePointStatValue());
    }

    /** HP 面板（独立成长与自由点系数）。 */
    private int computeHp(int baseHp, int aptitude, int freePoints, int levelBonus, SystemRuleConfig rules) {
        return (int) Math.round(baseHp
                + rules.getLevelHpGrowth() * levelBonus
                + baseHp * (aptitude - 50) / 100.0
                + freePoints * rules.getFreePointHpValue());
    }

    /** 敌方阵容：test-battle.yml 配置，以与玩家单位完全相同的路径进入引擎。 */
    private BattleSide buildEnemySide() {
        TestBattleConfig config = registry.getTestBattleConfig();
        int activeSlots = registry.getSystemRules().getStandardBattleSlots();

        BattleSide side = new BattleSide("ENEMY");
        int index = 0;
        for (TestBattleConfig.TestEnemyUnit enemy : config.getEnemies()) {
            BattleUnit unit = new BattleUnit();
            unit.setUnitId(enemy.getUnitId());
            unit.setName(enemy.getName());
            unit.setElement(enemy.getElement());
            unit.setLevel(enemy.getLevel());
            unit.setMaxHp(enemy.getMaxHp());
            unit.setStrength(enemy.getStrength());
            unit.setSpirit(enemy.getSpirit());
            unit.setDefense(enemy.getDefense());
            unit.setResistance(enemy.getResistance());
            unit.setSpeed(enemy.getSpeed());
            unit.setCurrentHp(enemy.getMaxHp());
            unit.setActive(index < activeSlots);
            unit.setPosition(index < activeSlots ? index : -1);
            unit.getSkillIds().addAll(enemy.getSkillIds());
            for (String passiveId : enemy.getPassiveIds()) {
                PassiveSkillConfig passive = registry.getPassive(passiveId);
                if (passive != null) {
                    unit.getPassives().add(passive);
                }
            }
            side.getUnits().add(unit);
            index++;
        }
        return side;
    }

    // ---- 快照 ----

    private BattleSnapshot toSnapshot(BattleContext ctx, List<BattleEvent> events) {
        BattleSnapshot snapshot = new BattleSnapshot();
        snapshot.setBattleId(ctx.getBattleId());
        snapshot.setSeed(ctx.getRandomSeed());
        snapshot.setCurrentRound(ctx.getCurrentRound());
        snapshot.setFinished(ctx.isFinished());
        snapshot.setWinner(ctx.getWinner());
        snapshot.setPlayerUnits(ctx.getPlayerSide().getUnits().stream().map(this::toUnitSnapshot).toList());
        snapshot.setEnemyUnits(ctx.getEnemySide().getUnits().stream().map(this::toUnitSnapshot).toList());
        snapshot.setEvents(events);
        return snapshot;
    }

    private UnitSnapshot toUnitSnapshot(BattleUnit unit) {
        UnitSnapshot snapshot = new UnitSnapshot();
        snapshot.setUnitId(unit.getUnitId());
        snapshot.setName(unit.getName());
        snapshot.setElement(unit.getElement());
        snapshot.setLevel(unit.getLevel());
        snapshot.setMaxHp(unit.getMaxHp());
        snapshot.setCurrentHp(unit.getCurrentHp());
        snapshot.setShield(unit.getShield());
        snapshot.setStrength(unit.getStrength());
        snapshot.setSpirit(unit.getSpirit());
        snapshot.setDefense(unit.getDefense());
        snapshot.setResistance(unit.getResistance());
        snapshot.setSpeed(unit.getSpeed());
        snapshot.setAlive(unit.isAlive());
        snapshot.setActive(unit.isActive());
        snapshot.setPosition(unit.getPosition());
        snapshot.setDefending(unit.isDefending());
        snapshot.setCharging(unit.getChargingSkillId() != null);
        snapshot.setChargingSkillId(unit.getChargingSkillId());
        snapshot.setChargeRemaining(unit.getChargeRemaining());
        snapshot.getSkillIds().addAll(unit.getSkillIds());
        snapshot.getCooldowns().putAll(unit.getCooldowns());
        for (StatusInstance status : unit.getStatuses()) {
            StatusEffectConfig config = registry.getStatus(status.getStatusId());
            snapshot.getStatuses().add(new UnitSnapshot.StatusView(
                    status.getStatusId(),
                    config != null ? config.getName() : status.getStatusId(),
                    config != null ? config.getCategory() : "DEBUFF",
                    status.getRemainingTurns()));
        }
        return snapshot;
    }
}
