package com.petgame.battle.event;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 战斗事件（技术方案 §22）。
 * <p>
 * 后端输出标准化事件，前端仅根据事件播放表现。
 * 常用字段直接建模，扩展数据放 data 负载。
 */
@Data
public class BattleEvent {

    /** 事件类型。 */
    private BattleEventType type;

    /** 回合号。 */
    private int round;

    /** 行动方单位 ID。 */
    private String sourceId;

    /** 目标单位 ID。 */
    private String targetId;

    /** 技能 ID（技能相关事件）。 */
    private String skillId;

    /** 状态 ID（状态相关事件）。 */
    private String statusId;

    /** 数值（伤害/治疗/护盾值等）。 */
    private Integer value;

    /** 是否暴击。 */
    private Boolean critical;

    /** 属性克制方向：ADVANTAGE / DISADVANTAGE / NEUTRAL。 */
    private String elementRelation;

    /** 扩展负载。 */
    private Map<String, Object> data = new LinkedHashMap<>();

    public static BattleEvent of(BattleEventType type, int round) {
        BattleEvent event = new BattleEvent();
        event.setType(type);
        event.setRound(round);
        return event;
    }

    public BattleEvent source(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public BattleEvent target(String targetId) {
        this.targetId = targetId;
        return this;
    }

    public BattleEvent skill(String skillId) {
        this.skillId = skillId;
        return this;
    }

    public BattleEvent status(String statusId) {
        this.statusId = statusId;
        return this;
    }

    public BattleEvent value(int value) {
        this.value = value;
        return this;
    }

    public BattleEvent critical(boolean critical) {
        this.critical = critical;
        return this;
    }

    public BattleEvent elementRelation(String relation) {
        this.elementRelation = relation;
        return this;
    }

    public BattleEvent put(String key, Object val) {
        this.data.put(key, val);
        return this;
    }
}
