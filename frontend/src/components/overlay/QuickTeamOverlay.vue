<script setup lang="ts">
/**
 * 快捷队伍浮层（Overlay 架构 P1）。
 * <p>
 * 以 BottomSheet 形态展示队伍每只宠物的 HP 状态，支持快速恢复（选宠物 → 选恢复道具 → 即时使用），
 * 并可一键进入完整队伍编辑。整个过程不离开本浮层。
 * 遵循架构边界：恢复结果由后端裁定，组件只提交使用意图并刷新状态。
 */
import { computed, onMounted, ref } from 'vue'
import { apiGet, apiPost, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { InventoryView, UseItemResult } from '../../types/pet'
import { useGameStore } from '../../stores/game'
import { useOverlayStore } from '../../stores/overlay'
import { petIconUrl } from '../../game-assets'

const gameStore = useGameStore()
const overlayStore = useOverlayStore()

/** 快捷队伍成员（合并 teamMembers/pets/petSummaries）。 */
interface QuickPet {
  petId: number
  position: number
  nickname: string
  speciesId: string
  level: number
  currentHp: number
  maxHp: number
}

const team = computed<QuickPet[]>(() => {
  const members = (gameStore.teamMembers as any[]) || []
  return members
    .map((m) => {
      const pet = (gameStore.pets as any[]).find((p) => p.id === m.petId)
      const summary = gameStore.petSummaries.find((s) => s.pet.id === m.petId)
      const maxHp = summary?.panelStats?.maxHp ?? pet?.maxHp ?? 0
      return {
        petId: m.petId,
        position: m.position ?? 0,
        nickname: pet?.nickname || summary?.speciesName || '',
        speciesId: pet?.speciesId || summary?.pet.speciesId || '',
        level: pet?.level ?? summary?.pet.level ?? 0,
        currentHp: pet?.currentHp ?? 0,
        maxHp,
      }
    })
    .sort((a, b) => a.position - b.position)
})

const hpPct = (p: QuickPet) => (p.maxHp > 0 ? Math.min(100, Math.round((p.currentHp / p.maxHp) * 100)) : 0)

// ---- 快速恢复 ----
const recoveryItems = ref<InventoryView['items']>([])
const using = ref(false)
const error = ref('')
const usedMsg = ref('')
/** 当前选择快速恢复的宠物。 */
const recoveringPet = ref<QuickPet | null>(null)

async function loadRecoveryItems() {
  try {
    const res = await apiGet<InventoryView>('/api/inventory')
    const inv = (res as ApiResponse<InventoryView>).data
    recoveryItems.value = (inv?.items ?? []).filter(
      (i) => i.usableOutsideBattle && i.quantity > 0 && (i.itemType === 'HEAL_HP' || i.itemType === 'REVIVE'),
    )
  } catch {
    recoveryItems.value = []
  }
}

onMounted(async () => {
  await gameStore.loadBootstrap()
  await loadRecoveryItems()
})

/** 点击宠物：若 HP 缺失则进入快速恢复选择。 */
function tapRecover(p: QuickPet) {
  recoveringPet.value = p
  usedMsg.value = ''
  error.value = ''
}

async function useItem(itemId: string) {
  if (!recoveringPet.value || using.value) return
  using.value = true
  error.value = ''
  usedMsg.value = ''
  const petId = recoveringPet.value.petId
  try {
    const res = await apiPost<UseItemResult>('/api/inventory/use', { itemId, petId })
    const result = (res as ApiResponse<UseItemResult>).data
    usedMsg.value = result
      ? `${recoveringPet.value!.nickname} 使用 ${result.itemName} 成功：HP ${result.beforeHp} → ${result.afterHp}`
      : '使用成功'
    await gameStore.loadBootstrap()
    await loadRecoveryItems()
  } catch (e: any) {
    error.value = e instanceof BusinessError ? e.message : (e.message || '使用失败')
  } finally {
    using.value = false
  }
}

/** 进入完整队伍编辑（替换快捷队伍浮层）。 */
function openFullTeam() {
  overlayStore.replaceTop('TEAM', undefined, { source: 'CONTEXT' })
}
</script>

<template>
  <div class="quick-team">
    <div class="qt-list">
      <div v-for="p in team" :key="p.petId" class="qt-row" @click="tapRecover(p)">
        <img class="qt-icon" :src="petIconUrl(p.speciesId, 64)" alt="" />
        <div class="qt-info">
          <span class="qt-name">{{ p.nickname }} Lv.{{ p.level }}</span>
          <div class="hp-bar">
            <div class="hp-fill" :class="{ low: hpPct(p) < 25 }" :style="{ width: hpPct(p) + '%' }"></div>
          </div>
          <span class="qt-hp">{{ p.currentHp }}/{{ p.maxHp }}</span>
        </div>
        <button v-if="p.currentHp < p.maxHp" class="qt-recover">恢复</button>
        <span v-else class="qt-ok">健康</span>
      </div>
    </div>

    <!-- 快速恢复面板 -->
    <div v-if="recoveringPet" class="qt-recover-panel">
      <div class="qt-recover-head">
        <span>为 {{ recoveringPet.nickname }} 使用恢复道具</span>
        <button class="qt-cancel" @click="recoveringPet = null">取消</button>
      </div>
      <p v-if="error" class="qt-error">{{ error }}</p>
      <p v-if="usedMsg" class="qt-success">{{ usedMsg }}</p>
      <div v-if="recoveryItems.length === 0" class="qt-empty">背包中没有可用的恢复道具</div>
      <div v-else class="qt-items">
        <button
          v-for="item in recoveryItems"
          :key="item.itemId"
          class="qt-item"
          :disabled="using"
          @click="useItem(item.itemId)"
        >
          <span class="qt-item-name">{{ item.name }}</span>
          <span v-if="item.itemType === 'HEAL_HP'" class="qt-item-val">恢复 {{ item.value }} HP</span>
          <span v-else class="qt-item-val">复活 {{ item.value }}%</span>
          <span class="qt-item-qty">×{{ item.quantity }}</span>
        </button>
      </div>
    </div>

    <button class="qt-full" @click="openFullTeam">进入完整队伍编辑 ›</button>
  </div>
</template>

<style scoped>
.quick-team {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 46vh;
  overflow-y: auto;
}

.qt-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm, 8px);
  background-color: rgba(74, 144, 217, 0.06);
  cursor: pointer;
  transition: background-color 0.2s;
}

.qt-row:hover {
  background-color: rgba(74, 144, 217, 0.14);
}

.qt-icon {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border-radius: var(--radius-sm, 6px);
  background-color: rgba(255, 255, 255, 0.1);
}

.qt-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.qt-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #222);
}

.hp-bar {
  width: 120px;
  height: 7px;
  background-color: rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  background-color: #4caf50;
  border-radius: 4px;
  transition: width 0.3s;
}

.hp-fill.low {
  background-color: #ef5350;
}

.qt-hp {
  font-size: 11px;
  color: var(--text-secondary, #666);
}

.qt-recover {
  border: none;
  border-radius: 999px;
  padding: 5px 12px;
  background-color: var(--color-primary, #4a90d9);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.qt-ok {
  font-size: 12px;
  color: #4caf50;
  font-weight: 600;
}

.qt-recover-panel {
  border-radius: var(--radius-sm, 8px);
  background-color: rgba(0, 0, 0, 0.04);
  padding: 10px;
}

.qt-recover-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.qt-cancel {
  border: none;
  background: none;
  color: var(--color-primary, #4a90d9);
  cursor: pointer;
  font-size: 12px;
}

.qt-error { color: #d32f2f; font-size: 12px; margin: 4px 0; }
.qt-success { color: #2e7d32; font-size: 12px; margin: 4px 0; }
.qt-empty { color: var(--text-secondary, #666); font-size: 12px; }

.qt-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.qt-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid rgba(74, 144, 217, 0.3);
  border-radius: var(--radius-sm, 8px);
  background-color: #fff;
  cursor: pointer;
  font-size: 12px;
}

.qt-item:disabled { opacity: 0.5; cursor: not-allowed; }
.qt-item-name { font-weight: 600; color: var(--text-primary, #222); }
.qt-item-val { color: var(--text-secondary, #666); }
.qt-item-qty { color: var(--color-primary, #4a90d9); font-weight: 600; }

.qt-full {
  border: none;
  border-radius: var(--radius-md, 8px);
  padding: 10px;
  background-color: rgba(74, 144, 217, 0.1);
  color: var(--color-primary, #4a90d9);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.qt-full:hover {
  background-color: var(--color-primary, #4a90d9);
  color: #fff;
}
</style>
