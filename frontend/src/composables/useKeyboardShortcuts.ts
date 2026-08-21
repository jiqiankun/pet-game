/**
 * 键盘快捷键（Overlay 架构 P3，需求 §41 P3-18）。
 * <p>
 * M/Q/B/J 打开对应功能浮层，复用统一 Overlay 栈。
 * 边界保护：输入框聚焦、战斗打开（Back 保护）、无存档（首页）时不触发。
 * 遵循架构边界：快捷键只负责「打开对应 Overlay」，不承载业务逻辑。
 */
import { onBeforeUnmount, type Ref } from 'vue'
import { useOverlayStore, type OverlayType } from '../stores/overlay'
import { useGameStore } from '../stores/game'
import { isEditableTarget } from '../utils/keyboard'

/** 按键（小写）→ Overlay 类型。 */
const KEY_MAP: Record<string, OverlayType> = {
  q: 'QUICK_TEAM', // Q = 快捷队伍
  b: 'INVENTORY', // B = 背包
  m: 'WORLD_MAP', // M = 大地图
  j: 'QUEST', // J = 任务（S 保留给向下移动）
}

/**
 * 注册全局键盘快捷键。需在组件 setup 中调用（依赖 onBeforeUnmount 清理监听）。
 */
export function useKeyboardShortcuts(enabled: Readonly<Ref<boolean>>) {
  const overlayStore = useOverlayStore()
  const gameStore = useGameStore()

  function onKeydown(e: KeyboardEvent) {
    // 组合键（Ctrl/⌘/Alt）不触发
    if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return
    // 输入框/文本域聚焦时不触发
    if (isEditableTarget(e.target)) return
    // 仅常驻世界且没有阻塞 Context 时接收功能快捷键。
    if (!enabled.value || overlayStore.inputContext !== 'WORLD') return
    // 无存档（首页）不触发
    if (!gameStore.hasSave) return

    const type = KEY_MAP[e.key.toLowerCase()]
    if (!type) return
    e.preventDefault()
    // 仅切换真正栈顶，避免关闭下层同类型 Context。
    if (overlayStore.top?.type === type) {
      overlayStore.closeTop()
    } else {
      overlayStore.open(type, undefined, { source: 'SHORTCUT' })
    }
  }

  window.addEventListener('keydown', onKeydown)
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
}
