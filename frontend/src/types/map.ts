/**
 * 地图探索与区域系统类型定义（阶段 6），与后端 DTO 对齐。
 */

/** 大地图视图（GET /api/maps/world）。 */
export interface WorldMapView {
  currentMapId: string
  regions: RegionView[]
  /** 已激活永久地图变更 ID（与后端 DTO 对齐）。 */
  activatedMapChanges: string[]
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
  /** 已激活永久地图变更 ID（与后端 DTO 对齐）。 */
  activatedMapChanges: string[]
}

/** 世界图谱视图（GET /api/world），阶段 2：World → Region → Map，按玩家知识过滤。 */
export interface WorldView {
  worldId: string
  name: string
  currentMapId: string
  /** 世界状态版本（图谱变更时递增，前端据此刷新投影）。 */
  worldVersion: number
  maps: MapNodeView[]
}

/** WorldGraph 地图节点视图。 */
export interface MapNodeView {
  mapId: string
  regionId: string
  name: string
  /** BASE 起始据点 / AREA 主要区域。 */
  type: string
  recommendedLevel: string
  /** 地图职责描述。 */
  mapRole?: string
  /** 出生锚点对象 ID。 */
  spawnAnchorId: string
  /** 安全区锚点（营地 / 出生点对象 ID）。 */
  safeZoneAnchorIds: string[]
  current: boolean
  /** 是否已发现（知识状态）。 */
  discovered: boolean
  outgoing: ConnectionView[]
}

export interface ConnectionView {
  connectionId: string
  fromMapId: string
  toMapId: string
  fromGatewayId: string
  toGatewayId: string
  name: string
  hidden: boolean
  shortcut: boolean
  oneWay: boolean
}

/** 当前精确位置（GET /api/world/current、POST /api/world/position）。 */
export interface CurrentLocationView {
  mapId: string
  regionId: string
  posX: number | null
  posY: number | null
  facing: string | null
  /** 相机/安全回退锚点对象 ID。 */
  safeAnchorId: string
  /** 地图出生锚点对象 ID。 */
  spawnAnchorId: string
  worldVersion: number
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
