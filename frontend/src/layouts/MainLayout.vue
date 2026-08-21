<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TutorialOverlay from '../views/Quest/components/TutorialOverlay.vue'
import GlobalToast from '../components/feedback/GlobalToast.vue'
import ErrorFeedback from '../components/feedback/ErrorFeedback.vue'
import OverlayLayer from '../components/overlay/OverlayLayer.vue'
import { useGameStore } from '../stores/game'
import { useOverlayStore } from '../stores/overlay'
import { useKeyboardShortcuts } from '../composables/useKeyboardShortcuts'
import { gameBridge } from '../game/bridge/GameBridge'
import { isEditableTarget } from '../utils/keyboard'

const gameStore = useGameStore()
const overlayStore = useOverlayStore()
const route = useRoute()
const router = useRouter()
const isWorldRoute = computed(() => route.name === 'Explore')
const WORLD_BACK_GUARD_KEY = '__petGameWorldBackGuard'
let restoringWorldBackGuard = false

// ---- 桌面世界快捷键（M/Q/B/J；W/A/S/D/E 交给 Phaser）----
useKeyboardShortcuts(isWorldRoute)

// ---- 统一返回行为 ----
// 仅 Esc 处理游戏 Context；不可取消 Context 会消费事件而不退出。
function handleKeydown(event: KeyboardEvent) {
  if (event.repeat || event.key !== 'Escape' || isEditableTarget(event.target)) return
  if (overlayStore.handleBack()) event.preventDefault()
}

function releaseWorldInput() {
  gameBridge.emit('cmd:clear-input', {})
}

function syncTextInputFocus() {
  window.setTimeout(() => {
    overlayStore.setTextInputFocused(isEditableTarget(document.activeElement))
  }, 0)
}

/**
 * Hash 路由的浏览器返回与普通导航共用同一规则：有 Context 时优先处理它；
 * 栈为空时才允许真正切换路由，避免旧实现反复 pushState 造成历史死循环。
 */
const removeNavigationGuard = router.beforeEach((to, from) => {
  if (to.fullPath !== from.fullPath && overlayStore.handleBack()) {
    return false
  }
  return true
})

/**
 * 世界页只保留一个同 URL 的历史保护位。浏览器返回先落到该保护位：
 * 有 Context 时关闭栈顶并前进恢复保护位；空栈时才继续返回真实路由。
 * 不按每次打开浮层 pushState，避免形成历史循环。
 */
function ensureWorldBackGuard() {
  if (!isWorldRoute.value || history.state?.[WORLD_BACK_GUARD_KEY] === 'active') return
  history.replaceState({ ...(history.state ?? {}), [WORLD_BACK_GUARD_KEY]: 'base' }, '', location.href)
  history.pushState({ ...(history.state ?? {}), [WORLD_BACK_GUARD_KEY]: 'active' }, '', location.href)
}

function handleWorldPopState(event: PopStateEvent) {
  if (!isWorldRoute.value || restoringWorldBackGuard || event.state?.[WORLD_BACK_GUARD_KEY] !== 'base') return
  if (overlayStore.handleBack()) {
    restoringWorldBackGuard = true
    history.go(1)
    return
  }
  history.back()
}

function restoreWorldBackGuard() {
  restoringWorldBackGuard = false
}

watch(isWorldRoute, (inWorld) => {
  if (inWorld) ensureWorldBackGuard()
}, { immediate: true })

window.addEventListener('keydown', handleKeydown)
window.addEventListener('popstate', handleWorldPopState, true)
window.addEventListener('popstate', restoreWorldBackGuard)
window.addEventListener('blur', releaseWorldInput)
document.addEventListener('visibilitychange', releaseWorldInput)
window.addEventListener('focusin', syncTextInputFocus)
window.addEventListener('focusout', syncTextInputFocus)
onBeforeUnmount(() => {
  removeNavigationGuard()
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('popstate', handleWorldPopState, true)
  window.removeEventListener('popstate', restoreWorldBackGuard)
  window.removeEventListener('blur', releaseWorldInput)
  document.removeEventListener('visibilitychange', releaseWorldInput)
  window.removeEventListener('focusin', syncTextInputFocus)
  window.removeEventListener('focusout', syncTextInputFocus)
})
</script>

<template>
  <div class="app-layout" :class="{ 'app-layout-world': isWorldRoute }">
    <!-- 桌面游戏壳：世界内只保留状态提示和返回首页，功能入口由 HUD/快捷键承担。 -->
    <header class="app-header" :class="{ 'app-header-world': isWorldRoute }">
      <div class="header-brand">
        <img class="header-logo" src="/assets/ui/logo_game.png" alt="" />
        <h1 class="header-title">宠物精灵</h1>
        <span class="header-badge">{{ isWorldRoute ? '桌面世界' : 'Phase 11' }}</span>
      </div>
      <nav class="header-nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink v-if="!isWorldRoute" to="/explore">探索</RouterLink>
        <span v-else class="world-key-hint">Q 队伍 · B 背包 · J 任务 · M 地图 · Esc 返回</span>
        <RouterLink v-if="!isWorldRoute && gameStore.developerMode" to="/dev-tools">开发者工具</RouterLink>
      </nav>
    </header>

    <!-- 主内容区 -->
    <main class="app-main" :class="{ 'app-main-world': isWorldRoute }">
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

.app-header-world {
  height: 44px;
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

.world-key-hint {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
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

.app-main-world {
  max-width: none;
  padding: 0;
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
