package com.petgame.quest.controller;

import com.petgame.common.ApiResponse;
import com.petgame.quest.service.NpcDialogueService;
import com.petgame.quest.service.QuestService;
import com.petgame.quest.service.TutorialService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务系统 REST API（阶段 9）。
 * <p>
 * 包含任务管理、NPC 对话、新手教学、永久地图变更等接口。
 */
@RestController
@RequestMapping("/api")
public class QuestController {

    private final QuestService questService;
    private final NpcDialogueService npcDialogueService;
    private final TutorialService tutorialService;

    public QuestController(QuestService questService,
                           NpcDialogueService npcDialogueService,
                           TutorialService tutorialService) {
        this.questService = questService;
        this.npcDialogueService = npcDialogueService;
        this.tutorialService = tutorialService;
    }

    // ==================== 任务 ====================

    /** 任务列表（主线/支线/隐藏分组）。 */
    @GetMapping("/quests")
    public ApiResponse<QuestService.QuestListView> getQuestList() {
        return ApiResponse.success(questService.getQuestList());
    }

    /** 任务详情（目标进度/奖励预览）。 */
    @GetMapping("/quests/{questId}")
    public ApiResponse<QuestService.QuestDetailView> getQuestDetail(@PathVariable String questId) {
        return ApiResponse.success(questService.getQuestDetail(questId));
    }

    /** 接受任务。 */
    @PostMapping("/quests/{questId}/accept")
    public ApiResponse<Void> acceptQuest(@PathVariable String questId) {
        questService.acceptQuest(questId);
        return ApiResponse.success(null);
    }

    /** 完成任务。 */
    @PostMapping("/quests/{questId}/complete")
    public ApiResponse<QuestService.QuestCompleteView> completeQuest(@PathVariable String questId) {
        return ApiResponse.success(questService.completeQuest(questId));
    }

    /** 三选一奖励选择。 */
    @PostMapping("/quests/{questId}/choose-reward")
    public ApiResponse<Void> chooseReward(@PathVariable String questId,
                                          @RequestBody ChooseRewardRequest request) {
        questService.chooseReward(questId, request.getChoiceId(), request.getOptionIndex());
        return ApiResponse.success(null);
    }

    /** 首页主线摘要。 */
    @GetMapping("/quests/active-summary")
    public ApiResponse<QuestService.ActiveQuestSummary> getActiveQuestSummary() {
        return ApiResponse.success(questService.getActiveQuestSummary());
    }

    // ==================== NPC 对话 ====================

    /** NPC 对话。 */
    @PostMapping("/npcs/{npcId}/talk")
    public ApiResponse<NpcDialogueService.DialogueView> talkNpc(@PathVariable String npcId) {
        return ApiResponse.success(npcDialogueService.talk(npcId));
    }

    // ==================== 教学 ====================

    /** 教学状态。 */
    @GetMapping("/tutorial")
    public ApiResponse<TutorialService.TutorialStateView> getTutorialState() {
        return ApiResponse.success(tutorialService.getTutorialState());
    }

    /** 完成教学步骤。 */
    @PostMapping("/tutorial/{stepId}/complete")
    public ApiResponse<Void> completeTutorialStep(@PathVariable String stepId) {
        tutorialService.completeStep(stepId);
        return ApiResponse.success(null);
    }

    /** 跳过教学。 */
    @PostMapping("/tutorial/skip")
    public ApiResponse<Void> skipTutorial() {
        tutorialService.skipTutorial();
        return ApiResponse.success(null);
    }

    // ==================== 地图变更 ====================

    /** 永久地图变更列表。 */
    @GetMapping("/map-changes")
    public ApiResponse<List<QuestService.MapChangeView>> getMapChanges() {
        return ApiResponse.success(questService.getMapChanges());
    }

    // ==================== DTO ====================

    @Data
    public static class ChooseRewardRequest {
        private String choiceId;
        private int optionIndex;
    }
}
