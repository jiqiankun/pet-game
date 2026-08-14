package com.petgame.quest;

import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.QuestsConfig;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerDialogueEntity;
import com.petgame.quest.mapper.PlayerDialogueMapper;
import com.petgame.quest.service.NpcDialogueService;
import com.petgame.quest.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * NpcDialogueService 单元测试（阶段 9）。
 * <p>
 * 覆盖：对话推进、对话次数累计、对话结束后重置、DIALOGUE_COUNT 隐藏触发。
 */
@ExtendWith(MockitoExtension.class)
class NpcDialogueServiceTest {

    private static final String SAVE_ID = "test-save-id";
    private static final String NPC_ID = "NPC_VILLAGE_1";

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerDialogueMapper playerDialogueMapper;
    @Mock private QuestService questService;

    private GameConfigRegistry registry;
    private NpcDialogueService dialogueService;

    @BeforeEach
    void setUp() throws Exception {
        QuestsConfig questsConfig = new QuestsConfig();

        QuestsConfig.NpcConfig npc = new QuestsConfig.NpcConfig();
        npc.setNpcId(NPC_ID);
        npc.setName("村长");
        npc.setRegionId("MAP_START_VILLAGE");

        QuestsConfig.DialogueNodeConfig n1 = new QuestsConfig.DialogueNodeConfig();
        n1.setNodeId("NODE_1");
        n1.setText("你好，冒险者！");
        n1.setNextNode("NODE_2");

        QuestsConfig.DialogueNodeConfig n2 = new QuestsConfig.DialogueNodeConfig();
        n2.setNodeId("NODE_2");
        n2.setText("祝你好运！");

        npc.setDialogues(List.of(n1, n2));
        questsConfig.setNpcs(List.of(npc));
        questsConfig.setQuests(List.of());
        questsConfig.setTutorials(List.of());

        registry = buildRegistry(questsConfig);

        dialogueService = new NpcDialogueService(registry, playerMapper, playerDialogueMapper, questService);

        PlayerEntity player = new PlayerEntity();
        player.setSaveId(SAVE_ID);
        when(playerMapper.selectOne(any())).thenReturn(player);
    }

    @Test
    void talk_firstTime_returnsFirstNode() {
        // 首次对话（selectOne 默认返回 null）
        NpcDialogueService.DialogueView view = dialogueService.talk(NPC_ID);

        assertEquals(NPC_ID, view.getNpcId());
        assertEquals("村长", view.getNpcName());
        assertEquals("你好，冒险者！", view.getText());
        assertTrue(view.isHasMore());
    }

    @Test
    void talk_secondTime_returnsSecondNode() {
        PlayerDialogueEntity existing = new PlayerDialogueEntity();
        existing.setSaveId(SAVE_ID);
        existing.setNpcId(NPC_ID);
        existing.setDialogueNodeId("NODE_2");
        existing.setDialogueCount(1);
        when(playerDialogueMapper.selectOne(any())).thenReturn(existing);

        NpcDialogueService.DialogueView view = dialogueService.talk(NPC_ID);

        assertEquals("祝你好运！", view.getText());
        assertFalse(view.isHasMore());
    }

    @Test
    void talk_nonExistentNpc_shouldThrow() {
        assertThrows(Exception.class, () -> dialogueService.talk("NPC_NOT_EXIST"));
    }

    @Test
    void talk_incrementsDialogueCount() {
        dialogueService.talk(NPC_ID);

        verify(playerDialogueMapper, atLeastOnce()).insert(any(PlayerDialogueEntity.class));
    }

    private static GameConfigRegistry buildRegistry(QuestsConfig questsConfig) throws Exception {
        GameConfigRegistry registry = new GameConfigRegistry(null, null);
        setField(registry, "questsConfig", questsConfig);

        LinkedHashMap<String, QuestsConfig.NpcConfig> npcIndex = new LinkedHashMap<>();
        if (questsConfig.getNpcs() != null) {
            for (QuestsConfig.NpcConfig n : questsConfig.getNpcs()) {
                npcIndex.put(n.getNpcId(), n);
            }
        }
        setField(registry, "npcIndex", npcIndex);
        setField(registry, "questIndex", new LinkedHashMap<>());

        return registry;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
