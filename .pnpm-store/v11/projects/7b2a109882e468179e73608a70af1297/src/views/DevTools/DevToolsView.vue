<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiGet, apiPost } from '../../api/client'
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()

const message = ref('')
const error = ref('')
const busy = ref(false)

// 资源
const goldAmount = ref(1000)
const expAmount = ref(1000)
const itemId = ref('ITEM_POTION_SMALL')
const itemQty = ref(1)

// 宠物
const speciesId = ref('')
const petLevel = ref(10)
const petIdToReset = ref<number | null>(null)

// 地图
const mapId = ref('')

// Boss
const bossId = ref('')
const difficulty = ref('NORMAL')
const bossCount = ref(1)
const bossLuck = ref(0)

// 操作日志
const logs = ref<any[]>([])

// 战斗调试开关
const battleState = ref({
  playerInvincible: false,
  playerOneHitKill: false,
  playerFixedCrit: false,
  debugDamage: false,
  fixedSeed: null as number | null,
})
const fixedSeedInput = ref(12345)

async function loadBattleState() {
  try {
    const res = await apiGet<any>('/api/dev/battle/state')
    battleState.value = { ...battleState.value, ...(res.data || {}) }
  } catch (e: any) {
    error.value = e.message ?? '加载战斗调试状态失败'
  }
}

const setInvincible = () => run(() => apiPost('/api/dev/battle/invincible', { on: !battleState.value.playerInvincible }).then((res: any) => { battleState.value = { ...battleState.value, ...(res.data || {}) } }))
const setOneHitKill = () => run(() => apiPost('/api/dev/battle/one-hit-kill', { on: !battleState.value.playerOneHitKill }).then((res: any) => { battleState.value = { ...battleState.value, ...(res.data || {}) } }))
const setFixedCrit = () => run(() => apiPost('/api/dev/battle/fixed-crit', { on: !battleState.value.playerFixedCrit }).then((res: any) => { battleState.value = { ...battleState.value, ...(res.data || {}) } }))
const setDebugDamage = () => run(() => apiPost('/api/dev/battle/debug-damage', { on: !battleState.value.debugDamage }).then((res: any) => { battleState.value = { ...battleState.value, ...(res.data || {}) } }))
const setFixedSeed = () => run(() => apiPost('/api/dev/battle/fixed-seed', { seed: Number(fixedSeedInput.value) }).then((res: any) => { battleState.value = { ...battleState.value, ...(res.data || {}) } }))

async function run(action: () => Promise<unknown>) {
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    await action()
    message.value = '操作成功'
  } catch (e: any) {
    error.value = e.message ?? '操作失败'
  } finally {
    busy.value = false
  }
}

const grantGold = () => run(() => apiPost('/api/dev/gold', { amount: Number(goldAmount.value) }))
const grantExp = () => run(() => apiPost('/api/dev/exp', { amount: Number(expAmount.value) }))
const grantItem = () => run(() => apiPost('/api/dev/item', { itemId: itemId.value, quantity: Number(itemQty.value) }))
const addPet = () => run(() => apiPost('/api/dev/pet', { speciesId: speciesId.value, level: Number(petLevel.value) }))
const resetPet = () => {
  if (!petIdToReset.value) {
    error.value = '请输入宠物 ID'
    return
  }
  run(() => apiPost('/api/dev/pet/reset', { petId: Number(petIdToReset.value) }))
}
const unlockRegion = () => run(() => apiPost('/api/dev/map/unlock', { mapId: mapId.value }))
const forceRefresh = () => run(() => apiPost('/api/dev/map/refresh'))
const forceElite = () => run(() => apiPost('/api/dev/map/force-elite'))
const forceRandomEvent = () => run(() => apiPost('/api/dev/map/force-random-event'))
const unlockBoss = () => run(() => apiPost('/api/dev/boss/unlock', { bossId: bossId.value, difficulty: difficulty.value }))
const directBoss = () => run(() => apiPost('/api/dev/boss/direct', { bossId: bossId.value, difficulty: difficulty.value }))
const setBossCount = () => run(() => apiPost('/api/dev/boss/defeat-count', { bossId: bossId.value, difficulty: difficulty.value, count: Number(bossCount.value) }))
const setBossLuck = () => run(() => apiPost('/api/dev/boss/luck', { bossId: bossId.value, luck: Number(bossLuck.value) }))
const forceBossDrop = () => run(() => apiPost('/api/dev/boss/force-drop', { bossId: bossId.value }))

async function loadLogs() {
  try {
    const res = await apiGet<any[]>('/api/dev/logs?limit=30')
    logs.value = res.data || []
  } catch (e: any) {
    error.value = e.message ?? '加载操作日志失败'
  }
}

onMounted(() => {
  loadLogs()
  loadBattleState()
})
</script>

<template>
  <div class="dev-page">
    <h2>开发者工具</h2>
    <p v-if="!gameStore.developerMode" class="notice">
      开发者模式未开启。请在服务端配置 <code>game.developer-mode=true</code> 后重启。
    </p>

    <template v-if="gameStore.developerMode">
      <div class="grid">
        <!-- 资源 -->
        <section class="dev-card">
          <h3>资源管理</h3>
          <label>金币 <input v-model.number="goldAmount" type="number" min="1" /></label>
          <button class="btn" :disabled="busy" @click="grantGold">加金币</button>
          <label>经验池 <input v-model.number="expAmount" type="number" min="1" /></label>
          <button class="btn" :disabled="busy" @click="grantExp">加经验池</button>
          <label>道具 ID <input v-model="itemId" type="text" /></label>
          <label>数量 <input v-model.number="itemQty" type="number" min="1" /></label>
          <button class="btn" :disabled="busy" @click="grantItem">添加道具</button>
        </section>

        <!-- 宠物 -->
        <section class="dev-card">
          <h3>宠物管理</h3>
          <label>种族 ID <input v-model="speciesId" type="text" placeholder="如 PET_001" /></label>
          <label>等级 <input v-model.number="petLevel" type="number" min="1" max="50" /></label>
          <button class="btn" :disabled="busy" @click="addPet">添加宠物</button>
          <label>宠物 ID <input v-model.number="petIdToReset" type="number" /></label>
          <button class="btn" :disabled="busy" @click="resetPet">重置宠物</button>
        </section>

        <!-- 地图 -->
        <section class="dev-card">
          <h3>地图管理</h3>
          <label>区域 ID <input v-model="mapId" type="text" placeholder="如 MAP_AREA_MEADOW" /></label>
          <button class="btn" :disabled="busy" @click="unlockRegion">解锁区域</button>
          <button class="btn" :disabled="busy" @click="forceRefresh">强制刷新</button>
          <button class="btn" :disabled="busy" @click="forceElite">强制精英</button>
          <button class="btn" :disabled="busy" @click="forceRandomEvent">强制随机事件</button>
        </section>

        <!-- Boss -->
        <section class="dev-card">
          <h3>Boss 管理</h3>
          <label>Boss ID <input v-model="bossId" type="text" /></label>
          <label>难度 <select v-model="difficulty">
            <option value="NORMAL">普通</option>
            <option value="ELITE">精英</option>
            <option value="NIGHTMARE">噩梦</option>
            <option value="HELL">地狱</option>
          </select></label>
          <button class="btn" :disabled="busy" @click="unlockBoss">解锁难度</button>
          <button class="btn" :disabled="busy" @click="directBoss">直达难度</button>
          <label>击败次数 <input v-model.number="bossCount" type="number" min="0" /></label>
          <button class="btn" :disabled="busy" @click="setBossCount">设击败次数</button>
          <label>幸运值 <input v-model.number="bossLuck" type="number" min="0" /></label>
          <button class="btn" :disabled="busy" @click="setBossLuck">设幸运值</button>
          <button class="btn" :disabled="busy" @click="forceBossDrop">强制掉落</button>
        </section>

        <!-- 战斗调试 -->
        <section class="dev-card">
          <h3>战斗调试</h3>
          <p class="tip">开关为持久状态，开战生效；固定种子为一次性，下次战斗消费。</p>
          <button class="btn" :disabled="busy" @click="setInvincible">
            {{ battleState.playerInvincible ? '关闭' : '开启' }}无敌
          </button>
          <button class="btn" :disabled="busy" @click="setOneHitKill">
            {{ battleState.playerOneHitKill ? '关闭' : '开启' }}一击必杀
          </button>
          <button class="btn" :disabled="busy" @click="setFixedCrit">
            {{ battleState.playerFixedCrit ? '关闭' : '开启' }}固定暴击
          </button>
          <button class="btn" :disabled="busy" @click="setDebugDamage">
            {{ battleState.debugDamage ? '关闭' : '开启' }}伤害明细/随机数
          </button>
          <label>固定随机种子 <input v-model.number="fixedSeedInput" type="number" /></label>
          <button class="btn" :disabled="busy" @click="setFixedSeed">设固定种子</button>
          <p class="state">
            当前：无敌 {{ battleState.playerInvincible ? '开' : '关' }} ·
            一击必杀 {{ battleState.playerOneHitKill ? '开' : '关' }} ·
            固定暴击 {{ battleState.playerFixedCrit ? '开' : '关' }} ·
            调试信息 {{ battleState.debugDamage ? '开' : '关' }} ·
            固定种子 {{ battleState.fixedSeed ?? '无' }}
          </p>
        </section>
      </div>

      <p v-if="message" class="message">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>

      <section class="dev-card">
        <h3>操作日志</h3>
        <button class="btn" :disabled="busy" @click="loadLogs">刷新日志</button>
        <ul v-if="logs.length" class="log-list">
          <li v-for="log in logs" :key="log.id" class="log-item">
            <span class="log-time">{{ log.createdAt }}</span>
            <span class="log-action">{{ log.action }}</span>
            <span class="log-detail">{{ log.detail }}</span>
          </li>
        </ul>
        <p v-else class="empty">暂无操作日志。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.dev-page { padding: 24px; max-width: 960px; }
.dev-page h2 { margin: 0 0 16px; color: var(--color-primary); font-size: 20px; }
.notice { color: #b5800a; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.dev-card { padding: 18px; border-radius: var(--radius-md); background: var(--bg-card); box-shadow: var(--shadow-1); }
.dev-card h3 { margin: 0 0 12px; font-size: 15px; }
.dev-card label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--text-secondary); margin-bottom: 10px; }
.dev-card input, .dev-card select { padding: 6px 8px; border: 1px solid #d6d6d6; border-radius: 6px; margin-top: 4px; }
.btn { padding: 8px 14px; margin: 4px 6px 4px 0; border: 0; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; }
.btn:disabled { cursor: not-allowed; opacity: .6; }
.message { color: #20864b !important; }
.error { color: #c43d3d !important; }
.log-list { margin: 12px 0 0; padding: 0; list-style: none; }
.log-item { display: flex; gap: 12px; padding: 6px 0; border-bottom: 1px solid #eee; font-size: 13px; }
.log-time { color: var(--text-secondary); font-family: monospace; flex-shrink: 0; }
.log-action { font-weight: 600; color: var(--color-primary); flex-shrink: 0; }
.log-detail { word-break: break-all; }
.empty { color: var(--text-secondary); }
.tip { font-size: 12px; color: var(--text-secondary); margin: 0 0 10px; }
.state { font-size: 12px; color: var(--text-secondary); margin: 10px 0 0; line-height: 1.6; }

@media (max-width: 768px) {
  .dev-page { padding: 12px; }
  .grid { grid-template-columns: 1fr; }
  .log-item { flex-direction: column; gap: 2px; }
}
</style>