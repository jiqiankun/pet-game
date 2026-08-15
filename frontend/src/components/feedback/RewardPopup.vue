<script setup lang="ts">
/**
 * 奖励弹窗（Overlay 架构 P0：GlobalFeedbackLayer 的 RewardPopup）。
 * 采集/宝箱等奖励结果展示，点击遮罩或按钮关闭。
 */
import type { RewardResultView } from '../../types/map'

defineProps<{
  reward: RewardResultView
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<template>
  <div class="reward-mask" @click.self="emit('close')">
    <div class="reward-card">
      <h3>{{ reward.objectName }}：获得奖励</h3>
      <ul class="reward-list">
        <li v-if="reward.goldGained > 0">金币 +{{ reward.goldGained }}</li>
        <li v-for="item in reward.items" :key="item.itemId">
          {{ item.name }} ×{{ item.quantity }}
        </li>
        <li v-if="reward.goldGained === 0 && reward.items.length === 0">（空）</li>
      </ul>
      <div class="reward-actions">
        <button class="btn-primary" @click="emit('close')">收下</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reward-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 500;
}

.reward-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  width: 420px;
  max-width: 90vw;
  box-shadow: var(--shadow-2);
}

.reward-card h3 {
  font-size: 17px;
  color: var(--color-primary);
  margin-bottom: 10px;
}

.reward-list {
  margin: 0 0 14px 18px;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.9;
}

.reward-actions {
  display: flex;
  justify-content: flex-end;
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
</style>