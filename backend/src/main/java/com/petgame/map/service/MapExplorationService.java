package com.petgame.map.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.MapsConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerCampActivationEntity;
import com.petgame.map.entity.PlayerChestLootEntity;
import com.petgame.map.entity.PlayerGatherUsedEntity;
import com.petgame.map.entity.PlayerMapSessionEntity;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerCampActivationMapper;
import com.petgame.map.mapper.PlayerChestLootMapper;
import com.petgame.map.mapper.PlayerGatherUsedMapper;
import com.petgame.map.mapper.PlayerMapSessionMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.entity.PlayerTeamEntity;
import com.petgame.team.entity.PlayerTeamMemberEntity;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.petgame.quest.mapper.PlayerMapChangeMapper;
import com.petgame.quest.entity.PlayerMapChangeEntity;
import com.petgame.quest.service.QuestService;
import com.petgame.achievement.service.AchievementService;

/**
 * 地图探索与区域服务（阶段 6）。
 * <p>
 * 规则来源：需求 §44（战败零惩罚）、§45（HP 持续消耗）、§69~§76（地图/营地/刷新/采集）、
 * 规划阶段 6 实现范围与核心业务规则。
 * <ul>
 *   <li>区域解锁：AUTO 区域懒写入解锁记录（主线解锁 BOSS/QUEST 属阶段 7/9）。</li>
 *   <li>营地：免费恢复全队 HP（含复苏倒下宠物）；激活后可在已激活营地间免费传送。</li>
 *   <li>地图刷新：离开区域重新进入 / 营地休息生成新会话，野怪与普通采集点按会话刷新；
 *       不使用现实时间。</li>
 *   <li>采集点单次访问内一次性；隐藏宝箱全局一次性。</li>
 *   <li>战败流程：全队恢复 + 返回最近恢复点 + 轻度嘲讽式提示，无任何惩罚。</li>
 * </ul>
 * 业务数据一律存 MySQL，Phaser 场景不承载本类状态（架构边界）。
 */
@Service
public class MapExplorationService {

    private static final Logger log = LoggerFactory.getLogger(MapExplorationService.class);

    private final GameConfigRegistry registry;
    private final PetGrowthService growthService;
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerTeamMapper playerTeamMapper;
    private final PlayerTeamMemberMapper playerTeamMemberMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final PlayerCampActivationMapper campActivationMapper;
    private final PlayerChestLootMapper chestLootMapper;
    private final PlayerMapSessionMapper mapSessionMapper;
    private final PlayerGatherUsedMapper gatherUsedMapper;
    private final QuestService questService;
    private final PlayerMapChangeMapper playerMapChangeMapper;
    private final AchievementService achievementService;

    public MapExplorationService(GameConfigRegistry registry,
                                 PetGrowthService growthService,
                                 PlayerMapper playerMapper,
                                 PlayerPetMapper playerPetMapper,
                                 PlayerTeamMapper playerTeamMapper,
                                 PlayerTeamMemberMapper playerTeamMemberMapper,
                                 PlayerInventoryMapper playerInventoryMapper,
                                 PlayerRegionUnlockMapper regionUnlockMapper,
                                 PlayerCampActivationMapper campActivationMapper,
                                 PlayerChestLootMapper chestLootMapper,
                                 PlayerMapSessionMapper mapSessionMapper,
                                 PlayerGatherUsedMapper gatherUsedMapper,
                                 @Lazy QuestService questService,
                                 PlayerMapChangeMapper playerMapChangeMapper,
                                 AchievementService achievementService) {
        this.registry = registry;
        this.growthService = growthService;
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerTeamMapper = playerTeamMapper;
        this.playerTeamMemberMapper = playerTeamMemberMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.campActivationMapper = campActivationMapper;
        this.chestLootMapper = chestLootMapper;
        this.mapSessionMapper = mapSessionMapper;
        this.gatherUsedMapper = gatherUsedMapper;
        this.questService = questService;
        this.playerMapChangeMapper = playerMapChangeMapper;
        this.achievementService = achievementService;
    }

    // ==================== 大地图 / 区域 ====================

    /**
     * 大地图视图（需求 §116）：已实装区域 + 推荐等级 + 解锁状态 + 营地 + Boss 占位。
     */
    public WorldMapView getWorldMap() {
        PlayerEntity player = requirePlayer();
        ensureAutoUnlocks(player.getSaveId());

        WorldMapView view = new WorldMapView();
        view.setCurrentMapId(player.getCurrentMapId());

        Set<String> unlockedIds = loadUnlockedRegionIds(player.getSaveId());
        List<PlayerCampActivationEntity> camps = campActivationMapper.selectList(
                new LambdaQueryWrapper<PlayerCampActivationEntity>()
                        .eq(PlayerCampActivationEntity::getSaveId, player.getSaveId())
                        .orderByAsc(PlayerCampActivationEntity::getActivatedAt));
        Set<String> activatedCampIds = new HashSet<>();
        for (PlayerCampActivationEntity camp : camps) {
            activatedCampIds.add(camp.getCampId());
        }

        // 阶段 9：已激活永久地图变更
        Set<String> activatedMapChangeIds = new HashSet<>();
        if (playerMapChangeMapper != null) {
            for (PlayerMapChangeEntity mc : playerMapChangeMapper.selectList(
                    new LambdaQueryWrapper<PlayerMapChangeEntity>()
                            .eq(PlayerMapChangeEntity::getSaveId, player.getSaveId()))) {
                activatedMapChangeIds.add(mc.getChangeId());
            }
        }
        view.setActivatedMapChanges(activatedMapChangeIds);

        for (MapsConfig.RegionConfig region : registry.getImplementedRegions()) {
            WorldMapView.RegionView rv = new WorldMapView.RegionView();
            rv.setMapId(region.getId());
            rv.setName(region.getName());
            rv.setType(region.getType());
            rv.setRecommendedLevel(region.getRecommendedLevel());
            rv.setUnlocked(unlockedIds.contains(region.getId()));
            rv.setCurrent(region.getId().equals(player.getCurrentMapId()));
            // Boss 状态占位（阶段 7 启用）
            rv.setBossStatus("NOT_OPEN");
            for (MapsConfig.CampConfig camp : region.getCamps()) {
                WorldMapView.CampView cv = new WorldMapView.CampView();
                cv.setCampId(camp.getCampId());
                cv.setName(camp.getName());
                cv.setActivated(activatedCampIds.contains(camp.getCampId()));
                rv.getCamps().add(cv);
            }
            view.getRegions().add(rv);
        }
        return view;
    }

    /**
     * 进入区域（出口移动 / 传送 / 初次进入统一入口）。
     * <p>
     * 校验区域已实装且已解锁；更新玩家当前位置并生成新访问会话（触发野怪与采集点刷新，
     * 需求 §76）。出口移动时传 exitId，由后端权威解析对应入口对象；
     * 大地图进入传 null 落到区域默认出生点。
     *
     * @param mapId  目标区域 ID
     * @param exitId 出发出口 ID（可为 null）
     */
    @Transactional
    public MapEnterView enterRegion(String mapId, String exitId) {
        PlayerEntity player = requirePlayer();
        ensureAutoUnlocks(player.getSaveId());
        MapsConfig.RegionConfig region = requireImplementedRegion(mapId);
        requireUnlocked(player.getSaveId(), region);

        // 出口校验：必须来自当前区域的合法出口且目标一致；解析入口对象
        String entryObjectId = null;
        if (exitId != null && !exitId.isBlank()) {
            MapsConfig.RegionConfig from = registry.getRegion(player.getCurrentMapId());
            MapsConfig.ExitConfig exit = from == null ? null : from.getExits().stream()
                    .filter(e -> e.getExitId().equals(exitId))
                    .filter(e -> mapId.equals(e.getTargetMapId()))
                    .findFirst().orElse(null);
            if (exit == null) {
                throw new BusinessException("EXIT_NOT_FOUND",
                        "当前区域不存在该出口: " + exitId + " → " + mapId);
            }
            entryObjectId = exit.getEntryObjectId();
        }

        player.setCurrentMapId(region.getId());
        playerMapper.updateById(player);

        PlayerMapSessionEntity session = startNewSession(player.getSaveId(), region.getId());
        log.info("进入区域：mapId={}, sessionId={}, exit={}, entry={}",
                mapId, session.getSessionId(), exitId, entryObjectId);
        fireEnterRegionEvents(player, mapId);
        return buildEnterView(player, region, session,
                entryObjectId != null ? entryObjectId : region.getSpawnObjectId());
    }

    // 阶段 9：进入区域事件钩子（在 enterRegion 内部调用）
    private void fireEnterRegionEvents(PlayerEntity player, String mapId) {
        if (questService == null) return;
        String saveId = player.getSaveId();
        // ARRIVE 事件
        questService.checkObjectiveProgress(saveId, "ARRIVE", mapId, 1);
        // 隐藏任务 LOCATION 触发器
        questService.checkHiddenTrigger(saveId, "LOCATION", mapId);
    }

    /**
     * 查询当前所在区域的访问状态（前端恢复场景用；无会话时补建会话）。
     */
    @Transactional
    public MapEnterView getCurrentMapView() {
        PlayerEntity player = requirePlayer();
        ensureAutoUnlocks(player.getSaveId());
        MapsConfig.RegionConfig region = requireImplementedRegion(player.getCurrentMapId());

        PlayerMapSessionEntity session = mapSessionMapper.selectOne(
                new LambdaQueryWrapper<PlayerMapSessionEntity>()
                        .eq(PlayerMapSessionEntity::getSaveId, player.getSaveId())
                        .eq(PlayerMapSessionEntity::getMapId, region.getId())
                        .last("LIMIT 1"));
        if (session == null) {
            session = startNewSession(player.getSaveId(), region.getId());
        }
        return buildEnterView(player, region, session, region.getSpawnObjectId());
    }

    // ==================== 营地 ====================

    /**
     * 营地休息（需求 §75）：免费恢复当前队伍全体 HP（含复苏倒下宠物、清除跨战斗不保留的异常），
     * 首次使用激活营地，并触发地图刷新（新会话）。
     */
    @Transactional
    public CampRestView restAtCamp(String campId) {
        PlayerEntity player = requirePlayer();
        MapsConfig.RegionConfig region = locateCampRegion(campId);
        requireUnlocked(player.getSaveId(), region);

        boolean firstActivation = !isCampActivated(player.getSaveId(), campId);
        if (firstActivation) {
            PlayerCampActivationEntity activation = new PlayerCampActivationEntity();
            activation.setSaveId(player.getSaveId());
            activation.setCampId(campId);
            activation.setActivatedAt(LocalDateTime.now());
            campActivationMapper.insert(activation);
        }

        // 移动到营地所在区域并触发刷新
        player.setCurrentMapId(region.getId());
        playerMapper.updateById(player);
        PlayerMapSessionEntity session = startNewSession(player.getSaveId(), region.getId());

        int healed = healActiveTeam(player.getSaveId());

        CampRestView view = new CampRestView();
        view.setCampId(campId);
        view.setMapId(region.getId());
        view.setFirstActivation(firstActivation);
        view.setHealedPets(healed);
        view.setSessionId(session.getSessionId());
        log.info("营地休息：campId={}, 首次激活={}, 恢复宠物 {} 只", campId, firstActivation, healed);
        return view;
    }

    /**
     * 已激活营地间免费快速传送（需求 §75/§116）。
     * 传送视为重新进入区域，触发地图刷新；不恢复 HP（恢复请使用营地休息）。
     */
    @Transactional
    public MapEnterView teleportToCamp(String campId) {
        PlayerEntity player = requirePlayer();
        MapsConfig.RegionConfig region = locateCampRegion(campId);
        if (!isCampActivated(player.getSaveId(), campId)) {
            throw new BusinessException("CAMP_NOT_ACTIVATED", "营地尚未激活，无法传送: " + campId);
        }

        player.setCurrentMapId(region.getId());
        playerMapper.updateById(player);
        PlayerMapSessionEntity session = startNewSession(player.getSaveId(), region.getId());
        log.info("营地传送：campId={}, mapId={}", campId, region.getId());
        // 传送落点为营地对象本身（Tiled 对象 ID 与 campId 一致约定）
        return buildEnterView(player, region, session, campId);
    }

    // ==================== 采集 / 宝箱 ====================

    /**
     * 采集普通采集点（需求 §73）：单次访问内一次性，奖励入背包；离开重进区域后刷新。
     */
    @Transactional
    public RewardResultView gather(String gatherId) {
        PlayerEntity player = requirePlayer();
        MapsConfig.RegionConfig region = requireCurrentRegion(player, "采集点所在区域");
        MapsConfig.GatherPointConfig point = region.getGathers().stream()
                .filter(g -> g.getGatherId().equals(gatherId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("GATHER_NOT_FOUND",
                        "当前区域不存在采集点: " + gatherId));

        PlayerMapSessionEntity session = requireSession(player.getSaveId(), region.getId());
        Long used = gatherUsedMapper.selectCount(
                new LambdaQueryWrapper<PlayerGatherUsedEntity>()
                        .eq(PlayerGatherUsedEntity::getSaveId, player.getSaveId())
                        .eq(PlayerGatherUsedEntity::getGatherId, gatherId)
                        .eq(PlayerGatherUsedEntity::getSessionId, session.getSessionId()));
        if (used != null && used > 0) {
            throw new BusinessException("GATHER_ALREADY_USED", "该采集点本次访问已采集，离开区域后刷新");
        }

        GameRandom random = new GameRandom();
        RewardResultView result = grantRewards(player, point.getRewards(),
                point.getGoldMin(), point.getGoldMax(), random);
        result.setObjectName(point.getName());

        gatherUsedMapper.insert(new PlayerGatherUsedEntity(
                player.getSaveId(), gatherId, session.getSessionId(), LocalDateTime.now()));
        // 阶段 9：采集事件钩子
        if (questService != null) {
            questService.checkObjectiveProgress(player.getSaveId(), "GATHER", gatherId, 1);
        }
        log.info("采集完成：gatherId={}, 金币+{}, 道具 {} 项", gatherId,
                result.getGoldGained(), result.getItems().size());
        return result;
    }

    /**
     * 开启隐藏宝箱（需求 §73）：全局一次性，奖励入背包。
     */
    @Transactional
    public RewardResultView openChest(String chestId) {
        PlayerEntity player = requirePlayer();
        MapsConfig.RegionConfig region = requireCurrentRegion(player, "宝箱所在区域");
        MapsConfig.ChestConfig chest = region.getChests().stream()
                .filter(c -> c.getChestId().equals(chestId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CHEST_NOT_FOUND",
                        "当前区域不存在宝箱: " + chestId));

        Long looted = chestLootMapper.selectCount(
                new LambdaQueryWrapper<PlayerChestLootEntity>()
                        .eq(PlayerChestLootEntity::getSaveId, player.getSaveId())
                        .eq(PlayerChestLootEntity::getChestId, chestId));
        if (looted != null && looted > 0) {
            throw new BusinessException("CHEST_ALREADY_LOOTED", "该宝箱已被开启过（隐藏宝箱一次性）");
        }

        GameRandom random = new GameRandom();
        RewardResultView result = grantRewards(player, chest.getRewards(),
                chest.getGoldMin(), chest.getGoldMax(), random);
        result.setObjectName(chest.getName());

        chestLootMapper.insert(new PlayerChestLootEntity(
                player.getSaveId(), chestId, LocalDateTime.now()));
        log.info("宝箱开启：chestId={}, 金币+{}, 道具 {} 项", chestId,
                result.getGoldGained(), result.getItems().size());
        return result;
    }

    // ==================== 遭遇校验 ====================

    /**
     * 校验刷新组允许在玩家当前区域发起遭遇（地图只声明「哪里用哪个刷新组」）。
     * 由地图遭遇入口在开战前调用；阶段 5 简化遭遇入口不走此校验。
     */
    public void validateEncounterGroup(String groupId) {
        PlayerEntity player = requirePlayer();
        MapsConfig.RegionConfig region = requireCurrentRegion(player, "遭遇发起区域");
        if (!region.getEncounterGroups().contains(groupId)) {
            throw new BusinessException("ENCOUNTER_GROUP_NOT_ALLOWED",
                    "当前区域不允许该刷新组的遭遇: " + groupId);
        }
    }

    // ==================== 战败流程 ====================

    /**
     * 战败流程（需求 §44）：零惩罚 + 返回最近恢复点 + 队伍恢复 + 轻度嘲讽式提示。
     * <p>
     * 由 BattleService 结算检测到玩家战败时在同一事务内调用。
     * 恢复点优先级：当前区域内最近激活的营地 → 区域默认出生点。
     */
    @Transactional
    public DefeatView handleDefeat(PlayerEntity player) {
        // 1. 队伍恢复（全体当前队伍宠物 HP 回满，倒下宠物复苏）
        int healed = healActiveTeam(player.getSaveId());

        // 2. 返回最近恢复点：优先当前区域最近激活营地，否则区域出生点
        MapsConfig.RegionConfig region = registry.getRegion(player.getCurrentMapId());
        String respawnMapId = player.getCurrentMapId();
        String respawnObjectId = region != null ? region.getSpawnObjectId() : null;

        List<PlayerCampActivationEntity> camps = campActivationMapper.selectList(
                new LambdaQueryWrapper<PlayerCampActivationEntity>()
                        .eq(PlayerCampActivationEntity::getSaveId, player.getSaveId())
                        .orderByDesc(PlayerCampActivationEntity::getActivatedAt));
        for (PlayerCampActivationEntity camp : camps) {
            MapsConfig.RegionConfig campRegion = registry.getRegionByCamp(camp.getCampId());
            if (campRegion != null && campRegion.getId().equals(player.getCurrentMapId())) {
                respawnObjectId = camp.getCampId();
                break;
            }
        }

        // 3. 轻度嘲讽式提示（配置池随机）
        List<String> messages = registry.getMapsConfig().getExploration().getDefeatMessages();
        String message = messages.isEmpty()
                ? "看来这支队伍还需要再磨合一下。"
                : messages.get(new GameRandom().nextInt(0, messages.size() - 1));

        DefeatView view = new DefeatView();
        view.setMessage(message);
        view.setRespawnMapId(respawnMapId);
        view.setRespawnObjectId(respawnObjectId);
        view.setHealedPets(healed);
        log.info("战败流程：respawn={}@{}, 恢复宠物 {} 只", respawnObjectId, respawnMapId, healed);
        return view;
    }

    // ==================== 内部工具 ====================

    /** AUTO 解锁区域与自动激活营地的懒写入（新游戏后首次访问地图体系时补齐）。 */
    @Transactional
    public void ensureAutoUnlocks(String saveId) {
        Set<String> unlocked = loadUnlockedRegionIds(saveId);
        Set<String> activatedCamps = new HashSet<>();
        for (PlayerCampActivationEntity camp : campActivationMapper.selectList(
                new LambdaQueryWrapper<PlayerCampActivationEntity>()
                        .eq(PlayerCampActivationEntity::getSaveId, saveId))) {
            activatedCamps.add(camp.getCampId());
        }
        for (MapsConfig.RegionConfig region : registry.getImplementedRegions()) {
            if ("AUTO".equals(region.getUnlockType()) && !unlocked.contains(region.getId())) {
                regionUnlockMapper.insert(new PlayerRegionUnlockEntity(
                        saveId, region.getId(), LocalDateTime.now()));
            }
            for (MapsConfig.CampConfig camp : region.getCamps()) {
                if (camp.isAutoActivate() && !activatedCamps.contains(camp.getCampId())) {
                    campActivationMapper.insert(new PlayerCampActivationEntity(
                            saveId, camp.getCampId(), LocalDateTime.now()));
                }
            }
        }
        // 阶段 11：区域解锁类成就检查（失败不阻断主流程）
        if (achievementService != null) {
            achievementService.checkAchievements(saveId);
        }
    }

    /** 恢复当前激活队伍全部宠物 HP 至上限（含复苏倒下宠物）。返回恢复数量。 */
    private int healActiveTeam(String saveId) {
        PlayerTeamEntity team = playerTeamMapper.selectOne(
                new LambdaQueryWrapper<PlayerTeamEntity>()
                        .eq(PlayerTeamEntity::getSaveId, saveId)
                        .eq(PlayerTeamEntity::getIsActive, true)
                        .last("LIMIT 1"));
        if (team == null) {
            return 0;
        }
        List<PlayerTeamMemberEntity> members = playerTeamMemberMapper.selectList(
                new LambdaQueryWrapper<PlayerTeamMemberEntity>()
                        .eq(PlayerTeamMemberEntity::getTeamId, team.getId()));
        int healed = 0;
        for (PlayerTeamMemberEntity member : members) {
            PlayerPetEntity pet = playerPetMapper.selectById(member.getPetId());
            if (pet == null) {
                continue;
            }
            PetSpeciesConfig species = registry.getSpecies(pet.getSpeciesId());
            if (species == null) {
                continue;
            }
            int maxHp = growthService.computePanelStats(pet, species).getMaxHp();
            pet.setCurrentHp(maxHp);
            playerPetMapper.updateById(pet);
            healed++;
        }
        return healed;
    }

    /** 发放道具 + 金币奖励（采集/宝箱共用）。 */
    private RewardResultView grantRewards(PlayerEntity player, List<MapsConfig.RewardEntry> rewards,
                                          int goldMin, int goldMax, GameRandom random) {
        RewardResultView result = new RewardResultView();

        int gold = goldMax > 0 ? random.nextInt(Math.max(0, goldMin), goldMax) : 0;
        if (gold > 0) {
            player.setGold(player.getGold() + gold);
            playerMapper.updateById(player);
        }
        result.setGoldGained(gold);

        for (MapsConfig.RewardEntry entry : rewards) {
            ItemConfig item = registry.getItem(entry.getItemId());
            if (item == null) {
                log.warn("地图奖励引用道具配置缺失，跳过: {}", entry.getItemId());
                continue;
            }
            int qty = entry.getQtyMax() >= entry.getQtyMin()
                    ? random.nextInt(entry.getQtyMin(), entry.getQtyMax())
                    : entry.getQtyMin();
            addInventoryItem(player.getSaveId(), entry.getItemId(), qty);
            RewardResultView.ItemReward ir = new RewardResultView.ItemReward();
            ir.setItemId(item.getId());
            ir.setName(item.getName());
            ir.setQuantity(qty);
            result.getItems().add(ir);
        }
        return result;
    }

    /** 增加背包道具数量（已存在则累加）。 */
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

    /** 生成新的地图访问会话（替换旧会话，旧会话的采集记录随之失效 = 刷新）。 */
    private PlayerMapSessionEntity startNewSession(String saveId, String mapId) {
        PlayerMapSessionEntity existing = mapSessionMapper.selectOne(
                new LambdaQueryWrapper<PlayerMapSessionEntity>()
                        .eq(PlayerMapSessionEntity::getSaveId, saveId)
                        .eq(PlayerMapSessionEntity::getMapId, mapId)
                        .last("LIMIT 1"));
        PlayerMapSessionEntity session = new PlayerMapSessionEntity(
                saveId, mapId, UUID.randomUUID().toString(), LocalDateTime.now());
        if (existing != null) {
            mapSessionMapper.delete(new LambdaQueryWrapper<PlayerMapSessionEntity>()
                    .eq(PlayerMapSessionEntity::getSaveId, saveId)
                    .eq(PlayerMapSessionEntity::getMapId, mapId));
        }
        mapSessionMapper.insert(session);
        return session;
    }

    private PlayerMapSessionEntity requireSession(String saveId, String mapId) {
        PlayerMapSessionEntity session = mapSessionMapper.selectOne(
                new LambdaQueryWrapper<PlayerMapSessionEntity>()
                        .eq(PlayerMapSessionEntity::getSaveId, saveId)
                        .eq(PlayerMapSessionEntity::getMapId, mapId)
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException("MAP_SESSION_MISSING", "请先进入该区域");
        }
        return session;
    }

    private MapEnterView buildEnterView(PlayerEntity player, MapsConfig.RegionConfig region,
                                        PlayerMapSessionEntity session, String spawnObjectId) {
        MapEnterView view = new MapEnterView();
        view.setMapId(region.getId());
        view.setName(region.getName());
        view.setMapFile(region.getMapFile());
        view.setSessionId(session.getSessionId());
        view.setSpawnObjectId(spawnObjectId);

        // 已消耗对象：隐藏宝箱（全局）+ 采集点（本次会话）
        for (PlayerChestLootEntity chest : chestLootMapper.selectList(
                new LambdaQueryWrapper<PlayerChestLootEntity>()
                        .eq(PlayerChestLootEntity::getSaveId, player.getSaveId()))) {
            view.getConsumedChestIds().add(chest.getChestId());
        }
        for (PlayerGatherUsedEntity used : gatherUsedMapper.selectList(
                new LambdaQueryWrapper<PlayerGatherUsedEntity>()
                        .eq(PlayerGatherUsedEntity::getSaveId, player.getSaveId())
                        .eq(PlayerGatherUsedEntity::getSessionId, session.getSessionId()))) {
            view.getUsedGatherIds().add(used.getGatherId());
        }

        // 营地激活状态
        for (MapsConfig.CampConfig camp : region.getCamps()) {
            if (isCampActivated(player.getSaveId(), camp.getCampId())) {
                view.getActivatedCampIds().add(camp.getCampId());
            }
        }
        // 已激活永久地图变更（阶段 9）
        if (playerMapChangeMapper != null) {
            for (PlayerMapChangeEntity mc : playerMapChangeMapper.selectList(
                    new LambdaQueryWrapper<PlayerMapChangeEntity>()
                            .eq(PlayerMapChangeEntity::getSaveId, player.getSaveId()))) {
                view.getActivatedMapChanges().add(mc.getChangeId());
            }
        }
        return view;
    }

    private boolean isCampActivated(String saveId, String campId) {
        Long count = campActivationMapper.selectCount(
                new LambdaQueryWrapper<PlayerCampActivationEntity>()
                        .eq(PlayerCampActivationEntity::getSaveId, saveId)
                        .eq(PlayerCampActivationEntity::getCampId, campId));
        return count != null && count > 0;
    }

    private MapsConfig.RegionConfig locateCampRegion(String campId) {
        MapsConfig.RegionConfig region = registry.getRegionByCamp(campId);
        if (region == null || region.isPlanned()) {
            throw new BusinessException("CAMP_NOT_FOUND", "营地不存在: " + campId);
        }
        return region;
    }

    private MapsConfig.RegionConfig requireImplementedRegion(String mapId) {
        MapsConfig.RegionConfig region = registry.getRegion(mapId);
        if (region == null || region.isPlanned()) {
            throw new BusinessException("REGION_NOT_FOUND", "区域不存在或尚未开放: " + mapId);
        }
        return region;
    }

    private void requireUnlocked(String saveId, MapsConfig.RegionConfig region) {
        if (!loadUnlockedRegionIds(saveId).contains(region.getId())) {
            throw new BusinessException("REGION_LOCKED", "区域尚未解锁: " + region.getId());
        }
    }

    /** 校验并返回玩家当前所在区域（未实装/不存在则报错）。 */
    private MapsConfig.RegionConfig requireCurrentRegion(PlayerEntity player, String contextDesc) {
        if (player.getCurrentMapId() == null) {
            throw new BusinessException("NO_CURRENT_MAP", "玩家当前不在任何区域");
        }
        MapsConfig.RegionConfig region = registry.getRegion(player.getCurrentMapId());
        if (region == null || region.isPlanned()) {
            throw new BusinessException("REGION_NOT_FOUND", contextDesc + "无效: " + player.getCurrentMapId());
        }
        return region;
    }

    private Set<String> loadUnlockedRegionIds(String saveId) {
        Set<String> ids = new HashSet<>();
        for (PlayerRegionUnlockEntity unlock : regionUnlockMapper.selectList(
                new LambdaQueryWrapper<PlayerRegionUnlockEntity>()
                        .eq(PlayerRegionUnlockEntity::getSaveId, saveId))) {
            ids.add(unlock.getRegionId());
        }
        return ids;
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    // ==================== DTO ====================

    /** 大地图视图（需求 §116）。 */
    @Data
    public static class WorldMapView {
        private String currentMapId;
        private List<RegionView> regions = new ArrayList<>();
        /** 已激活永久地图变更 ID（阶段 9）。 */
        private Set<String> activatedMapChanges = new HashSet<>();

        @Data
        public static class RegionView {
            private String mapId;
            private String name;
            private String type;
            private String recommendedLevel;
            private boolean unlocked;
            private boolean current;
            /** Boss 状态占位（阶段 7 启用，本阶段恒为 NOT_OPEN）。 */
            private String bossStatus;
            private List<CampView> camps = new ArrayList<>();
        }

        @Data
        public static class CampView {
            private String campId;
            private String name;
            private boolean activated;
        }
    }

    /** 进入区域结果（前端 Phaser 场景初始化数据）。 */
    @Data
    public static class MapEnterView {
        private String mapId;
        private String name;
        /** Tiled 地图资源文件名。 */
        private String mapFile;
        /** 本次访问会话 ID（刷新判定用）。 */
        private String sessionId;
        /** 落点对象 ID（Tiled 对象）。 */
        private String spawnObjectId;
        /** 已开启的隐藏宝箱（全局一次性）。 */
        private Set<String> consumedChestIds = new HashSet<>();
        /** 本次会话已采集的采集点。 */
        private Set<String> usedGatherIds = new HashSet<>();
        /** 已激活营地 ID。 */
        private Set<String> activatedCampIds = new HashSet<>();
        /** 已激活永久地图变更 ID（阶段 9）。 */
        private Set<String> activatedMapChanges = new HashSet<>();
    }

    /** 营地休息结果。 */
    @Data
    public static class CampRestView {
        private String campId;
        private String mapId;
        private boolean firstActivation;
        private int healedPets;
        private String sessionId;
    }

    /** 采集/宝箱奖励结果。 */
    @Data
    public static class RewardResultView {
        private String objectName;
        private int goldGained;
        private List<ItemReward> items = new ArrayList<>();

        @Data
        public static class ItemReward {
            private String itemId;
            private String name;
            private int quantity;
        }
    }

    /** 战败流程结果（需求 §44：零惩罚）。 */
    @Data
    public static class DefeatView {
        /** 轻度嘲讽式提示。 */
        private String message;
        /** 恢复点所在区域。 */
        private String respawnMapId;
        /** 恢复点对象 ID（营地或出生点）。 */
        private String respawnObjectId;
        /** 恢复的宠物数量。 */
        private int healedPets;
    }
}
