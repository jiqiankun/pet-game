package com.petgame.battle.model;

import com.petgame.config.model.BossesConfig;
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

    /** 存档宠物 DB ID（测试/野生敌方单位为 null）。 */
    private Long petDbId;

    /** 种族 ID（玩家单位为其宠物种族，野生单位为物种配置 ID）。 */
    private String speciesId;

    // ---- 展示标识（阶段 14 美术验收，仅用于资源定位，不参与战斗计算）----

    /** 展示资源类型：PET=宠物立绘/图标、BOSS=Boss 核心、null=无资源（测试敌人）。 */
    private String artType;

    /** 展示资源 ID：PET 对应 speciesId，BOSS 对应 Boss ID；无资源时为 null。 */
    private String artId;

    /** 是否已被捕捉（捕捉成功后立即退出敌方队伍，不再参与战斗）。 */
    private boolean captured;

    /** 野生单位捕捉落库数据（仅野生单位非 null）。 */
    private WildUnitData wildData;

    /** 显示名称（昵称优先）。 */
    private String name;

    /** 属性 ID。 */
    private String element;

    /** 等级。 */
    private int level;

    /** 真实等级（阶段 13；与 level 保持一致，显式供前端展示）。 */
    private int actualLevel;

    /** 本场战斗有效等级（高难 Boss 可低于真实等级）。 */
    private int effectiveLevel;

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

    // ---- 新机制运行时字段（REV-006/REV-009）----

    /** 行动顺序干预加成（仅当前回合有效，回合结束清零；不修改基础速度）。 */
    private double actionOrderBoost = 0;

    /** 濒死保护次数（PROTECT_FROM_DEFEAT 效果附加，致死时保留 1HP 并消耗）。 */
    private int protectCharges = 0;

    /** 本回合已触发过的 oncePerTurn 被动 ID 集合（每回合开始清空）。 */
    private java.util.Set<String> passiveTurnMarks = new java.util.HashSet<>();

    /** 本行动已触发过的 oncePerAction 被动 ID 集合（每次行动开始清空）。 */
    private java.util.Set<String> passiveActionMarks = new java.util.HashSet<>();

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

    // ---- Boss 战斗扩展（阶段 7）----

    /** 控制抗性系数（1.0=无抗性，0.6=Boss，0.8=精英）。 */
    private double controlResistance = 1.0;

    /** 连续被控制计数（施加控制命中后递增）。 */
    private int consecutiveControlCount = 0;

    /** 连续未受控回合数（达到阈值时归零 consecutiveControlCount）。 */
    private int roundsWithoutControl = 0;

    /** 阶段触发器列表（Boss 专用，配置化）。 */
    private List<BossesConfig.PhaseTrigger> phaseTriggers = new ArrayList<>();

    /** 阶段触发状态（对应 phaseTriggers 每个元素是否已激活）。 */
    private List<Boolean> phaseActivated = new ArrayList<>();

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

    /**
     * 野生单位捕捉落库数据。
     * <p>
     * 捕捉成功结算时据此创建玩家宠物：六维资质、个体浮动、额外技能（稀有技能）、特殊外观。
     */
    @Data
    public static class WildUnitData {
        private int hpAptitude;
        private int strengthAptitude;
        private int spiritAptitude;
        private int defenseAptitude;
        private int resistanceAptitude;
        private int speedAptitude;
        private int baseHpOffset;
        private int baseStrengthOffset;
        private int baseSpiritOffset;
        private int baseDefenseOffset;
        private int baseResistanceOffset;
        private int baseSpeedOffset;
        /** 额外技能 ID（低概率携带的稀有技能）。 */
        private List<String> extraSkillIds = new ArrayList<>();
        /** 特殊外观标记（null = 无，阶段 10 多变体：APPEARANCE_SHINY/APPEARANCE_GLOW 等）。 */
        private String specialAppearance;
        /** 是否精英个体（阶段 10，需求 §57）。 */
        private boolean elite = false;
    }
}
