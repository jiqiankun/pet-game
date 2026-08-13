<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '../../stores/game'
import { useAppStore } from '../../stores'
import { apiGet } from '../../api/client'
import type { ApiResponse } from '../../types/api'

const router = useRouter()
const appStore = useAppStore()
const gameStore = useGameStore()

onMounted(async () => {
  // 检查后端连接
  try {
    const res = await apiGet<{ status: string; version: string }>('/api/health')
    const data = (res as ApiResponse<{ status: string; version: string }>).data
    appStore.setServerStatus(true, data.version)
  } catch {
    appStore.setServerStatus(false, '')
  }

  // 加载 Bootstrap 数据
  await gameStore.loadBootstrap()

  // 如果没有存档，跳转到新游戏页面
  if (gameStore.hasSave === false) {
    router.replace('/new-game')
  }
})

async function handleSave() {
  await gameStore.manualSave()
}
</script>

<template>
  <div class="home-view">
    <!-- 加载中 -->
    <div v-if="gameStore.loading" class="loading-state">
      <p>加载中...</p>
    </div>

    <!-- 有存档：显示首页 -->
    <div v-else-if="gameStore.hasSave && gameStore.player" class="home-content">
      <div class="hero-section">
        <h2 class="hero-title">欢迎回来，{{ gameStore.player.playerName }}</h2>
        <p class="hero-subtitle">继续你的冒险之旅</p>
      </div>

      <!-- 状态卡片 -->
      <div class="status-cards">
        <div class="status-card">
          <div class="card-label">金币</div>
          <div class="card-value gold">{{ gameStore.player.gold }}</div>
        </div>
        <div class="status-card">
          <div class="card-label">经验池</div>
          <div class="card-value exp">{{ gameStore.player.expPool }}</div>
        </div>
        <div class="status-card">
          <div class="card-label">当前区域</div>
          <div class="card-value map">{{ gameStore.player.currentMapId }}</div>
        </div>
      </div>

      <!-- 当前队伍 -->
      <div class="section-card">
        <h3>当前队伍</h3>
        <div v-if="gameStore.pets.length > 0" class="team-pets">
          <div v-for="pet in gameStore.pets" :key="pet.id" class="pet-item">
            <span class="pet-species">{{ pet.speciesId }}</span>
            <span class="pet-level">Lv.{{ pet.level }}</span>
            <span class="pet-hp">HP {{ pet.currentHp }}</span>
            <span v-if="pet.isStarter" class="starter-badge">初始伙伴</span>
          </div>
        </div>
        <p v-else class="empty-text">队伍中没有宠物</p>
      </div>

      <!-- 快捷操作 -->
      <div class="action-section">
        <button class="btn-primary" @click="router.push('/explore')">继续探索</button>
        <button class="btn-primary" @click="router.push('/battle')">测试战斗</button>
        <button class="btn-primary" @click="router.push('/pets')">宠物培养</button>
        <button class="btn-primary" @click="router.push('/team')">队伍编辑</button>
        <button class="btn-primary" @click="router.push('/inventory')">背包</button>
        <button class="btn-secondary" @click="handleSave">保存游戏</button>
      </div>
    </div>

    <!-- 无存档提示（等待跳转） -->
    <div v-else-if="gameStore.hasSave === false" class="loading-state">
      <p>正在准备新游戏...</p>
    </div>

    <!-- 错误状态 -->
    <div v-if="gameStore.error" class="error-card">
      <p>{{ gameStore.error }}</p>
    </div>
  </div>
</template>

<style scoped>
.home-view {
  padding: 24px;
  max-width: 640px;
  margin: 0 auto;
}

.loading-state {
  text-align: center;
  padding: 60px 0;
  color: var(--text-secondary);
}

.hero-section {
  text-align: center;
  margin-bottom: 32px;
}

.hero-title {
  font-size: 24px;
  color: var(--color-primary);
  margin-bottom: 8px;
}

.hero-subtitle {
  color: var(--text-secondary);
  font-size: 14px;
}

.status-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.status-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  text-align: center;
  box-shadow: var(--shadow-1);
}

.card-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.card-value {
  font-size: 20px;
  font-weight: 600;
}

.card-value.gold { color: #F5A623; }
.card-value.exp { color: #4A90D9; }
.card-value.map { color: #7ED321; font-size: 14px; }

.section-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-1);
}

.section-card h3 {
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.team-pets {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pet-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background-color: var(--bg-secondary, #f5f5f5);
  border-radius: 6px;
}

.pet-species {
  font-weight: 500;
  flex: 1;
}

.pet-level {
  color: var(--color-primary);
  font-weight: 600;
}

.pet-hp {
  color: #7ED321;
}

.starter-badge {
  font-size: 11px;
  background-color: #F5A623;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
}

.empty-text {
  color: var(--text-secondary);
  font-size: 14px;
}

.action-section {
  display: flex;
  gap: 12px;
  justify-content: center;
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

.btn-secondary {
  padding: 10px 28px;
  background-color: var(--bg-secondary, #f0f0f0);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  cursor: pointer;
}

.error-card {
  margin-top: 16px;
  padding: 12px;
  background-color: #fff3f3;
  border: 1px solid #ffcdd2;
  border-radius: var(--radius-md);
  color: #d32f2f;
  text-align: center;
  font-size: 14px;
}
</style>
