<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type Phaser from 'phaser'
import { createPhaserGame } from '../../game/PhaserGame'
import {
  gameBridge,
  type ExitTouchPayload,
  type MapSceneData,
  type WildTouchPayload,
} from '../../game/bridge/GameBridge'
import { useMapStore } from '../../stores/map'
import { useBattleStore } from '../../stores/battle'
import { useGameStore } from '../../stores/game'
import type { RewardResultView } from '../../types/map'

const router = useRouter()
const mapStore = useMapStore()
const battleStore = useBattleStore()
const gameStore = useGameStore()

// ---- Phaser 实例 ----
const phaserContainer = ref<HTMLElement | null>(null)
let game: Phaser.Game | null = null

// ---- 交互状态 ----
const encounterReq = ref<WildTouchPayload | null>(null)
const campDialogId = ref<string | null>(null)
const exitReq = ref<ExitTouchPayload | null>(null)
const rewardPopup = ref<RewardResultView | null>(null)
const toast = ref('')
const busy = ref(false)
const actionError = ref('')
let toastTimer: ReturnType<typeof setTimeout> | null = null

const regionName = computed(() => mapStore.currentMap?.name ?? '')
const dialogOpen = computed(
  () => encounterReq.value !== null || campDialogId.value !== null || exitReq.value !== null,
)

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
  toast.value = message
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 3200)
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
    // 占位对象提示（后续阶段启用）
    gameBridge.on('boss:touch', () => {
      showToast('Boss 入口（占位）：Boss 挑战将在阶段 7 开放')
    }),
    gameBridge.on('npc:touch', () => {
      showToast('NPC（占位）：对话与任务将在阶段 9 开放')
    }),
    gameBridge.on('hidden:touch', () => {
      showToast('这里似乎藏着什么……（隐藏遭遇内容将在后续阶段开放）')
    }),
  )
}

// ==================== 交互动作 ====================

/** 遭遇确认 → 开始地图遭遇战斗。 */
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
    await router.push('/battle')
  } catch (e) {
    actionError.value = battleStore.error || '遭遇发起失败'
  } finally {
    busy.value = false
    syncInputLock()
  }
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

function openWorldMap() {
  router.push('/world-map')
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
})

onBeforeUnmount(() => {
  for (const unsub of unsubscribers) unsub()
  unsubscribers.length = 0
  if (toastTimer) clearTimeout(toastTimer)
  if (game) {
    game.destroy(true)
    game = null
  }
})
</script>

<template>
  <div class="explore-view">
    <div class="explore-header">
      <h2>区域探索 <span v-if="regionName" class="region-badge">{{ regionName }}</span></h2>
      <button class="btn-world-map" @click="openWorldMap">大地图</button>
    </div>

    <!-- Phaser 画布容器 -->
    <div ref="phaserContainer" class="phaser-container"></div>

    <p v-if="mapStore.error && !mapStore.currentMap" class="error-text">{{ mapStore.error }}</p>

    <!-- 遭遇确认（可见野怪接触前可调整首发） -->
    <div v-if="encounterReq" class="modal-mask">
      <div class="modal-card">
        <h3>遭遇野生宠物！</h3>
        <p class="modal-text">
          发现一只野生的野生宠物（行为：{{ encounterReq.behavior }}）。
          可以在开战前调整队伍首发，也可以绕开它继续探索。
        </p>
        <p v-if="actionError" class="error-text">{{ actionError }}</p>
        <div class="modal-actions">
          <RouterLink to="/team" class="btn-secondary">调整首发</RouterLink>
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

    <!-- 奖励弹窗 -->
    <div v-if="rewardPopup" class="modal-mask" @click.self="closeRewardPopup">
      <div class="modal-card">
        <h3>{{ rewardPopup.objectName }}：获得奖励</h3>
        <ul class="reward-list">
          <li v-if="rewardPopup.goldGained > 0">金币 +{{ rewardPopup.goldGained }}</li>
          <li v-for="item in rewardPopup.items" :key="item.itemId">
            {{ item.name }} ×{{ item.quantity }}
          </li>
          <li v-if="rewardPopup.goldGained === 0 && rewardPopup.items.length === 0">（空）</li>
        </ul>
        <div class="modal-actions">
          <button class="btn-primary" @click="closeRewardPopup">收下</button>
        </div>
      </div>
    </div>

    <!-- 轻提示 -->
    <div v-if="toast" class="toast">{{ toast }}</div>
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

.toast {
  position: fixed;
  bottom: 96px;
  left: 50%;
  transform: translateX(-50%);
  background-color: rgba(20, 26, 34, 0.92);
  color: #fff;
  padding: 10px 18px;
  border-radius: var(--radius-md);
  font-size: 14px;
  z-index: 300;
  max-width: 80vw;
}

.error-text {
  color: #d32f2f;
  font-size: 13px;
  margin-bottom: 10px;
}
</style>
