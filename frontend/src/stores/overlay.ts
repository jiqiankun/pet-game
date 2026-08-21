import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
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
  | 'REGION_MAP'
  | 'NPC_DIALOG'
  | 'SHOP'
  | 'SETTINGS'
  | 'ACHIEVEMENT'
  | 'STATISTICS'
  | 'WAREHOUSE'
  | 'PET_STORAGE'
  | 'SAVE_BACKUP'
  | 'BOSS'
  | 'ENCOUNTER_CONFIRM'
  | 'CAMP_CONFIRM'
  | 'EXIT_CONFIRM'
  | 'RANDOM_EVENT'
  | 'EVENT_RESULT'

/** 地图暂停等级（GamePauseLevel）：0=不暂停；1=锁玩家输入；2=暂停探索逻辑；3=战斗锁定。 */
export type GamePauseLevel = 0 | 1 | 2 | 3

/** 世界阻塞语义：NONE 不阻塞；INPUT 只清空输入；WORLD 停止探索；BATTLE 锁定战斗上下文。 */
export type WorldBlockLevel = 'NONE' | 'INPUT' | 'WORLD' | 'BATTLE'

/** 当前键盘应服务的上下文。只有 WORLD 允许 Phaser 接收移动与交互键。 */
export type InputContext = 'WORLD' | 'PANEL' | 'DIALOG' | 'BATTLE' | 'TEXT_INPUT'

/** Context 的关闭策略。EXPLICIT 只能由业务内明确操作关闭，Esc/遮罩/浏览器返回均只消费事件。 */
export type OverlayClosePolicy = 'ESCAPE' | 'EXPLICIT'

/** Context 来源，用于追踪进入路径和关闭后的焦点恢复。 */
export type OverlaySource = 'HUD' | 'SHORTCUT' | 'WORLD' | 'CONTEXT' | 'ROUTE' | 'SYSTEM'

interface ContextSpec {
  inputContext: Exclude<InputContext, 'TEXT_INPUT'>
  blockLevel: WorldBlockLevel
  closePolicy: OverlayClosePolicy
}

const PANEL: ContextSpec = { inputContext: 'PANEL', blockLevel: 'WORLD', closePolicy: 'ESCAPE' }
const DIALOG: ContextSpec = { inputContext: 'DIALOG', blockLevel: 'WORLD', closePolicy: 'ESCAPE' }

/** 每种 Context 的默认交互语义；不再由无含义的数字散落在调用方决定。 */
const CONTEXT_SPEC_BY_TYPE: Record<OverlayType, ContextSpec> = {
  BATTLE: { inputContext: 'BATTLE', blockLevel: 'BATTLE', closePolicy: 'EXPLICIT' },
  REWARD: { inputContext: 'WORLD', blockLevel: 'NONE', closePolicy: 'ESCAPE' },
  QUICK_TEAM: PANEL,
  TEAM: PANEL,
  PET: PANEL,
  INVENTORY: PANEL,
  POKEDEX: PANEL,
  QUEST: PANEL,
  WORLD_MAP: PANEL,
  REGION_MAP: PANEL,
  NPC_DIALOG: DIALOG,
  SHOP: PANEL,
  SETTINGS: PANEL,
  ACHIEVEMENT: PANEL,
  STATISTICS: PANEL,
  WAREHOUSE: PANEL,
  PET_STORAGE: PANEL,
  SAVE_BACKUP: PANEL,
  BOSS: PANEL,
  ENCOUNTER_CONFIRM: DIALOG,
  CAMP_CONFIRM: DIALOG,
  EXIT_CONFIRM: DIALOG,
  RANDOM_EVENT: { inputContext: 'DIALOG', blockLevel: 'WORLD', closePolicy: 'EXPLICIT' },
  EVENT_RESULT: DIALOG,
}

const PAUSE_LEVEL_BY_BLOCK_LEVEL: Record<WorldBlockLevel, GamePauseLevel> = {
  NONE: 0,
  INPUT: 1,
  WORLD: 2,
  BATTLE: 3,
}

/** 打开 Context 时可覆盖的元数据。 */
export interface OverlayOpenOptions {
  parentId?: number | null
  source?: OverlaySource
  closePolicy?: OverlayClosePolicy
  blockLevel?: WorldBlockLevel
  inputContext?: Exclude<InputContext, 'TEXT_INPUT'>
  triggerElement?: HTMLElement | null
  returnData?: unknown
}

/** Overlay 条目。 */
export interface OverlayEntry {
  type: OverlayType
  /** 唯一 id（自增）。 */
  id: number
  /** 可用于日志、DOM 和精确关闭的唯一实例键。 */
  key: string
  /** 打开参数（如 BATTLE 传 { encounterSpawnId }；FEATURE 传 { feature }）。 */
  data?: unknown
  /** 打开时的父 Context；关闭子层后由栈自然恢复父层。 */
  parentId: number | null
  /** Context 来源。 */
  source: OverlaySource
  /** 阻塞世界的语义等级。 */
  blockLevel: WorldBlockLevel
  /** 当前输入应服务的 Context。 */
  inputContext: Exclude<InputContext, 'TEXT_INPUT'>
  /** 是否允许 Esc / 遮罩关闭。 */
  closePolicy: OverlayClosePolicy
  /** 可选返回数据，供后续上下文消费；当前不承载业务事件。 */
  returnData?: unknown
  /** 打开前获得焦点的元素；回到世界时用于恢复焦点。 */
  triggerElement: HTMLElement | null
}

export const useOverlayStore = defineStore('overlay', () => {
  const stack = ref<OverlayEntry[]>([])
  let seq = 0
  const textInputFocused = ref(false)

  /** 当前最上层 Overlay。 */
  const top = computed<OverlayEntry | null>(() => stack.value[stack.value.length - 1] ?? null)
  const inputContext = computed<InputContext>(() => (
    textInputFocused.value ? 'TEXT_INPUT' : (top.value?.inputContext ?? 'WORLD')
  ))
  const worldPauseLevel = computed<GamePauseLevel>(() => {
    const contextLevel = top.value ? PAUSE_LEVEL_BY_BLOCK_LEVEL[top.value.blockLevel] : 0
    return textInputFocused.value ? Math.max(contextLevel, 1) as GamePauseLevel : contextLevel
  })

  function currentTrigger(): HTMLElement | null {
    const active = document.activeElement
    if (active instanceof HTMLElement && active !== document.body) return active
    return document.querySelector<HTMLElement>('[data-world-focus-root]')
  }

  /** 将栈顶的阻塞语义同步给 Phaser；场景尚未创建时可由 WorldRoot 在激活后再次同步。 */
  function syncWorldState() {
    gameBridge.emit('cmd:set-pause-level', { level: worldPauseLevel.value })
    if (worldPauseLevel.value > 0) gameBridge.emit('cmd:clear-input', {})
  }

  function restoreFocus(entry: OverlayEntry) {
    if (stack.value.length > 0) return
    requestAnimationFrame(() => {
      const target = entry.triggerElement?.isConnected
        ? entry.triggerElement
        : document.querySelector<HTMLElement>('[data-world-focus-root]')
      target?.focus()
    })
  }

  function closeAt(index: number, returnData?: unknown): boolean {
    if (index < 0) return false
    const entry = stack.value[index]!
    const wasTop = index === stack.value.length - 1
    if (returnData !== undefined) entry.returnData = returnData
    stack.value.splice(index, 1)
    syncWorldState()
    if (wasTop) restoreFocus(entry)
    return true
  }

  /** 打开一个 Context，记录来源/父层/焦点并根据语义冻结世界。 */
  function open(type: OverlayType, data?: unknown, options: OverlayOpenOptions = {}): OverlayEntry {
    const spec = CONTEXT_SPEC_BY_TYPE[type]
    const id = ++seq
    const entry: OverlayEntry = {
      type,
      id,
      key: `${type}:${id}`,
      data,
      parentId: options.parentId ?? top.value?.id ?? null,
      source: options.source ?? 'SYSTEM',
      blockLevel: options.blockLevel ?? spec.blockLevel,
      inputContext: options.inputContext ?? spec.inputContext,
      closePolicy: options.closePolicy ?? spec.closePolicy,
      returnData: options.returnData,
      triggerElement: options.triggerElement ?? currentTrigger(),
    }
    stack.value.push(entry)
    syncWorldState()
    return entry
  }

  /**
   * 按实例 id 或类型关闭 Context。
   * 类型关闭始终命中该类型最靠上的实例，避免旧实现误删数组中第一个同类条目。
   */
  function close(target: number | OverlayType, returnData?: unknown): boolean {
    if (typeof target === 'number') {
      return closeAt(stack.value.findIndex((entry) => entry.id === target), returnData)
    }
    for (let index = stack.value.length - 1; index >= 0; index--) {
      if (stack.value[index]!.type === target) return closeAt(index, returnData)
    }
    return false
  }

  /** 关闭最上层 Overlay。 */
  function closeTop(returnData?: unknown): boolean {
    return closeAt(stack.value.length - 1, returnData)
  }

  /** 原子替换栈顶 Context，保留原始返回焦点且不触发中间的世界焦点恢复。 */
  function replaceTop(type: OverlayType, data?: unknown, options: OverlayOpenOptions = {}): OverlayEntry {
    const previous = top.value
    if (!previous) return open(type, data, options)
    stack.value.pop()
    return open(type, data, {
      ...options,
      parentId: options.parentId ?? previous.parentId,
      triggerElement: options.triggerElement ?? previous.triggerElement,
    })
  }

  /** 清空全部 Overlay。 */
  function closeAll() {
    const last = top.value
    stack.value = []
    syncWorldState()
    if (last) restoreFocus(last)
  }

  /** 指定类型是否处于打开状态。 */
  function isOpen(type: OverlayType): boolean {
    return stack.value.some((e) => e.type === type)
  }

  /** 文本输入获得焦点时，不再向世界透传快捷键或持续移动。 */
  function setTextInputFocused(focused: boolean) {
    if (textInputFocused.value === focused) return
    textInputFocused.value = focused
    syncWorldState()
  }

  /**
   * 统一返回行为：返回 = 只关闭最上层 Overlay。
   * 不允许返回的 Context（如战斗、不可取消事件）仍会消费事件，防止浏览器退回或路由跳转。
   * @returns 是否消费了本次返回。
   */
  function handleBack(): boolean {
    const t = top.value
    if (!t) return false
    if (t.closePolicy === 'EXPLICIT') return true
    closeTop()
    return true
  }

  return {
    stack,
    top,
    inputContext,
    worldPauseLevel,
    textInputFocused,
    open,
    close,
    closeTop,
    replaceTop,
    closeAll,
    isOpen,
    setTextInputFocused,
    syncWorldState,
    handleBack,
  }
})
