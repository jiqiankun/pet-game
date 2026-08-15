<script setup lang="ts">
/**
 * 全局轻提示（Overlay 架构 P0：GlobalFeedbackLayer 的 Toast/Error 反馈）。
 * 由 `useUiStore.toasts` 驱动，全局挂载于 MainLayout，覆盖所有页面。
 * 错误反馈（ErrorFeedback 语义）复用 toast 的 error 变体展示。
 */
import { computed } from 'vue'
import { useUiStore } from '../../stores/ui'

const uiStore = useUiStore()

/** 轻量提示（info/success）。错误反馈由 ErrorFeedback 单独渲染，避免重复。 */
const infoToasts = computed(() => uiStore.toasts.filter((t) => t.type !== 'error'))
</script>

<template>
  <div class="global-toasts" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="t in infoToasts"
        :key="t.id"
        :class="['toast-item', `toast-${t.type}`]"
        @click="uiStore.dismiss(t.id)"
      >
        {{ t.msg }}
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.global-toasts {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  z-index: 600;
  pointer-events: none;
  max-width: 80vw;
}

.toast-item {
  pointer-events: auto;
  padding: 10px 18px;
  border-radius: var(--radius-md, 8px);
  font-size: 14px;
  color: #fff;
  background-color: rgba(20, 26, 34, 0.92);
  box-shadow: var(--shadow-2, 0 8px 24px rgba(0, 0, 0, 0.2));
  cursor: pointer;
}

.toast-success { background-color: rgba(39, 174, 96, 0.92); }
.toast-error { background-color: rgba(211, 47, 47, 0.92); }

.toast-enter-active,
.toast-leave-active {
  transition: transform 0.2s, opacity 0.2s;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>