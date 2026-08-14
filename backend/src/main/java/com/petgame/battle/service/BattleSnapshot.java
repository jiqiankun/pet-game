package com.petgame.battle.service;

import com.petgame.battle.event.BattleEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗快照（返回给前端的完整战斗状态视图）。
 * <p>
 * 开战/提交行动/查询战斗均返回此结构；events 为本次调用新增的事件序列
 * （开战时为登场被动等初始事件，提交行动时为本回合事件）。
 */
@Data
public class BattleSnapshot {

    private String battleId;

    /** 战斗类型：TEST / WILD / BOSS。 */
    private String battleType;

    /** Boss 战斗禁止捕捉（阶段 7）。 */
    private boolean uncapturable;

    /** 本场全局难度快照（阶段 13）。 */
    private String gameDifficulty;

    /** Boss 遭遇快照 ID（非 Boss 战为 null）。 */
    private Long bossSnapshotId;

    /** Boss 战玩家有效等级上限（null 表示未启用压制）。 */
    private Integer playerLevelCap;

    /** 随机种子（开发者模式可用于复现）。 */
    private long seed;

    /** 当前回合号（0 = 尚未开始回合）。 */
    private int currentRound;

    private boolean finished;

    /** 胜方：PLAYER / ENEMY / null（未结束或逃跑）。 */
    private String winner;

    /** 玩家是否已逃跑（野生战斗，同战败结算）。 */
    private boolean fled;

    private List<UnitSnapshot> playerUnits = new ArrayList<>();

    private List<UnitSnapshot> enemyUnits = new ArrayList<>();

    /** 本次调用新增的战斗事件序列。 */
    private List<BattleEvent> events = new ArrayList<>();

    // ---- 战斗调试信息（阶段 14，仅开发者调试开启时返回）----

    /** 是否开启伤害明细/随机数调试（debugDamage）。 */
    private boolean debugDamage;

    /** 本次战斗已录制的随机数序列（debugDamage 开启时返回，可查看/replay）。 */
    private List<String> debugRandomDraws = new ArrayList<>();
}
