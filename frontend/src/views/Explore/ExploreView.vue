<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

/**
 * 阶段 5 简化遭遇入口（临时验收手段）：跳转到战斗页发起野生遭遇。
 * 阶段 6 起由地图承接真实遭遇，本入口不与地图逻辑耦合。
 */
function goWildEncounter() {
  router.push('/battle')
}
</script>

<template>
  <div class="page-placeholder">
    <h2>区域探索</h2>
    <p class="placeholder-text">
      2D 地图自由探索将在阶段 6 实现（Phaser + Tiled）。当前阶段提供临时野生遭遇入口用于捕捉系统验收。
    </p>
    <button class="btn-wild" @click="goWildEncounter">寻找野生宠物（临时遭遇入口）</button>
  </div>
</template>

<style scoped>
.page-placeholder {
  padding: 24px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.page-placeholder h2 {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.placeholder-text {
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 16px;
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

.btn-wild:hover {
  opacity: 0.9;
}
</style>
