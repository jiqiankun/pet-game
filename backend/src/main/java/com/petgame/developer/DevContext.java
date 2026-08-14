package com.petgame.developer;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 开发者工具上下文（阶段 14）。
 * <p>
 * 保存开发者工具需要跨请求生效的状态：
 * <ul>
 *   <li>数据操作类一次性标志：强制精英、强制随机事件（消费后清除）。</li>
 *   <li>战斗调试持久开关：玩家方无敌 / 一击必杀 / 固定暴击 / 调试信息（开战时快照到战斗上下文）。</li>
 *   <li>一次性固定随机种子：下一次战斗使用该种子（消费后清除）。</li>
 * </ul>
 * 单机单用户，使用原子变量即可，无并发竞争问题。
 */
@Component
public class DevContext {

    /** 强制下一次野生遭遇为精英遭遇。 */
    private final AtomicBoolean forceElite = new AtomicBoolean(false);

    /** 强制下一次探索触发随机事件。 */
    private final AtomicBoolean forceRandomEvent = new AtomicBoolean(false);

    // ---- 战斗调试开关（持久，开战时快照到 BattleContext）----

    /** 玩家方无敌：战斗内玩家方单位不受伤害。 */
    private final AtomicBoolean playerInvincible = new AtomicBoolean(false);

    /** 玩家方一击必杀：战斗内玩家方攻击直接击杀目标。 */
    private final AtomicBoolean playerOneHitKill = new AtomicBoolean(false);

    /** 玩家方固定暴击：战斗内玩家方攻击必定暴击。 */
    private final AtomicBoolean playerFixedCrit = new AtomicBoolean(false);

    /** 记录伤害明细与随机数序列（战斗调试信息）。 */
    private final AtomicBoolean debugDamage = new AtomicBoolean(false);

    /** 一次性固定随机种子：下一次战斗使用（消费后清除）。 */
    private final AtomicReference<Long> fixedBattleSeed = new AtomicReference<>(null);

    // ------------------------------------------------------------
    // 强制精英 / 强制随机事件（一次性标志）
    // ------------------------------------------------------------

    /** 设置强制精英遭遇标志。 */
    public void setForceElite(boolean force) {
        forceElite.set(force);
    }

    /**
     * 消费强制精英标志：若置位则返回 true 并清除，否则返回 false。
     * 由野生遭遇生成流程调用。
     */
    public boolean consumeForceElite() {
        return forceElite.getAndSet(false);
    }

    /** 设置强制随机事件标志。 */
    public void setForceRandomEvent(boolean force) {
        forceRandomEvent.set(force);
    }

    /**
     * 消费强制随机事件标志：若置位则返回 true 并清除，否则返回 false。
     * 由随机事件触发流程调用。
     */
    public boolean consumeForceRandomEvent() {
        return forceRandomEvent.getAndSet(false);
    }

    // ------------------------------------------------------------
    // 战斗调试开关（持久）
    // ------------------------------------------------------------

    public void setPlayerInvincible(boolean on) {
        playerInvincible.set(on);
    }

    public boolean isPlayerInvincible() {
        return playerInvincible.get();
    }

    public void setPlayerOneHitKill(boolean on) {
        playerOneHitKill.set(on);
    }

    public boolean isPlayerOneHitKill() {
        return playerOneHitKill.get();
    }

    public void setPlayerFixedCrit(boolean on) {
        playerFixedCrit.set(on);
    }

    public boolean isPlayerFixedCrit() {
        return playerFixedCrit.get();
    }

    public void setDebugDamage(boolean on) {
        debugDamage.set(on);
    }

    public boolean isDebugDamage() {
        return debugDamage.get();
    }

    // ------------------------------------------------------------
    // 固定随机种子（一次性）
    // ------------------------------------------------------------

    /** 设置下一次战斗使用的固定随机种子。 */
    public void setFixedBattleSeed(Long seed) {
        fixedBattleSeed.set(seed);
    }

    /** 消费固定随机种子：返回并清除；未设置返回 null。 */
    public Long consumeFixedBattleSeed() {
        return fixedBattleSeed.getAndSet(null);
    }

    /** 查看固定随机种子（不消费，用于状态展示）；未设置返回 null。 */
    public Long peekFixedBattleSeed() {
        return fixedBattleSeed.get();
    }
}