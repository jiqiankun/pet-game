<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import TutorialOverlay from '../views/Quest/components/TutorialOverlay.vue'
import GlobalToast from '../components/feedback/GlobalToast.vue'
import ErrorFeedback from '../components/feedback/ErrorFeedback.vue'
import OverlayLayer from '../components/overlay/OverlayLayer.vue'
import { useGameStore } from '../stores/game'
import { useOverlayStore } from '../stores/overlay'
import { useKeyboardShortcuts } from '../composables/useKeyboardShortcuts'

const gameStore = useGameStore()
const overlayStore = useOverlayStore()

// ---- 键盘快捷键（P3：T/B/I/M/Q/P/G/S 打开对应浮层）----
useKeyboardShortcuts()

// ---- 统一返回行为（Overlay 架构 P0）----
// 返回 = 只关闭最上层 Overlay；战斗不通过返回键退出。
function handleEscape() {
  overlayStore.handleBack()
}

function handlePopState() {
  // Android / 浏览器返回键：若存在浮层则关闭最上层并保持当前 hash，不触发路由回退
  if (overlayStore.handleBack()) {
    window.history.pushState(null, '', window.location.href)
  }
}

window.addEventListener('keydown', handleEscape)
window.addEventListener('popstate', handlePopState)
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEscape)
  window.removeEventListener('popstate', handlePopState)
})
</script>

<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <header class="app-header">
      <div class="header-brand">
        <img class="header-logo" src="/assets/ui/logo_game.png" alt="" />
        <h1 class="header-title">宠物精灵</h1>
        <span class="header-badge">Phase 11</span>
      </div>
      <nav class="header-nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/explore">探索</RouterLink>
        <RouterLink to="/world-map">大地图</RouterLink>
        <RouterLink to="/battle">战斗</RouterLink>
        <RouterLink to="/pets">宠物</RouterLink>
        <RouterLink to="/team">队伍</RouterLink>
        <RouterLink to="/storage">仓库</RouterLink>
        <RouterLink to="/pokedex">图鉴</RouterLink>
        <RouterLink to="/boss">Boss</RouterLink>
        <RouterLink to="/inventory">背包</RouterLink>
        <RouterLink to="/shop">商店</RouterLink>
        <RouterLink to="/quest">任务</RouterLink>
        <RouterLink to="/achievement">成就</RouterLink>
        <RouterLink to="/statistics">统计</RouterLink>
        <RouterLink to="/settings">设置</RouterLink>
        <RouterLink to="/save-backup">存档备份</RouterLink>
        <RouterLink v-if="gameStore.developerMode" to="/dev-tools">开发者工具</RouterLink>
      </nav>
    </header>

    <!-- 主内容区 -->
    <main class="app-main">
      <slot />
    </main>

    <!-- 全局新手教学浮层 -->
    <TutorialOverlay />
    <!-- 统一浮层渲染层（OverlayLayer：按 overlay 栈渲染功能浮层，含 NPC 对话） -->
    <OverlayLayer />
    <!-- 全局轻提示（GlobalFeedbackLayer） -->
    <GlobalToast />
    <!-- 全局错误反馈条（GlobalFeedbackLayer） -->
    <ErrorFeedback />
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background-color: var(--bg-card);
  box-shadow: var(--shadow-1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.header-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
}

.header-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background-color: var(--color-secondary);
  color: var(--text-white);
  font-weight: 600;
}

.header-nav {
  display: flex;
  gap: 16px;
}

.header-nav a {
  font-size: 14px;
  color: var(--text-secondary);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: color 0.2s, background-color 0.2s;
}

.header-nav a:hover {
  color: var(--color-primary);
  text-decoration: none;
}

.header-nav a.router-link-active {
  color: var(--color-primary);
  background-color: rgba(74, 144, 217, 0.1);
  font-weight: 600;
}

.app-main {
  flex: 1;
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

/* 移动端底部导航 */
@media (max-width: 768px) {
  .header-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background-color: var(--bg-card);
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
    padding: 8px 4px;
    justify-content: space-around;
    flex-wrap: wrap;
    gap: 4px;
    z-index: 100;
  }

  .header-nav a {
    font-size: 11px;
    padding: 4px 6px;
  }

  .app-main {
    padding: 16px;
    padding-bottom: 80px;
  }
}
</style>
