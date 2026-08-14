package com.petgame.quest;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerTutorialEntity;
import com.petgame.quest.mapper.PlayerTutorialMapper;
import com.petgame.quest.service.TutorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TutorialService 单元测试（阶段 9）。
 * <p>
 * 覆盖：获取教学状态、完成步骤、跳过教学、技能书发放。
 */
@ExtendWith(MockitoExtension.class)
class TutorialServiceTest {

    private static final String SAVE_ID = "test-save-id";

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerTutorialMapper playerTutorialMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;

    private GameConfigRegistry registry;
    private TutorialService tutorialService;

    @BeforeEach
    void setUp() throws Exception {
        QuestsConfig questsConfig = new QuestsConfig();
        questsConfig.setQuests(List.of());
        questsConfig.setNpcs(List.of());

        QuestsConfig.TutorialStepConfig tut1 = new QuestsConfig.TutorialStepConfig();
        tut1.setStepId("TUT_MOVE");
        tut1.setName("移动");
        tut1.setDescription("使用方向键移动");
        tut1.setOrder(1);
        tut1.setSkippable(true);

        QuestsConfig.TutorialStepConfig tut2 = new QuestsConfig.TutorialStepConfig();
        tut2.setStepId("TUT_CAPTURE");
        tut2.setName("捕捉");
        tut2.setDescription("使用捕捉球捕捉宠物");
        tut2.setOrder(2);
        tut2.setSkippable(false);

        QuestsConfig.RewardEntry captureRewardEntry = new QuestsConfig.RewardEntry();
        captureRewardEntry.setType("SKILL_BOOK");
        captureRewardEntry.setItemId("ITEM_SKILL_BOOK_LEAVE_ALIVE");
        captureRewardEntry.setQuantity(1);
        tut2.setRewards(List.of(captureRewardEntry));

        questsConfig.setTutorials(List.of(tut1, tut2));

        registry = buildRegistry(questsConfig);
        tutorialService = new TutorialService(registry, playerMapper, playerTutorialMapper, playerInventoryMapper);

        PlayerEntity player = new PlayerEntity();
        player.setSaveId(SAVE_ID);
        when(playerMapper.selectOne(any())).thenReturn(player);
    }

    @Test
    void getTutorialState_noProgress_returnsAllIncomplete() {
        when(playerTutorialMapper.selectList(any())).thenReturn(Collections.emptyList());

        TutorialService.TutorialStateView state = tutorialService.getTutorialState();

        assertEquals(2, state.getTotalCount());
        assertEquals(0, state.getCompletedCount());
        assertFalse(state.isAllCompleted());
        assertEquals(2, state.getSteps().size());
    }

    @Test
    void completeStep_marksAsComplete() {
        // 未完成过（selectOne 默认返回 null）
        tutorialService.completeStep("TUT_MOVE");

        verify(playerTutorialMapper).insert(any(PlayerTutorialEntity.class));
    }

    @Test
    void completeStep_captureStep_grantsSkillBook() {
        tutorialService.completeStep("TUT_CAPTURE");

        verify(playerTutorialMapper).insert(any(PlayerTutorialEntity.class));
        // 技能书奖励
        ArgumentCaptor<PlayerInventoryEntity> captor = ArgumentCaptor.forClass(PlayerInventoryEntity.class);
        verify(playerInventoryMapper).insert(captor.capture());
        assertEquals("ITEM_SKILL_BOOK_LEAVE_ALIVE", captor.getValue().getItemId());
    }

    @Test
    void skipTutorial_skipsSkippableSteps() {
        tutorialService.skipTutorial();

        // TUT_MOVE is skippable, TUT_CAPTURE is not
        verify(playerTutorialMapper, times(1)).insert(any(PlayerTutorialEntity.class));
    }

    private static GameConfigRegistry buildRegistry(QuestsConfig questsConfig) throws Exception {
        GameConfigRegistry registry = new GameConfigRegistry(null, null);
        setField(registry, "questsConfig", questsConfig);
        setField(registry, "questIndex", new LinkedHashMap<>());
        setField(registry, "npcIndex", new LinkedHashMap<>());
        setField(registry, "itemIndex", new LinkedHashMap<>());
        return registry;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
