<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useBattleStore, isActiveAlive } from '../../stores/battle'
import { useGameStore } from '../../stores/game'
import { useMapStore } from '../../stores/map'
import { apiGet } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { AutoStrategy, BattleAction, UnitSnapshot } from '../../types/battle'
import type { InventoryItemView } from '../../types/pet'

const battleStore = useBattleStore()
const gameStore = useGameStore()
const mapStore = useMapStore()

// 战斗速度控制（阶段 10：1x / 2x / 3x）
const battleSpeed = ref<1 | 2 | 3>(1)
const autoPlay = ref(false)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

/** 速度对应的自动回合间隔（毫秒） */
const speedInterval = computed(() => {
  const map = { 1: 1500, 2: 600, 3: 200 }
  return map[battleSpeed.value]
})

function setSpeed(s: 1 | 2 | 3) {
  battleSpeed.value = s
  if (autoPlay.value) restartAutoPlay()
}

function toggleAutoPlay() {
  autoPlay.value = !autoPlay.value
  if (autoPlay.value) {
    restartAutoPlay()
  } else {
    stopAutoPlay()
  }
}

function restartAutoPlay() {
  stopAutoPlay()
  autoPlayTimer = setInterval(async () => {
    if (snapshot.value?.finished || battleStore.loading) {
      stopAutoPlay()
      autoPlay.value = false
      return
    }
    // 自动提交回合（未选行动的宠物自动防御）
    await battleStore.submitActions()
  }, speedInterval.value)
}

function stopAutoPlay() {
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

// ==================== 自动战斗策略（阶段 10） ====================

/** 策略选项（后端四套预设）。 */
const strategyOptions: Array<{ value: AutoStrategy; label: string; desc: string }> = [
  { value: 'BALANCED', label: '均衡', desc: '攻击/治疗/控制平衡，默认推荐' },
  { value: 'AGGRESSIVE', label: '进攻', desc: '尽快结束战斗，强化斩杀与克制打击' },
  { value: 'DEFENSIVE', label: '稳健', desc: '降低死亡风险，提前恢复与换宠' },
  { value: 'CAPTURE', label: '捕捉', desc: '安全削血压至 1HP，避免误杀后捕捉' },
]

const autoPanelOpen = ref(false)
const autoStrategy = ref<AutoStrategy>('BALANCED')
const autoSwitch = ref(true)
const autoSwitchHpThreshold = ref(25)
const autoUseRecoveryItem = ref(false)
const autoRecoveryHpThreshold = ref(35)
const autoRevive = ref(false)
const captureTargetId = ref<string | null>(null)

/** 开启/关闭自动战斗（策略与开关同步持久化到玩家存档）。 */
async function toggleAutoBattle() {
  if (!snapshot.value) return
  try {
    await battleStore.configureAuto(snapshot.value.battleId, {
      enabled: !battleStore.autoEnabled,
      strategy: autoStrategy.value,
      autoSwitch: autoSwitch.value,
      autoSwitchHpThreshold: autoSwitchHpThreshold.value,
      autoUseRecoveryItem: autoUseRecoveryItem.value,
      autoRecoveryHpThreshold: autoRecoveryHpThreshold.value,
      autoRevive: autoRevive.value,
      captureTargetId: captureTargetId.value,
    })
    // 开启自动时联动启动自动播放（按速度档位自动提交回合）
    if (battleStore.autoEnabled && !autoPlay.value && !snapshot.value.finished) {
      autoPlay.value = true
      restartAutoPlay()
    }
    if (!battleStore.autoEnabled) {
      captureTargetId.value = null
    }
  } catch {
    // 错误已写入 store.error
  }
}

/** 仅保存策略/开关偏好（保持当前开关状态）。 */
async function saveAutoPreference() {
  if (!snapshot.value) return
  try {
    await battleStore.configureAuto(snapshot.value.battleId, {
      enabled: battleStore.autoEnabled,
      strategy: autoStrategy.value,
      autoSwitch: autoSwitch.value,
      autoSwitchHpThreshold: autoSwitchHpThreshold.value,
      autoUseRecoveryItem: autoUseRecoveryItem.value,
      autoRecoveryHpThreshold: autoRecoveryHpThreshold.value,
      autoRevive: autoRevive.value,
      captureTargetId: captureTargetId.value,
    })
  } catch {
    // 错误已写入 store.error
  }
}

// 固定种子输入（开发者模式复现用，可留空）
const seedInput = ref('')

// 目标选择：正在等待选目标的单位与技能
const targeting = ref<{ petId: string; skillId: string } | null>(null)

// 捕捉流程（阶段 5）：发起单位 + 球选择 + 目标选择
const captureMode = ref<{ petId: string } | null>(null)
const selectedBall = ref<InventoryItemView | null>(null)
const captureBalls = ref<InventoryItemView[]>([])

// 捕捉成功去向选择（需求 §48：队伍未满 6 只可直接入队）
const joinTeamChoice = ref(false)

const snapshot = computed(() => battleStore.snapshot)
const settlement = computed(() => battleStore.settlement)
const playerActive = computed(() => snapshot.value?.playerUnits.filter(isActiveAlive) ?? [])
const playerBench = computed(() => snapshot.value?.playerUnits.filter((u) => u.alive && !u.active) ?? [])
const isWild = computed(() => snapshot.value?.battleType === 'WILD')
const isBoss = computed(() => snapshot.value?.battleType === 'BOSS')
/** Boss 战斗禁止捕捉/逃跑。 */
const uncapturable = computed(() => isBoss.value || snapshot.value?.uncapturable === true)

/** 是否需要捕捉去向选择：野生战斗、有被捕捉宠物且队伍未满 6 只。 */
const needDestChoice = computed(() => {
  if (!snapshot.value || snapshot.value.battleType !== 'WILD') return false
  const hasCaptured = snapshot.value.enemyUnits.some((u) => u.captured)
  return hasCaptured && gameStore.teamMembers.length < 6
})

onMounted(() => {
  battleStore.loadSkillConfig()
  battleStore.loadAutoPreference()
})

// 新战斗开始/偏好加载完成时，同步面板表单默认值
watch(
  () => [snapshot.value?.battleId, battleStore.autoPreference],
  () => {
    const pref = battleStore.autoPreference
    if (pref) {
      autoStrategy.value = pref.strategy
      autoSwitch.value = pref.autoSwitch
      autoSwitchHpThreshold.value = pref.autoSwitchHpThreshold
      autoUseRecoveryItem.value = pref.autoUseRecoveryItem
      autoRecoveryHpThreshold.value = pref.autoRecoveryHpThreshold
      autoRevive.value = pref.autoRevive
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopAutoPlay()
})

/** 加载背包中的捕捉球（野生战斗用）。 */
async function loadCaptureBalls() {
  try {
    const res = await apiGet<{ items: InventoryItemView[]; gold: number }>('/api/inventory')
    captureBalls.value = ((res as ApiResponse<{ items: InventoryItemView[] }>).data.items || [])
      .filter((i) => i.itemType === 'CAPTURE_BALL' && i.quantity > 0)
  } catch {
    captureBalls.value = []
  }
}

// 进入野生战斗时加载捕捉球存量
watch(
  () => snapshot.value?.battleType,
  (type) => {
    if (type === 'WILD') loadCaptureBalls()
  },
  { immediate: true },
)

// 野生战斗回合变化后实时刷新捕捉率（需求 §47：宠物状态变化后实时更新）
watch(
  () => snapshot.value?.currentRound,
  () => {
    if (captureMode.value && !snapshot.value?.finished) {
      battleStore.loadCaptureRates()
    }
  },
)

/** 战斗结束时自动停止自动播放 */
watch(
  () => snapshot.value?.finished,
  (finished) => {
    if (finished) {
      stopAutoPlay()
      autoPlay.value = false
    }
    if (finished && !settlement.value) {
      captureMode.value = null
      if (needDestChoice.value) {
        joinTeamChoice.value = false
      } else {
        battleStore.settleBattle()
      }
    }
  },
)

/**
 * 结算完成后的地图联动（阶段 6）：
 * 野生战斗胜利时标记地图野怪刷新点移除；战败流程由后端随结算完成（零惩罚）。
 */
watch(settlement, (result) => {
  if (!result) return
  if (result.playerWon && mapStore.activeEncounterSpawnId) {
    mapStore.markWildDefeated(mapStore.activeEncounterSpawnId)
    mapStore.activeEncounterSpawnId = null
  }
  if (!result.playerWon && !result.fled) {
    // 战败后地图遭遇野怪不再标记移除（仍在原地）
    mapStore.activeEncounterSpawnId = null
  }
})

async function startBattle() {
  const seed = seedInput.value.trim() ? Number(seedInput.value.trim()) : undefined
  try {
    await battleStore.startTestBattle(Number.isFinite(seed as number) ? seed : undefined)
  } catch {
    // 错误已写入 store.error
  }
}

async function startWildBattle() {
  const seed = seedInput.value.trim() ? Number(seedInput.value.trim()) : undefined
  try {
    await battleStore.startWildBattle(Number.isFinite(seed as number) ? seed : undefined)
  } catch {
    // 错误已写入 store.error
  }
}

/** 进入捕捉模式：加载捕捉球与后端实时捕捉率。 */
async function enterCaptureMode(unit: UnitSnapshot) {
  await loadCaptureBalls()
  if (captureBalls.value.length === 0) {
    battleStore.error = '没有可用的捕捉球'
    return
  }
  await battleStore.loadCaptureRates()
  captureMode.value = { petId: unit.unitId }
  selectedBall.value = captureBalls.value[0] ?? null
  targeting.value = null
}

function exitCaptureMode() {
  captureMode.value = null
  selectedBall.value = null
}

/** 确认结算（含捕捉去向选择）。 */
async function confirmSettle() {
  await battleStore.settleBattle(joinTeamChoice.value)
}

/** 点击技能按钮：单体敌方技能进入目标选择，其余直接收集行动。 */
function handleSkillClick(unit: UnitSnapshot, skillId: string) {
  const skill = battleStore.skillIndex[skillId]
  if (skill?.target === 'ENEMY_SINGLE') {
    targeting.value = { petId: unit.unitId, skillId }
  } else if (skill?.target === 'ALLY_SINGLE') {
    // 阶段 3 基础页面：己方单体默认指向自身上场单位第一个（简化交互）
    const target = playerActive.value[0]
    if (target) {
      battleStore.setAction({ type: 'SKILL', petId: unit.unitId, skillId, targetId: target.unitId })
    }
  } else {
    battleStore.setAction({ type: 'SKILL', petId: unit.unitId, skillId })
  }
}

/** 点击敌方单位：捕捉模式下提交捕捉行动；否则技能目标选择。 */
function handleEnemyClick(enemy: UnitSnapshot) {
  if (captureMode.value) {
    if (!selectedBall.value || !enemy.alive || !enemy.active || enemy.captured) return
    battleStore.setAction({
      type: 'CAPTURE',
      petId: captureMode.value.petId,
      itemId: selectedBall.value.itemId,
      targetId: enemy.unitId,
    })
    exitCaptureMode()
    return
  }
  if (!targeting.value) return
  const action: BattleAction = {
    type: 'SKILL',
    petId: targeting.value.petId,
    skillId: targeting.value.skillId,
    targetId: enemy.unitId,
  }
  battleStore.setAction(action)
  targeting.value = null
}

function handleDefend(unit: UnitSnapshot) {
  battleStore.setAction({ type: 'DEFEND', petId: unit.unitId })
  targeting.value = null
}

function handleSwitch(unit: UnitSnapshot, bench: UnitSnapshot) {
  battleStore.setAction({ type: 'SWITCH', petId: unit.unitId, switchPetId: bench.unitId })
  targeting.value = null
}

/** 逃跑行动（野生战斗，用户裁决：必定成功、同战败结算）。 */
function handleFlee(unit: UnitSnapshot) {
  battleStore.setAction({ type: 'FLEE', petId: unit.unitId })
  targeting.value = null
}

function cancelTargeting() {
  targeting.value = null
}

function hpPercent(unit: UnitSnapshot): number {
  return unit.maxHp > 0 ? Math.max(0, Math.min(100, (unit.currentHp / unit.maxHp) * 100)) : 0
}

function cooldownOf(unit: UnitSnapshot, skillId: string): number {
  return unit.cooldowns[skillId] ?? 0
}

/** 当前选中捕捉球对指定敌方单位的捕捉率（后端计算）。 */
function captureRateText(unit: UnitSnapshot): string {
  if (!selectedBall.value) return ''
  const rate = battleStore.captureRateOf(unit.unitId, selectedBall.value.itemId)
  return rate === null ? '' : `${(rate * 100).toFixed(1)}%`
}

/** 返回：结算完成后刷新首页数据（经验池/金币/宠物 HP）。 */
async function handleLeave() {
  if (settlement.value) {
    await gameStore.loadBootstrap()
  }
  battleStore.leaveBattle()
}
</script>

<template>
  <div class="battle-view">
    <!-- 未开始：入口面板 -->
    <div v-if="!snapshot" class="start-panel">
      <h2 class="panel-title">战斗</h2>
      <p class="panel-desc">
        测试战斗：当前激活队伍 VS 固定敌方阵容；野生遭遇：可捕捉可逃跑（阶段 5 临时遭遇入口）。
        战斗结果全部由后端 BattleEngine 计算。
      </p>
      <div class="seed-row">
        <label for="seed-input">随机种子（可选，复现战斗）</label>
        <input id="seed-input" v-model="seedInput" type="text" placeholder="留空则随机" />
      </div>
      <div class="start-buttons">
        <button class="btn-primary" :disabled="battleStore.loading" @click="startBattle">
          {{ battleStore.loading ? '正在创建战斗...' : '开始测试战斗' }}
        </button>
        <button class="btn-wild" :disabled="battleStore.loading" @click="startWildBattle">
          野生遭遇（捕捉）
        </button>
      </div>
      <p v-if="battleStore.error" class="error-text">{{ battleStore.error }}</p>
    </div>

    <!-- 战斗中 / 已结束 -->
    <div v-else class="battle-panel">
      <div class="battle-header">
        <span class="round-badge">回合 {{ snapshot.currentRound }}</span>
        <span v-if="isWild" class="wild-badge">野生遭遇</span>
        <span v-if="isBoss" class="boss-badge">Boss 战</span>
        <span v-if="snapshot.finished" class="result-badge" :class="snapshot.fled ? 'flee' : snapshot.winner === 'PLAYER' ? 'win' : 'lose'">
          {{ snapshot.fled ? '已逃跑' : snapshot.winner === 'PLAYER' ? '胜利' : '失败' }}
        </span>
        <!-- 战斗速度控制（阶段 10） -->
        <div class="speed-control">
          <span class="speed-label">速度</span>
          <button
            v-for="s in ([1, 2, 3] as const)"
            :key="s"
            class="speed-btn"
            :class="{ active: battleSpeed === s }"
            @click="setSpeed(s)"
          >{{ s }}x</button>
          <button
            v-if="!snapshot.finished"
            class="auto-btn"
            :class="{ active: autoPlay }"
            @click="toggleAutoPlay"
          >{{ autoPlay ? '自动中' : '自动' }}</button>
        </div>
        <button v-if="snapshot.finished && !needDestChoice" class="btn-secondary small" :disabled="battleStore.loading" @click="handleLeave">
          {{ settlement ? '返回' : '结算中...' }}
        </button>
      </div>

      <!-- 自动战斗策略面板（阶段 10） -->
      <div v-if="!snapshot.finished" class="auto-battle-panel">
        <div class="auto-battle-bar">
          <button
            class="auto-battle-toggle"
            :class="{ active: battleStore.autoEnabled }"
            :disabled="battleStore.loading"
            @click="toggleAutoBattle"
          >
            {{ battleStore.autoEnabled ? '■ 停止自动战斗' : '▶ 自动战斗' }}
          </button>
          <span v-if="battleStore.autoEnabled" class="auto-strategy-tag">
            {{ strategyOptions.find(s => s.value === autoStrategy)?.label }}策略
          </span>
          <button class="btn-link" @click="autoPanelOpen = !autoPanelOpen">
            {{ autoPanelOpen ? '收起设置' : '策略设置' }}
          </button>
        </div>
        <div v-if="autoPanelOpen" class="auto-battle-settings">
          <div class="setting-row">
            <span class="setting-label">策略预设</span>
            <div class="strategy-options">
              <label
                v-for="opt in strategyOptions"
                :key="opt.value"
                class="strategy-option"
                :class="{ active: autoStrategy === opt.value }"
              >
                <input v-model="autoStrategy" type="radio" name="autoStrategy" :value="opt.value" />
                <span class="strategy-name">{{ opt.label }}</span>
                <span class="strategy-desc">{{ opt.desc }}</span>
              </label>
            </div>
          </div>
          <div v-if="autoStrategy === 'CAPTURE'" class="setting-row">
            <span class="setting-label">捕捉目标</span>
            <select v-model="captureTargetId" class="setting-select">
              <option :value="null">自动选择（最低 HP 可捕捉敌人）</option>
              <option
                v-for="enemy in snapshot.enemyUnits.filter(u => u.alive && !u.captured)"
                :key="enemy.unitId"
                :value="enemy.unitId"
              >{{ enemy.name }} Lv.{{ enemy.level }}</option>
            </select>
          </div>
          <div class="setting-row">
            <label class="setting-check">
              <input v-model="autoSwitch" type="checkbox" />
              自动换宠（HP 低于
              <input v-model.number="autoSwitchHpThreshold" type="number" min="5" max="80" class="threshold-input" />% 时考虑）
            </label>
          </div>
          <div class="setting-row">
            <label class="setting-check">
              <input v-model="autoUseRecoveryItem" type="checkbox" />
              自动使用恢复道具（默认关闭；HP 低于
              <input v-model.number="autoRecoveryHpThreshold" type="number" min="5" max="80" class="threshold-input" />% 时使用）
            </label>
          </div>
          <div class="setting-row">
            <label class="setting-check">
              <input v-model="autoRevive" type="checkbox" />
              自动复苏倒下宠物（默认关闭）
            </label>
          </div>
          <div class="setting-actions">
            <button class="btn-secondary small" :disabled="battleStore.loading" @click="saveAutoPreference">保存设置</button>
            <span class="setting-hint">消耗型道具默认关闭，不会静默消耗资源</span>
          </div>
        </div>
      </div>

      <!-- 捕捉去向选择（需求 §48：队伍未满 6 只可直接入队） -->
      <div v-if="snapshot.finished && needDestChoice && !settlement" class="dest-choice-panel">
        <h3>捕捉成功！</h3>
        <p class="dest-desc">
          队伍当前 {{ gameStore.teamMembers.length }}/6 只，被捕捉的宠物可以直接加入队伍，也可以留在仓库。
        </p>
        <label class="dest-checkbox">
          <input v-model="joinTeamChoice" type="checkbox" />
          捕捉后直接加入队伍
        </label>
        <button class="btn-primary" :disabled="battleStore.loading" @click="confirmSettle">
          {{ battleStore.loading ? '结算中...' : '确认结算' }}
        </button>
      </div>

      <!-- 战斗结算结果（阶段 4；阶段 5 含捕捉结果） -->
      <div v-if="settlement" class="settlement-panel">
        <h3>战斗结算</h3>
        <div class="settlement-summary">
          <span v-if="settlement.fled" class="reward-item none">逃跑成功，无奖励</span>
          <template v-else>
            <span v-if="settlement.playerWon" class="reward-item exp">经验 +{{ settlement.expGained }}</span>
            <span v-if="settlement.playerWon" class="reward-item gold">金币 +{{ settlement.goldGained }}</span>
            <span v-if="!settlement.playerWon" class="reward-item none">无奖励（战败零惩罚）</span>
          </template>
        </div>

        <!-- 战败流程（阶段 6，需求 §44）：返回最近恢复点 + 队伍恢复 + 嘲讽提示 -->
        <div v-if="settlement.defeat" class="defeat-section">
          <div class="defeat-message">“{{ settlement.defeat.message }}”</div>
          <div class="defeat-detail">
            你被送回了恢复点，队伍 {{ settlement.defeat.healedPets }} 只宠物已全部恢复。
            未损失任何金币、经验与物品。
          </div>
        </div>
        <div v-if="settlement.capturedPets.length" class="captured-section">
          <span class="captured-label">捕捉成功：</span>
          <div v-for="cp in settlement.capturedPets" :key="cp.petId" class="captured-item">
            {{ cp.name }} Lv.{{ cp.level }}（{{ cp.rarity }}）
            <span v-if="cp.specialAppearance" class="tag special">特殊外观</span>
            <span v-if="cp.extraSkillIds.length" class="tag rare-skill">稀有技能</span>
            <span v-if="cp.teamPosition" class="tag team">已入队 · 位置 {{ cp.teamPosition }}</span>
            <span v-else class="tag storage">已进仓库</span>
          </div>
        </div>
        <div v-if="settlement.drops.length" class="drops-section">
          <span class="drops-label">掉落道具：</span>
          <span v-for="drop in settlement.drops" :key="drop.itemId" class="drop-tag">
            {{ drop.name }} ×{{ drop.quantity }}
          </span>
        </div>
        <div class="hp-writeback-section">
          <span class="writeback-label">宠物 HP 回写：</span>
          <div v-for="wb in settlement.hpWritebacks" :key="wb.petId" class="writeback-item">
            {{ wb.name }}：{{ wb.beforeHp }} → {{ wb.afterHp }}
            <span v-if="!wb.alive" class="dead-tag">倒下</span>
          </div>
        </div>
      </div>

      <!-- 行动顺序条（来自后端 ACTION_ORDER 事件，每回合重算） -->
      <div v-if="battleStore.actionOrder.length" class="order-bar">
        <span class="order-label">行动顺序</span>
        <span
          v-for="(unitId, idx) in battleStore.actionOrder"
          :key="unitId"
          class="order-chip"
          :class="{ enemy: unitId.startsWith('ENEMY') }"
        >
          {{ idx + 1 }}. {{ battleStore.unitName(unitId) }}
        </span>
      </div>

      <!-- 捕捉球选择条（捕捉模式） -->
      <div v-if="captureMode" class="capture-bar">
        <span class="capture-bar-label">选择捕捉球，然后点击目标（捕捉失败时球被消耗）：</span>
        <button
          v-for="ball in captureBalls"
          :key="ball.itemId"
          class="ball-btn"
          :class="{ selected: selectedBall?.itemId === ball.itemId }"
          @click="selectedBall = ball"
        >
          {{ ball.name }} ×{{ ball.quantity }}
        </button>
        <button class="btn-link" @click="exitCaptureMode">取消</button>
      </div>

      <!-- 敌方阵容 -->
      <div class="side-section enemy">
        <h3>敌方</h3>
        <div class="unit-row">
          <div
            v-for="unit in snapshot.enemyUnits"
            :key="unit.unitId"
            class="unit-card"
            :class="{
              dead: !unit.alive,
              bench: !unit.active && unit.alive && !unit.captured,
              captured: unit.captured,
              clickable: (targeting !== null || captureMode !== null) && unit.alive && unit.active && !unit.captured,
            }"
            @click="handleEnemyClick(unit)"
          >
            <div class="unit-name">
              {{ unit.name }} <span class="unit-element">{{ unit.element }}</span>
              <span class="unit-level">Lv.{{ unit.level }}</span>
              <span v-if="unit.elite" class="elite-badge">✨精英</span>
            </div>
            <div class="hp-bar">
              <div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div>
            </div>
            <div class="unit-hp">{{ unit.currentHp }} / {{ unit.maxHp }}</div>
            <div class="status-row">
              <span v-for="status in unit.statuses" :key="status.statusId" class="status-tag" :class="{ 'capture-stun': status.captureStun }">
                {{ status.name }}({{ status.remainingTurns }}){{ (status.stack ?? 1) > 1 ? ' ×' + status.stack : '' }}
              </span>
              <span v-if="unit.charging" class="status-tag charging">蓄力中</span>
              <span v-if="unit.defending" class="status-tag defending">防御</span>
              <span v-if="unit.captured" class="status-tag captured-tag">已捕捉</span>
              <span v-if="captureMode && unit.alive && unit.active && !unit.captured && captureRateText(unit)" class="capture-rate-tag">
                捕捉率 {{ captureRateText(unit) }}
              </span>
              <!-- 安全捕捉窗口（REV-018，需求 §142：震慑仅提供安全窗口，不提高捕获率） -->
              <span v-if="unit.statuses.some(s => s.captureStun)" class="status-tag safe-window">
                💫 安全捕捉窗口
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 目标选择提示 -->
      <div v-if="targeting" class="targeting-hint">
        选择技能目标 <button class="btn-link" @click="cancelTargeting">取消</button>
      </div>

      <!-- 玩家阵容 -->
      <div class="side-section player">
        <h3>我方</h3>
        <div class="unit-row">
          <div
            v-for="unit in snapshot.playerUnits"
            :key="unit.unitId"
            class="unit-card player-card"
            :class="{ dead: !unit.alive, bench: !unit.active && unit.alive }"
          >
            <div class="unit-name">
              {{ unit.name }} <span class="unit-element">{{ unit.element }}</span>
              <span class="unit-level">Lv.{{ unit.level }}</span>
            </div>
            <div class="hp-bar">
              <div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div>
            </div>
            <div class="unit-hp">{{ unit.currentHp }} / {{ unit.maxHp }}</div>
            <div class="status-row">
              <span v-for="status in unit.statuses" :key="status.statusId" class="status-tag" :class="{ 'capture-stun': status.captureStun }">
                {{ status.name }}({{ status.remainingTurns }}){{ (status.stack ?? 1) > 1 ? ' ×' + status.stack : '' }}
              </span>
              <span v-if="unit.charging" class="status-tag charging">蓄力中</span>
              <span v-if="unit.defending" class="status-tag defending">防御</span>
            </div>

            <!-- 行动面板：仅存活上场且战斗未结束 -->
            <div v-if="unit.alive && unit.active && !snapshot.finished" class="action-panel">
              <div class="action-label">
                <template v-if="battleStore.getAction(unit.unitId)">
                  已选择：
                  <template v-if="battleStore.getAction(unit.unitId)!.type === 'SKILL'">
                    {{ battleStore.skillName(battleStore.getAction(unit.unitId)!.skillId) }}
                  </template>
                  <template v-else-if="battleStore.getAction(unit.unitId)!.type === 'SWITCH'">换宠</template>
                  <template v-else-if="battleStore.getAction(unit.unitId)!.type === 'CAPTURE'">捕捉</template>
                  <template v-else-if="battleStore.getAction(unit.unitId)!.type === 'FLEE'">逃跑</template>
                  <template v-else>防御</template>
                </template>
                <template v-else>选择行动</template>
              </div>
              <div class="skill-buttons">
                <button
                  v-for="skillId in unit.skillIds"
                  :key="skillId"
                  class="skill-btn"
                  :disabled="battleStore.loading || cooldownOf(unit, skillId) > 0"
                  :title="battleStore.skillIndex[skillId]?.description ?? ''"
                  @click="handleSkillClick(unit, skillId)"
                >
                  {{ battleStore.skillName(skillId) }}
                  <span v-if="cooldownOf(unit, skillId) > 0" class="cooldown-tag">
                    CD{{ cooldownOf(unit, skillId) }}
                  </span>
                </button>
                <button class="skill-btn defend" :disabled="battleStore.loading" @click="handleDefend(unit)">
                  防御
                </button>
                <!-- 野生战斗：捕捉与逃跑（阶段 5；Boss 战禁用） -->
                <template v-if="isWild && !uncapturable">
                  <button
                    class="skill-btn capture"
                    :disabled="battleStore.loading || captureMode !== null"
                    @click="enterCaptureMode(unit)"
                  >
                    捕捉
                  </button>
                  <button class="skill-btn flee" :disabled="battleStore.loading" @click="handleFlee(unit)">
                    逃跑
                  </button>
                </template>
                <span v-if="isBoss" class="boss-action-hint">Boss 战不可捕捉/逃跑</span>
              </div>
              <!-- 换宠：存在存活候补时可用 -->
              <div v-if="playerBench.length > 0" class="switch-row">
                <button
                  v-for="bench in playerBench"
                  :key="bench.unitId"
                  class="skill-btn switch"
                  :disabled="battleStore.loading"
                  @click="handleSwitch(unit, bench)"
                >
                  换上 {{ bench.name }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 回合提交 -->
      <div v-if="!snapshot.finished && battleStore.autoEnabled" class="turn-actions">
        <span class="auto-running-hint">自动战斗中：行动由 AI 决策（{{ strategyOptions.find(s => s.value === autoStrategy)?.label }}），点击上方按钮可停止</span>
      </div>
      <div v-else-if="!snapshot.finished" class="turn-actions">
        <button class="btn-primary" :disabled="battleStore.loading" @click="battleStore.submitActions()">
          {{ battleStore.loading ? '结算中...' : '结束回合' }}
        </button>
        <span class="turn-hint">未选择行动的宠物将自动防御</span>
      </div>
      <p v-if="battleStore.error" class="error-text">{{ battleStore.error }}</p>

      <!-- 事件日志 -->
      <div class="event-log">
        <h3>战斗记录</h3>
        <div class="log-list">
          <p v-for="(line, index) in battleStore.eventLog" :key="index" class="log-line">{{ line }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.battle-view {
  padding: 24px;
  max-width: 760px;
  margin: 0 auto;
}

.start-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 24px;
  text-align: center;
  box-shadow: var(--shadow-1);
}

.panel-title {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 8px;
}

.panel-desc {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 16px;
}

.seed-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.seed-row input {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
}

.battle-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.round-badge {
  background-color: var(--color-primary);
  color: #fff;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 14px;
}

/* 战斗速度控制（阶段 10） */
.speed-control {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.speed-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-right: 2px;
}

.speed-btn {
  padding: 2px 8px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: var(--radius-sm);
  background: var(--bg-main);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.15s;
}

.speed-btn.active {
  background-color: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.auto-btn {
  padding: 2px 10px;
  border: 1px solid var(--color-secondary, #6c757d);
  border-radius: var(--radius-sm);
  background: var(--bg-main);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  margin-left: 4px;
  transition: all 0.15s;
}

.auto-btn.active {
  background-color: var(--color-success, #38a169);
  color: #fff;
  border-color: var(--color-success, #38a169);
}

/* 精英个体标识（阶段 10） */
.elite-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f6d365, #fda085);
  color: #7a4a00;
  font-weight: 700;
  border: 1px solid #e6a817;
}

/* 自动战斗策略面板（阶段 10） */
.auto-battle-panel {
  background-color: var(--bg-card);
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-md);
  padding: 10px 14px;
  margin-bottom: 12px;
}

.auto-battle-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.auto-battle-toggle {
  padding: 6px 18px;
  border: none;
  border-radius: var(--radius-sm);
  background-color: var(--color-primary);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.auto-battle-toggle.active {
  background-color: var(--color-danger, #e53e3e);
}

.auto-battle-toggle:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.auto-strategy-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  background-color: rgba(56, 161, 105, 0.12);
  color: var(--color-success, #38a169);
  font-weight: 600;
}

.auto-battle-settings {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-row {
  font-size: 13px;
}

.setting-label {
  font-weight: 600;
  margin-right: 8px;
}

.strategy-options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 6px;
  margin-top: 4px;
}

.strategy-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.strategy-option.active {
  border-color: var(--color-primary);
  background-color: rgba(74, 144, 217, 0.06);
}

.strategy-option input {
  display: none;
}

.strategy-name {
  font-weight: 600;
  font-size: 13px;
}

.strategy-desc {
  font-size: 11px;
  color: var(--text-secondary);
}

.setting-check {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.threshold-input {
  width: 52px;
  padding: 2px 4px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.setting-select {
  padding: 4px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  max-width: 320px;
}

.setting-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-hint {
  font-size: 11px;
  color: var(--text-secondary);
}

.auto-running-hint {
  font-size: 13px;
  color: var(--color-success, #38a169);
  font-weight: 600;
}

.order-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 6px 10px;
  margin: 8px 0;
  background-color: var(--bg-main);
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  font-size: 12px;
}

.order-label {
  color: var(--text-secondary);
  margin-right: 4px;
}

.order-chip {
  padding: 2px 8px;
  border-radius: 10px;
  background-color: #e8f1ff;
  color: #2b5fa8;
}

.order-chip.enemy {
  background-color: #ffecec;
  color: #a83a2b;
}

.result-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 14px;
  color: #fff;
}

.result-badge.win { background-color: #7ED321; }
.result-badge.lose { background-color: #d32f2f; }
.result-badge.flee { background-color: #8e8e93; }

.wild-badge {
  background-color: #e8f5e9;
  color: #2e7d32;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}

.boss-badge {
  background-color: #fde0dc;
  color: #b71c1c;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.boss-action-hint {
  font-size: 11px;
  color: #b71c1c;
  margin-left: 4px;
}

.start-buttons {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-wild {
  padding: 10px 28px;
  background-color: #2e7d32;
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  cursor: pointer;
}

.btn-wild:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 捕捉球选择条 */
.capture-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background-color: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 13px;
}

.capture-bar-label {
  color: #2e7d32;
}

.ball-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #2e7d32;
  background-color: #fff;
  color: #2e7d32;
  border-radius: 6px;
  cursor: pointer;
}

.ball-btn.selected {
  background-color: #2e7d32;
  color: #fff;
}

.capture-rate-tag {
  font-size: 11px;
  background-color: #2e7d32;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
}

.unit-card.captured {
  opacity: 0.5;
  border: 1px solid #2e7d32;
}

.status-tag.captured-tag {
  background-color: #2e7d32;
  color: #fff;
}

.skill-btn.capture { background-color: #2e7d32; }
.skill-btn.flee { background-color: #b87800; }

/* 捕捉去向选择 */
.dest-choice-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-1);
  border-left: 4px solid #2e7d32;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.dest-choice-panel h3 {
  font-size: 16px;
  color: #2e7d32;
}

.dest-desc {
  font-size: 13px;
  color: var(--text-secondary);
}

.dest-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  cursor: pointer;
}

/* 结算捕捉结果 */
.captured-section {
  margin-bottom: 10px;
  font-size: 13px;
}

.captured-label {
  color: var(--text-secondary);
}

.captured-item {
  padding: 2px 0;
}

.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 4px;
}

.tag.special { background-color: #f3e5f5; color: #7b1fa2; }
.tag.rare-skill { background-color: #fff3cd; color: #856404; }
.tag.team { background-color: #e8f1ff; color: #2b5fa8; }
.tag.storage { background-color: #f0f0f0; color: #555; }

.side-section h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.unit-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.unit-card {
  flex: 1;
  min-width: 180px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
  transition: opacity 0.2s;
}

.unit-card.dead {
  opacity: 0.45;
}

.unit-card.bench {
  opacity: 0.7;
  border: 1px dashed #ccc;
}

.unit-card.clickable {
  cursor: pointer;
  outline: 2px solid var(--color-primary);
}

.unit-name {
  font-weight: 600;
  margin-bottom: 6px;
}

.unit-element {
  font-size: 12px;
  color: var(--color-primary);
  margin-left: 4px;
}

.unit-level {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.hp-bar {
  height: 8px;
  background-color: #eee;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 4px;
}

.hp-fill {
  height: 100%;
  background-color: #7ED321;
  transition: width 0.3s;
}

.unit-hp {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 20px;
}

.status-tag {
  font-size: 11px;
  background-color: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
}

.status-tag.charging { background-color: #fff3cd; color: #856404; }
.status-tag.defending { background-color: #d1ecf1; color: #0c5460; }
.status-tag.capture-stun { background-color: #e2d9f3; color: #4a2d7a; }
.status-tag.safe-window { background-color: #d4edda; color: #155724; }

.targeting-hint {
  background-color: #fff3cd;
  color: #856404;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 12px;
}

.btn-link {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  text-decoration: underline;
  font-size: 13px;
}

.action-panel {
  margin-top: 8px;
  border-top: 1px solid #eee;
  padding-top: 8px;
}

.action-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.skill-buttons, .switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.skill-btn {
  padding: 6px 10px;
  font-size: 12px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.skill-btn:disabled {
  background-color: #bbb;
  cursor: not-allowed;
}

.skill-btn.defend { background-color: #4A90D9; }
.skill-btn.switch { background-color: #8e8e93; }

.cooldown-tag {
  margin-left: 4px;
  font-size: 10px;
  background-color: rgba(0, 0, 0, 0.25);
  padding: 1px 4px;
  border-radius: 3px;
}

.turn-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.turn-hint {
  font-size: 12px;
  color: var(--text-secondary);
}

.event-log {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
}

.event-log h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.log-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column-reverse;
}

.log-line {
  font-size: 12px;
  color: var(--text-primary);
  padding: 2px 0;
}

.btn-primary {
  padding: 10px 28px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary.small {
  padding: 6px 16px;
  background-color: var(--bg-secondary, #f0f0f0);
  color: var(--text-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.error-text {
  margin-top: 12px;
  color: #d32f2f;
  font-size: 14px;
  text-align: center;
}

/* 战斗结算面板 */
.settlement-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-1);
  border-left: 4px solid var(--color-primary);
}

.settlement-panel h3 {
  font-size: 16px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.settlement-summary {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}

.reward-item {
  font-size: 14px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 12px;
}

.reward-item.exp { background-color: #e8f1ff; color: #2b5fa8; }
.reward-item.gold { background-color: #fff5e0; color: #b87800; }
.reward-item.none { color: var(--text-secondary); }

.drops-section {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  font-size: 13px;
}

.drops-label { color: var(--text-secondary); }

.drop-tag {
  background-color: #e8f5e9;
  color: #2e7d32;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.hp-writeback-section {
  font-size: 12px;
  color: var(--text-secondary);
}

.writeback-label { display: block; margin-bottom: 4px; }

.writeback-item {
  padding: 2px 0;
}

.writeback-item .dead-tag {
  color: #d32f2f;
  font-size: 11px;
  margin-left: 4px;
}

.defeat-section {
  background-color: #fff3cd;
  border-radius: var(--radius-md);
  padding: 12px 14px;
  margin: 10px 0;
}

.defeat-message {
  font-size: 15px;
  color: #856404;
  font-weight: 600;
  margin-bottom: 6px;
}

.defeat-detail {
  font-size: 13px;
  color: #6d5a1e;
  line-height: 1.6;
}
</style>
