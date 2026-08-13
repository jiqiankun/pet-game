package com.petgame.quest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerDialogueEntity;
import com.petgame.quest.mapper.PlayerDialogueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NPC 对话服务（阶段 9）。
 * <p>
 * 负责 NPC 对话节点推进、对话次数累计、触发隐藏任务（DIALOGUE_COUNT 类型）。
 * 线性对话树：每次 talk 返回当前节点文本 + 推进到 nextNode（对话结束后重置到第一个节点）。
 */
@Service
public class NpcDialogueService {

    private static final Logger log = LoggerFactory.getLogger(NpcDialogueService.class);

    private final GameConfigRegistry registry;
    private final PlayerMapper playerMapper;
    private final PlayerDialogueMapper playerDialogueMapper;
    private final QuestService questService;

    public NpcDialogueService(GameConfigRegistry registry,
                              PlayerMapper playerMapper,
                              PlayerDialogueMapper playerDialogueMapper,
                              @Lazy QuestService questService) {
        this.registry = registry;
        this.playerMapper = playerMapper;
        this.playerDialogueMapper = playerDialogueMapper;
        this.questService = questService;
    }

    /**
     * 与 NPC 对话（返回当前节点文本，推进到下一节点，累计对话次数，触发隐藏任务）。
     *
     * @param npcId NPC ID（对应 Tiled 对象层 npc 对象）
     * @return 对话结果
     */
    @Transactional
    public DialogueView talk(String npcId) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();

        QuestsConfig.NpcConfig npcConfig = registry.getNpc(npcId);
        if (npcConfig == null) {
            throw new BusinessException("NPC_NOT_FOUND", "NPC 不存在: " + npcId);
        }
        if (npcConfig.getDialogues() == null || npcConfig.getDialogues().isEmpty()) {
            throw new BusinessException("NPC_NO_DIALOGUE", "NPC 无对话配置: " + npcId);
        }

        // 加载或创建对话记录
        PlayerDialogueEntity dialogue = playerDialogueMapper.selectOne(
                new LambdaQueryWrapper<PlayerDialogueEntity>()
                        .eq(PlayerDialogueEntity::getSaveId, saveId)
                        .eq(PlayerDialogueEntity::getNpcId, npcId));
        if (dialogue == null) {
            dialogue = new PlayerDialogueEntity();
            dialogue.setSaveId(saveId);
            dialogue.setNpcId(npcId);
            dialogue.setDialogueNodeId(null);
            dialogue.setDialogueCount(0);
            dialogue.setLastSpokenAt(null);
            playerDialogueMapper.insert(dialogue);
        }

        // 累计对话次数
        dialogue.setDialogueCount(dialogue.getDialogueCount() + 1);
        dialogue.setLastSpokenAt(LocalDateTime.now());

        // 构建对话索引
        var nodeIndex = new java.util.LinkedHashMap<String, QuestsConfig.DialogueNodeConfig>();
        for (QuestsConfig.DialogueNodeConfig node : npcConfig.getDialogues()) {
            nodeIndex.put(node.getNodeId(), node);
        }

        // 确定当前节点（首次对话或对话结束后重置到第一个节点）
        QuestsConfig.DialogueNodeConfig currentNode;
        if (dialogue.getDialogueNodeId() == null || dialogue.getDialogueNodeId().isEmpty()) {
            currentNode = npcConfig.getDialogues().get(0);
        } else {
            currentNode = nodeIndex.get(dialogue.getDialogueNodeId());
            if (currentNode == null) {
                currentNode = npcConfig.getDialogues().get(0);
            }
        }

        DialogueView view = new DialogueView();
        view.setNpcId(npcId);
        view.setNpcName(npcConfig.getName());
        view.setText(currentNode.getText());
        view.setNodeId(currentNode.getNodeId());
        view.setDialogueCount(dialogue.getDialogueCount());

        // 推进到下一节点
        if (currentNode.getNextNode() != null && !currentNode.getNextNode().isEmpty()) {
            dialogue.setDialogueNodeId(currentNode.getNextNode());
            view.setHasMore(true);
        } else {
            // 对话结束，重置（下次再对话从头开始）
            dialogue.setDialogueNodeId(null);
            view.setHasMore(false);
        }

        playerDialogueMapper.update(dialogue,
                new LambdaQueryWrapper<PlayerDialogueEntity>()
                        .eq(PlayerDialogueEntity::getSaveId, saveId)
                        .eq(PlayerDialogueEntity::getNpcId, npcId));

        // 触发隐藏任务（DIALOGUE_COUNT 类型，异步传播，不阻断对话）
        questService.checkHiddenTrigger(saveId, "DIALOGUE_COUNT", npcId);

        // DIALOGUE 事件推进任务目标
        questService.checkObjectiveProgress(saveId, "DIALOGUE", npcId, 1);

        log.info("NPC 对话：npcId={}, nodeId={}, 累计次数={}", npcId, currentNode.getNodeId(), dialogue.getDialogueCount());
        return view;
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
    public static class DialogueView {
        private String npcId;
        private String npcName;
        private String nodeId;
        private String text;
        private boolean hasMore;
        private int dialogueCount;
    }
}
