<script setup lang="ts">
/**
 * 情境交互层（Overlay 架构 P1）。
 * <p>
 * 根据玩家附近对象动态显示一个动作按钮（如「对话」「打开」「采集」）。
 * 数据由 MapScene 节流上报到 useMapStore.nearbyObject；点击复用既有 bridge 交互事件，
 * 由 ExploreView 的 bridge 处理器统一处理后端逻辑。
 * 遵循架构边界：本组件只负责「附近对象 → 动作按钮」的展示与事件转发，不承载业务计算。
 */
import { computed } from 'vue'
import { useMapStore } from '../../stores/map'
import { gameBridge } from '../../game/bridge/GameBridge'

const mapStore = useMapStore()

/** 对象类型 → 动作文案与对应 bridge 事件。 */
const ACTION_MAP: Record<string, { label: string; event: string }> = {
  camp: { label: '休息', event: 'camp:touch' },
  chest: { label: '打开', event: 'chest:request' },
  gather: { label: '采集', event: 'gather:request' },
  npc: { label: '对话', event: 'npc:touch' },
  boss_entrance: { label: '挑战', event: 'boss:touch' },
  hidden_spot: { label: '观察', event: 'hidden:touch' },
}

const nearby = computed(() => mapStore.nearbyObject)

const action = computed(() => {
  if (!nearby.value) return null
  return ACTION_MAP[nearby.value.type] ?? null
})

function interact() {
  const n = nearby.value
  if (!n || !action.value) return
  gameBridge.emit(action.value.event as 'camp:touch', { id: n.id })
}
</script>

<template>
  <div v-if="nearby && action" class="context-panel">
    <button class="context-btn" @click="interact">
      <span v-if="nearby.label" class="ctx-hint">{{ nearby.label }}</span>
      <span class="ctx-action">{{ action.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.context-panel {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  z-index: 30;
  pointer-events: auto;
}

.context-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border: none;
  border-radius: 999px;
  background-color: rgba(16, 24, 32, 0.82);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: background-color 0.2s, transform 0.1s;
}

.context-btn:hover {
  background-color: rgba(74, 144, 217, 0.9);
  transform: translateY(-1px);
}

.ctx-hint {
  font-size: 12px;
  color: #cfd8e3;
}

.ctx-action {
  font-weight: 600;
}
</style>