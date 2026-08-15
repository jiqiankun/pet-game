/**
 * 键盘快捷键（Overlay 架构 P3，需求 §41 P3-18）。
 * <p>
 * T/B/I/M/Q/P/G/S 打开对应功能浮层，复用统一 Overlay 栈。
 * 边界保护：输入框聚焦、战斗打开（Back 保护）、无存档（首页）时不触发。
 * 遵循架构边界：快捷键只负责「打开对应 Overlay」，不承载业务逻辑。
 */
import { onBeforeUnmount } from 'vue'
import { useOverlayStore, type OverlayType } from '../stores/overlay'
import { useGameStore } from '../stores/game'

/** 按键（小写）→ Overlay 类型。 */
const KEY_MAP: Record<string, OverlayType> = {
  t: 'TEAM', // T = 队伍
  b: 'INVENTORY', // B = 背包
  i: 'INVENTORY', // I = 背包（别名）
  m: 'WORLD_MAP', // M = 大地图
  q: 'QUEST', // Q = 任务
  p: 'PET', // P = 宠物
  g: 'POKEDEX', // G = 图鉴
  s: 'SETTINGS', // S = 设置
}

/** 判定事件目标是否为可编辑节点（输入框/文本域/可编辑区域），编辑时不触发快捷键。 */
function isEditable(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

/**
 * 注册全局键盘快捷键。需在组件 setup 中调用（依赖 onBeforeUnmount 清理监听）。
 */
export function useKeyboardShortcuts() {
  const overlayStore = useOverlayStore()
  const gameStore = useGameStore()

  function onKeydown(e: KeyboardEvent) {
    // 组合键（Ctrl/⌘/Alt）不触发
    if (e.ctrlKey || e.metaKey || e.altKey) return
    // 输入框/文本域聚焦时不触发
    if (isEditable(e.target)) return
    // 战斗打开时忽略（战斗保护，避免误触中断战斗）
    if (overlayStore.isOpen('BATTLE')) return
    // 无存档（首页）不触发
    if (!gameStore.hasSave) return

    const type = KEY_MAP[e.key.toLowerCase()]
    if (!type) return
    e.preventDefault()
    // 上下文记忆（P3-22）：再次按下同一键关闭对应浮层，返回原探索上下文
    if (overlayStore.isOpen(type)) {
      overlayStore.close(type)
    } else {
      overlayStore.open(type)
    }
  }

  window.addEventListener('keydown', onKeydown)
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
}