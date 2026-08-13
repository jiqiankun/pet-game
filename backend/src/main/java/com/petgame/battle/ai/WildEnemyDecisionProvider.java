package com.petgame.battle.ai;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 敌方 AI 基础版（阶段 3）：普通野怪偏随机选择。
 * <p>
 * 规则：从冷却就绪的技能中随机选一个，单体技能随机选择存活目标；
 * 无可用技能时防御。精英/Boss 级 AI 属于后续阶段，不在本阶段实现。
 */
@Component
public class WildEnemyDecisionProvider implements DecisionProvider {

    private final GameConfigRegistry registry;

    public WildEnemyDecisionProvider(GameConfigRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<BattleAction> decide(BattleContext ctx, BattleSide side) {
        List<BattleAction> actions = new ArrayList<>();
        BattleSide enemyOfSide = ctx.getOpposite(side);

        for (BattleUnit unit : side.getActiveAliveUnits()) {
            List<String> readySkills = unit.getReadySkillIds();
            // 沉默时无技能可用，交给引擎兜底为防御
            boolean silenced = com.petgame.battle.calculator.StatusModifiers
                    .of(unit, registry.getStatusIndex()).isSilenced();
            if (silenced) {
                readySkills = List.of();
            }
            if (readySkills.isEmpty()) {
                actions.add(BattleAction.defend(unit.getUnitId()));
                continue;
            }

            String skillId = readySkills.get(ctx.getRandom().nextInt(0, readySkills.size() - 1));
            SkillConfig skill = registry.getSkill(skillId);
            String targetId = resolveTarget(ctx, skill, unit, enemyOfSide, side);
            actions.add(BattleAction.skill(unit.getUnitId(), skillId, targetId));
        }
        return actions;
    }

    private String resolveTarget(BattleContext ctx, SkillConfig skill, BattleUnit unit,
                                 BattleSide enemyOfSide, BattleSide ownSide) {
        return switch (skill.getTarget()) {
            case "ENEMY_SINGLE" -> {
                List<BattleUnit> targets = enemyOfSide.getActiveAliveUnits();
                // 嘲讽重定向由引擎统一处理，这里随机选择即可
                yield targets.isEmpty() ? null
                        : targets.get(ctx.getRandom().nextInt(0, targets.size() - 1)).getUnitId();
            }
            case "ALLY_SINGLE" -> {
                List<BattleUnit> allies = ownSide.getActiveAliveUnits();
                yield allies.isEmpty() ? null
                        : allies.get(ctx.getRandom().nextInt(0, allies.size() - 1)).getUnitId();
            }
            default -> null;
        };
    }
}
