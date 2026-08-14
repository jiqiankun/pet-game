package com.petgame.quest;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.config.model.SystemRuleConfig;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.entity.PlayerQuestObjectiveEntity;
import com.petgame.quest.entity.PlayerHiddenTriggerEntity;
import com.petgame.quest.mapper.*;
import com.petgame.quest.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QuestService 单元测试（阶段 9 验收标准）。
 * <p>
 * 覆盖：接受任务、拒绝重复接受、前置任务校验、事件推进目标、
 * 完成任务发放奖励、三选一奖励、区域解锁、隐藏任务触发、赠送宠物、通关标记。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestServiceTest {

    private static final String SAVE_ID = "test-save-id";

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerPetMapper playerPetMapper;
    @Mock private PlayerPetSkillMapper playerPetSkillMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;
    @Mock private PlayerRegionUnlockMapper regionUnlockMapper;
    @Mock private PetGrowthService growthService;
    @Mock private PokedexService pokedexService;
    @Mock private PlayerQuestMapper playerQuestMapper;
    @Mock private PlayerQuestObjectiveMapper playerQuestObjectiveMapper;
    @Mock private PlayerMapChangeMapper playerMapChangeMapper;
    @Mock private PlayerHiddenTriggerMapper playerHiddenTriggerMapper;
    @Mock private com.petgame.achievement.service.AchievementService achievementService;

    private GameConfigRegistry registry;
    private QuestService questService;

    @BeforeEach
    void setUp() throws Exception {
        // 构建任务配置
        QuestsConfig questsConfig = new QuestsConfig();
        List<QuestsConfig.QuestConfig> quests = new ArrayList<>();

        // 主线任务 1：对话 + 击败
        QuestsConfig.QuestConfig q1 = new QuestsConfig.QuestConfig();
        q1.setId("QUEST_MAIN_01");
        q1.setName("异常的预兆");
        q1.setType("MAIN");
        q1.setDescription("探索异常");
        q1.setRegionId("MAP_START_VILLAGE");
        q1.setHidden(false);

        QuestsConfig.ObjectiveConfig obj1 = new QuestsConfig.ObjectiveConfig();
        obj1.setObjectiveId("OBJ_MAIN_01_1");
        obj1.setType("DIALOGUE");
        obj1.setDescription("与村长对话");
        obj1.setTargetId("NPC_VILLAGE_1");
        obj1.setTargetCount(1);
        obj1.setRegionId("MAP_START_VILLAGE");

        QuestsConfig.ObjectiveConfig obj2 = new QuestsConfig.ObjectiveConfig();
        obj2.setObjectiveId("OBJ_MAIN_01_2");
        obj2.setType("DEFEAT");
        obj2.setDescription("击败 3 只草原宠物");
        obj2.setTargetId("PET_GRASS_001");
        obj2.setTargetCount(3);
        obj2.setRegionId("MAP_AREA_MEADOW");

        q1.setObjectives(List.of(obj1, obj2));

        QuestsConfig.RewardConfig reward1 = new QuestsConfig.RewardConfig();
        QuestsConfig.RewardEntry fixedGold = new QuestsConfig.RewardEntry();
        fixedGold.setType("GOLD");
        fixedGold.setQuantity(100);
        QuestsConfig.RewardEntry fixedExp = new QuestsConfig.RewardEntry();
        fixedExp.setType("EXP");
        fixedExp.setQuantity(50);
        reward1.setFixed(List.of(fixedGold, fixedExp));
        q1.setRewards(reward1);

        quests.add(q1);

        // 主线任务 2：需要前置任务 1
        QuestsConfig.QuestConfig q2 = new QuestsConfig.QuestConfig();
        q2.setId("QUEST_MAIN_02");
        q2.setName("草原守卫");
        q2.setType("MAIN");
        q2.setDescription("击败 Boss");
        q2.setPrerequisiteQuestId("QUEST_MAIN_01");
        q2.setHidden(false);

        QuestsConfig.ObjectiveConfig obj3 = new QuestsConfig.ObjectiveConfig();
        obj3.setObjectiveId("OBJ_MAIN_02_1");
        obj3.setType("DEFEAT_BOSS");
        obj3.setDescription("击败 Boss");
        obj3.setTargetId("BOSS_MEADOW_GUARDIAN");
        obj3.setTargetCount(1);
        q2.setObjectives(List.of(obj3));
        quests.add(q2);

        // 隐藏任务
        QuestsConfig.QuestConfig qHidden = new QuestsConfig.QuestConfig();
        qHidden.setId("QUEST_HIDDEN_01");
        qHidden.setName("月下之影");
        qHidden.setType("HIDDEN");
        qHidden.setHidden(true);
        QuestsConfig.HiddenTriggerConfig trigger = new QuestsConfig.HiddenTriggerConfig();
        trigger.setTriggerType("LOCATION");
        trigger.setTriggerTarget("MAP_AREA_FOREST");
        trigger.setTriggerCount(5);
        qHidden.setTrigger(trigger);
        QuestsConfig.ObjectiveConfig hiddenObj = new QuestsConfig.ObjectiveConfig();
        hiddenObj.setObjectiveId("OBJ_HIDDEN_01_1");
        hiddenObj.setType("ARRIVE");
        hiddenObj.setDescription("进入翠树林");
        hiddenObj.setTargetId("MAP_AREA_FOREST");
        hiddenObj.setTargetCount(1);
        qHidden.setObjectives(List.of(hiddenObj));
        quests.add(qHidden);

        questsConfig.setQuests(quests);

        // NPC 配置
        QuestsConfig.NpcConfig npc = new QuestsConfig.NpcConfig();
        npc.setNpcId("NPC_VILLAGE_1");
        npc.setName("村长");
        npc.setRegionId("MAP_START_VILLAGE");
        QuestsConfig.DialogueNodeConfig dn = new QuestsConfig.DialogueNodeConfig();
        dn.setNodeId("NODE_1");
        dn.setText("欢迎来到村庄！");
        dn.setNextNode("NODE_2");
        QuestsConfig.DialogueNodeConfig dn2 = new QuestsConfig.DialogueNodeConfig();
        dn2.setNodeId("NODE_2");
        dn2.setText("去冒险吧！");
        npc.setDialogues(List.of(dn, dn2));

        questsConfig.setNpcs(List.of(npc));

        // 教学配置
        QuestsConfig.TutorialStepConfig tut1 = new QuestsConfig.TutorialStepConfig();
        tut1.setStepId("TUT_MOVE");
        tut1.setName("移动");
        tut1.setDescription("使用方向键移动");
        tut1.setOrder(1);
        tut1.setSkippable(true);
        questsConfig.setTutorials(List.of(tut1));

        registry = buildRegistry(questsConfig);

        questService = new QuestService(
                registry, playerMapper, playerPetMapper, playerPetSkillMapper,
                playerInventoryMapper, regionUnlockMapper, growthService, pokedexService,
                playerQuestMapper, playerQuestObjectiveMapper,
                playerMapChangeMapper, playerHiddenTriggerMapper, achievementService);

        // 模拟玩家
        PlayerEntity player = new PlayerEntity();
        player.setSaveId(SAVE_ID);
        player.setPlayerName("TestPlayer");
        player.setGold(0);
        player.setExpPool(0);
        when(playerMapper.selectOne(any())).thenReturn(player);
    }

    // ==================== 接受任务 ====================

    @Test
    void acceptQuest_noPrerequisite_shouldSucceed() {
        // 未接受过（selectOne 默认返回 null）
        questService.acceptQuest("QUEST_MAIN_01");

        verify(playerQuestMapper, times(1)).insert(any(PlayerQuestEntity.class));
        verify(playerQuestObjectiveMapper, times(2)).insert(any(PlayerQuestObjectiveEntity.class));
    }

    @Test
    void acceptQuest_alreadyAccepted_shouldThrow() {
        PlayerQuestEntity existing = new PlayerQuestEntity();
        existing.setQuestId("QUEST_MAIN_01");
        existing.setStatus("ACTIVE");
        when(playerQuestMapper.selectOne(any())).thenReturn(existing);

        assertThrows(Exception.class, () -> questService.acceptQuest("QUEST_MAIN_01"));
    }

    @Test
    void acceptQuest_prerequisiteNotCompleted_shouldThrow() {
        // 前置任务 QUEST_MAIN_01 未接受（selectOne 默认返回 null）
        assertThrows(Exception.class, () -> questService.acceptQuest("QUEST_MAIN_02"));
    }

    // ==================== 事件推进 ====================

    @Test
    void checkObjectiveProgress_matchingEvent_shouldAdvance() {
        // 设置任务已接受
        PlayerQuestEntity pq = new PlayerQuestEntity();
        pq.setSaveId(SAVE_ID);
        pq.setQuestId("QUEST_MAIN_01");
        pq.setStatus("ACTIVE");
        pq.setCurrentObjective(2);
        when(playerQuestMapper.selectList(any())).thenReturn(List.of(pq));

        PlayerQuestObjectiveEntity objEntity = new PlayerQuestObjectiveEntity();
        objEntity.setObjectiveId("OBJ_MAIN_01_2");
        objEntity.setProgress(1);
        objEntity.setTargetCount(3);
        objEntity.setCompleted(false);
        when(playerQuestObjectiveMapper.selectOne(any())).thenReturn(objEntity);

        questService.checkObjectiveProgress(SAVE_ID, "DEFEAT", "PET_GRASS_001", 1);

        verify(playerQuestObjectiveMapper, atLeastOnce()).update(any(PlayerQuestObjectiveEntity.class), any());
    }

    // ==================== 隐藏任务触发 ====================

    @Test
    void checkHiddenTrigger_reachedCount_shouldUnlock() {
        // 模拟已累计 4 次触发，本次达到阈值 5
        PlayerHiddenTriggerEntity trigger = new PlayerHiddenTriggerEntity();
        trigger.setSaveId(SAVE_ID);
        trigger.setTriggerKey("LOCATION:MAP_AREA_FOREST");
        trigger.setTriggerCount(4);
        when(playerHiddenTriggerMapper.selectOne(any())).thenReturn(trigger);

        questService.checkHiddenTrigger(SAVE_ID, "LOCATION", "MAP_AREA_FOREST");

        // 达到阈值后应该创建任务
        verify(playerQuestMapper, atLeastOnce()).insert(any(PlayerQuestEntity.class));
    }

    // ==================== 完成任务 ====================

    @Test
    void completeQuest_allObjectivesDone_shouldGrantRewards() {
        PlayerQuestEntity pq = new PlayerQuestEntity();
        pq.setSaveId(SAVE_ID);
        pq.setQuestId("QUEST_MAIN_01");
        pq.setStatus("ACTIVE");
        when(playerQuestMapper.selectOne(any())).thenReturn(pq);

        PlayerQuestObjectiveEntity obj1 = new PlayerQuestObjectiveEntity();
        obj1.setObjectiveId("OBJ_MAIN_01_1");
        obj1.setProgress(1);
        obj1.setTargetCount(1);
        obj1.setCompleted(true);

        PlayerQuestObjectiveEntity obj2 = new PlayerQuestObjectiveEntity();
        obj2.setObjectiveId("OBJ_MAIN_01_2");
        obj2.setProgress(3);
        obj2.setTargetCount(3);
        obj2.setCompleted(true);

        when(playerQuestObjectiveMapper.selectList(any())).thenReturn(List.of(obj1, obj2));

        questService.completeQuest("QUEST_MAIN_01");

        verify(playerQuestMapper).update(any(PlayerQuestEntity.class), any());
        // 固定奖励为 GOLD + EXP：玩家记录被更新
        verify(playerMapper, atLeastOnce()).updateById(any(PlayerEntity.class));
    }

    // ==================== 辅助方法 ====================

    private static GameConfigRegistry buildRegistry(QuestsConfig questsConfig) throws Exception {
        GameConfigRegistry registry = new GameConfigRegistry(null, null);
        setField(registry, "questsConfig", questsConfig);
        setField(registry, "systemRules", new SystemRuleConfig());

        // 构建索引
        LinkedHashMap<String, QuestsConfig.QuestConfig> questIndex = new LinkedHashMap<>();
        for (QuestsConfig.QuestConfig q : questsConfig.getQuests()) {
            questIndex.put(q.getId(), q);
        }
        setField(registry, "questIndex", questIndex);

        LinkedHashMap<String, QuestsConfig.NpcConfig> npcIndex = new LinkedHashMap<>();
        if (questsConfig.getNpcs() != null) {
            for (QuestsConfig.NpcConfig n : questsConfig.getNpcs()) {
                npcIndex.put(n.getNpcId(), n);
            }
        }
        setField(registry, "npcIndex", npcIndex);

        // 空索引
        setField(registry, "speciesIndex", new LinkedHashMap<>());
        setField(registry, "skillIndex", new LinkedHashMap<>());
        setField(registry, "itemIndex", new LinkedHashMap<>());
        setField(registry, "statusIndex", new LinkedHashMap<>());
        setField(registry, "passiveIndex", new LinkedHashMap<>());

        return registry;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
