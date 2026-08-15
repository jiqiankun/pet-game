<script setup lang="ts">
/**
 * 统一错误反馈条（Overlay 架构 P0：GlobalFeedbackLayer 的 ErrorFeedback）。
 * <p>
 * 由 `useUiStore` 的错误 toast 驱动，作为醒目错误提示条展示，全局挂载于 MainLayout。
 * 与 GlobalToast 分离：GlobalToast 展示轻量 info/success，本组件聚焦错误反馈的醒目展示。
 */
import { computed } from 'vue'
import { useUiStore } from '../../stores/ui'

const uiStore = useUiStore()

/** 仅取错误类型 toast 渲染为反馈条。 */
const errorToasts = computed(() => uiStore.toasts.filter((t) => t.type === 'error'))

function dismiss(id: number) {
  uiStore.dismiss(id)
}
</script>

<template>
  <div class="error-feedback" aria-live="assertive">
    <TransitionGroup name="err">
      <div
        v-for="t in errorToasts"
        :key="t.id"
        class="error-item"
        @click="dismiss(t.id)"
      >
        <span class="error-icon">⚠</span>
        <span class="error-msg">{{ t.msg }}</span>
        <button class="error-close" aria-label="关闭" @click.stop="dismiss(t.id)">✕</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.error-feedback {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  z-index: 700;
  pointer-events: none;
  width: min(520px, 90vw);
}

.error-item {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border-radius: var(--radius-md, 8px);
  font-size: 14px;
  color: #fff;
  background-color: rgba(211, 47, 47, 0.95);
  box-shadow: var(--shadow-2, 0 8px 24px rgba(0, 0, 0, 0.2));
  cursor: pointer;
}

.error-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.error-msg {
  flex: 1;
  line-height: 1.5;
}

.error-close {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.2);
  color: #fff;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}
.error-close:hover {
  background-color: rgba(0, 0, 0, 0.35);
}

.err-enter-active,
.err-leave-active {
  transition: transform 0.2s, opacity 0.2s;
}
.err-enter-from,
.err-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>