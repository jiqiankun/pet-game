<script setup lang="ts">
/**
 * 通用悬浮提示（Hover Tooltip，Overlay 架构 P3-19）。
 * <p>
 * 包裹插槽内容，hover/focus 时在内容上方显示气泡提示；支持方向与延迟。
 * 遵循架构边界：纯 UI 展示组件，不承载业务逻辑。
 */
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 提示文本。 */
    tip: string
    /** 提示方向。 */
    position?: 'top' | 'bottom'
    /** 延迟显示毫秒数。 */
    delay?: number
  }>(),
  { position: 'top', delay: 250 },
)

const show = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

function enter() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    show.value = true
  }, props.delay)
}

function leave() {
  clearTimeout(timer)
  show.value = false
}
</script>

<template>
  <span
    class="tooltip-wrap"
    @mouseenter="enter"
    @mouseleave="leave"
    @focus="enter"
    @blur="leave"
  >
    <slot />
    <span
      v-if="show"
      class="tooltip-bubble"
      :class="`pos-${position}`"
      role="tooltip"
    >{{ tip }}</span>
  </span>
</template>

<style scoped>
.tooltip-wrap {
  position: relative;
  display: inline-block;
}

.tooltip-bubble {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  max-width: 220px;
  padding: 5px 10px;
  background-color: rgba(16, 24, 32, 0.92);
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
  border-radius: var(--radius-sm, 6px);
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.tooltip-bubble.pos-top {
  bottom: calc(100% + 6px);
}

.tooltip-bubble.pos-bottom {
  top: calc(100% + 6px);
}
</style>