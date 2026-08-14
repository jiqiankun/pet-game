package com.petgame.battle.passive;

import com.petgame.config.model.PassiveSkillConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 被动效果归一化 / 去重（阶段 14：被动技能体系重构）。
 * <p>
 * 在战斗单位装配被动后、进入 {@link PassiveManager} 前调用，按 effectGroup 与 stackRule
 * 归一化 {@code unit.passives}，避免同名 / 同组被动重复生效造成的数值膨胀。
 * <p>
 * 规则（仅同 effectGroup 内生效）：
 * <ul>
 *   <li>同名被动（同 id）始终只保留一个。</li>
 *   <li>UNIQUE：同组只保留优先级最高（再按数值）的一个。</li>
 *   <li>HIGHEST_ONLY：同组只取最高效果（优先级 → 数值），避免「固有迅捷 + 技能书迅捷」双倍。</li>
 *   <li>ADDITIVE / LIMITED：允许叠加，保留同组全部（数值累加由效果解释层负责）。</li>
 *   <li>无 effectGroup：不参与跨被动去重，全部保留。</li>
 * </ul>
 */
public final class PassiveEffectResolver {

    private PassiveEffectResolver() {
    }

    /**
     * 归一化被动列表。
     *
     * @param passives 装配后的被动列表（可为 null）
     * @return 归一化后的被动列表（顺序稳定，不改变入参对象）
     */
    public static List<PassiveSkillConfig> normalize(List<PassiveSkillConfig> passives) {
        if (passives == null || passives.size() <= 1) {
            return passives;
        }
        List<PassiveSkillConfig> result = new ArrayList<>(passives.size());
        Map<String, PassiveSkillConfig> byId = new LinkedHashMap<>();
        Map<String, List<PassiveSkillConfig>> byGroup = new LinkedHashMap<>();

        for (PassiveSkillConfig p : passives) {
            // 同名去重：同 id 只保留一个
            if (byId.putIfAbsent(p.getId(), p) != null) {
                continue;
            }
            String group = p.getEffectGroup();
            if (group == null || group.isBlank()) {
                // 无组归属：直接保留
                result.add(p);
            } else {
                byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(p);
            }
        }

        for (Map.Entry<String, List<PassiveSkillConfig>> e : byGroup.entrySet()) {
            List<PassiveSkillConfig> group = e.getValue();
            String stackRule = group.get(0).getStackRule();
            if ("UNIQUE".equalsIgnoreCase(stackRule) || "HIGHEST_ONLY".equalsIgnoreCase(stackRule)) {
                // 只保留优先级最高（数值次之）的一个
                PassiveSkillConfig best = group.stream()
                        .max(Comparator.comparingInt(PassiveSkillConfig::getPriority)
                                .thenComparing(PassiveSkillConfig::getValue))
                        .orElse(group.get(0));
                result.add(best);
            } else {
                // ADDITIVE / LIMITED：允许叠加，保留全部
                result.addAll(group);
            }
        }
        return result;
    }
}