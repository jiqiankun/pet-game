package com.petgame.battle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.battle.ai.BossDecisionProvider;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.battle.calculator.CaptureCalculator;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.engine.TurnResult;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.BattleUnit.WildUnitData;
import com.petgame.battle.model.StatusInstance;
import com.petgame.capture.WildEncounterService;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.EncountersConfig;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
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
import com.petgame.team.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.petgame.pokedex.service.PokedexService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.petgame.quest.service.QuestService;

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
    /** Boss 战专用引擎实例（同一 BattleEngine 类，敌方决策为 BossDecisionProvider，阶段 7）。 */
    private final BattleEngine bossEngine;
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PetGrowthService growthService;
    private final WildEncounterService wildEncounterService;
    private final TeamService teamService;
    private final com.petgame.map.service.MapExplorationService mapExplorationService;
    private final PokedexService pokedexService;
    private final QuestService questService;

    /** 战斗上下文内存池：battleId → BattleContext。不落库。 */
    private final Map<String, BattleContext> battles = new ConcurrentHashMap<>();

    /** 已结算战斗 ID 集合，防止重复结算。 */
    private final Set<String> settledBattles = ConcurrentHashMap.newKeySet();

    public BattleService(GameConfigRegistry registry,
                         WildEnemyDecisionProvider enemyDecisionProvider,
                         BossDecisionProvider bossDecisionProvider,
                         PlayerMapper playerMapper,
                         PlayerPetMapper playerPetMapper,
                         PlayerPetSkillMapper playerPetSkillMapper,
                         PlayerTeamMapper playerTeamMapper,
                         PlayerTeamMemberMapper playerTeamMemberMapper,
                         PlayerInventoryMapper playerInventoryMapper,
                         PetGrowthService growthService,
                         WildEncounterService wildEncounterService,
                         TeamService teamService,
                         com.petgame.map.service.MapExplorationService mapExplorationService,
                         PokedexService pokedexService,
                         @Lazy QuestService questService) {
        this.registry = registry;
        this.engine = new BattleEngine(registry, enemyDecisionProvider);
        this.bossEngine = new BattleEngine(registry, bossDecisionProvider);
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.growthService = growthService;
        this.wildEncounterService = wildEncounterService;
        this.teamService = teamService;
        this.mapExplorationService = mapExplorationService;
        this.pokedexService = pokedexService;
        this.questService = questService;
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
        requireFightablePet(teamPets);

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
     * 开始野生战斗（阶段 5 捕捉）：当前激活队伍 VS 刷新组生成的野生阵容。
     * <p>
     * 开战时快照玩家背包捕捉球存量到战斗上下文（战斗内数量校验用，
     * 战斗过程零数据库写入，结算时统一扣除）。
     */
    public BattleSnapshot startWildBattle(String groupId, Long seed) {
        PlayerEntity player = requirePlayer();
        List<PlayerPetEntity> teamPets = loadActiveTeamPets(player.getSaveId());
        if (teamPets.isEmpty()) {
            throw new BusinessException("NO_BATTLE_UNITS", "当前激活队伍没有可参战宠物");
        }
        requireFightablePet(teamPets);

        long battleSeed = seed != null ? seed : System.nanoTime();
        String battleId = UUID.randomUUID().toString();
        BattleContext ctx = new BattleContext(battleId, battleSeed);
        ctx.setBattleType("WILD");
        ctx.setEncounterGroupId(groupId);
        ctx.setPlayerSide(buildPlayerSide(teamPets));

        // 野生敌方：与测试敌人同一引擎入口（野生单位携带捕捉落库数据）
        BattleSide enemySide = new BattleSide("ENEMY");
        List<BattleUnit> wildUnits = wildEncounterService.generateEncounter(groupId, ctx.getRandom());
        enemySide.getUnits().addAll(wildUnits);
        ctx.setEnemySide(enemySide);

        // 阶段 8：遭遇时发现记录
        Set<String> enemySpeciesIds = new LinkedHashSet<>();
        for (BattleUnit unit : wildUnits) {
            if (unit.getSpeciesId() != null) {
                enemySpeciesIds.add(unit.getSpeciesId());
            }
        }
        for (String sid : enemySpeciesIds) {
            try {
                pokedexService.recordDiscovery(player.getSaveId(), sid);
            } catch (Exception e) {
                log.warn("图鉴发现记录失败（不阻断战斗）：species={}", sid, e);
            }
        }

        // 捕捉球存量快照（仅捕捉球道具）
        for (PlayerInventoryEntity inv : loadCaptureBalls(player.getSaveId())) {
            ctx.getAvailableCaptureBalls().put(inv.getItemId(), inv.getQuantity());
        }

        engine.startBattle(ctx);
        battles.put(battleId, ctx);

        log.info("野生战斗开始：battleId={}, groupId={}, seed={}, 玩家单位={}, 敌方单位={}",
                battleId, groupId, battleSeed, ctx.getPlayerSide().getUnits().size(),
                ctx.getEnemySide().getUnits().size());

        return toSnapshot(ctx, new ArrayList<>(ctx.getEvents()));
    }

    /**
     * 查询当前野生战斗内、玩家存量足够的捕捉球对各存活上场野生单位的捕捉率（前端展示用）。
     */
    public List<CaptureRateView> getCaptureRates(String battleId) {
        BattleContext ctx = requireBattle(battleId);
        if (!"WILD".equals(ctx.getBattleType())) {
            return List.of();
        }
        SystemRuleConfig rules = registry.getSystemRules();
        List<CaptureRateView> views = new ArrayList<>();
        for (BattleUnit target : ctx.getEnemySide().getActiveAliveUnits()) {
            PetSpeciesConfig species = registry.getSpecies(target.getSpeciesId());
            if (species == null || target.isCaptured()) {
                continue;
            }
            double hpRatio = target.getMaxHp() > 0
                    ? (double) target.getCurrentHp() / target.getMaxHp() : 0.0;
            int statusCount = CaptureCalculator.countCaptureBonusStatuses(target, registry.getStatusIndex());
            for (Map.Entry<String, Integer> entry : ctx.getAvailableCaptureBalls().entrySet()) {
                int used = ctx.getConsumedCaptureBalls().getOrDefault(entry.getKey(), 0);
                if (used >= entry.getValue()) {
                    continue; // 本场已用完该球
                }
                ItemConfig ball = registry.getItem(entry.getKey());
                if (ball == null || !"CAPTURE_BALL".equals(ball.getItemType())) {
                    continue;
                }
                double rate = CaptureCalculator.computeCaptureRate(species.getCaptureRate(), hpRatio,
                        statusCount, ball.getValue(), 1.0, rules);
                CaptureRateView view = new CaptureRateView();
                view.setUnitId(target.getUnitId());
                view.setUnitName(target.getName());
                view.setBallItemId(ball.getId());
                view.setBallName(ball.getName());
                view.setRate(Math.round(rate * 1000.0) / 1000.0);
                views.add(view);
            }
        }
        return views;
    }

    /**
     * 开发者模式临时补充捕捉球（阶段 5 过渡方案）。
     * <p>
     * 捕捉球正式获取途径（商店/掉落）属后续阶段，本阶段仅新游戏赠送；
     * 为避免球用完后无法继续体验捕捉，提供开发者模式入口：每种捕捉球 +5。
     *
     * @return itemId → 补充后的存量
     */
    @Transactional
    public Map<String, Integer> devRefillCaptureBalls() {
        PlayerEntity player = requirePlayer();
        Map<String, Integer> result = new HashMap<>();
        for (ItemConfig item : registry.getItemsConfig().getItems()) {
            if (!"CAPTURE_BALL".equals(item.getItemType())) {
                continue;
            }
            addInventoryItem(player.getSaveId(), item.getId(), 5);
            PlayerInventoryEntity inv = playerInventoryMapper.selectOne(
                    new LambdaQueryWrapper<PlayerInventoryEntity>()
                            .eq(PlayerInventoryEntity::getSaveId, player.getSaveId())
                            .eq(PlayerInventoryEntity::getItemId, item.getId()));
            result.put(item.getId(), inv != null ? inv.getQuantity() : 0);
        }
        return result;
    }

    /**
     * 开发者模式临时补充技能（REV-014）：为存档内全部宠物学会指定技能（来源记 SKILL_BOOK）。
     * 留生一击正式获取途径（商店/教学赠书）属阶段 9/10，本入口仅供阶段 5 验收与存量存档补充。
     *
     * @return 实际学会该技能的宠物数量
     */
    @Transactional
    public int devGrantSkill(String skillId) {
        PlayerEntity player = requirePlayer();
        if (skillId == null || registry.getSkill(skillId) == null) {
            throw new BusinessException("INVALID_SKILL", "技能不存在: " + skillId);
        }
        List<PlayerPetEntity> pets = playerPetMapper.selectList(
                new LambdaQueryWrapper<PlayerPetEntity>()
                        .eq(PlayerPetEntity::getSaveId, player.getSaveId()));
        int granted = 0;
        for (PlayerPetEntity pet : pets) {
            Long exists = playerPetSkillMapper.selectCount(
                    new LambdaQueryWrapper<PlayerPetSkillEntity>()
                            .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                            .eq(PlayerPetSkillEntity::getSkillId, skillId));
            if (exists != null && exists > 0) {
                continue;
            }
            // 槽位未满 4 个时自动装备，否则仅入库（REV-011 同款规则）
            int equippedCount = Math.toIntExact(playerPetSkillMapper.selectCount(
                    new LambdaQueryWrapper<PlayerPetSkillEntity>()
                            .eq(PlayerPetSkillEntity::getPetId, pet.getId())
                            .isNotNull(PlayerPetSkillEntity::getSlot)));
            PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
            petSkill.setPetId(pet.getId());
            petSkill.setSkillId(skillId);
            petSkill.setSourceType("SKILL_BOOK");
            petSkill.setSlot(equippedCount < 4 ? equippedCount + 1 : null);
            playerPetSkillMapper.insert(petSkill);
            granted++;
        }
        log.info("开发者补充技能：skillId={}，学会宠物数 {}", skillId, granted);
        return granted;
    }

    /** 查询玩家背包中的捕捉球存量。 */
    private List<PlayerInventoryEntity> loadCaptureBalls(String saveId) {
        List<PlayerInventoryEntity> all = playerInventoryMapper.selectList(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, saveId));
        List<PlayerInventoryEntity> balls = new ArrayList<>();
        for (PlayerInventoryEntity inv : all) {
            ItemConfig item = registry.getItem(inv.getItemId());
            if (item != null && "CAPTURE_BALL".equals(item.getItemType())) {
                balls.add(inv);
            }
        }
        return balls;
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    /**
     * HP 持续消耗规则（阶段 6，需求 §45）：全队倒下（全部 0 HP）时不可开战，
     * 需先用药品/复苏道具或营地恢复。
     */
    private void requireFightablePet(List<PlayerPetEntity> teamPets) {
        boolean anyFightable = teamPets.stream()
                .anyMatch(p -> p.getCurrentHp() != null && p.getCurrentHp() > 0);
        if (!anyFightable) {
            throw new BusinessException("NO_FIGHTABLE_PETS",
                    "队伍中所有宠物均已倒下，请先使用药品、复苏道具或回营地恢复");
        }
    }

    /**
     * 是否存在未结束的战斗（阶段 6：战斗中禁止队伍预设切换等敏感操作）。
     */
    public boolean hasActiveBattle() {
        for (BattleContext ctx : battles.values()) {
            if (!ctx.isFinished()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提交玩家行动意图，结算一整个回合。
     * <p>
     * BOSS 战斗路由到 bossEngine（敌方决策 = BossDecisionProvider），
     * TEST/WILD 仍用默认引擎（WildEnemyDecisionProvider）。
     */
    public BattleSnapshot submitActions(String battleId, List<BattleAction> actions) {
        BattleContext ctx = requireBattle(battleId);
        TurnResult result = engineFor(ctx).playTurn(ctx, actions);
        return toSnapshot(ctx, result.getEvents());
    }

    /** 按战斗类型选择引擎实例（同一 BattleEngine 类，仅 DecisionProvider 不同）。 */
    private BattleEngine engineFor(BattleContext ctx) {
        return "BOSS".equals(ctx.getBattleType()) ? bossEngine : engine;
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
        return settleBattle(battleId, false);
    }

    @Transactional
    public BattleSettlement settleBattle(String battleId, boolean joinTeam) {
        BattleContext ctx = requireBattle(battleId);
        if (!ctx.isFinished()) {
            throw new BusinessException("BATTLE_NOT_FINISHED", "战斗尚未结束，无法结算");
        }
        if (settledBattles.contains(battleId)) {
            throw new BusinessException("BATTLE_ALREADY_SETTLED", "战斗已结算，不可重复结算: " + battleId);
        }

        PlayerEntity player = requirePlayer();

        boolean playerWon = "PLAYER".equals(ctx.getWinner());
        BattleSettlement settlement = new BattleSettlement();
        settlement.setBattleId(battleId);
        settlement.setWinner(ctx.getWinner());
        settlement.setPlayerWon(playerWon);
        settlement.setFled(ctx.isFled());

        // 1. HP 回写 + 统计累加（所有参战玩家宠物，胜负均执行）
        List<BattleSettlement.PetHpWriteback> hpWritebacks = new ArrayList<>();
        Set<String> participantSpecies = new LinkedHashSet<>();
        Set<String> winnerSpecies = new LinkedHashSet<>();
        for (BattleUnit unit : ctx.getPlayerSide().getUnits()) {
            if (unit.getSpeciesId() != null) {
                participantSpecies.add(unit.getSpeciesId());
                if (playerWon) {
                    winnerSpecies.add(unit.getSpeciesId());
                }
            }
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

        // 2. 奖励发放（仅 PLAYER 胜方；逃跑/战败均无奖励）
        if (playerWon) {
            if ("WILD".equals(ctx.getBattleType())) {
                settleWildRewards(ctx, player, settlement);
            } else {
                settleTestRewards(ctx, player, settlement);
            }
        }

        // 2.3 阶段 8：图鉴研究值 — 战斗参与/获胜
        try {
            if (!participantSpecies.isEmpty()) {
                pokedexService.recordBattleParticipation(player.getSaveId(), participantSpecies);
            }
            if (playerWon && !winnerSpecies.isEmpty()) {
                pokedexService.recordBattleWins(player.getSaveId(), winnerSpecies);
            }
        } catch (Exception e) {
            log.warn("图鉴战斗记录失败（不阻断结算）：{}", e.getMessage());
        }

        // 2.5 野生战斗：捕捉落库（被捕捉宠物）+ 捕捉球扣除（无论成败均消耗）
        if ("WILD".equals(ctx.getBattleType())) {
            settleCaptures(ctx, player, settlement, joinTeam);
            consumeCaptureBalls(ctx, player);
        }

        // 2.6 阶段 9：任务系统事件钩子（REQUIRES_NEW 传播，失败不阻断主流程）
        if (playerWon && questService != null) {
            String saveId = player.getSaveId();
            // CAPTURE 事件
            for (BattleUnit captured : ctx.getCapturedUnits()) {
                if (captured.getSpeciesId() != null) {
                    questService.checkObjectiveProgress(saveId, "CAPTURE", captured.getSpeciesId(), 1);
                }
            }
            // DEFEAT 事件（未被捕捉的敌方宠物）
            Set<String> capturedSpeciesIds = new LinkedHashSet<>();
            for (BattleUnit captured : ctx.getCapturedUnits()) {
                capturedSpeciesIds.add(captured.getSpeciesId());
            }
            for (BattleUnit enemy : ctx.getEnemySide().getUnits()) {
                if (enemy.getSpeciesId() != null && !capturedSpeciesIds.contains(enemy.getSpeciesId())) {
                    questService.checkObjectiveProgress(saveId, "DEFEAT", enemy.getSpeciesId(), 1);
                }
            }
            // BOSS 战斗 DEFEAT_BOSS 事件
            if ("BOSS".equals(ctx.getBattleType()) && ctx.getBossId() != null) {
                questService.checkObjectiveProgress(saveId, "DEFEAT_BOSS", ctx.getBossId(), 1);
            }
        }

        // 2.7 战败流程（阶段 6，需求 §44）：零惩罚 + 返回最近恢复点 + 队伍恢复 + 嘲讽提示。
        //     逃跑成功同战败结算但不触发战败流程（玩家主动退出，队伍不自动恢复）。
        if (!playerWon && !ctx.isFled() && mapExplorationService != null) {
            settlement.setDefeat(mapExplorationService.handleDefeat(player));
        }

        // 3. 标记已结算并清理内存
        settledBattles.add(battleId);
        // 保留战斗上下文一段时间，供前端再次查询最终状态；定期清理交给后续阶段或重启
        log.info("战斗结算完成：battleId={}, 胜方={}, 逃跑={}, 经验+{}, 金币+{}, 掉落 {} 项, "
                        + "捕捉 {} 只, HP 回写 {} 只",
                battleId, ctx.getWinner(), ctx.isFled(),
                settlement.getExpGained(), settlement.getGoldGained(),
                settlement.getDrops().size(), settlement.getCapturedPets().size(), hpWritebacks.size());

        return settlement;
    }

    /** 测试战斗奖励：test-battle.yml 固定奖励（阶段 3/4 路径）。 */
    private void settleTestRewards(BattleContext ctx, PlayerEntity player, BattleSettlement settlement) {
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

    /**
     * 野生战斗奖励（用户裁决：遭遇组配置）。
     * 奖励 = expPerLevel/goldPerLevel × 敌等级 × 稀有度系数（system.yml），
     * 被捕捉宠物不参与奖励计算（需求 §50）。
     */
    private void settleWildRewards(BattleContext ctx, PlayerEntity player, BattleSettlement settlement) {
        EncountersConfig.EncounterGroup group = wildEncounterService.getEncounterGroup(ctx.getEncounterGroupId());
        SystemRuleConfig rules = registry.getSystemRules();

        double expGained = 0;
        double goldGained = 0;
        for (BattleUnit unit : ctx.getEnemySide().getUnits()) {
            if (unit.isCaptured()) {
                continue; // 被捕捉宠物不参与奖励
            }
            PetSpeciesConfig species = registry.getSpecies(unit.getSpeciesId());
            double rarityMultiplier = species == null ? 1.0
                    : rules.getWildRewardRarityMultiplier().getOrDefault(species.getRarity(), 1.0);
            expGained += group.getExpPerLevel() * unit.getLevel() * rarityMultiplier;
            goldGained += group.getGoldPerLevel() * unit.getLevel() * rarityMultiplier;
        }
        int expInt = (int) Math.round(expGained);
        int goldInt = (int) Math.round(goldGained);

        if (expInt > 0) {
            player.setExpPool(player.getExpPool() + expInt);
        }
        if (goldInt > 0) {
            player.setGold(player.getGold() + goldInt);
        }
        playerMapper.updateById(player);

        settlement.setExpGained(expInt);
        settlement.setGoldGained(goldInt);
    }

    /**
     * 捕捉落库（需求 §48/§49）：为每只被捕捉野生单位创建玩家宠物并学习技能。
     * <ul>
     *   <li>等级 = 野生单位等级；资质/个体浮动/特殊外观 = 遭遇生成时固化的数据；</li>
     *   <li>HP 保留捕捉时余量（HP 跨战斗保留原则）；</li>
     *   <li>已解锁种族技能按配置槽位自动装备，稀有技能仅学习不装备；</li>
     *   <li>自由属性点全 0（玩家可自由分配已获得点数）。</li>
     * </ul>
     */
    private void settleCaptures(BattleContext ctx, PlayerEntity player, BattleSettlement settlement,
                                boolean joinTeam) {
        for (BattleUnit captured : ctx.getCapturedUnits()) {
            PetSpeciesConfig species = registry.getSpecies(captured.getSpeciesId());
            BattleUnit.WildUnitData wd = captured.getWildData();
            if (species == null || wd == null) {
                log.warn("被捕捉单位数据缺失，跳过: {}", captured.getUnitId());
                continue;
            }

            PlayerPetEntity pet = new PlayerPetEntity();
            pet.setSaveId(player.getSaveId());
            pet.setSpeciesId(captured.getSpeciesId());
            pet.setLevel(captured.getLevel());
            pet.setCapturedLevel(captured.getLevel());
            pet.setHpAptitude(wd.getHpAptitude());
            pet.setStrengthAptitude(wd.getStrengthAptitude());
            pet.setSpiritAptitude(wd.getSpiritAptitude());
            pet.setDefenseAptitude(wd.getDefenseAptitude());
            pet.setResistanceAptitude(wd.getResistanceAptitude());
            pet.setSpeedAptitude(wd.getSpeedAptitude());
            pet.setBaseHpOffset(wd.getBaseHpOffset());
            pet.setBaseStrengthOffset(wd.getBaseStrengthOffset());
            pet.setBaseSpiritOffset(wd.getBaseSpiritOffset());
            pet.setBaseDefenseOffset(wd.getBaseDefenseOffset());
            pet.setBaseResistanceOffset(wd.getBaseResistanceOffset());
            pet.setBaseSpeedOffset(wd.getBaseSpeedOffset());
            pet.setFreePointHp(0);
            pet.setFreePointStrength(0);
            pet.setFreePointSpirit(0);
            pet.setFreePointDefense(0);
            pet.setFreePointResistance(0);
            pet.setFreePointSpeed(0);
            pet.setCurrentHp(Math.max(0, Math.min(captured.getCurrentHp(), captured.getMaxHp())));
            pet.setIsStarter(false);
            pet.setLocked(false);
            pet.setFavorite(false);
            pet.setSpecialAppearance(wd.getSpecialAppearance());
            pet.setCapturedMapId(player.getCurrentMapId());
            pet.setCapturedAt(LocalDateTime.now());
            pet.setBattleCount(0);
            pet.setWinCount(0);
            playerPetMapper.insert(pet);

            // 已解锁种族技能：按配置槽位自动装备
            for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
                if (slot.getUnlockLevel() > captured.getLevel()) {
                    continue;
                }
                PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
                petSkill.setPetId(pet.getId());
                petSkill.setSkillId(slot.getSkillId());
                petSkill.setSourceType("LEVEL_UP");
                petSkill.setSlot(slot.getSlot());
                playerPetSkillMapper.insert(petSkill);
            }
            // 稀有技能：仅学习不装备
            for (String extraSkillId : wd.getExtraSkillIds()) {
                boolean learned = species.getSkills().stream()
                        .anyMatch(s -> s.getSkillId().equals(extraSkillId));
                if (learned) {
                    continue;
                }
                PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
                petSkill.setPetId(pet.getId());
                petSkill.setSkillId(extraSkillId);
                petSkill.setSourceType("CAPTURE");
                petSkill.setSlot(null);
                playerPetSkillMapper.insert(petSkill);
            }

            BattleSettlement.CapturedPetView view = new BattleSettlement.CapturedPetView();
            view.setPetId(pet.getId());
            view.setSpeciesId(species.getId());
            view.setName(species.getName());
            view.setRarity(species.getRarity());
            view.setLevel(pet.getLevel());
            view.setSpecialAppearance(wd.getSpecialAppearance());
            view.setExtraSkillIds(wd.getExtraSkillIds());

            // 捕捉成功去向选择（需求 §48）：队伍未满 6 只且前端选择入队时直接加入队伍，
            // 否则留在仓库（宠物已落库，未入队即在仓库）
            if (joinTeam) {
                try {
                    int position = teamService.addPetToActiveTeam(pet.getId());
                    view.setTeamPosition(position);
                } catch (BusinessException e) {
                    // 队伍已满/已在队伍：静默留在仓库，不阻断结算
                    log.info("捕捉宠物未入队（{}）：petId={}", e.getErrorCode(), pet.getId());
                }
            }
            settlement.getCapturedPets().add(view);

            // 阶段 8：图鉴捕获记录
            try {
                WildUnitData captureWd = captured.getWildData();
                int[] apts = captureWd != null ? new int[]{
                        captureWd.getHpAptitude(), captureWd.getStrengthAptitude(),
                        captureWd.getSpiritAptitude(), captureWd.getDefenseAptitude(),
                        captureWd.getResistanceAptitude(), captureWd.getSpeedAptitude()
                } : null;
                pokedexService.recordCapture(player.getSaveId(), captured.getSpeciesId(),
                        apts, captureWd != null ? captureWd.getExtraSkillIds() : null,
                        false, captureWd != null ? captureWd.getSpecialAppearance() : null);
            } catch (Exception e) {
                log.warn("图鉴捕获记录失败（不阻断结算）：species={}", captured.getSpeciesId(), e);
            }
        }
    }

    /** 捕捉球扣除：战斗内消耗（无论成败）统一在结算时从背包扣除。 */
    private void consumeCaptureBalls(BattleContext ctx, PlayerEntity player) {
        for (Map.Entry<String, Integer> entry : ctx.getConsumedCaptureBalls().entrySet()) {
            PlayerInventoryEntity inv = playerInventoryMapper.selectOne(
                    new LambdaQueryWrapper<PlayerInventoryEntity>()
                            .eq(PlayerInventoryEntity::getSaveId, player.getSaveId())
                            .eq(PlayerInventoryEntity::getItemId, entry.getKey()));
            if (inv == null || inv.getQuantity() < entry.getValue()) {
                throw new BusinessException("CAPTURE_BALL_MISSING",
                        "捕捉球库存不足，无法扣除: " + entry.getKey());
            }
            int remaining = inv.getQuantity() - entry.getValue();
            if (remaining <= 0) {
                playerInventoryMapper.deleteById(inv.getId());
            } else {
                inv.setQuantity(remaining);
                playerInventoryMapper.updateById(inv);
            }
        }
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
            PetSpeciesConfig species = findSpecies(pet.getSpeciesId());
            if (species == null) {
                throw new BusinessException("SPECIES_CONFIG_MISSING",
                        "宠物种族配置缺失: " + pet.getSpeciesId());
            }
            side.getUnits().add(buildPlayerUnit(pet, species, index < activeSlots ? index : -1));
            index++;
        }
        return side;
    }

    private PetSpeciesConfig findSpecies(String speciesId) {
        return registry.getSpecies(speciesId);
    }

    /**
     * 玩家宠物 → 战斗单位。
     * <p>
     * 面板属性统一走 {@link PetGrowthService#computePanelStats}（需求 §9/§12）：
     * 种族基础（含个体浮动）+ 等级固定成长 + 资质成长修正 + 自由属性点。
     * 战斗 Buff/Debuff 由引擎状态体系在运行时叠加，不进面板。
     */
    private BattleUnit buildPlayerUnit(PlayerPetEntity pet, PetSpeciesConfig species,
                                       int position) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("P_" + pet.getId());
        unit.setPetDbId(pet.getId());
        unit.setSpeciesId(species.getId());
        unit.setName(pet.getNickname() != null && !pet.getNickname().isBlank()
                ? pet.getNickname() : species.getName());
        unit.setElement(species.getElement());
        unit.setLevel(pet.getLevel());

        // 统一面板公式：与 PetService 详情页、加点预览完全一致
        PetPanelStats stats = growthService.computePanelStats(pet, species);
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
        // 被动技能按种族配置自动生效（不进 player_pet_skill 表）；REV-012：仅加载已解锁被动
        for (PetSpeciesConfig.SpeciesPassiveSlot passiveSlot : species.getPassives()) {
            if (passiveSlot.getUnlockLevel() > pet.getLevel()) {
                continue;
            }
            PassiveSkillConfig passive = registry.getPassive(passiveSlot.getPassiveId());
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
        snapshot.setBattleType(ctx.getBattleType());
        snapshot.setUncapturable(ctx.isUncapturable());
        snapshot.setSeed(ctx.getRandomSeed());
        snapshot.setCurrentRound(ctx.getCurrentRound());
        snapshot.setFinished(ctx.isFinished());
        snapshot.setWinner(ctx.getWinner());
        snapshot.setFled(ctx.isFled());
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
        snapshot.setCaptured(unit.isCaptured());
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
                    status.getRemainingTurns(),
                    Math.max(1, status.getStack()),
                    config != null && config.isCaptureStun()));
        }
        return snapshot;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== 结算 DTO ====================

    /** 野生战斗捕捉率视图（前端展示用）。 */
    @lombok.Data
    public static class CaptureRateView {
        private String unitId;
        private String unitName;
        private String ballItemId;
        private String ballName;
        /** 捕捉率（0~1）。 */
        private double rate;
    }

    /** 战斗结算结果摘要。 */
    @lombok.Data
    public static class BattleSettlement {

        /** 战斗 ID。 */
        private String battleId;

        /** 胜方：PLAYER / ENEMY。 */
        private String winner;

        /** 玩家是否获胜。 */
        private boolean playerWon;

        /** 玩家是否逃跑成功（同战败结算）。 */
        private boolean fled;

        /** 经验池增加量（仅胜方 > 0）。 */
        private int expGained;

        /** 金币增加量（仅胜方 > 0）。 */
        private int goldGained;

        /** 掉落道具列表。 */
        private List<DropResult> drops = new ArrayList<>();

        /** 本场被捕捉的宠物列表（野生战斗）。 */
        private List<CapturedPetView> capturedPets = new ArrayList<>();

        /** 参战宠物 HP 回写明细。 */
        private List<PetHpWriteback> hpWritebacks = new ArrayList<>();

        /** 战败流程结果（阶段 6，需求 §44；玩家战败且未逃跑时非空）。 */
        private com.petgame.map.service.MapExplorationService.DefeatView defeat;

        /** 单个掉落结果。 */
        @lombok.Data
        public static class DropResult {
            private String itemId;
            private String name;
            private int quantity;
        }

        /** 被捕捉宠物摘要。 */
        @lombok.Data
        public static class CapturedPetView {
            private Long petId;
            private String speciesId;
            private String name;
            private String rarity;
            private int level;
            private String specialAppearance;
            private List<String> extraSkillIds = new ArrayList<>();
            /** 直接入队时的队伍位置（null = 未入队，留在仓库）。 */
            private Integer teamPosition;
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

    // ---- 阶段 7：Boss 战斗支持 ----

    /**
     * 开始 Boss 战斗（阶段 7）。
     * <p>
     * 由 BossService 委托调用，创建 Boss 战斗上下文。
     */
    public String startBossBattle(String saveId, BossesConfig.BossConfig boss,
                                  BossesConfig.DifficultyConfig diffConfig,
                                  String bossId, String difficulty, Long seed) {
        String battleId = createBossBattle(saveId, boss, diffConfig, bossId, difficulty, seed);
        bossEngine.startBattle(battles.get(battleId));
        return battleId;
    }

    /**
     * 创建 Boss 战斗上下文但不执行 startBattle（自动挑战专用，阶段 7）。
     * <p>
     * {@code runFullBattle} 内部会调用 startBattle，自动挑战走此方法避免
     * 登场被动重复触发。
     */
    public String createBossBattle(String saveId, BossesConfig.BossConfig boss,
                                   BossesConfig.DifficultyConfig diffConfig,
                                   String bossId, String difficulty, Long seed) {
        List<PlayerPetEntity> teamPets = loadActiveTeamPets(saveId);
        if (teamPets == null || teamPets.isEmpty()) {
            throw new BusinessException("NO_TEAM", "未设置战斗队伍");
        }
        // 检查队伍有可战斗宠物
        boolean hasAlive = teamPets.stream().anyMatch(p -> p.getCurrentHp() != null && p.getCurrentHp() > 0);
        if (!hasAlive) {
            throw new BusinessException("NO_ALIVE_PET", "队伍中没有存活的宠物");
        }

        String battleId = UUID.randomUUID().toString();
        long randomSeed = seed != null ? seed : System.nanoTime();
        BattleContext ctx = new BattleContext(battleId, randomSeed);
        ctx.setBattleType("BOSS");
        ctx.setBossId(bossId);
        ctx.setBossDifficulty(difficulty);
        ctx.setUncapturable(true);

        // 构建玩家方
        ctx.setPlayerSide(buildPlayerSide(teamPets));

        // 构建 Boss 敌方
        ctx.setEnemySide(buildBossSide(boss, diffConfig, difficulty));

        battles.put(battleId, ctx);
        return battleId;
    }

    /** 获取战斗上下文（供自动挑战使用）。 */
    public BattleContext getBattleContext(String battleId) {
        return battles.get(battleId);
    }

    /** 全队 HP 回满（Boss 重复战恢复，需求 §88）。 */
    @Transactional
    public void healTeamFully(String saveId) {
        List<PlayerPetEntity> pets = playerPetMapper.selectList(
                new LambdaQueryWrapper<PlayerPetEntity>()
                        .eq(PlayerPetEntity::getSaveId, saveId));
        for (PlayerPetEntity pet : pets) {
            PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
            if (species == null) continue;
            PetPanelStats stats = growthService.computePanelStats(pet, species);
            pet.setCurrentHp(stats.getMaxHp());
            playerPetMapper.updateById(pet);
        }
    }

    /** 添加经验和金币（供自动挑战结算使用）。 */
    @Transactional
    public void addExpAndGold(String saveId, int exp, int gold) {
        PlayerEntity player = playerMapper.selectOne(
                new LambdaQueryWrapper<PlayerEntity>()
                        .eq(PlayerEntity::getSaveId, saveId));
        if (player == null) return;
        player.setExpPool(player.getExpPool() + exp);
        player.setGold(player.getGold() + gold);
        playerMapper.updateById(player);
    }

    /** 添加背包物品（供 BossService 调用）。 */
    public void addInventoryItemPublic(String saveId, String itemId, int quantity) {
        addInventoryItem(saveId, itemId, quantity);
    }

    /** 构建 Boss 敌方阵容。 */
    private BattleSide buildBossSide(BossesConfig.BossConfig boss,
                                     BossesConfig.DifficultyConfig diffConfig,
                                     String difficulty) {
        BattleSide side = new BattleSide("ENEMY");
        BattleUnit unit = new BattleUnit();
        unit.setUnitId("BOSS_" + boss.getId() + "_" + difficulty);
        unit.setName(boss.getName() + " (" + difficulty + ")");
        unit.setElement(boss.getElement());
        unit.setLevel(boss.getRecommendedLevel());

        BossesConfig.StatsConfig stats = diffConfig.getStats();
        unit.setMaxHp(stats.getMaxHp());
        unit.setStrength(stats.getStrength());
        unit.setSpirit(stats.getSpirit());
        unit.setDefense(stats.getDefense());
        unit.setResistance(stats.getResistance());
        unit.setSpeed(stats.getSpeed());
        unit.setCurrentHp(stats.getMaxHp());
        unit.setActive(true);
        unit.setPosition(0);

        // 技能
        unit.getSkillIds().addAll(diffConfig.getSkills());

        // 被动
        for (String passiveId : diffConfig.getPassives()) {
            PassiveSkillConfig passive = registry.getPassive(passiveId);
            if (passive != null) {
                unit.getPassives().add(passive);
            }
        }

        // Boss 控制抗性
        Map<String, Double> controlResistance = registry.getSystemRules().getControlResistance();
        if (controlResistance != null && controlResistance.containsKey("BOSS")) {
            unit.setControlResistance(controlResistance.get("BOSS"));
        } else {
            unit.setControlResistance(0.6);
        }

        // 阶段触发器
        if (diffConfig.getPhases() != null) {
            unit.getPhaseTriggers().addAll(diffConfig.getPhases());
            for (int i = 0; i < diffConfig.getPhases().size(); i++) {
                unit.getPhaseActivated().add(false);
            }
        }

        side.getUnits().add(unit);
        return side;
    }
}
