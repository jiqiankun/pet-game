/**
 * 地图探索与区域系统类型定义（阶段 6），与后端 DTO 对齐。
 */

/** 大地图视图（GET /api/maps/world）。 */
export interface WorldMapView {
  currentMapId: string
  regions: RegionView[]
}

export interface RegionView {
  mapId: string
  name: string
  /** BASE 起始据点 / AREA 主要区域。 */
  type: string
  recommendedLevel: string
  unlocked: boolean
  current: boolean
  /** Boss 状态占位（阶段 7 启用，本阶段恒为 NOT_OPEN）。 */
  bossStatus: string
  camps: CampView[]
}

export interface CampView {
  campId: string
  name: string
  activated: boolean
}

/** 进入区域结果（POST /api/maps/{mapId}/enter、GET /api/maps/current）。 */
export interface MapEnterView {
  mapId: string
  name: string
  /** Tiled 地图资源文件名。 */
  mapFile: string
  /** 本次访问会话 ID（刷新判定用）。 */
  sessionId: string
  /** 落点对象 ID（Tiled 对象名）。 */
  spawnObjectId: string
  consumedChestIds: string[]
  usedGatherIds: string[]
  activatedCampIds: string[]
}

/** 营地休息结果（POST /api/maps/camps/{campId}/rest）。 */
export interface CampRestView {
  campId: string
  mapId: string
  firstActivation: boolean
  healedPets: number
  sessionId: string
}

/** 采集/宝箱奖励结果。 */
export interface RewardResultView {
  objectName: string
  goldGained: number
  items: ItemReward[]
}

export interface ItemReward {
  itemId: string
  name: string
  quantity: number
}

/** 战败流程结果（需求 §44：零惩罚），随战斗结算返回。 */
export interface DefeatView {
  /** 轻度嘲讽式提示。 */
  message: string
  respawnMapId: string
  respawnObjectId: string
  healedPets: number
}
