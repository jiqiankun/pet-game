<script setup lang="ts">
/**
 * 通用 Overlay 外壳（Overlay 架构 P0）。
 * 统一所有游戏内浮层的视觉语言：遮罩 + 标题栏 + 返回/关闭 + 内容区 + 形态（panel/bottom-sheet/drawer/fullscreen）。
 * 业务内容通过 slot 复用各功能 View 组件。
 * 遵循架构边界：本组件只负责 UI 壳与关闭生命周期，不承载具体业务逻辑。
 */

import { nextTick, onMounted, ref, watch } from 'vue'
import { focusFirstIn, trapFocus } from '../../utils/focus'

/** 浮层形态：panel=居中面板；bottom-sheet=底部抽屉；drawer=右侧面板；fullscreen=全屏。 */
export type OverlayShellVariant = 'panel' | 'bottom-sheet' | 'drawer' | 'fullscreen'

const props = withDefaults(
  defineProps<{
    /** 浮层标题。 */
    title: string
    /** 浮层形态。 */
    variant?: OverlayShellVariant
    /** 是否显示「返回」按钮（用于二级浮层返回上一层）。 */
    showBack?: boolean
    /** 是否显示遮罩（非顶层浮层传 false 使遮罩透明，避免多重遮罩叠加）。 */
    masked?: boolean
    /** 层级 z-index（OverlayLayer 按栈位置递增传入）。 */
    zIndex?: number
    /** 当前是否为 Context 栈顶；只有栈顶窗口接管焦点与 Tab。 */
    active?: boolean
    /** Context 实例 id，用于 aria 标题关联。 */
    contextId?: number
    /** 是否允许点击遮罩关闭。 */
    closeOnMask?: boolean
  }>(),
  {
    variant: 'panel',
    showBack: false,
    masked: true,
    zIndex: 400,
    active: true,
    contextId: 0,
    closeOnMask: true,
  },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'back'): void
}>()

const panel = ref<HTMLElement | null>(null)
const titleId = `overlay-title-${props.contextId}`

function focusInitial() {
  nextTick(() => {
    if (props.active) focusFirstIn(panel.value)
  })
}

function handleKeydown(event: KeyboardEvent) {
  if (props.active) trapFocus(event, panel.value)
}

function closeFromMask() {
  if (props.closeOnMask) emit('close')
}

onMounted(focusInitial)
watch(() => props.active, (active) => {
  if (active) focusInitial()
})
</script>

<template>
  <div
    class="overlay-mask"
    :class="[`ov-${variant}`, { 'ov-mask-clear': !props.masked }]"
    :style="{ zIndex: props.zIndex }"
    @click.self="closeFromMask"
  >
    <div
      ref="panel"
      class="overlay-panel"
      role="dialog"
      tabindex="-1"
      :aria-modal="props.active ? 'true' : undefined"
      :aria-labelledby="title ? titleId : undefined"
      :aria-label="title || undefined"
      @keydown="handleKeydown"
    >
      <header class="overlay-header">
        <button v-if="showBack" class="ov-btn ov-back" aria-label="返回" @click="emit('back')">‹ 返回</button>
        <h3 :id="titleId" class="overlay-title">{{ title }}</h3>
        <button class="ov-btn ov-close" aria-label="关闭" @click="emit('close')">✕</button>
      </header>
      <div class="overlay-body">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay-mask {
  position: fixed;
  inset: 0;
  z-index: 400;
  display: flex;
  background-color: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(2px);
  animation: ov-mask-fade 0.2s ease-out;
}

/* P3：统一转场动画（150~300ms）—— 遮罩淡入 */
@keyframes ov-mask-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 非顶层浮层：遮罩透明，仅当被上层覆盖时用于承载层级顺序 */
.ov-mask-clear {
  background-color: transparent;
  backdrop-filter: none;
}

/* ---- 形态：居中面板 ---- */
.ov-panel {
  align-items: center;
  justify-content: center;
}
.ov-panel .overlay-panel {
  width: min(920px, 92vw);
  max-height: 86vh;
  border-radius: var(--radius-lg, 12px);
  animation: ov-panel-pop 0.2s ease-out;
}
/* P3：居中面板缩放淡入 */
@keyframes ov-panel-pop {
  from { transform: scale(0.96); opacity: 0.5; }
  to { transform: scale(1); opacity: 1; }
}

/* ---- 形态：底部抽屉 ---- */
.ov-bottom-sheet {
  align-items: flex-end;
  justify-content: center;
}
.ov-bottom-sheet .overlay-panel {
  width: 100%;
  max-width: 720px;
  max-height: 90vh;
  border-radius: var(--radius-lg, 12px) var(--radius-lg, 12px) 0 0;
  animation: ov-sheet-up 0.2s ease-out;
}
@keyframes ov-sheet-up {
  from { transform: translateY(40px); opacity: 0.6; }
  to { transform: translateY(0); opacity: 1; }
}

/* ---- 形态：右侧面板 ---- */
.ov-drawer {
  justify-content: flex-end;
}
.ov-drawer .overlay-panel {
  width: min(480px, 92vw);
  height: 100%;
  border-radius: 0;
  animation: ov-drawer-in 0.2s ease-out;
}
@keyframes ov-drawer-in {
  from { transform: translateX(60px); opacity: 0.6; }
  to { transform: translateX(0); opacity: 1; }
}

/* ---- 形态：全屏 ---- */
.ov-fullscreen {
  align-items: stretch;
}
.ov-fullscreen .overlay-panel {
  width: 100%;
  height: 100%;
  border-radius: 0;
  animation: ov-fullscreen-fade 0.2s ease-out;
}
/* P3：全屏淡入 */
@keyframes ov-fullscreen-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ---- 通用面板样式 ---- */
.overlay-panel {
  display: flex;
  flex-direction: column;
  background-color: var(--bg-card, #fff);
  box-shadow: var(--shadow-2, 0 8px 24px rgba(0, 0, 0, 0.2));
  overflow: hidden;
}

.overlay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.overlay-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-primary, #4a90d9);
}

.ov-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.06);
  color: var(--text-secondary, #666);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s, color 0.2s;
}
.ov-btn:hover {
  background-color: rgba(0, 0, 0, 0.12);
  color: var(--color-primary, #4a90d9);
}
.ov-back {
  border-radius: var(--radius-md, 8px);
  width: auto;
  padding: 0 10px;
  font-size: 14px;
}

.overlay-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 移动端：居中面板转全屏 */
@media (max-width: 768px) {
  .ov-panel .overlay-panel {
    width: 100%;
    height: 100%;
    max-height: 100%;
    border-radius: 0;
  }
}
</style>
