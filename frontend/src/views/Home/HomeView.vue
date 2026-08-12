<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '../../stores'
import { apiGet } from '../../api/client'
import type { ApiResponse } from '../../types/api'

const appStore = useAppStore()

onMounted(async () => {
  try {
    const res = await apiGet<{ status: string; version: string }>('/api/health')
    const data = (res as ApiResponse<{ status: string; version: string }>).data
    appStore.setServerStatus(true, data.version)
  } catch {
    appStore.setServerStatus(false, '')
  }
})
</script>

<template>
  <div class="home-view">
    <div class="hero-section">
      <h2 class="hero-title">欢迎来到宠物精灵世界</h2>
      <p class="hero-subtitle">收集、培养、战斗 —— 开启你的冒险之旅</p>
    </div>

    <div class="status-card">
      <h3>服务器状态</h3>
      <div class="status-indicator">
        <span
          class="status-dot"
          :class="appStore.serverConnected ? 'connected' : 'disconnected'"
        ></span>
        <span>{{ appStore.serverConnected ? '已连接' : '未连接' }}</span>
        <span v-if="appStore.serverVersion" class="version-tag">
          v{{ appStore.serverVersion }}
        </span>
      </div>
    </div>

    <div class="placeholder-notice">
      <p>🎮 当前为阶段 0（工程脚手架），所有游戏功能页面均为占位状态。</p>
      <p>后续阶段将逐步实现：区域探索、宠物收集、队伍构筑、回合制战斗等核心玩法。</p>
    </div>
  </div>
</template>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-section {
  text-align: center;
  padding: 48px 24px;
  background: linear-gradient(135deg, var(--color-primary), #6BB3F0);
  border-radius: var(--radius-lg);
  color: var(--text-white);
}

.hero-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 16px;
  opacity: 0.9;
}

.status-card {
  padding: 16px 24px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.status-card h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.connected {
  background-color: var(--color-success);
}

.status-dot.disconnected {
  background-color: var(--color-danger);
}

.version-tag {
  font-size: 12px;
  padding: 2px 8px;
  background-color: var(--bg-main);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.placeholder-notice {
  padding: 16px 24px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  border-left: 4px solid var(--color-secondary);
  box-shadow: var(--shadow-1);
  line-height: 1.8;
}
</style>
