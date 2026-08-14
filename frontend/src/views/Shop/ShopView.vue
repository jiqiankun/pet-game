<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useShopStore } from '../../stores/shop'

const shopStore = useShopStore()

const buyQty = ref<Record<string, number>>({})

onMounted(async () => {
  await shopStore.loadShop()
})

function getQty(itemId: string): number {
  return buyQty.value[itemId] ?? 1
}

function setQty(itemId: string, val: number) {
  buyQty.value[itemId] = Math.max(1, val)
}

async function handleBuy(itemId: string) {
  await shopStore.buyItem(itemId, getQty(itemId))
  if (!shopStore.error) {
    buyQty.value[itemId] = 1
  }
}

function categoryLabel(cat: string): string {
  const map: Record<string, string> = {
    CAPTURE: '捕捉',
    RECOVERY: '恢复',
    BUFF: '增益',
    MATERIAL: '材料',
    SKILL_BOOK: '技能书',
  }
  return map[cat] ?? cat
}
</script>

<template>
  <div class="shop-view">
    <h2 class="page-title">商店</h2>
    <div class="gold-bar">
      <span class="gold-label">金币</span>
      <span class="gold-value">{{ shopStore.shopView?.gold ?? '—' }}</span>
    </div>

    <p v-if="shopStore.error" class="error-msg">{{ shopStore.error }}</p>
    <p v-if="shopStore.lastBuyResult" class="success-msg">
      购买成功：{{ shopStore.lastBuyResult.itemName }} x{{ shopStore.lastBuyResult.quantity }}，
      花费 {{ shopStore.lastBuyResult.totalCost }} 金币
    </p>

    <div v-if="shopStore.loading && !shopStore.shopView" class="loading-text">加载中...</div>

    <div v-if="shopStore.shopView" class="shop-grid">
      <div
        v-for="item in shopStore.shopView.items"
        :key="item.itemId"
        class="shop-card"
        :class="{ locked: !item.unlocked }"
      >
        <div class="card-header">
          <span class="item-name">{{ item.name }}</span>
          <span class="item-category">{{ categoryLabel(item.category) }}</span>
        </div>
        <p class="item-desc">{{ item.description }}</p>
        <div class="card-footer">
          <span class="item-price">{{ item.price }} 金币</span>
          <template v-if="item.unlocked">
            <div class="qty-control">
              <button class="qty-btn" @click="setQty(item.itemId, getQty(item.itemId) - 1)">-</button>
              <span class="qty-val">{{ getQty(item.itemId) }}</span>
              <button class="qty-btn" @click="setQty(item.itemId, getQty(item.itemId) + 1)">+</button>
            </div>
            <button
              class="btn-primary buy-btn"
              :disabled="shopStore.loading"
              @click="handleBuy(item.itemId)"
            >
              购买
            </button>
          </template>
          <span v-else class="lock-hint">
            需完成任务解锁
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shop-view {
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 16px;
}

.gold-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  margin-bottom: 16px;
}

.gold-label {
  font-weight: 600;
  color: var(--text-secondary);
}

.gold-value {
  font-size: 20px;
  font-weight: 700;
  color: #e6a817;
}

.error-msg {
  color: var(--color-danger, #e53e3e);
  padding: 8px 12px;
  background: #fff5f5;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}

.success-msg {
  color: var(--color-success, #38a169);
  padding: 8px 12px;
  background: #f0fff4;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}

.loading-text {
  color: var(--text-secondary);
  text-align: center;
  padding: 32px;
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}

.shop-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-1);
}

.shop-card.locked {
  opacity: 0.6;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-name {
  font-weight: 600;
  font-size: 16px;
}

.item-category {
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(74, 144, 217, 0.1);
  border-radius: var(--radius-sm);
  color: var(--color-primary);
}

.item-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.item-price {
  font-weight: 600;
  color: #e6a817;
}

.qty-control {
  display: flex;
  align-items: center;
  gap: 4px;
}

.qty-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: var(--radius-sm);
  background: var(--bg-main);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qty-val {
  min-width: 20px;
  text-align: center;
  font-weight: 600;
}

.buy-btn {
  margin-left: auto;
  padding: 4px 16px;
  font-size: 13px;
}

.lock-hint {
  font-size: 12px;
  color: var(--text-secondary);
  font-style: italic;
}

.btn-primary {
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  padding: 6px 16px;
  font-weight: 600;
  transition: opacity 0.2s;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
