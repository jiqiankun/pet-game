/**
 * 阶段 5 宠物仓库相关类型（与后端 PetStorageService DTO 一一对应）。
 */

/** 仓库查询请求（全部条件可选）。 */
export interface StorageQueryRequest {
  name?: string
  element?: string
  rarity?: string
  levelMin?: number | null
  levelMax?: number | null
  aptitudeMin?: number | null
  hasRareSkill?: boolean | null
  hasSpecialAppearance?: boolean | null
  favoriteOnly?: boolean | null
  lockedOnly?: boolean | null
  /** 是否在队伍（true/false 筛选，null 不过滤）。 */
  inTeam?: boolean | null
  /** LEVEL / RARITY / APTITUDE / CAPTURED_AT（默认）。 */
  sortBy?: string
  /** ASC / DESC（默认）。 */
  sortDirection?: string
}

/** 仓库宠物视图。 */
export interface StoragePetView {
  petId: number
  speciesId: string
  /** 种族名称（始终保留，昵称叠加展示）。 */
  speciesName: string
  nickname: string | null
  element: string
  rarity: string
  level: number
  capturedLevel: number
  /** 综合资质（六维总和）。 */
  aptitudeTotal: number
  /** 平均资质（保留 1 位小数）。 */
  aptitudeAverage: number
  locked: boolean
  favorite: boolean
  inTeam: boolean
  starter: boolean
  specialAppearance: string | null
  capturedMapId: string | null
  capturedAt: string | null
  currentHp: number
  rareSkillIds: string[]
}

/** 放生预览（单只信息）。 */
export interface PetReleaseInfo {
  petId: number
  name: string
  rarity: string
  releasable: boolean
  /** 保护原因：LOCKED / FAVORITE / IN_TEAM。 */
  blockReasons: string[]
  /** 额外警告原因：HIGH_RARITY / HIGH_APTITUDE / RARE_SKILL / SPECIAL_APPEARANCE。 */
  warningReasons: string[]
  giftPoints: number
}

/** 放生预览结果。 */
export interface ReleasePreview {
  pets: PetReleaseInfo[]
  totalGiftPoints: number
}

/** 单个礼物结果。 */
export interface GiftResult {
  type: 'GOLD' | 'EXP' | 'ITEM' | string
  itemId: string | null
  quantity: number
  value: number
}

/** 放生结果。 */
export interface ReleaseResult {
  released: {
    petId: number
    speciesId: string
    name: string
    level: number
    giftPoints: number
  }[]
  skipped: {
    petId: number
    reason: string
  }[]
  totalGiftPoints: number
  gifts: GiftResult[]
}
