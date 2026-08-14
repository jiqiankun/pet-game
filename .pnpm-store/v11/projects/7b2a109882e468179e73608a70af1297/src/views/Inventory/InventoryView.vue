<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { apiGet, apiPost, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { InventoryView, UseItemResult } from '../../types/pet'
import { itemIconUrl } from '../../game-assets'

const gameStore = useGameStore()

const inventory = ref<InventoryView | null>(null)
const loading = ref(false)
const using = ref(false)
const error = ref('')
const useResult = ref<UseItemResult | null>(null)

// 使用道具时的选择
const selectedItemId = ref('')
const selectedPetId = ref<number | null>(null)

// 分类标签
const CATEGORY_LABELS: Record<string, string> = {
  CAPTURE: '捕捉道具',
  RECOVERY: '恢复道具',
  MATERIAL: '材料',
  SKILL_BOOK: '技能书',
  KEY_ITEM: '重要物品',
}

onMounted(async () => {
  await gameStore.loadBootstrap()
  await loadInventory()
})

async function loadInventory() {
  loading.value = true
  error.value = ''
  try {
    const res = await apiGet<InventoryView>('/api/inventory')
    inventory.value = (res as ApiResponse<InventoryView>).data
  } catch (e: any) {
    error.value = e.message || '加载背包失败'
  } finally {
    loading.value = false
  }
}

/** 按分类分组道具。 */
const groupedItems = computed(() => {
  if (!inventory.value) return []
  const groups: Record<string, InventoryView['items']> = {}
  for (const item of inventory.value.items) {
    const cat = item.category || 'OTHER'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(item)
  }
  return Object.entries(groups).map(([cat, items]) => ({
    category: cat,
    label: CATEGORY_LABELS[cat] || cat,
    items,
  }))
})

/** 可使用的恢复道具（usableOutsideBattle 且数量 > 0）。 */
const usableItems = computed(() => {
  if (!inventory.value) return []
  return inventory.value.items.filter(
    (i) => i.usableOutsideBattle && i.quantity > 0,
  )
})

/** 使用恢复道具。 */
async function useItem() {
  if (!selectedItemId.value || !selectedPetId.value) return
  using.value = true
  error.value = ''
  useResult.value = null
  try {
    const res = await apiPost<UseItemResult>('/api/inventory/use', {
      itemId: selectedItemId.value,
      petId: selectedPetId.value,
    })
    useResult.value = (res as ApiResponse<UseItemResult>).data
    // 刷新背包与宠物列表
    await loadInventory()
    await gameStore.loadBootstrap()
  } catch (e: any) {
    error.value = e instanceof BusinessError ? e.message : (e.message || '使用失败')
  } finally {
    using.value = false
  }
}
</script>

<template>
  <div class="inventory-view">
    <div v-if="loading" class="loading-text">加载中...</div>

    <template v-if="inventory">
      <!-- 金币 -->
      <div class="gold-bar">
        <span class="gold-label">金币</span>
        <span class="gold-value">{{ inventory.gold }}</span>
      </div>

      <!-- 恢复道具使用区 -->
      <div v-if="usableItems.length > 0" class="use-section">
        <h3>使用恢复道具</h3>
        <div class="use-row">
          <select v-model="selectedItemId" class="use-select">
            <option value="">选择道具</option>
            <option v-for="item in usableItems" :key="item.itemId" :value="item.itemId">
              {{ item.name }}（{{ item.itemType === 'HEAL_HP' ? `恢复 ${item.value} HP` : `复活 ${item.value}%` }}）×{{ item.quantity }}
            </option>
          </select>
          <select v-model="selectedPetId" class="use-select">
            <option :value="null">选择宠物</option>
            <option v-for="pet in gameStore.pets" :key="pet.id" :value="pet.id">
              {{ pet.nickname || pet.speciesId }} (Lv.{{ pet.level }}, HP {{ pet.currentHp }})
            </option>
          </select>
          <button
            class="btn-action"
            :disabled="using || !selectedItemId || !selectedPetId"
            @click="useItem"
          >
            {{ using ? '使用中...' : '使用' }}
          </button>
        </div>
        <div v-if="useResult" class="use-result">
          <span class="result-name">{{ useResult.itemName }}</span> →
          <span class="result-hp">{{ useResult.beforeHp }} → {{ useResult.afterHp }}</span>
          <span class="result-max">/ {{ useResult.maxHp }}</span>
          <span class="result-remaining">剩余 {{ useResult.remainingQuantity }}</span>
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
      </div>

      <!-- 道具列表（按分类） -->
      <div v-for="group in groupedItems" :key="group.category" class="category-group">
        <h3>{{ group.label }}</h3>
        <div class="item-list">
          <div v-for="item in group.items" :key="item.itemId" class="item-card">
            <div class="item-header">
              <span class="item-name"><img class="item-icon" :src="itemIconUrl(item.itemId)" alt="" />{{ item.name }}</span>
              <span class="item-quantity">×{{ item.quantity }}</span>
            </div>
            <p class="item-desc">{{ item.description }}</p>
            <div class="item-tags">
              <span class="tag">{{ item.itemType }}</span>
              <span v-if="item.usableOutsideBattle" class="tag usable">战斗外可用</span>
              <span v-if="item.usableInBattle" class="tag battle">战斗内可用</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="inventory.items.length === 0" class="empty-text">背包是空的</div>
    </template>
  </div>
</template>

<style scoped>
.inventory-view {
  padding: 24px;
  max-width: 720px;
  margin: 0 auto;
}

.gold-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  box-shadow: var(--shadow-1);
  margin-bottom: 20px;
}

.gold-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.gold-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-secondary);
}

.use-section {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-1);
  margin-bottom: 20px;
}

.use-section h3 {
  font-size: 16px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.use-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.use-select {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
}

.btn-action {
  padding: 6px 16px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.btn-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.use-result {
  margin-top: 10px;
  padding: 8px 12px;
  background-color: #e8f5e9;
  border-radius: 6px;
  font-size: 13px;
  color: #2e7d32;
}

.result-name {
  font-weight: 600;
}

.result-hp {
  font-weight: 600;
}

.result-max {
  color: var(--text-secondary);
  font-size: 12px;
}

.result-remaining {
  margin-left: 8px;
  font-size: 12px;
}

.category-group {
  margin-bottom: 20px;
}

.category-group h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 10px;
  padding-bottom: 4px;
  border-bottom: 1px solid #eee;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  box-shadow: var(--shadow-1);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.item-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  font-size: 14px;
}

.item-icon {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.item-quantity {
  font-weight: 600;
  color: var(--color-primary);
  font-size: 14px;
}

.item-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
  margin-bottom: 4px;
}

.item-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background-color: #f0f0f0;
  color: var(--text-secondary);
}

.tag.usable {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.tag.battle {
  background-color: #fff3cd;
  color: #856404;
}

.loading-text {
  text-align: center;
  padding: 60px 0;
  color: var(--text-secondary);
}

.empty-text {
  text-align: center;
  padding: 40px 0;
  color: var(--text-secondary);
}

.error-text {
  margin-top: 8px;
  color: #d32f2f;
  font-size: 13px;
}

@media (max-width: 768px) {
  .inventory-view { padding: 8px; }
  .category-tabs { flex-wrap: wrap; }
  .item-card { padding: 8px; }
}
</style>
