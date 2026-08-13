package com.petgame.quest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.QuestsConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.entity.PlayerRegionUnlockEntity;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.entity.PlayerPetSkillEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.*;
import com.petgame.quest.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务系统核心服务（阶段 9）。
 * <p>
 * 负责任务接受/推进/完成/奖励/区域解锁/地图变更/赠送宠物的完整业务逻辑。
 * 事件驱动推进：由外部系统（战斗/地图/NPC）通过 checkObjectiveProgress 推送事件。
 */
@Service
public class QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestService.class);

    private final GameConfigRegistry registry;
    private final PlayerMapper playerMapper;
    private final PlayerPetMapper playerPetMapper;
    private final PlayerPetSkillMapper playerPetSkillMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final PetGrowthService growthService;
    private final PokedexService pokedexService;
    private final PlayerQuestMapper playerQuestMapper;
    private final PlayerQuestObjectiveMapper playerQuestObjectiveMapper;
    private final PlayerMapChangeMapper playerMapChangeMapper;
    private final PlayerHiddenTriggerMapper playerHiddenTriggerMapper;

    public QuestService(GameConfigRegistry registry,
                        PlayerMapper playerMapper,
                        PlayerPetMapper playerPetMapper,
                        PlayerPetSkillMapper playerPetSkillMapper,
                        PlayerInventoryMapper playerInventoryMapper,
                        PlayerRegionUnlockMapper regionUnlockMapper,
                        PetGrowthService growthService,
                        @Lazy PokedexService pokedexService,
                        PlayerQuestMapper playerQuestMapper,
                        PlayerQuestObjectiveMapper playerQuestObjectiveMapper,
                        PlayerMapChangeMapper playerMapChangeMapper,
                        PlayerHiddenTriggerMapper playerHiddenTriggerMapper) {
        this.registry = registry;
        this.playerMapper = playerMapper;
        this.playerPetMapper = playerPetMapper;
        this.playerPetSkillMapper = playerPetSkillMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.growthService = growthService;
        this.pokedexService = pokedexService;
        this.playerQuestMapper = playerQuestMapper;
        this.playerQuestObjectiveMapper = playerQuestObjectiveMapper;
        this.playerMapChangeMapper = playerMapChangeMapper;
        this.playerHiddenTriggerMapper = playerHiddenTriggerMapper;
    }

    // ==================== 查询 ====================

    /**
     * 任务列表（主线/支线/隐藏分组，隐藏任务过滤未触发的）。
     */
    public QuestListView getQuestList() {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        Map<String, PlayerQuestEntity> questStates = loadQuestStates(saveId);

        List<QuestSummary> mainQuests = new ArrayList<>();
        List<QuestSummary> sideQuests = new ArrayList<>();
        List<QuestSummary> hiddenQuests = new ArrayList<>();

        for (QuestsConfig.QuestConfig config : registry.getQuestsConfig().getQuests()) {
            PlayerQuestEntity state = questStates.get(config.getId());
            // 隐藏任务：未触发且未接受/完成的不显示
            if (config.isHidden() && state == null) {
                continue;
            }
            QuestSummary summary = toQuestSummary(config, state);
            switch (config.getType()) {
                case "MAIN" -> mainQuests.add(summary);
                case "SIDE" -> sideQuests.add(summary);
                case "HIDDEN" -> hiddenQuests.add(summary);
            }
        }

        QuestListView view = new QuestListView();
        view.setMainQuests(mainQuests);
        view.setSideQuests(sideQuests);
        view.setHiddenQuests(hiddenQuests);
        return view;
    }

    /**
     * 任务详情（目标进度/奖励预览）。
     */
    public QuestDetailView getQuestDetail(String questId) {
        PlayerEntity player = requirePlayer();
        QuestsConfig.QuestConfig config = registry.getQuest(questId);
        if (config == null) {
            throw new BusinessException("QUEST_NOT_FOUND", "任务不存在: " + questId);
        }

        PlayerQuestEntity state = loadQuestState(player.getSaveId(), questId);
        List<PlayerQuestObjectiveEntity> objectives = loadObjectiveStates(player.getSaveId(), questId);

        QuestDetailView detail = new QuestDetailView();
        detail.setQuestId(config.getId());
        detail.setName(config.getName());
        detail.setType(config.getType());
        detail.setDescription(config.getDescription());
        detail.setRegionId(config.getRegionId());
        detail.setStatus(state != null ? state.getStatus() : computeAvailability(config, player.getSaveId()));
        detail.setHidden(config.isHidden());

        // 目标进度
        Map<String, PlayerQuestObjectiveEntity> objStates = new HashMap<>();
        for (PlayerQuestObjectiveEntity obj : objectives) {
            objStates.put(obj.getObjectiveId(), obj);
        }
        List<ObjectiveView> objectiveViews = new ArrayList<>();
        for (QuestsConfig.ObjectiveConfig objConfig : config.getObjectives()) {
            ObjectiveView ov = new ObjectiveView();
            ov.setObjectiveId(objConfig.getObjectiveId());
            ov.setType(objConfig.getType());
            ov.setDescription(objConfig.getDescription());
            ov.setTargetCount(objConfig.getTargetCount());
            PlayerQuestObjectiveEntity objState = objStates.get(objConfig.getObjectiveId());
            ov.setProgress(objState != null ? objState.getProgress() : 0);
            ov.setCompleted(objState != null && Boolean.TRUE.equals(objState.getCompleted()));
            objectiveViews.add(ov);
        }
        detail.setObjectives(objectiveViews);

        // 奖励预览
        if (config.getRewards() != null) {
            detail.setRewards(toRewardPreview(config.getRewards()));
        }
        // 地图变更预览
        detail.setMapChanges(config.getMapChanges());
        // 赠送宠物预览
        if (config.getRewards() != null && config.getRewards().getGiftPet() != null) {
            QuestsConfig.GiftPetConfig gift = config.getRewards().getGiftPet();
            GiftPetPreview gp = new GiftPetPreview();
            PetSpeciesConfig species = registry.getSpecies(gift.getSpeciesId());
            gp.setSpeciesId(gift.getSpeciesId());
            gp.setSpeciesName(species != null ? species.getName() : gift.getSpeciesId());
            gp.setLevel(gift.getLevel());
            gp.setSource(gift.getSource());
            detail.setGiftPet(gp);
        }

        // 三选一奖励选择状态
        if (state != null && state.getStatus().equals("COMPLETED")) {
            detail.setRewardChosen(true);
        }

        return detail;
    }

    /**
     * 首页主线摘要。
     */
    public ActiveQuestSummary getActiveQuestSummary() {
        PlayerEntity player = requirePlayer();
        Map<String, PlayerQuestEntity> questStates = loadQuestStates(player.getSaveId());

        // 找到当前进行中的主线任务（按配置顺序第一个 ACTIVE 的）
        for (QuestsConfig.QuestConfig config : registry.getMainQuests()) {
            PlayerQuestEntity state = questStates.get(config.getId());
            if (state != null && "ACTIVE".equals(state.getStatus())) {
                ActiveQuestSummary summary = new ActiveQuestSummary();
                summary.setQuestId(config.getId());
                summary.setName(config.getName());
                summary.setDescription(config.getDescription());
                summary.setRegionId(config.getRegionId());

                // 当前目标进度
                List<PlayerQuestObjectiveEntity> objectives = loadObjectiveStates(player.getSaveId(), config.getId());
                for (PlayerQuestObjectiveEntity obj : objectives) {
                    if (!Boolean.TRUE.equals(obj.getCompleted())) {
                        summary.setCurrentObjectiveDescription(findObjectiveDescription(config, obj.getObjectiveId()));
                        summary.setCurrentProgress(obj.getProgress());
                        summary.setCurrentTarget(obj.getTargetCount());
                        break;
                    }
                }
                return summary;
            }
        }
        return null;
    }

    /**
     * 已激活永久地图变更列表。
     */
    public List<MapChangeView> getMapChanges() {
        PlayerEntity player = requirePlayer();
        List<PlayerMapChangeEntity> changes = playerMapChangeMapper.selectList(
                new LambdaQueryWrapper<PlayerMapChangeEntity>()
                        .eq(PlayerMapChangeEntity::getSaveId, player.getSaveId()));

        // 构建配置索引
        Map<String, QuestsConfig.MapChangeConfig> changeConfigIndex = new HashMap<>();
        for (QuestsConfig.QuestConfig quest : registry.getQuestsConfig().getQuests()) {
            for (QuestsConfig.MapChangeConfig mc : quest.getMapChanges()) {
                changeConfigIndex.put(mc.getChangeId(), mc);
            }
        }

        List<MapChangeView> views = new ArrayList<>();
        for (PlayerMapChangeEntity entity : changes) {
            QuestsConfig.MapChangeConfig mcConfig = changeConfigIndex.get(entity.getChangeId());
            MapChangeView view = new MapChangeView();
            view.setChangeId(entity.getChangeId());
            view.setActivatedAt(entity.getActivatedAt());
            if (mcConfig != null) {
                view.setChangeType(mcConfig.getChangeType());
                view.setRegionId(mcConfig.getRegionId());
                view.setDescription(mcConfig.getDescription());
                view.setObjectId(mcConfig.getObjectId());
            }
            views.add(view);
        }
        return views;
    }

    // ==================== 操作 ====================

    /**
     * 接受任务（校验前置任务 + 重复接受拒绝）。
     */
    @Transactional
    public void acceptQuest(String questId) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        QuestsConfig.QuestConfig config = registry.getQuest(questId);
        if (config == null) {
            throw new BusinessException("QUEST_NOT_FOUND", "任务不存在: " + questId);
        }

        // 隐藏任务不可主动接受
        if (config.isHidden()) {
            throw new BusinessException("QUEST_HIDDEN", "隐藏任务不可主动接受");
        }

        // 重复接受检查
        PlayerQuestEntity existing = loadQuestState(saveId, questId);
        if (existing != null) {
            if ("ACTIVE".equals(existing.getStatus())) {
                throw new BusinessException("QUEST_ALREADY_ACTIVE", "任务已在进行中: " + questId);
            }
            if ("COMPLETED".equals(existing.getStatus())) {
                throw new BusinessException("QUEST_ALREADY_COMPLETED", "任务已完成: " + questId);
            }
        }

        // 前置任务校验
        if (!checkPrerequisites(saveId, config)) {
            throw new BusinessException("QUEST_PREREQUISITE_NOT_MET", "前置任务尚未完成");
        }

        // 创建任务记录
        PlayerQuestEntity quest = new PlayerQuestEntity();
        quest.setSaveId(saveId);
        quest.setQuestId(questId);
        quest.setStatus("ACTIVE");
        quest.setCurrentObjective(1);
        quest.setAcceptedAt(LocalDateTime.now());
        playerQuestMapper.insert(quest);

        // 初始化目标进度
        for (QuestsConfig.ObjectiveConfig objConfig : config.getObjectives()) {
            PlayerQuestObjectiveEntity obj = new PlayerQuestObjectiveEntity();
            obj.setSaveId(saveId);
            obj.setQuestId(questId);
            obj.setObjectiveId(objConfig.getObjectiveId());
            obj.setProgress(0);
            obj.setTargetCount(objConfig.getTargetCount());
            obj.setCompleted(false);
            playerQuestObjectiveMapper.insert(obj);
        }

        log.info("接受任务：questId={}, saveId={}", questId, saveId);
    }

    /**
     * 事件驱动推进任务目标进度（REQUIRES_NEW 传播，失败不阻断主流程）。
     *
     * @param saveId    存档 ID
     * @param eventType 事件类型（DIALOGUE/GATHER/CAPTURE/DEFEAT/DEFEAT_BOSS/ARRIVE）
     * @param targetId  目标 ID（NPC/采集点/种族/Boss/区域）
     * @param count     本次事件计数（默认 1）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkObjectiveProgress(String saveId, String eventType, String targetId, int count) {
        try {
            doCheckObjectiveProgress(saveId, eventType, targetId, count);
        } catch (Exception e) {
            log.warn("任务目标推进异常（不阻断主流程）：saveId={}, event={}:{}, error={}",
                    saveId, eventType, targetId, e.getMessage());
        }
    }

    private void doCheckObjectiveProgress(String saveId, String eventType, String targetId, int count) {
        // 查找所有 ACTIVE 状态的任务
        List<PlayerQuestEntity> activeQuests = playerQuestMapper.selectList(
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, saveId)
                        .eq(PlayerQuestEntity::getStatus, "ACTIVE"));

        for (PlayerQuestEntity questState : activeQuests) {
            QuestsConfig.QuestConfig config = registry.getQuest(questState.getQuestId());
            if (config == null) {
                continue;
            }

            for (QuestsConfig.ObjectiveConfig objConfig : config.getObjectives()) {
                // 类型匹配
                if (!eventType.equals(objConfig.getType())) {
                    continue;
                }
                // 目标 ID 匹配（targetId 为 * 表示任意匹配）
                if (!"*".equals(objConfig.getTargetId()) && !objConfig.getTargetId().equals(targetId)) {
                    continue;
                }

                // 更新进度
                PlayerQuestObjectiveEntity objState = playerQuestObjectiveMapper.selectOne(
                        new LambdaQueryWrapper<PlayerQuestObjectiveEntity>()
                                .eq(PlayerQuestObjectiveEntity::getSaveId, saveId)
                                .eq(PlayerQuestObjectiveEntity::getQuestId, questState.getQuestId())
                                .eq(PlayerQuestObjectiveEntity::getObjectiveId, objConfig.getObjectiveId()));
                if (objState == null || Boolean.TRUE.equals(objState.getCompleted())) {
                    continue;
                }

                int newProgress = Math.min(objState.getProgress() + count, objState.getTargetCount());
                objState.setProgress(newProgress);
                if (newProgress >= objState.getTargetCount()) {
                    objState.setCompleted(true);
                    log.info("任务目标完成：questId={}, objectiveId={}", questState.getQuestId(), objConfig.getObjectiveId());
                }
                playerQuestObjectiveMapper.update(objState,
                        new LambdaQueryWrapper<PlayerQuestObjectiveEntity>()
                                .eq(PlayerQuestObjectiveEntity::getSaveId, saveId)
                                .eq(PlayerQuestObjectiveEntity::getQuestId, questState.getQuestId())
                                .eq(PlayerQuestObjectiveEntity::getObjectiveId, objConfig.getObjectiveId()));
            }
        }
    }

    /**
     * 隐藏任务触发检查（REQUIRES_NEW 传播，失败不阻断主流程）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkHiddenTrigger(String saveId, String triggerType, String triggerTarget) {
        try {
            doCheckHiddenTrigger(saveId, triggerType, triggerTarget);
        } catch (Exception e) {
            log.warn("隐藏任务触发检查异常（不阻断主流程）：saveId={}, trigger={}:{}, error={}",
                    saveId, triggerType, triggerTarget, e.getMessage());
        }
    }

    private void doCheckHiddenTrigger(String saveId, String triggerType, String triggerTarget) {
        String triggerKey = triggerType + ":" + triggerTarget;

        // 累计触发计数
        PlayerHiddenTriggerEntity trigger = playerHiddenTriggerMapper.selectOne(
                new LambdaQueryWrapper<PlayerHiddenTriggerEntity>()
                        .eq(PlayerHiddenTriggerEntity::getSaveId, saveId)
                        .eq(PlayerHiddenTriggerEntity::getTriggerKey, triggerKey));
        if (trigger == null) {
            trigger = new PlayerHiddenTriggerEntity();
            trigger.setSaveId(saveId);
            trigger.setTriggerKey(triggerKey);
            trigger.setTriggerCount(1);
            playerHiddenTriggerMapper.insert(trigger);
        } else {
            trigger.setTriggerCount(trigger.getTriggerCount() + 1);
            playerHiddenTriggerMapper.update(trigger,
                    new LambdaQueryWrapper<PlayerHiddenTriggerEntity>()
                            .eq(PlayerHiddenTriggerEntity::getSaveId, saveId)
                            .eq(PlayerHiddenTriggerEntity::getTriggerKey, triggerKey));
        }

        // 检查是否满足隐藏任务触发条件
        for (QuestsConfig.QuestConfig quest : registry.getHiddenQuests()) {
            if (quest.getTrigger() == null) {
                continue;
            }
            QuestsConfig.HiddenTriggerConfig tc = quest.getTrigger();
            String questTriggerKey = tc.getTriggerType() + ":" + tc.getTriggerTarget();
            if (!questTriggerKey.equals(triggerKey)) {
                continue;
            }
            // 已接受/完成则跳过
            PlayerQuestEntity existing = loadQuestState(saveId, quest.getId());
            if (existing != null) {
                continue;
            }
            // 触发次数达标则自动接受
            if (trigger.getTriggerCount() >= tc.getTriggerCount()) {
                autoAcceptHiddenQuest(saveId, quest);
            }
        }
    }

    private void autoAcceptHiddenQuest(String saveId, QuestsConfig.QuestConfig quest) {
        PlayerQuestEntity questEntity = new PlayerQuestEntity();
        questEntity.setSaveId(saveId);
        questEntity.setQuestId(quest.getId());
        questEntity.setStatus("ACTIVE");
        questEntity.setCurrentObjective(1);
        questEntity.setAcceptedAt(LocalDateTime.now());
        playerQuestMapper.insert(questEntity);

        for (QuestsConfig.ObjectiveConfig objConfig : quest.getObjectives()) {
            PlayerQuestObjectiveEntity obj = new PlayerQuestObjectiveEntity();
            obj.setSaveId(saveId);
            obj.setQuestId(quest.getId());
            obj.setObjectiveId(objConfig.getObjectiveId());
            obj.setProgress(0);
            obj.setTargetCount(objConfig.getTargetCount());
            obj.setCompleted(false);
            playerQuestObjectiveMapper.insert(obj);
        }

        log.info("隐藏任务触发并接受：questId={}, saveId={}", quest.getId(), saveId);
    }

    /**
     * 完成任务（校验所有目标完成 + 发放固定奖励 + 区域解锁 + 地图变更 + 赠送宠物）。
     */
    @Transactional
    public QuestCompleteView completeQuest(String questId) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        QuestsConfig.QuestConfig config = registry.getQuest(questId);
        if (config == null) {
            throw new BusinessException("QUEST_NOT_FOUND", "任务不存在: " + questId);
        }

        PlayerQuestEntity questState = loadQuestState(saveId, questId);
        if (questState == null || !"ACTIVE".equals(questState.getStatus())) {
            throw new BusinessException("QUEST_NOT_ACTIVE", "任务未在进行中: " + questId);
        }

        // 校验所有目标已完成
        List<PlayerQuestObjectiveEntity> objectives = loadObjectiveStates(saveId, questId);
        for (PlayerQuestObjectiveEntity obj : objectives) {
            if (!Boolean.TRUE.equals(obj.getCompleted())) {
                throw new BusinessException("QUEST_OBJECTIVES_INCOMPLETE",
                        "任务目标尚未全部完成: " + obj.getObjectiveId());
            }
        }

        QuestCompleteView result = new QuestCompleteView();
        result.setQuestId(questId);
        result.setName(config.getName());

        // 1. 更新任务状态为 COMPLETED
        questState.setStatus("COMPLETED");
        questState.setCompletedAt(LocalDateTime.now());
        playerQuestMapper.update(questState,
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, saveId)
                        .eq(PlayerQuestEntity::getQuestId, questId));

        // 2. 发放固定奖励
        if (config.getRewards() != null && config.getRewards().getFixed() != null) {
            grantRewardEntries(saveId, player, config.getRewards().getFixed(), result);
        }

        // 3. 区域解锁
        if (config.getUnlockRegionId() != null && !config.getUnlockRegionId().isEmpty()) {
            String[] regionIds = config.getUnlockRegionId().split(",");
            for (String regionId : regionIds) {
                regionId = regionId.trim();
                unlockRegion(saveId, regionId);
                result.getUnlockedRegions().add(regionId);
            }
        }

        // 4. 永久地图变更
        for (QuestsConfig.MapChangeConfig mc : config.getMapChanges()) {
            PlayerMapChangeEntity mcEntity = new PlayerMapChangeEntity();
            mcEntity.setSaveId(saveId);
            mcEntity.setChangeId(mc.getChangeId());
            mcEntity.setActivatedAt(LocalDateTime.now());
            playerMapChangeMapper.insert(mcEntity);
            result.getActivatedMapChanges().add(mc.getChangeId());
            log.info("永久地图变更激活：changeId={}, regionId={}", mc.getChangeId(), mc.getRegionId());
        }

        // 5. 赠送宠物
        if (config.getRewards() != null && config.getRewards().getGiftPet() != null) {
            GiftPetResult giftResult = grantGiftPet(saveId, config.getRewards().getGiftPet());
            result.setGiftPet(giftResult);
        }

        // 6. 通关标记（最终 Boss 任务 QUEST_MAIN_11）
        if ("QUEST_MAIN_11".equals(questId)) {
            player.setStoryCompleted(true);
            playerMapper.updateById(player);
            result.setStoryCompleted(true);
            log.info("玩家通关标记设置：saveId={}", saveId);
        }

        // 7. 更新 player.mainQuestId 到下一个未完成的主线
        updateMainQuestProgress(saveId);

        log.info("任务完成：questId={}, saveId={}, 奖励金={}, 经验={}, 道具={} 项, 解锁区域={}, 地图变更={}",
                questId, saveId, result.getGoldGained(), result.getExpGained(),
                result.getItemsGained().size(), result.getUnlockedRegions().size(),
                result.getActivatedMapChanges().size());

        return result;
    }

    /**
     * 三选一奖励选择（锁定不可更改）。
     */
    @Transactional
    public void chooseReward(String questId, String choiceId, int optionIndex) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        QuestsConfig.QuestConfig config = registry.getQuest(questId);
        if (config == null) {
            throw new BusinessException("QUEST_NOT_FOUND", "任务不存在: " + questId);
        }

        PlayerQuestEntity questState = loadQuestState(saveId, questId);
        if (questState == null || !"COMPLETED".equals(questState.getStatus())) {
            throw new BusinessException("QUEST_NOT_COMPLETED", "任务尚未完成: " + questId);
        }

        // 查找选择组
        QuestsConfig.RewardChoiceGroup choiceGroup = null;
        if (config.getRewards() != null) {
            for (QuestsConfig.RewardChoiceGroup cg : config.getRewards().getChoices()) {
                if (cg.getChoiceId().equals(choiceId)) {
                    choiceGroup = cg;
                    break;
                }
            }
        }
        if (choiceGroup == null) {
            throw new BusinessException("CHOICE_NOT_FOUND", "三选一奖励组不存在: " + choiceId);
        }
        if (optionIndex < 0 || optionIndex >= choiceGroup.getOptions().size()) {
            throw new BusinessException("INVALID_OPTION", "选项索引越界: " + optionIndex);
        }

        // 发放选中的奖励
        QuestsConfig.RewardEntry selected = choiceGroup.getOptions().get(optionIndex);
        grantSingleReward(saveId, player, selected);

        log.info("三选一奖励：questId={}, choiceId={}, optionIndex={}", questId, choiceId, optionIndex);
    }

    /**
     * QUEST 类型区域解锁（外部调用）。
     */
    public void unlockRegionByQuest(String saveId, String regionId) {
        unlockRegion(saveId, regionId);
    }

    // ==================== 内部工具 ====================

    private boolean checkPrerequisites(String saveId, QuestsConfig.QuestConfig config) {
        if (config.getPrerequisiteQuestId() == null || config.getPrerequisiteQuestId().isEmpty()) {
            return true;
        }
        String[] prereqs = config.getPrerequisiteQuestId().split(",");
        for (String prereqId : prereqs) {
            prereqId = prereqId.trim();
            PlayerQuestEntity prereq = loadQuestState(saveId, prereqId);
            if (prereq == null || !"COMPLETED".equals(prereq.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private String computeAvailability(QuestsConfig.QuestConfig config, String saveId) {
        if (checkPrerequisites(saveId, config)) {
            return "AVAILABLE";
        }
        return "LOCKED";
    }

    private void unlockRegion(String saveId, String regionId) {
        Long existing = regionUnlockMapper.selectCount(
                new LambdaQueryWrapper<PlayerRegionUnlockEntity>()
                        .eq(PlayerRegionUnlockEntity::getSaveId, saveId)
                        .eq(PlayerRegionUnlockEntity::getRegionId, regionId));
        if (existing != null && existing > 0) {
            return;
        }
        PlayerRegionUnlockEntity unlock = new PlayerRegionUnlockEntity(
                saveId, regionId, LocalDateTime.now());
        regionUnlockMapper.insert(unlock);
        log.info("区域解锁：regionId={}, saveId={}", regionId, saveId);
    }

    private void grantRewardEntries(String saveId, PlayerEntity player,
                                    List<QuestsConfig.RewardEntry> entries,
                                    QuestCompleteView result) {
        for (QuestsConfig.RewardEntry entry : entries) {
            grantSingleReward(saveId, player, entry);
            switch (entry.getType()) {
                case "GOLD" -> result.setGoldGained(result.getGoldGained() + entry.getQuantity());
                case "EXP" -> result.setExpGained(result.getExpGained() + entry.getQuantity());
                case "ITEM", "SKILL_BOOK" -> result.getItemsGained().add(
                        new ItemGainedView(entry.getItemId(), entry.getQuantity()));
            }
        }
    }

    private void grantSingleReward(String saveId, PlayerEntity player, QuestsConfig.RewardEntry entry) {
        switch (entry.getType()) {
            case "GOLD" -> {
                player.setGold(player.getGold() + entry.getQuantity());
                playerMapper.updateById(player);
            }
            case "EXP" -> {
                player.setExpPool(player.getExpPool() + entry.getQuantity());
                playerMapper.updateById(player);
            }
            case "ITEM", "SKILL_BOOK" -> {
                PlayerInventoryEntity existing = playerInventoryMapper.selectOne(
                        new LambdaQueryWrapper<PlayerInventoryEntity>()
                                .eq(PlayerInventoryEntity::getSaveId, saveId)
                                .eq(PlayerInventoryEntity::getItemId, entry.getItemId()));
                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + entry.getQuantity());
                    playerInventoryMapper.updateById(existing);
                } else {
                    PlayerInventoryEntity inv = new PlayerInventoryEntity();
                    inv.setSaveId(saveId);
                    inv.setItemId(entry.getItemId());
                    inv.setQuantity(entry.getQuantity());
                    playerInventoryMapper.insert(inv);
                }
            }
        }
    }

    private GiftPetResult grantGiftPet(String saveId, QuestsConfig.GiftPetConfig giftConfig) {
        PetSpeciesConfig species = registry.getSpecies(giftConfig.getSpeciesId());
        if (species == null) {
            log.warn("赠送宠物种族配置缺失: {}", giftConfig.getSpeciesId());
            return null;
        }

        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setSaveId(saveId);
        pet.setSpeciesId(giftConfig.getSpeciesId());
        pet.setLevel(giftConfig.getLevel());
        pet.setCapturedLevel(giftConfig.getLevel());
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        int apt = giftConfig.getAptitudeAll();
        pet.setHpAptitude(apt);
        pet.setStrengthAptitude(apt);
        pet.setSpiritAptitude(apt);
        pet.setDefenseAptitude(apt);
        pet.setResistanceAptitude(apt);
        pet.setSpeedAptitude(apt);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(growthService.computePanelStats(pet, species).getMaxHp());
        pet.setIsStarter(false);
        pet.setLocked(false);
        pet.setFavorite(false);
        pet.setCapturedMapId(null);
        pet.setCapturedAt(LocalDateTime.now());
        pet.setBattleCount(0);
        pet.setWinCount(0);
        playerPetMapper.insert(pet);

        // 种族技能：按配置槽位自动装备
        for (PetSpeciesConfig.SpeciesSkillSlot slot : species.getSkills()) {
            if (slot.getUnlockLevel() > giftConfig.getLevel()) {
                continue;
            }
            PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
            petSkill.setPetId(pet.getId());
            petSkill.setSkillId(slot.getSkillId());
            petSkill.setSourceType("LEVEL_UP");
            petSkill.setSlot(slot.getSlot());
            playerPetSkillMapper.insert(petSkill);
        }

        // 额外赠送技能
        if (giftConfig.getSkills() != null) {
            for (String skillId : giftConfig.getSkills()) {
                PlayerPetSkillEntity petSkill = new PlayerPetSkillEntity();
                petSkill.setPetId(pet.getId());
                petSkill.setSkillId(skillId);
                petSkill.setSourceType(giftConfig.getSource());
                petSkill.setSlot(null);
                playerPetSkillMapper.insert(petSkill);
            }
        }

        // 图鉴补录
        try {
            int[] apts = new int[]{apt, apt, apt, apt, apt, apt};
            pokedexService.recordCapture(saveId, giftConfig.getSpeciesId(), apts,
                    giftConfig.getSkills() != null ? giftConfig.getSkills() : List.of(),
                    false, null);
        } catch (Exception e) {
            log.warn("赠送宠物图鉴补录失败（不阻断）：{}", e.getMessage());
        }

        GiftPetResult result = new GiftPetResult();
        result.setPetId(pet.getId());
        result.setSpeciesId(species.getId());
        result.setSpeciesName(species.getName());
        result.setLevel(giftConfig.getLevel());
        result.setSource(giftConfig.getSource());
        log.info("赠送宠物：speciesId={}, petId={}, level={}", giftConfig.getSpeciesId(), pet.getId(), giftConfig.getLevel());
        return result;
    }

    private void updateMainQuestProgress(String saveId) {
        Map<String, PlayerQuestEntity> questStates = loadQuestStates(saveId);
        for (QuestsConfig.QuestConfig config : registry.getMainQuests()) {
            PlayerQuestEntity state = questStates.get(config.getId());
            if (state == null || !"COMPLETED".equals(state.getStatus())) {
                // 更新 mainQuestId
                PlayerEntity player = requirePlayer();
                player.setMainQuestId(config.getId());
                playerMapper.updateById(player);
                return;
            }
        }
    }

    private Map<String, PlayerQuestEntity> loadQuestStates(String saveId) {
        Map<String, PlayerQuestEntity> map = new HashMap<>();
        for (PlayerQuestEntity entity : playerQuestMapper.selectList(
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, saveId))) {
            map.put(entity.getQuestId(), entity);
        }
        return map;
    }

    private PlayerQuestEntity loadQuestState(String saveId, String questId) {
        return playerQuestMapper.selectOne(
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, saveId)
                        .eq(PlayerQuestEntity::getQuestId, questId));
    }

    private List<PlayerQuestObjectiveEntity> loadObjectiveStates(String saveId, String questId) {
        return playerQuestObjectiveMapper.selectList(
                new LambdaQueryWrapper<PlayerQuestObjectiveEntity>()
                        .eq(PlayerQuestObjectiveEntity::getSaveId, saveId)
                        .eq(PlayerQuestObjectiveEntity::getQuestId, questId));
    }

    private String findObjectiveDescription(QuestsConfig.QuestConfig config, String objectiveId) {
        for (QuestsConfig.ObjectiveConfig obj : config.getObjectives()) {
            if (obj.getObjectiveId().equals(objectiveId)) {
                return obj.getDescription();
            }
        }
        return null;
    }

    private QuestSummary toQuestSummary(QuestsConfig.QuestConfig config, PlayerQuestEntity state) {
        QuestSummary summary = new QuestSummary();
        summary.setQuestId(config.getId());
        summary.setName(config.getName());
        summary.setType(config.getType());
        summary.setDescription(config.getDescription());
        summary.setRegionId(config.getRegionId());
        summary.setHidden(config.isHidden());
        if (state != null) {
            summary.setStatus(state.getStatus());
        }
        return summary;
    }

    private RewardPreview toRewardPreview(QuestsConfig.RewardConfig rewards) {
        RewardPreview preview = new RewardPreview();
        if (rewards.getFixed() != null) {
            for (QuestsConfig.RewardEntry entry : rewards.getFixed()) {
                RewardEntryView ev = new RewardEntryView();
                ev.setType(entry.getType());
                ev.setItemId(entry.getItemId());
                ev.setQuantity(entry.getQuantity());
                preview.getFixed().add(ev);
            }
        }
        if (rewards.getChoices() != null) {
            for (QuestsConfig.RewardChoiceGroup cg : rewards.getChoices()) {
                ChoiceGroupView cgv = new ChoiceGroupView();
                cgv.setChoiceId(cg.getChoiceId());
                for (QuestsConfig.RewardEntry opt : cg.getOptions()) {
                    RewardEntryView ev = new RewardEntryView();
                    ev.setType(opt.getType());
                    ev.setItemId(opt.getItemId());
                    ev.setQuantity(opt.getQuantity());
                    cgv.getOptions().add(ev);
                }
                preview.getChoices().add(cgv);
            }
        }
        return preview;
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    // ==================== DTO ====================

    @lombok.Data
    public static class QuestListView {
        private List<QuestSummary> mainQuests = new ArrayList<>();
        private List<QuestSummary> sideQuests = new ArrayList<>();
        private List<QuestSummary> hiddenQuests = new ArrayList<>();
    }

    @lombok.Data
    public static class QuestSummary {
        private String questId;
        private String name;
        private String type;
        private String description;
        private String regionId;
        private String status; // AVAILABLE / ACTIVE / COMPLETED / LOCKED
        private boolean hidden;
    }

    @lombok.Data
    public static class QuestDetailView {
        private String questId;
        private String name;
        private String type;
        private String description;
        private String regionId;
        private String status;
        private boolean hidden;
        private boolean rewardChosen;
        private List<ObjectiveView> objectives = new ArrayList<>();
        private RewardPreview rewards;
        private List<QuestsConfig.MapChangeConfig> mapChanges = new ArrayList<>();
        private GiftPetPreview giftPet;
    }

    @lombok.Data
    public static class ObjectiveView {
        private String objectiveId;
        private String type;
        private String description;
        private int targetCount;
        private int progress;
        private boolean completed;
    }

    @lombok.Data
    public static class RewardPreview {
        private List<RewardEntryView> fixed = new ArrayList<>();
        private List<ChoiceGroupView> choices = new ArrayList<>();
    }

    @lombok.Data
    public static class RewardEntryView {
        private String type;
        private String itemId;
        private int quantity;
    }

    @lombok.Data
    public static class ChoiceGroupView {
        private String choiceId;
        private List<RewardEntryView> options = new ArrayList<>();
    }

    @lombok.Data
    public static class GiftPetPreview {
        private String speciesId;
        private String speciesName;
        private int level;
        private String source;
    }

    @lombok.Data
    public static class ActiveQuestSummary {
        private String questId;
        private String name;
        private String description;
        private String regionId;
        private String currentObjectiveDescription;
        private int currentProgress;
        private int currentTarget;
    }

    @lombok.Data
    public static class MapChangeView {
        private String changeId;
        private String changeType;
        private String regionId;
        private String description;
        private String objectId;
        private LocalDateTime activatedAt;
    }

    @lombok.Data
    public static class QuestCompleteView {
        private String questId;
        private String name;
        private int goldGained;
        private int expGained;
        private List<ItemGainedView> itemsGained = new ArrayList<>();
        private List<String> unlockedRegions = new ArrayList<>();
        private List<String> activatedMapChanges = new ArrayList<>();
        private GiftPetResult giftPet;
        private boolean storyCompleted;
    }

    @lombok.Data
    public static class ItemGainedView {
        private String itemId;
        private int quantity;

        public ItemGainedView() {}

        public ItemGainedView(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    @lombok.Data
    public static class GiftPetResult {
        private Long petId;
        private String speciesId;
        private String speciesName;
        private int level;
        private String source;
    }
}
