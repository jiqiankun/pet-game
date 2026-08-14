package com.petgame.battle.victory;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.model.BattleUnit;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.VictoryInteractionConfig.Interaction;
import com.petgame.statistics.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敌方胜利互动服务（阶段 12，技术方案 §83）。
 * <p>
 * 只做战败表现增强：玩家战败后根据获胜方类型 + 战况标签，从配置中选择一条互动返回前端播放。
 * 不参与战斗数值计算、不改变战败结算。选择算法：候选池 → 排除最近播放 → 按权重随机；
 * 无专属配置时回退公共池，无匹配时回退默认旁白。
 * <p>
 * 固定 Boss 复用 {@link StatisticsService} 记录挑战次数 / 连续战败（普通野怪不记录），
 * 用于 REPEATED_DEFEAT 标签与后续「复仇成功」反馈。
 */
@Service
public class VictoryInteractionService {

    private static final Logger log = LoggerFactory.getLogger(VictoryInteractionService.class);

    /** 统计键前缀：Boss 累计挑战次数（BOSS_CHALLENGE_<bossId>）。 */
    public static final String PREFIX_BOSS_CHALLENGE = "BOSS_CHALLENGE_";
    /** 统计键前缀：Boss 连续战败次数（BOSS_CONSECUTIVE_<bossId>）。 */
    public static final String PREFIX_BOSS_CONSECUTIVE = "BOSS_CONSECUTIVE_";

    /** 最近互动防重复队列长度。 */
    private static final int MAX_RECENT = 5;
    /** 彩蛋入场概率（1%）。 */
    private static final double EASTER_EGG_CHANCE = 0.01;
    /** 行为型标签判定阈值（治疗/换宠/重复技能次数）。 */
    private static final int BEHAVIOR_THRESHOLD = 3;

    /** 战况标签常量。 */
    public static final String TAG_NORMAL = "NORMAL_LOSS";
    public static final String TAG_CLOSE = "CLOSE_LOSS";
    public static final String TAG_CRUSHED = "CRUSHED";
    public static final String TAG_COMEBACK = "COMEBACK_LOSS";
    public static final String TAG_REPEATED = "REPEATED_DEFEAT";
    public static final String TAG_HEAL = "EXCESSIVE_HEAL";
    public static final String TAG_SWITCH = "EXCESSIVE_SWITCH";
    public static final String TAG_SKILL = "REPEATED_SKILL";

    private final GameConfigRegistry registry;
    private final StatisticsService statisticsService;
    private final GameRandom random = new GameRandom();

    /** 每个存档最近播放的互动 ID 环形队列（内存态，重启即清空，可接受）。 */
    private final Map<String, Deque<String>> recentBySave = new ConcurrentHashMap<>();

    public VictoryInteractionService(GameConfigRegistry registry, StatisticsService statisticsService) {
        this.registry = registry;
        this.statisticsService = statisticsService;
    }

    /**
     * 记录固定 Boss 的挑战次数与连续战败（普通野怪不记录）。
     * 玩家获胜时重置连续战败；战败时累加。REQUIRES_NEW，失败不阻断主流程。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBossChallenge(String saveId, BattleContext ctx, boolean playerWon) {
        if (statisticsService == null || !"BOSS".equals(ctx.getBattleType()) || ctx.getBossId() == null) {
            return;
        }
        String bossId = ctx.getBossId();
        statisticsService.increment(saveId, PREFIX_BOSS_CHALLENGE + bossId, 1);
        String consecutiveKey = PREFIX_BOSS_CONSECUTIVE + bossId;
        if (playerWon) {
            statisticsService.set(saveId, consecutiveKey, 0);
        } else {
            statisticsService.increment(saveId, consecutiveKey, 1);
        }
    }

    /**
     * 选择一条胜利互动。任何异常只记日志并返回 null，不阻断主结算。
     *
     * @param battleCtx 已结束且玩家战败的战斗上下文
     * @param saveId    存档 ID（防重复与挑战统计用）
     * @return 胜利互动视图；无可用内容时返回 null（前端隐藏互动区）
     */
    public VictoryInteractionView select(BattleContext battleCtx, String saveId) {
        try {
            BattleDefeatContext ctx = buildContext(battleCtx, saveId);
            computeTags(ctx);
            Interaction chosen = weightedRandom(collectCandidates(ctx), saveId);
            if (chosen == null) {
                return null;
            }
            return toView(ctx, chosen);
        } catch (Exception e) {
            log.warn("胜利互动选择异常（不阻断结算，返回 null）：{}", e.getMessage());
            return null;
        }
    }

    // ==================== 上下文构建 ====================

    private BattleDefeatContext buildContext(BattleContext battleCtx, String saveId) {
        BattleDefeatContext ctx = new BattleDefeatContext();
        String winnerType = resolveWinnerType(battleCtx.getBattleType());
        ctx.setWinnerType(winnerType);

        String winnerId = null;
        String winnerName = null;
        BattleUnit repUnit = repEnemyUnit(battleCtx);
        if ("BOSS".equals(winnerType)) {
            winnerId = battleCtx.getBossId();
            BossesConfig.BossConfig boss = registry.getBoss(winnerId);
            winnerName = boss != null ? boss.getName() : winnerId;
        } else if ("WILD_PET".equals(winnerType)) {
            winnerName = repUnit != null ? repUnit.getName() : null;
        } else {
            winnerName = repUnit != null ? repUnit.getName() : "训练师";
        }
        ctx.setWinnerId("BOSS".equals(winnerType) ? winnerId : null);
        ctx.setWinnerName(winnerName);
        // 获胜方风格：Boss 取 victoryStyle，野外取 victoryBehavior（无则 null → 通用公共池）
        if ("BOSS".equals(winnerType)) {
            BossesConfig.BossConfig boss = registry.getBoss(winnerId);
            ctx.setWinnerStyle(boss != null ? boss.getVictoryStyle() : null);
        } else if ("WILD_PET".equals(winnerType) && repUnit != null && repUnit.getSpeciesId() != null) {
            var species = registry.getSpecies(repUnit.getSpeciesId());
            ctx.setWinnerStyle(species != null ? species.getVictoryBehavior() : null);
        }

        ctx.setWinnerHpPercent(avgAliveHpPercent(battleCtx));
        ctx.setTurnCount(battleCtx.getCurrentRound());
        ctx.setPlayerHealCount(countEvents(battleCtx, event -> isPlayerSide(battleCtx, event.getTargetId())
                && "HEAL".equals(event.getType().name())));
        ctx.setPlayerSwitchCount(countEvents(battleCtx, event -> isPlayerSide(battleCtx, event.getSourceId())
                && "PET_SWITCHED".equals(event.getType().name())));
        ctx.setRepeatedSkillCount(maxRepeatedSkill(battleCtx));
        ctx.setEnemyKnockoutCount((int) countEvents(battleCtx,
                event -> "PET_DEFEATED".equals(event.getType().name()) && isEnemySide(battleCtx, event.getTargetId())));

        // 固定 Boss 读取挑战次数（普通野怪不读，保持轻量）
        if ("BOSS".equals(winnerType) && winnerId != null && statisticsService != null) {
            ctx.setChallengeCount((int) statisticsService.getStat(saveId, PREFIX_BOSS_CHALLENGE + winnerId));
            ctx.setConsecutiveDefeatCount(
                    (int) statisticsService.getStat(saveId, PREFIX_BOSS_CONSECUTIVE + winnerId));
        }
        return ctx;
    }

    /** 战斗类型 → 获胜方类型：BOSS→BOSS、WILD→WILD_PET、其余→TRAINER（含 TEST 预留）。 */
    private String resolveWinnerType(String battleType) {
        if ("BOSS".equals(battleType)) {
            return "BOSS";
        }
        if ("WILD".equals(battleType)) {
            return "WILD_PET";
        }
        return "TRAINER";
    }

    /** 取敌方存活单位的代表（优先在场且存活；否则存活）。无存活则取第一个。 */
    private BattleUnit repEnemyUnit(BattleContext battleCtx) {
        List<BattleUnit> units = battleCtx.getEnemySide().getUnits();
        for (BattleUnit u : units) {
            if (u.isAlive() && u.isActive()) {
                return u;
            }
        }
        for (BattleUnit u : units) {
            if (u.isAlive()) {
                return u;
            }
        }
        return units.isEmpty() ? null : units.get(0);
    }

    /** 敌方存活单位平均 HP 百分比（0~1）。无存活时返回 0。 */
    private double avgAliveHpPercent(BattleContext battleCtx) {
        double sum = 0;
        int n = 0;
        for (BattleUnit u : battleCtx.getEnemySide().getUnits()) {
            if (u.isAlive()) {
                sum += (double) Math.max(0, u.getCurrentHp()) / Math.max(1, u.getMaxHp());
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private boolean isPlayerSide(BattleContext battleCtx, String unitId) {
        return unitId != null && battleCtx.getPlayerSide().findUnit(unitId) != null;
    }

    private boolean isEnemySide(BattleContext battleCtx, String unitId) {
        return unitId != null && battleCtx.getEnemySide().findUnit(unitId) != null;
    }

    private int countEvents(BattleContext battleCtx, java.util.function.Predicate<BattleEvent> pred) {
        return (int) battleCtx.getEvents().stream().filter(pred).count();
    }

    /** 玩家侧同一技能最高使用次数（用于 REPEATED_SKILL）。 */
    private int maxRepeatedSkill(BattleContext battleCtx) {
        Map<String, Integer> counter = new HashMap<>();
        for (BattleEvent e : battleCtx.getEvents()) {
            if (!"SKILL_CAST".equals(e.getType().name()) || !isPlayerSide(battleCtx, e.getSourceId())) {
                continue;
            }
            if (e.getSkillId() != null) {
                counter.merge(e.getSkillId(), 1, Integer::sum);
            }
        }
        return counter.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    // ==================== 战况标签 ====================

    private void computeTags(BattleDefeatContext ctx) {
        List<String> tags = new ArrayList<>();
        double hp = ctx.getWinnerHpPercent();
        if (hp <= 0.15) {
            tags.add(TAG_CLOSE);
        }
        if (hp >= 0.85) {
            tags.add(TAG_CRUSHED);
        }
        // 先倒后输（玩家曾击败至少 1 个敌方单位）→ 反杀
        if (ctx.getEnemyKnockoutCount() > 0) {
            tags.add(TAG_COMEBACK);
        }
        // 固定 Boss 连续战败 ≥ 2 → 宿敌感
        if ("BOSS".equals(ctx.getWinnerType()) && ctx.getConsecutiveDefeatCount() >= 2) {
            tags.add(TAG_REPEATED);
        }
        if (ctx.getPlayerHealCount() >= BEHAVIOR_THRESHOLD) {
            tags.add(TAG_HEAL);
        }
        if (ctx.getPlayerSwitchCount() >= BEHAVIOR_THRESHOLD) {
            tags.add(TAG_SWITCH);
        }
        if (ctx.getRepeatedSkillCount() >= BEHAVIOR_THRESHOLD) {
            tags.add(TAG_SKILL);
        }
        if (tags.isEmpty()) {
            tags.add(TAG_NORMAL);
        }
        ctx.setTags(tags);
    }

    // ==================== 候选与随机 ====================

    private List<Interaction> collectCandidates(BattleDefeatContext ctx) {
        List<Interaction> all = registry.getVictoryInteractionConfig() != null
                ? registry.getVictoryInteractionConfig().getInteractions()
                : List.of();
        String winnerType = ctx.getWinnerType();
        String winnerStyle = ctx.getWinnerStyle();
        String winnerId = ctx.getWinnerId();

        // Level 1：专属（targetId 匹配）
        List<Interaction> specific = all.stream()
                .filter(i -> winnerType.equals(i.getWinnerType()))
                .filter(i -> i.getTargetId() != null && !i.getTargetId().isBlank()
                        && i.getTargetId().equals(winnerId))
                .filter(i -> matchesStyle(i, winnerStyle))
                .filter(i -> matchesContext(i, ctx))
                .toList();
        if (!specific.isEmpty()) {
            return specific;
        }
        // Level 2：公共池（targetId 为空）+ 风格匹配
        List<Interaction> common = all.stream()
                .filter(i -> winnerType.equals(i.getWinnerType()))
                .filter(i -> i.getTargetId() == null || i.getTargetId().isBlank())
                .filter(i -> matchesStyle(i, winnerStyle))
                .filter(i -> matchesContext(i, ctx))
                .toList();
        if (!common.isEmpty()) {
            return common;
        }
        // Level 3：回退到仅 winnerType + context（忽略风格）
        return all.stream()
                .filter(i -> winnerType.equals(i.getWinnerType()))
                .filter(i -> matchesContext(i, ctx))
                .toList();
    }

    /** 风格匹配：互动无风格（通用）即匹配；有风格则需与获胜方已知风格一致；获胜方无已知风格时风格专属不适用。 */
    private boolean matchesStyle(Interaction i, String winnerStyle) {
        if (i.getStyle() == null || i.getStyle().isBlank()) {
            return true;
        }
        return winnerStyle != null && winnerStyle.equals(i.getStyle());
    }

    /** 战况匹配：互动无战况（通用）即匹配；否则需命中任一战况标签。 */
    private boolean matchesContext(Interaction i, BattleDefeatContext ctx) {
        if (i.getContexts() == null || i.getContexts().isEmpty()) {
            return true;
        }
        for (String c : i.getContexts()) {
            if (ctx.hasTag(c)) {
                return true;
            }
        }
        return false;
    }

    /** 按权重随机，彩蛋低概率入场，排除最近播放（防重复）。 */
    private Interaction weightedRandom(List<Interaction> candidates, String saveId) {
        List<Interaction> withEasterEggs = new ArrayList<>();
        for (Interaction i : candidates) {
            if ("EASTER_EGG".equals(i.getRarity())) {
                if (random.chance(EASTER_EGG_CHANCE)) {
                    withEasterEggs.add(i);
                }
            } else {
                withEasterEggs.add(i);
            }
        }
        if (withEasterEggs.isEmpty()) {
            return null;
        }
        // 防重复：排除最近播放的互动
        Deque<String> recent = recentBySave.computeIfAbsent(saveId, k -> new ArrayDeque<>());
        List<Interaction> deduped = new ArrayList<>();
        synchronized (recent) {
            for (Interaction i : withEasterEggs) {
                if (!recent.contains(i.getId())) {
                    deduped.add(i);
                }
            }
        }
        if (deduped.isEmpty()) {
            deduped = withEasterEggs; // 全部最近播过，放开限制
        }
        // 加权随机
        Interaction chosen = roll(deduped);
        if (chosen != null) {
            synchronized (recent) {
                recent.addLast(chosen.getId());
                while (recent.size() > MAX_RECENT) {
                    recent.removeFirst();
                }
            }
        }
        return chosen;
    }

    private Interaction roll(List<Interaction> pool) {
        int total = pool.stream().mapToInt(i -> Math.max(1, i.getWeight())).sum();
        int roll = random.nextInt(1, total);
        int acc = 0;
        for (Interaction i : pool) {
            acc += Math.max(1, i.getWeight());
            if (roll <= acc) {
                return i;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private VictoryInteractionView toView(BattleDefeatContext ctx, Interaction chosen) {
        VictoryInteractionView view = new VictoryInteractionView();
        view.setId(chosen.getId());
        view.setWinnerType(ctx.getWinnerType());
        view.setWinnerName(ctx.getWinnerName());
        view.setPresentationType(chosen.getPresentationType());
        view.setActionId(chosen.getActionId());
        view.setCry(chosen.getCry());
        view.setText(chosen.getText());
        // 记录命中的第一个战况标签（仅展示用）
        view.setContext(matchedContext(chosen, ctx));
        return view;
    }

    private String matchedContext(Interaction i, BattleDefeatContext ctx) {
        if (i.getContexts() == null || i.getContexts().isEmpty()) {
            return null;
        }
        for (String c : i.getContexts()) {
            if (ctx.hasTag(c)) {
                return c;
            }
        }
        return null;
    }
}