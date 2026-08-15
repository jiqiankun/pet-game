import { defineStore } from 'pinia'
import { ref } from 'vue'
import { gameBridge } from '../game/bridge/GameBridge'

/**
 * Overlay 管理（主场景常驻 + 功能层叠加架构）。
 * <p>
 * 统一 Overlay 栈：管理所有游戏内浮层（战斗、队伍、背包、宠物、图鉴、任务、地图、商店、NPC 对话等）。
 * 打开浮层时按类型下发地图暂停等级（PauseLevel），关闭时恢复探索；地图实例不销毁、
 * 玩家坐标/相机不丢失。
 * <p>
 * 遵循架构边界：Overlay 只负责 UI 生命周期与地图暂停/恢复，不承载任何业务计算。
 */

/** Overlay 类型全集（P0 建立；后续按阶段接入各 Overlay）。 */
export type OverlayType =
  | 'BATTLE'
  | 'REWARD'
  | 'QUICK_TEAM'
  | 'TEAM'
  | 'PET'
  | 'INVENTORY'
  | 'POKEDEX'
  | 'QUEST'
  | 'WORLD_MAP'
  | 'NPC_DIALOG'
  | 'SHOP'
  | 'SETTINGS'
  | 'ACHIEVEMENT'
  | 'STATISTICS'
  | 'WAREHOUSE'
  | 'PET_STORAGE'
  | 'SAVE_BACKUP'
  | 'BOSS'

/** 地图暂停等级（GamePauseLevel）：0=不暂停；1=锁玩家输入；2=暂停探索逻辑；3=战斗锁定。 */
export type GamePauseLevel = 0 | 1 | 2 | 3

/** 各 Overlay 类型默认暂停等级。 */
const PAUSE_LEVEL_BY_TYPE: Record<OverlayType, GamePauseLevel> = {
  BATTLE: 3,
  REWARD: 0,
  QUICK_TEAM: 2,
  TEAM: 2,
  PET: 2,
  INVENTORY: 2,
  POKEDEX: 2,
  QUEST: 2,
  WORLD_MAP: 2,
  NPC_DIALOG: 1,
  SHOP: 2,
  SETTINGS: 2,
  ACHIEVEMENT: 2,
  STATISTICS: 2,
  WAREHOUSE: 2,
  PET_STORAGE: 2,
  SAVE_BACKUP: 2,
  BOSS: 2,
}

/** Overlay 条目。 */
export interface OverlayEntry {
  type: OverlayType
  /** 唯一 id（自增）。 */
  id: number
  /** 打开参数（如 BATTLE 传 { encounterSpawnId }；FEATURE 传 { feature }）。 */
  data?: unknown
  /** 地图暂停等级（缺省按类型默认值）。 */
  pauseLevel?: GamePauseLevel
}

export const useOverlayStore = defineStore('overlay', () => {
  const stack = ref<OverlayEntry[]>([])
  let seq = 0

  /** 当前最上层 Overlay。 */
  const top = ref<OverlayEntry | null>(null)

  function recomputeTop() {
    top.value = stack.value.length > 0 ? stack.value[stack.value.length - 1]! : null
    // 按栈顶类型下发地图暂停等级（玩家位置不变，主场景常驻）
    const level = top.value ? (top.value.pauseLevel ?? PAUSE_LEVEL_BY_TYPE[top.value.type]) : 0
    gameBridge.emit('cmd:set-pause-level', { level })
  }

  /** 打开一个 Overlay，压栈并统一下发地图暂停命令。 */
  function open(type: OverlayType, data?: unknown, pauseLevel?: GamePauseLevel): OverlayEntry {
    const entry: OverlayEntry = { type, id: ++seq, data, pauseLevel }
    // 同一类型不重复叠加：关闭已有的同类型最上层后再压入
    const idx = stack.value.findIndex((e) => e.type === type)
    if (idx >= 0) {
      stack.value.splice(idx, 1)
    }
    stack.value.push(entry)
    recomputeTop()
    return entry
  }

  /** 关闭指定类型的最上层 Overlay。 */
  function close(type: OverlayType) {
    const idx = stack.value.findIndex((e) => e.type === type)
    if (idx >= 0) {
      stack.value.splice(idx, 1)
    }
    recomputeTop()
  }

  /** 关闭最上层 Overlay。 */
  function closeTop() {
    stack.value.pop()
    recomputeTop()
  }

  /** 清空全部 Overlay。 */
  function closeAll() {
    stack.value = []
    recomputeTop()
  }

  /** 指定类型是否处于打开状态。 */
  function isOpen(type: OverlayType): boolean {
    return stack.value.some((e) => e.type === type)
  }

  /**
   * 统一返回行为：返回 = 只关闭最上层 Overlay。
   * 战斗（BATTLE）不通过返回键退出，避免误触中断战斗。
   * @returns 是否消费了本次返回（有浮层被关闭返回 true）。
   */
  function handleBack(): boolean {
    const t = stack.value[stack.value.length - 1]
    if (!t) return false
    if (t.type === 'BATTLE') return false
    closeTop()
    return true
  }

  return {
    stack,
    top,
    open,
    close,
    closeTop,
    closeAll,
    isOpen,
    handleBack,
  }
})