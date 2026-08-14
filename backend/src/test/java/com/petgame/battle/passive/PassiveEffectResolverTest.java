package com.petgame.battle.passive;

import com.petgame.config.model.PassiveSkillConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PassiveEffectResolver 归一化 / 去重单元测试（阶段 14 被动体系重构）。
 * <p>
 * 覆盖：同名去重、UNIQUE 同组只取一个、HIGHEST_ONLY 只取最高、ADDITIVE/LIMITED 允许叠加、
 * 无 effectGroup 不参与去重、null/单元素直接返回。
 */
class PassiveEffectResolverTest {

    @Test
    void normalize_nullReturnsNull() {
        assertNull(PassiveEffectResolver.normalize(null));
    }

    @Test
    void normalize_singleElement_returnedAsIs() {
        PassiveSkillConfig p = passive("P1", "ATTACK_BONUS", "HIGHEST_ONLY", 1, 10);
        assertSame(p, PassiveEffectResolver.normalize(List.of(p)).get(0));
    }

    @Test
    void normalize_duplicateSameId_keepsOne() {
        PassiveSkillConfig a = passive("P1", "ATTACK_BONUS", "HIGHEST_ONLY", 1, 10);
        PassiveSkillConfig b = passive("P1", "ATTACK_BONUS", "HIGHEST_ONLY", 1, 10);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(1, r.size(), "同名被动只保留一个");
    }

    @Test
    void normalize_highestOnly_keepsHighestPriorityThenValue() {
        // 固有「迅捷」+ 技能书「迅捷」：同组 ATTS大HIGHEST_ONLY，取优先级更高者
        PassiveSkillConfig innate = passive("SPEED_INNATE", "SPEED_BONUS", "HIGHEST_ONLY", 1, 8);
        PassiveSkillConfig book = passive("SPEED_BOOK", "SPEED_BONUS", "HIGHEST_ONLY", 2, 12);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(innate, book));
        assertEquals(1, r.size(), "HIGHEST_ONLY 同组只保留一个");
        assertEquals("SPEED_BOOK", r.get(0).getId(), "优先级高者胜出");
    }

    @Test
    void normalize_highestOnly_valueTiebreakWithinSamePriority() {
        PassiveSkillConfig a = passive("A", "HP_BONUS", "HIGHEST_ONLY", 1, 5);
        PassiveSkillConfig b = passive("B", "HP_BONUS", "HIGHEST_ONLY", 1, 15);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(1, r.size());
        assertEquals("B", r.get(0).getId(), "同优先级取数值更高者");
    }

    @Test
    void normalize_unique_keepsOnePerGroup() {
        PassiveSkillConfig a = passive("A", "SURVIVE_LETHAL", "UNIQUE", 1, 0);
        PassiveSkillConfig b = passive("B", "SURVIVE_LETHAL", "UNIQUE", 2, 0);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(1, r.size(), "UNIQUE 同组只允许一个");
        assertEquals("B", r.get(0).getId(), "强机制同组取优先级最高者");
    }

    @Test
    void normalize_additive_allowsAllInGroup() {
        PassiveSkillConfig a = passive("A", "HP_BONUS", "ADDITIVE", 1, 10);
        PassiveSkillConfig b = passive("B", "HP_BONUS", "ADDITIVE", 1, 10);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(2, r.size(), "ADDITIVE 允许同组叠加，保留全部");
    }

    @Test
    void normalize_limited_allowsAllInGroup() {
        PassiveSkillConfig a = passive("A", "DAMAGE_REDUCTION", "LIMITED", 1, 5);
        PassiveSkillConfig b = passive("B", "DAMAGE_REDUCTION", "LIMITED", 1, 5);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(2, r.size(), "LIMITED 允许叠加（上限由效果解释层负责）");
    }

    @Test
    void normalize_noGroup_keepsAllPassives() {
        PassiveSkillConfig a = passive("A", null, "HIGHEST_ONLY", 1, 10);
        PassiveSkillConfig b = passive("B", null, "HIGHEST_ONLY", 1, 10);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(a, b));
        assertEquals(2, r.size(), "无 effectGroup 不参与跨被动去重，全部保留");
    }

    @Test
    void normalize_mixedGroups_isolatedPerGroup() {
        PassiveSkillConfig atkInnate = passive("X_INNATE", "ATTACK_BONUS", "HIGHEST_ONLY", 1, 10);
        PassiveSkillConfig atkBook = passive("X_BOOK", "ATTACK_BONUS", "HIGHEST_ONLY", 2, 20);
        PassiveSkillConfig hpBook = passive("HP_BOOK", "HP_BONUS", "ADDITIVE", 1, 10);
        List<PassiveSkillConfig> r = PassiveEffectResolver.normalize(List.of(atkInnate, atkBook, hpBook));
        assertEquals(2, r.size(), "不同 effectGroup 互不影响，同组按规则各自处理");
    }

    private static PassiveSkillConfig passive(String id, String effectGroup, String stackRule,
                                               int priority, int value) {
        PassiveSkillConfig p = new PassiveSkillConfig();
        p.setId(id);
        p.setName(id);
        p.setEffectGroup(effectGroup);
        p.setStackRule(stackRule);
        p.setPriority(priority);
        p.setValue(value);
        return p;
    }
}