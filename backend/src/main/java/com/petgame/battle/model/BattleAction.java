package com.petgame.battle.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 战斗行动意图（前端提交）。
 * <p>
 * 前端只提交「意图」（用什么技能打谁 / 防御 / 换谁上场），
 * 不允许提交计算结果；伤害、命中、暴击一律由后端计算。
 */
@Data
@NoArgsConstructor
public class BattleAction {

    /** 行动类型：SKILL / DEFEND / SWITCH / CAPTURE / FLEE。 */
    private String type;

    /** 行动发起方单位 ID（战斗内唯一）。 */
    private String petId;

    /** SKILL 行动的技能 ID。 */
    private String skillId;

    /** SKILL/CAPTURE 行动的目标单位 ID。 */
    private String targetId;

    /** SWITCH 行动的候补单位 ID。 */
    private String switchPetId;

    /** CAPTURE 行动使用的捕捉球道具 ID。 */
    private String itemId;

    public static BattleAction skill(String petId, String skillId, String targetId) {
        BattleAction action = new BattleAction();
        action.setType("SKILL");
        action.setPetId(petId);
        action.setSkillId(skillId);
        action.setTargetId(targetId);
        return action;
    }

    public static BattleAction defend(String petId) {
        BattleAction action = new BattleAction();
        action.setType("DEFEND");
        action.setPetId(petId);
        return action;
    }

    public static BattleAction switchPet(String petId, String switchPetId) {
        BattleAction action = new BattleAction();
        action.setType("SWITCH");
        action.setPetId(petId);
        action.setSwitchPetId(switchPetId);
        return action;
    }

    public static BattleAction capture(String petId, String itemId, String targetId) {
        BattleAction action = new BattleAction();
        action.setType("CAPTURE");
        action.setPetId(petId);
        action.setItemId(itemId);
        action.setTargetId(targetId);
        return action;
    }

    public static BattleAction flee(String petId) {
        BattleAction action = new BattleAction();
        action.setType("FLEE");
        action.setPetId(petId);
        return action;
    }
}
