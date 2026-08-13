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
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.config.model.TestBattleConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗服务（阶段 3 起提供战斗流程；阶段 4 接入结算）。
 * <p>
 * 管理内存中的战斗上下文（技术方案 §20-§21）：战斗临时数据只存服务器内存，
 * 战斗过程零数据库写入，服务重启后未完成战斗直接丢弃。
 * <p>
 * 阶段 4 关键约束（需求 §17/§85）：
 * <ul>
 *   <li>面板属性公式统一走 {@link PetGrowthService#computePanelStats}，禁止多套公式。</li>
 *   <li>战斗结算（HP 回写、经验池、金币、掉落）必须在同一事务内完成，避免部分成功脏数据。</li>
 *   <li>所有战斗经验统一进入玩家公共经验池，不直接发给参战宠物。</li>
 *   <li>HP 跨战斗保留：战斗结束后将 currentHp 回写到 player_pet。</li>
 *   <li>已结算的战斗不可重复结算。</li>
 * </ul>
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final GameConfigRegistry registry;
    private final BattleEngine engine;
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PetGrowthService growthService;

    /** 战斗上下文内存池：battleId → BattleContext。不落库。 */
    private final Map<String, BattleContext> battles = new ConcurrentHashMap<>();

    /** 已结算战斗 ID 集合，防止重复结算。 */
    private final Set<String> settledBattles = ConcurrentHashMap.newKeySet();

    public BattleService(GameConfigRegistry registry,
                         WildEnemyDecisionProvider enemyDecisionProvider,
                         PlayerMapper playerMapper,
                         PlayerPetMapper playerPetMapper,
                         PlayerPetSkillMapper playerPetSkillMapper,
                         PlayerTeamMapper playerTeamMapper,
                         PlayerTeamMemberMapper playerTeamMemberMapper,
                         PlayerInventoryMapper playerInventoryMapper,
                         PetGrowthService growthService) {
        this.registry = registry;
        this.engine = new BattleEngine(registry, enemyDecisionProvider);
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.growthService = growthService;
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

    /**
     * 战斗结算（阶段 4 需求 §17/§85）。
     * <p>
     * 必须在战斗已结束（finished=true）后调用，将战斗结果持久化到存档：
     * <ul>
     *   <li>HP 回写：所有参战玩家宠物 currentHp 写回 player_pet（HP 跨战斗保留，胜负均回写）。</li>
     *   <li>奖励发放：仅 PLAYER 胜方发放，经验进公共经验池、金币进玩家金币、掉落进背包。</li>
     *   <li>统计累加：参战宠物 battle_count +1；胜方宠物 win_count +1。</li>
     * </ul>
     * 以上全部在单个 {@code @Transactional} 事务内完成，任一失败回滚，避免部分成功脏数据。
     * <p>
     * 已结算的战斗不可重复结算（返回 BATTLE_ALREADY_SETTLED）。
     *
     * @param battleId 战斗 ID
     * @return 结算结果摘要
     */
    @Transactional
    public BattleSettlement settleBattle(String battleId) {
        BattleContext ctx = requireBattle(battleId);
        if (!ctx.isFinished()) {
            throw new BusinessException("BATTLE_NOT_FINISHED", "战斗尚未结束，无法结算");
        }
        if (settledBattles.contains(battleId)) {
            throw new BusinessException("BATTLE_ALREADY_SETTLED", "战斗已结算，不可重复结算: " + battleId);
        }

        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }

        boolean playerWon = "PLAYER".equals(ctx.getWinner());
        BattleSettlement settlement = new BattleSettlement();
        settlement.setBattleId(battleId);
        settlement.setWinner(ctx.getWinner());
        settlement.setPlayerWon(playerWon);

        // 1. HP 回写 + 统计累加（所有参战玩家宠物，胜负均执行）
        List<BattleSettlement.PetHpWriteback> hpWritebacks = new ArrayList<>();
        for (BattleUnit unit : ctx.getPlayerSide().getUnits()) {
            if (unit.getPetDbId() == null) {
                continue;
            }
            PlayerPetEntity pet = playerPetMapper.selectById(unit.getPetDbId());
            if (pet == null) {
                continue;
            }
            int beforeHp = pet.getCurrentHp() != null ? pet.getCurrentHp() : 0;
            int afterHp = Math.max(0, Math.min(unit.getCurrentHp(), unit.getMaxHp()));
            pet.setCurrentHp(afterHp);
            pet.setBattleCount(nz(pet.getBattleCount()) + 1);
            if (playerWon) {
                pet.setWinCount(nz(pet.getWinCount()) + 1);
            }
            playerPetMapper.updateById(pet);

            BattleSettlement.PetHpWriteback wb = new BattleSettlement.PetHpWriteback();
            wb.setPetId(pet.getId());
            wb.setName(unit.getName());
            wb.setBeforeHp(beforeHp);
            wb.setAfterHp(afterHp);
            wb.setMaxHp(unit.getMaxHp());
            wb.setAlive(unit.isAlive());
            hpWritebacks.add(wb);
        }
        settlement.setHpWritebacks(hpWritebacks);

        // 2. 奖励发放（仅 PLAYER 胜方）
        if (playerWon) {
            TestBattleConfig.BattleReward rewards = registry.getTestBattleConfig().getRewards();
            int expGained = rewards.getExp();
            int goldGained = rewards.getGold();

            if (expGained > 0) {
                player.setExpPool(player.getExpPool() + expGained);
            }
            if (goldGained > 0) {
                player.setGold(player.getGold() + goldGained);
            }
            playerMapper.updateById(player);

            settlement.setExpGained(expGained);
            settlement.setGoldGained(goldGained);

            // 掉落：按 chance 概率掉落，使用战斗上下文随机源保证可复现
            List<BattleSettlement.DropResult> drops = new ArrayList<>();
            for (TestBattleConfig.DropEntry drop : rewards.getDrops()) {
                if (ctx.getRandom().chance(drop.getChance())) {
                    ItemConfig item = registry.getItem(drop.getItemId());
                    if (item == null) {
                        log.warn("掉落道具配置缺失，跳过: {}", drop.getItemId());
                        continue;
                    }
                    addInventoryItem(player.getSaveId(), drop.getItemId(), drop.getQuantity());
                    BattleSettlement.DropResult dr = new BattleSettlement.DropResult();
                    dr.setItemId(drop.getItemId());
                    dr.setName(item.getName());
                    dr.setQuantity(drop.getQuantity());
                    drops.add(dr);
                }
            }
            settlement.setDrops(drops);
        }

        // 3. 标记已结算并清理内存
        settledBattles.add(battleId);
        // 保留战斗上下文一段时间，供前端再次查询最终状态；定期清理交给后续阶段或重启
        log.info("战斗结算完成：battleId={}, 胜方={}, 经验+{}, 金币+{}, 掉落 {} 项, HP 回写 {} 只",
                battleId, ctx.getWinner(),
                settlement.getExpGained(), settlement.getGoldGained(),
                settlement.getDrops().size(), hpWritebacks.size());

        return settlement;
    }

    /** 增加玩家背包道具数量（已存在则累加，不存在则新增）。 */
    private void addInventoryItem(String saveId, String itemId, int quantity) {
        PlayerInventoryEntity existing = playerInventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, saveId)
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            playerInventoryMapper.updateById(existing);
        } else {
            PlayerInventoryEntity inv = new PlayerInventoryEntity();
            inv.setSaveId(saveId);
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            playerInventoryMapper.insert(inv);
        }
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
     * 面板属性统一走 {@link PetGrowthService#computePanelStats}（需求 §9/§12）：
     * 种族基础（含个体浮动）+ 等级固定成长 + 资质成长修正 + 自由属性点。
     * 战斗 Buff/Debuff 由引擎状态体系在运行时叠加，不进面板。
     */
    private BattleUnit buildPlayerUnit(PlayerPetEntity pet, InitialPetsConfig.InitialPetOption option,
                                       int position) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("P_" + pet.getId());
        unit.setPetDbId(pet.getId());
        unit.setName(pet.getNickname() != null && !pet.getNickname().isBlank()
                ? pet.getNickname() : option.getName());
        unit.setElement(option.getElement());
        unit.setLevel(pet.getLevel());

        // 统一面板公式：与 PetService 详情页、加点预览完全一致
        PetPanelStats stats = growthService.computePanelStats(pet, option);
        unit.setMaxHp(stats.getMaxHp());
        unit.setStrength(stats.getStrength());
        unit.setSpirit(stats.getSpirit());
        unit.setDefense(stats.getDefense());
        unit.setResistance(stats.getResistance());
        unit.setSpeed(stats.getSpeed());

        // HP 跨战斗保留：取存档当前 HP，倒下宠物保持 0HP 参战（需求 §45，需恢复道具/营地恢复），
        // 超出上限或负数的异常值封顶/归零
        int currentHp = pet.getCurrentHp() != null ? pet.getCurrentHp() : unit.getMaxHp();
        unit.setCurrentHp(Math.max(0, Math.min(currentHp, unit.getMaxHp())));

        unit.setActive(position >= 0);
        unit.setPosition(position);

        // 已装备技能从 player_pet_skill 表加载（slot 不为 null），最多 4 个主动技能参战
        List<PlayerPetSkillEntity> equippedSkills = playerPetSkillMapper.selectList(
                new LambdaQueryWrapper<PlayerPetSkillEntity>()
                        .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                        .isNotNull(PlayerPetSkillEntity::getSlot)
                        .orderByAsc(PlayerPetSkillEntity::getSlot));
        for (PlayerPetSkillEntity ps : equippedSkills) {
            if (registry.getSkill(ps.getSkillId()) != null) {
                unit.getSkillIds().add(ps.getSkillId());
            }
        }
        // 被动技能按种族配置自动生效（不进 player_pet_skill 表）
        for (String passiveId : option.getPassives()) {
            PassiveSkillConfig passive = registry.getPassive(passiveId);
            if (passive != null) {
                unit.getPassives().add(passive);
            }
        }
        return unit;
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

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== 结算 DTO ====================

    /** 战斗结算结果摘要。 */
    @lombok.Data
    public static class BattleSettlement {

        /** 战斗 ID。 */
        private String battleId;

        /** 胜方：PLAYER / ENEMY。 */
        private String winner;

        /** 玩家是否获胜。 */
        private boolean playerWon;

        /** 经验池增加量（仅胜方 > 0）。 */
        private int expGained;

        /** 金币增加量（仅胜方 > 0）。 */
        private int goldGained;

        /** 掉落道具列表。 */
        private List<DropResult> drops = new ArrayList<>();

        /** 参战宠物 HP 回写明细。 */
        private List<PetHpWriteback> hpWritebacks = new ArrayList<>();

        /** 单个掉落结果。 */
        @lombok.Data
        public static class DropResult {
            private String itemId;
            private String name;
            private int quantity;
        }

        /** 单只宠物 HP 回写明细。 */
        @lombok.Data
        public static class PetHpWriteback {
            private Long petId;
            private String name;
            private int beforeHp;
            private int afterHp;
            private int maxHp;
            private boolean alive;
        }
    }
}
