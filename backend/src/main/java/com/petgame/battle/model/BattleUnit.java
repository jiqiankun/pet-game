package com.petgame.battle.model;

import com.petgame.config.model.PassiveSkillConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗单位（运行时宠物数据）。
 * <p>
 * 战斗临时数据只存服务器内存（技术方案 §20-§21）：
 * 当前 HP、护盾、技能冷却、Buff/Debuff/异常状态全部随战斗创建与销毁，不落库。
 */
@Data
public class BattleUnit {

    /** 战斗内唯一 ID（玩家宠用 "P_" + 存档宠物 ID，测试敌人用配置 unitId）。 */
    private String unitId;

    /** 存档宠物 DB ID（测试敌方单位为 null）。 */
    private Long petDbId;

    /** 显示名称（昵称优先）。 */
    private String name;

    /** 属性 ID。 */
    private String element;

    /** 等级。 */
    private int level;

    // ---- 基础六维（战斗开始时快照，不含战斗 Buff）----

    private int maxHp;
    private int strength;
    private int spirit;
    private int defense;
    private int resistance;
    private int speed;

    // ---- 运行时状态 ----

    /** 当前 HP（跨战斗保留的部分由阶段 4 结算写回，阶段 3 不落库）。 */
    private int currentHp;

    /** 护盾值。 */
    private int shield;

    /** 是否存活。 */
    private boolean alive = true;

    /** 是否在场（候补为 false）。 */
    private boolean active;

    /** 场上位置 0-2（候补为 -1）。 */
    private int position = -1;

    /** 防御姿态（受到伤害减半，持续到下次行动）。 */
    private boolean defending;

    /** 蓄力中的技能 ID（null = 未蓄力）。 */
    private String chargingSkillId;

    /** 蓄力技能锁定的目标单位 ID（单体技能）。 */
    private String chargingTargetId;

    /** 蓄力剩余回合数。 */
    private int chargeRemaining;

    /** 本回合是否已行动。 */
    private boolean actedThisRound;

    /** 援护目标单位 ID（本单位替该目标承担部分单体伤害）。 */
    private String guardTargetId;

    /** 最后一次受到伤害的来源单位 ID（用于击败被动）。 */
    private String lastDamageSourceId;

    // ---- 技能与被动 ----

    /** 技能 ID 列表。 */
    private List<String> skillIds = new ArrayList<>();

    /** 技能冷却：skillId → 剩余冷却回合数（每宠独立计算）。 */
    private Map<String, Integer> cooldowns = new HashMap<>();

    /** 被动技能配置。 */
    private List<PassiveSkillConfig> passives = new ArrayList<>();

    /** 被动触发次数（用于 maxTriggerPerBattle 限制）。 */
    private Map<String, Integer> passiveTriggerCounts = new HashMap<>();

    /** 携带的状态实例。 */
    private List<StatusInstance> statuses = new ArrayList<>();

    // ---- 查询辅助 ----

    /** 剩余冷却为 0（可用）的技能 ID 列表。 */
    public List<String> getReadySkillIds() {
        List<String> ready = new ArrayList<>();
        for (String skillId : skillIds) {
            if (cooldowns.getOrDefault(skillId, 0) <= 0) {
                ready.add(skillId);
            }
        }
        return ready;
    }

    /** 是否存在指定状态。 */
    public boolean hasStatus(String statusId) {
        return statuses.stream().anyMatch(s -> s.getStatusId().equals(statusId));
    }

    /** 移除指定状态实例。 */
    public void removeStatus(String statusId) {
        statuses.removeIf(s -> s.getStatusId().equals(statusId));
    }
}
