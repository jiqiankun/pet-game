package com.petgame.quest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerTutorialEntity;
import com.petgame.quest.mapper.PlayerTutorialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 新手教学服务（阶段 9）。
 * <p>
 * 负责教学步骤查询/完成/跳过，捕捉教学完成时发放技能书。
 */
@Service
public class TutorialService {

    private static final Logger log = LoggerFactory.getLogger(TutorialService.class);

    private final GameConfigRegistry registry;
    private final PlayerMapper playerMapper;
    private final PlayerTutorialMapper playerTutorialMapper;
    private final PlayerInventoryMapper playerInventoryMapper;

    public TutorialService(GameConfigRegistry registry,
                           PlayerMapper playerMapper,
                           PlayerTutorialMapper playerTutorialMapper,
                           PlayerInventoryMapper playerInventoryMapper) {
        this.registry = registry;
        this.playerMapper = playerMapper;
        this.playerTutorialMapper = playerTutorialMapper;
        this.playerInventoryMapper = playerInventoryMapper;
    }

    /**
     * 获取教学状态（所有步骤 + 完成/跳过状态）。
     */
    public TutorialStateView getTutorialState() {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        List<PlayerTutorialEntity> states = playerTutorialMapper.selectList(
                new LambdaQueryWrapper<PlayerTutorialEntity>()
                        .eq(PlayerTutorialEntity::getSaveId, saveId));
        var stateMap = new java.util.HashMap<String, PlayerTutorialEntity>();
        for (PlayerTutorialEntity entity : states) {
            stateMap.put(entity.getStepId(), entity);
        }

        List<TutorialStepView> steps = new ArrayList<>();
        int completedCount = 0;
        for (QuestsConfig.TutorialStepConfig config : registry.getTutorials()) {
            TutorialStepView stepView = new TutorialStepView();
            stepView.setStepId(config.getStepId());
            stepView.setName(config.getName());
            stepView.setDescription(config.getDescription());
            stepView.setOrder(config.getOrder());
            stepView.setSkippable(config.isSkippable());

            PlayerTutorialEntity state = stateMap.get(config.getStepId());
            stepView.setCompleted(state != null && Boolean.TRUE.equals(state.getCompleted()));
            stepView.setSkipped(state != null && Boolean.TRUE.equals(state.getSkipped()));
            if (stepView.isCompleted()) {
                completedCount++;
            }
            steps.add(stepView);
        }

        TutorialStateView view = new TutorialStateView();
        view.setSteps(steps);
        view.setAllCompleted(completedCount >= registry.getTutorials().size());
        view.setCompletedCount(completedCount);
        view.setTotalCount(registry.getTutorials().size());
        return view;
    }

    /**
     * 完成教学步骤（标记完成 + 发放该步骤奖励）。
     */
    @Transactional
    public void completeStep(String stepId) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        QuestsConfig.TutorialStepConfig config = findStepConfig(stepId);
        if (config == null) {
            throw new BusinessException("TUTORIAL_STEP_NOT_FOUND", "教学步骤不存在: " + stepId);
        }

        PlayerTutorialEntity existing = playerTutorialMapper.selectOne(
                new LambdaQueryWrapper<PlayerTutorialEntity>()
                        .eq(PlayerTutorialEntity::getSaveId, saveId)
                        .eq(PlayerTutorialEntity::getStepId, stepId));
        if (existing != null && Boolean.TRUE.equals(existing.getCompleted())) {
            return; // 已完成则幂等
        }

        if (existing == null) {
            existing = new PlayerTutorialEntity();
            existing.setSaveId(saveId);
            existing.setStepId(stepId);
            existing.setCompleted(true);
            existing.setSkipped(false);
            playerTutorialMapper.insert(existing);
        } else {
            existing.setCompleted(true);
            playerTutorialMapper.update(existing,
                    new LambdaQueryWrapper<PlayerTutorialEntity>()
                            .eq(PlayerTutorialEntity::getSaveId, saveId)
                            .eq(PlayerTutorialEntity::getStepId, stepId));
        }

        // 发放步骤奖励（如捕捉教学赠送技能书）；仅首次发放，重置后不重复发放
        if (config.getRewards() != null && !Boolean.TRUE.equals(existing.getRewardGranted())) {
            for (QuestsConfig.RewardEntry entry : config.getRewards()) {
                grantReward(saveId, player, entry);
            }
            existing.setRewardGranted(true);
            playerTutorialMapper.update(existing,
                    new LambdaQueryWrapper<PlayerTutorialEntity>()
                            .eq(PlayerTutorialEntity::getSaveId, saveId)
                            .eq(PlayerTutorialEntity::getStepId, stepId));
        }

        log.info("教学步骤完成：stepId={}, saveId={}", stepId, saveId);
    }

    /**
     * 跳过所有可跳过的教学步骤。
     */
    @Transactional
    public void skipTutorial() {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        for (QuestsConfig.TutorialStepConfig config : registry.getTutorials()) {
            if (!config.isSkippable()) {
                continue;
            }
            PlayerTutorialEntity existing = playerTutorialMapper.selectOne(
                    new LambdaQueryWrapper<PlayerTutorialEntity>()
                            .eq(PlayerTutorialEntity::getSaveId, saveId)
                            .eq(PlayerTutorialEntity::getStepId, config.getStepId()));
            if (existing != null) {
                if (!Boolean.TRUE.equals(existing.getSkipped())) {
                    existing.setSkipped(true);
                    playerTutorialMapper.update(existing,
                            new LambdaQueryWrapper<PlayerTutorialEntity>()
                                    .eq(PlayerTutorialEntity::getSaveId, saveId)
                                    .eq(PlayerTutorialEntity::getStepId, config.getStepId()));
                }
            } else {
                PlayerTutorialEntity entity = new PlayerTutorialEntity();
                entity.setSaveId(saveId);
                entity.setStepId(config.getStepId());
                entity.setCompleted(false);
                entity.setSkipped(true);
                playerTutorialMapper.insert(entity);
            }
        }

        log.info("跳过所有可跳过教学步骤：saveId={}", saveId);
    }

    /**
     * 重置教学提示（阶段 14）。
     * <p>
     * 将所有教学步骤的完成/跳过状态清空，教学从头开始重新引导；
     * 步骤奖励标记（rewardGranted）保留，避免重复发放捕捉教学技能书。
     */
    @Transactional
    public void resetTutorial() {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        for (QuestsConfig.TutorialStepConfig config : registry.getTutorials()) {
            PlayerTutorialEntity existing = playerTutorialMapper.selectOne(
                    new LambdaQueryWrapper<PlayerTutorialEntity>()
                            .eq(PlayerTutorialEntity::getSaveId, saveId)
                            .eq(PlayerTutorialEntity::getStepId, config.getStepId()));
            if (existing == null) {
                continue;
            }
            existing.setCompleted(false);
            existing.setSkipped(false);
            playerTutorialMapper.update(existing,
                    new LambdaQueryWrapper<PlayerTutorialEntity>()
                            .eq(PlayerTutorialEntity::getSaveId, saveId)
                            .eq(PlayerTutorialEntity::getStepId, config.getStepId()));
        }

        log.info("重置教学提示：saveId={}", saveId);
    }

    // ==================== 内部工具 ====================

    private QuestsConfig.TutorialStepConfig findStepConfig(String stepId) {
        for (QuestsConfig.TutorialStepConfig config : registry.getTutorials()) {
            if (config.getStepId().equals(stepId)) {
                return config;
            }
        }
        return null;
    }

    private void grantReward(String saveId, PlayerEntity player, QuestsConfig.RewardEntry entry) {
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
                log.info("教学奖励发放：stepReward={}, itemId={}, qty={}", entry.getType(), entry.getItemId(), entry.getQuantity());
            }
        }
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
    public static class TutorialStateView {
        private List<TutorialStepView> steps = new ArrayList<>();
        private boolean allCompleted;
        private int completedCount;
        private int totalCount;
    }

    @lombok.Data
    public static class TutorialStepView {
        private String stepId;
        private String name;
        private String description;
        private int order;
        private boolean skippable;
        private boolean completed;
        private boolean skipped;
    }
}
