<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type Phaser from 'phaser'
import { createPhaserGame } from '../../game/PhaserGame'
import {
  gameBridge,
  type ExitTouchPayload,
  type MapSceneData,
  type PlayerPositionPayload,
  type WildTouchPayload,
} from '../../game/bridge/GameBridge'
import { useMapStore } from '../../stores/map'
import { useBattleStore } from '../../stores/battle'
import { useGameStore } from '../../stores/game'
import { useQuestStore } from '../../stores/quest'
import { useOverlayStore, type OverlayType } from '../../stores/overlay'
import { useUiStore } from '../../stores/ui'
import { apiGet, apiPost } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { RewardResultView } from '../../types/map'
import BattleOverlay from '../Battle/components/BattleOverlay.vue'
import RewardPopup from '../../components/feedback/RewardPopup.vue'
import ExplorationHUD from '../../components/hud/ExplorationHUD.vue'
import ContextInteractionPanel from '../../components/hud/ContextInteractionPanel.vue'

const mapStore = useMapStore()
const battleStore = useBattleStore()
const gameStore = useGameStore()
const questStore = useQuestStore()
const overlayStore = useOverlayStore()
const uiStore = useUiStore()

/** 战斗浮层是否打开（主场景常驻，战斗作为全屏浮层叠加，不进行路由跳转）。 */
const battleOpen = ref(false)

// ---- Phaser 实例 ----
const phaserContainer = ref<HTMLElement | null>(null)
let game: Phaser.Game | null = null

// ---- 交互状态 ----
const encounterReq = ref<WildTouchPayload | null>(null)
const campDialogId = ref<string | null>(null)
const exitReq = ref<ExitTouchPayload | null>(null)
const rewardPopup = ref<RewardResultView | null>(null)
const busy = ref(false)
const actionError = ref('')

// 随机事件（阶段 10）
const randomEvent = ref<{
  eventId: string
  name: string
  description: string
  options: Array<{ optionId: string; text: string }>
} | null>(null)
const eventResult = ref<{
  type: string
  description: string
  goldGained?: number
  itemId?: string
  encounterGroupId?: string
} | null>(null)

const dialogOpen = computed(
  () => encounterReq.value !== null || campDialogId.value !== null || exitReq.value !== null || randomEvent.value !== null || eventResult.value !== null,
)

function eventArtUrl(eventId: string): string {
  return `/assets/events/event_${eventId}_cg.png`
}

/** 构建 Phaser 场景数据（业务状态全部来自后端）。 */
function buildSceneData(): MapSceneData | null {
  const view = mapStore.currentMap
  if (!view) return null
  return {
    mapId: view.mapId,
    mapFile: view.mapFile,
    spawnObjectId: view.spawnObjectId,
    consumedChestIds: [...view.consumedChestIds],
    usedGatherIds: [...view.usedGatherIds],
    activatedCampIds: [...view.activatedCampIds],
    defeatedWildIds: [...mapStore.defeatedWildIds],
  }
}

function showToast(message: string) {
  uiStore.toast(message)
}

function syncInputLock() {
  gameBridge.emit('cmd:set-input-lock', { locked: dialogOpen.value || busy.value })
}

// ==================== bridge 事件处理（Phaser → Vue） ====================

const unsubscribers: Array<() => void> = []

function registerBridgeHandlers() {
  unsubscribers.push(
    // 可见野怪接触：弹确认框，允许先调整首发再开战（规划阶段 6 遭遇规则）
    gameBridge.on('encounter:touch', (payload) => {
      encounterReq.value = payload
      actionError.value = ''
      syncInputLock()
    }),
    gameBridge.on('gather:request', async (payload) => {
      try {
        const reward = await mapStore.gather(payload.id)
        gameBridge.emit('cmd:remove-object', { id: payload.id })
        rewardPopup.value = reward
        await gameStore.loadBootstrap()
      } catch (e) {
        showToast(mapStore.error || '采集失败')
      }
    }),
    gameBridge.on('chest:request', async (payload) => {
      try {
        const reward = await mapStore.openChest(payload.id)
        gameBridge.emit('cmd:remove-object', { id: payload.id })
        rewardPopup.value = reward
        await gameStore.loadBootstrap()
      } catch (e) {
        showToast(mapStore.error || '开启宝箱失败')
      }
    }),
    gameBridge.on('camp:touch', (payload) => {
      campDialogId.value = payload.id
      actionError.value = ''
      syncInputLock()
    }),
    gameBridge.on('exit:touch', (payload) => {
      exitReq.value = payload
      actionError.value = ''
      syncInputLock()
    }),
    // Boss 入口（阶段 7 / Overlay 架构 P1）：打开 Boss 浮层，不离开地图
    gameBridge.on('boss:touch', (payload) => {
      overlayStore.open('BOSS', { bossId: payload.id })
    }),
    gameBridge.on('npc:touch', async (payload) => {
      // NPC 对话接入 Overlay 栈：打开 NPC_DIALOG 浮层（暂停玩家输入），再加载对话内容
      overlayStore.open('NPC_DIALOG')
      await questStore.talkNpc(payload.id)
    }),
    gameBridge.on('hidden:touch', () => {
      showToast('这里似乎藏着什么……（隐藏遭遇内容将在后续阶段开放）')
    }),
    // 玩家坐标写回 useMapStore（节流上报，保证 Overlay 关闭后上下文稳定）
    gameBridge.on('player:position', (payload: PlayerPositionPayload) => {
      mapStore.setPlayerPosition({ x: payload.x, y: payload.y })
    }),
  )
}

// ==================== 交互动作 ====================

/** 遭遇确认 → 开始地图遭遇战斗（战斗浮层化：不路由跳转，底层地图保留）。 */
async function startEncounter() {
  if (!encounterReq.value || busy.value) return
  busy.value = true
  actionError.value = ''
  syncInputLock()
  const payload = encounterReq.value
  try {
    await battleStore.startMapEncounter(payload.groupId)
    mapStore.activeEncounterSpawnId = payload.spawnId
    encounterReq.value = null
    openBattleOverlay()
  } catch (e) {
    actionError.value = battleStore.error || '遭遇发起失败'
  } finally {
    busy.value = false
    syncInputLock()
  }
}

/** 打开战斗浮层并暂停地图探索。 */
function openBattleOverlay() {
  battleOpen.value = true
  overlayStore.open('BATTLE')
  syncInputLock()
}

/** 战斗浮层关闭：恢复地图探索，结算后刷新首页数据。 */
async function handleBattleClose() {
  battleOpen.value = false
  overlayStore.close('BATTLE')
  if (battleStore.settlement) {
    await gameStore.loadBootstrap()
  }
  battleStore.leaveBattle()
  syncInputLock()
}

function closeEncounter() {
  encounterReq.value = null
  syncInputLock()
}

/** 营地休息：免费恢复全队 + 激活营地 + 触发地图刷新。 */
async function restAtCamp() {
  if (!campDialogId.value || busy.value) return
  busy.value = true
  actionError.value = ''
  syncInputLock()
  try {
    const rest = await mapStore.restAtCamp(campDialogId.value)
    campDialogId.value = null
    await gameStore.loadBootstrap()
    // 休息触发刷新：重启地图场景（新会话）
    const data = buildSceneData()
    if (data) gameBridge.emit('cmd:restart-map', data)
    showToast(`营地休息完成：恢复 ${rest.healedPets} 只宠物${rest.firstActivation ? '，营地已激活' : ''}，区域已刷新`)
  } catch (e) {
    actionError.value = mapStore.error || '营地休息失败'
  } finally {
    busy.value = false
    syncInputLock()
  }
}

function closeCampDialog() {
  campDialogId.value = null
  syncInputLock()
}

/** 出口确认 → 进入目标区域。 */
async function confirmExit() {
  if (!exitReq.value || busy.value) return
  busy.value = true
  actionError.value = ''
  syncInputLock()
  const payload = exitReq.value
  try {
    await mapStore.enterRegion(payload.targetMapId, payload.exitId)
    exitReq.value = null
    const data = buildSceneData()
    if (data) gameBridge.emit('cmd:restart-map', data)
    // 进入新区域后尝试触发随机事件
    await tryRollRandomEvent()
  } catch (e) {
    actionError.value = mapStore.error || '区域移动失败'
    exitReq.value = null // 非法出口直接关闭，避免反复触发
  } finally {
    busy.value = false
    syncInputLock()
  }
}

function closeExitDialog() {
  exitReq.value = null
  syncInputLock()
}

function closeRewardPopup() {
  rewardPopup.value = null
}

// ==================== 随机事件（阶段 10） ====================

/** 尝试触发随机事件（进入区域后调用）。 */
async function tryRollRandomEvent() {
  try {
    const res = await apiGet<any>('/api/maps/events/roll')
    const data = (res as ApiResponse<any>).data
    if (data) {
      randomEvent.value = data
      syncInputLock()
    }
  } catch {
    // 无事件触发或网络错误，忽略
  }
}

/** 解析随机事件选项。 */
async function resolveEvent(optionId: string) {
  if (!randomEvent.value || busy.value) return
  busy.value = true
  try {
    const res = await apiPost<any>('/api/maps/events/resolve', {
      eventId: randomEvent.value.eventId,
      optionId,
    })
    const result = (res as ApiResponse<any>).data
    eventResult.value = result
    randomEvent.value = null
    // 刷新玩家金币等状态
    await gameStore.loadBootstrap()
    // 如果触发了战斗/捕捉，打开战斗浮层
    if (result.type === 'TRIGGER_BATTLE' && result.encounterGroupId) {
      try {
        await battleStore.startMapEncounter(result.encounterGroupId)
        eventResult.value = null
        openBattleOverlay()
      } catch {
        showToast('战斗触发失败')
      }
    }
  } catch (e: any) {
    showToast(e?.message || '事件解析失败')
  } finally {
    busy.value = false
    syncInputLock()
  }
}

function closeEventResult() {
  eventResult.value = null
  syncInputLock()
}

/** 打开功能浮层（地图保留，暂停探索；浮层 UI 由 MainLayout 的 OverlayLayer 统一渲染）。 */
function openFeature(type: OverlayType) {
  overlayStore.open(type)
  syncInputLock()
}

// ==================== 生命周期 ====================

onMounted(async () => {
  registerBridgeHandlers()
  try {
    await gameStore.loadBootstrap()
    await mapStore.loadCurrentMap()
  } catch (e) {
    return // 无存档等情况由路由守卫处理
  }
  if (phaserContainer.value) {
    game = createPhaserGame(phaserContainer.value)
    const data = buildSceneData()
    if (data) {
      game.scene.start('BootScene', data)
    }
  }
  // 进入区域后尝试触发随机事件
  await tryRollRandomEvent()
})

onBeforeUnmount(() => {
  for (const unsub of unsubscribers) unsub()
  unsubscribers.length = 0
  if (game) {
    game.destroy(true)
    game = null
  }
})
</script>

<template>
  <div class="explore-view">
    <!-- 地图舞台：Phaser 画布 + 探索 HUD + 情境交互（Overlay 架构 P1） -->
    <div class="map-stage">
      <div ref="phaserContainer" class="phaser-container"></div>
      <ExplorationHUD />
      <ContextInteractionPanel />
    </div>

    <p v-if="mapStore.error && !mapStore.currentMap" class="error-text">{{ mapStore.error }}</p>

    <!-- 遭遇确认（可见野怪接触前可调整首发） -->
    <div v-if="encounterReq" class="modal-mask">
      <div class="modal-card">
        <h3>遭遇野生宠物！</h3>
        <p v-if="encounterReq.elite" class="elite-banner">✨ 精英个体！属性更强，捕捉难度更高</p>
        <p class="modal-text">
          发现一只野生的野生宠物（行为：{{ encounterReq.behavior }}）。
          可以在开战前调整队伍首发，也可以绕开它继续探索。
        </p>
        <p v-if="actionError" class="error-text">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="openFeature('TEAM')">调整首发</button>
          <button class="btn-secondary" :disabled="busy" @click="closeEncounter">绕开</button>
          <button class="btn-primary" :disabled="busy" @click="startEncounter">
            {{ busy ? '遭遇中...' : '开始战斗' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 营地交互 -->
    <div v-if="campDialogId" class="modal-mask">
      <div class="modal-card">
        <h3>营地</h3>
        <p class="modal-text">
          在营地休息可免费恢复全队 HP、复苏倒下宠物，并刷新本区域野怪与采集点。
          首次休息将激活营地，之后可在已激活营地间免费传送。
        </p>
        <p v-if="actionError" class="error-text">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" :disabled="busy" @click="closeCampDialog">离开</button>
          <button class="btn-primary" :disabled="busy" @click="restAtCamp">
            {{ busy ? '休息中...' : '休息（免费）' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 出口确认 -->
    <div v-if="exitReq" class="modal-mask">
      <div class="modal-card">
        <h3>区域出口</h3>
        <p class="modal-text">是否离开当前区域？离开后重新进入将刷新野怪与普通采集点。</p>
        <p v-if="actionError" class="error-text">{{ actionError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" :disabled="busy" @click="closeExitDialog">留下</button>
          <button class="btn-primary" :disabled="busy" @click="confirmExit">
            {{ busy ? '移动中...' : '出发' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 奖励弹窗（统一 RewardPopup） -->
    <RewardPopup v-if="rewardPopup" :reward="rewardPopup" @close="closeRewardPopup" />

    <!-- 随机事件对话框（阶段 10） -->
    <div v-if="randomEvent" class="modal-mask">
      <div class="modal-card">
        <img class="event-cg" :src="eventArtUrl(randomEvent.eventId)" :alt="`${randomEvent.name}插画`" />
        <h3>{{ randomEvent.name }}</h3>
        <p class="modal-text">{{ randomEvent.description }}</p>
        <div class="modal-actions">
          <button
            v-for="opt in randomEvent.options"
            :key="opt.optionId"
            class="btn-secondary"
            :disabled="busy"
            @click="resolveEvent(opt.optionId)"
          >{{ opt.text }}</button>
        </div>
      </div>
    </div>

    <!-- 随机事件结果（阶段 10） -->
    <div v-if="eventResult" class="modal-mask" @click.self="closeEventResult">
      <div class="modal-card">
        <h3>事件结果</h3>
        <p class="modal-text">{{ eventResult.description }}</p>
        <ul v-if="eventResult.goldGained" class="reward-list">
          <li>金币 +{{ eventResult.goldGained }}</li>
        </ul>
        <div class="modal-actions">
          <button class="btn-primary" @click="closeEventResult">继续探索</button>
        </div>
      </div>
    </div>

    <!-- 轻提示（全局 GlobalToast，MainLayout 挂载） -->

    <!-- 战斗浮层（战斗体验优化 P0：全屏覆盖地图，关闭后恢复探索，不重新加载地图） -->
    <BattleOverlay v-if="battleOpen" @close="handleBattleClose" />
  </div>
</template>

<style scoped>
.explore-view {
  max-width: 840px;
  margin: 0 auto;
}

.explore-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.explore-header h2 {
  font-size: 20px;
  color: var(--color-primary);
  display: flex;
  align-items: center;
  gap: 10px;
}

.region-badge {
  font-size: 13px;
  background-color: var(--color-secondary);
  color: #fff;
  padding: 2px 10px;
  border-radius: var(--radius-sm);
}

.explore-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-feature {
  padding: 8px 14px;
  background-color: rgba(74, 144, 217, 0.1);
  color: var(--color-primary);
  border: 1px solid rgba(74, 144, 217, 0.3);
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s;
}

.btn-feature:hover {
  background-color: var(--color-primary);
  color: #fff;
}

.btn-world-map {
  padding: 8px 20px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
}

.phaser-container {
  width: 800px;
  max-width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-1);
  background-color: #101820;
}

.phaser-container :deep(canvas) {
  display: block;
  max-width: 100%;
  height: auto;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  width: 420px;
  max-width: 90vw;
  box-shadow: var(--shadow-2);
}

.modal-card h3 {
  font-size: 17px;
  color: var(--color-primary);
  margin-bottom: 10px;
}

.event-cg {
  display: block;
  width: 100%;
  max-height: 220px;
  margin-bottom: 12px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.modal-text {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-primary {
  padding: 8px 20px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 8px 20px;
  background-color: var(--bg-main);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.reward-list {
  margin: 0 0 14px 18px;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.9;
}

.error-text {
  color: #d32f2f;
  font-size: 13px;
  margin-bottom: 10px;
}

/* 精英个体提示（阶段 10） */
.elite-banner {
  background: linear-gradient(135deg, #fff8e1, #ffe0b2);
  border: 1px solid #e6a817;
  color: #7a4a00;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-bottom: 10px;
}

@media (max-width: 768px) {
  .explore-view { padding: 0 4px; }
  .phaser-container { width: 100%; }
  .modal-card { width: 95vw; padding: 14px; }
}
</style>
