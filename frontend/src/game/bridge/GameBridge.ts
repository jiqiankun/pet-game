/**
 * Vue ↔ Phaser 事件桥接（阶段 6）。
 * <p>
 * Phaser 只做展示与交互：通过 bridge 向 Vue 层发送事件（遭遇接触、采集请求等），
 * Vue 层调用后端后通过 bridge 命令回传结果（移除对象、重启地图等）。
 * Phaser 场景中禁止直接操作 Pinia / 调用后端 API（架构边界）。
 */

/** 地图场景初始化数据（Vue → Phaser）。 */
export interface MapSceneData {
  mapId: string
  /** Tiled 地图资源文件名（对应 public/assets/maps/{mapFile}.json）。 */
  mapFile: string
  /** 玩家落点对象 ID（Tiled 对象名）。 */
  spawnObjectId: string
  /** 已开启的隐藏宝箱（全局一次性）。 */
  consumedChestIds: string[]
  /** 本次会话已采集的采集点。 */
  usedGatherIds: string[]
  /** 已激活营地 ID。 */
  activatedCampIds: string[]
  /** 本次会话已被击败/遭遇移除的野怪刷新点。 */
  defeatedWildIds: string[]
}

export interface IdPayload {
  id: string
}

export interface WildTouchPayload {
  spawnId: string
  groupId: string
  behavior: string
  /** 精英个体标记（阶段 10，可选）。 */
  elite?: boolean
}

export interface ExitTouchPayload {
  exitId: string
  targetMapId: string
}

export interface InputLockPayload {
  locked: boolean
}

/** 全部桥接事件类型约定。 */
export interface BridgeEventMap {
  // ---- Phaser → Vue（交互事件） ----
  /** 接触野生宠物（可见野怪；接触前 Vue 允许调整首发）。 */
  'encounter:touch': WildTouchPayload
  /** 请求采集（E 键交互）。 */
  'gather:request': IdPayload
  /** 请求开启宝箱（E 键交互）。 */
  'chest:request': IdPayload
  /** 接触/交互营地（E 键交互）。 */
  'camp:touch': IdPayload
  /** 到达出口（自动触发）。 */
  'exit:touch': ExitTouchPayload
  /** Boss 入口占位交互（阶段 7 启用）。 */
  'boss:touch': IdPayload
  /** NPC 占位交互（阶段 9 启用）。 */
  'npc:touch': IdPayload
  /** 隐藏点占位交互（阶段 10 完善）。 */
  'hidden:touch': IdPayload

  // ---- Vue → Phaser（命令） ----
  /** 重启地图场景（区域切换 / 营地休息刷新后）。 */
  'cmd:restart-map': MapSceneData
  /** 移除野怪刷新点（战斗胜利后）。 */
  'cmd:remove-wild': IdPayload
  /** 移除地图对象（采集点/宝箱消耗后）。 */
  'cmd:remove-object': IdPayload
  /** 锁定/解锁输入（Vue 弹层打开时暂停移动与交互）。 */
  'cmd:set-input-lock': InputLockPayload
}

type Handler<T> = (payload: T) => void

class GameBridge {
  private handlers = new Map<string, Set<Handler<unknown>>>()

  /** 订阅事件，返回取消订阅函数。 */
  on<K extends keyof BridgeEventMap>(event: K, handler: Handler<BridgeEventMap[K]>): () => void {
    const set = this.handlers.get(event) ?? new Set()
    set.add(handler as Handler<unknown>)
    this.handlers.set(event, set)
    return () => this.off(event, handler)
  }

  off<K extends keyof BridgeEventMap>(event: K, handler: Handler<BridgeEventMap[K]>): void {
    this.handlers.get(event)?.delete(handler as Handler<unknown>)
  }

  emit<K extends keyof BridgeEventMap>(event: K, payload: BridgeEventMap[K]): void {
    this.handlers.get(event)?.forEach((handler) => handler(payload))
  }
}

/** 全局事件桥（Vue 与 Phaser 共享同一实例）。 */
export const gameBridge = new GameBridge()
