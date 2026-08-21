<script setup lang="ts">
import { onActivated, onDeactivated } from 'vue'
import ExploreView from './ExploreView.vue'
import { gameBridge } from '../../game/bridge/GameBridge'
import { useOverlayStore } from '../../stores/overlay'

defineOptions({ name: 'WorldRoot' })

const overlayStore = useOverlayStore()

/** 缓存世界根后重新激活时，按当前 Context 恢复输入与暂停状态。 */
onActivated(() => {
  overlayStore.syncWorldState()
})

/** 兼容路由临时离开世界时，冻结缓存中的 Phaser 实例，避免后台继续接收输入。 */
onDeactivated(() => {
  gameBridge.emit('cmd:clear-input', {})
  gameBridge.emit('cmd:set-pause-level', { level: 2 })
})
</script>

<template>
  <ExploreView />
</template>
