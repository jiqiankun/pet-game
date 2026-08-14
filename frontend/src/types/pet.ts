/**
 * 阶段 4 前后端类型定义（与后端 DTO 一一对应）。
 * 前端只提交意图，所有计算在后端完成。
 */

import type { DefeatView } from './map'

// ==================== 宠物详情 ====================

/** 单维度属性分解。 */
export interface StatBreakdown {
  base: number
  growth: number
  aptBonus: number
  freeBonus: number
  total: number
}

/** 面板属性（含六维最终值与分解明细）。 */
export interface PetPanelStats {
  maxHp: number
  strength: number
  spirit: number
  defense: number
  resistance: number
  speed: number
  breakdowns: Record<string, StatBreakdown>
}

/** 种族配置摘要。 */
export interface SpeciesView {
  speciesId: string
  name: string
  element: string
  rarity: string
  description: string
  baseHp: number
  baseStrength: number
  baseSpirit: number
  baseDefense: number
  baseResistance: number
  baseSpeed: number
  aptitudeHp: number
  aptitudeStrength: number
  aptitudeSpirit: number
  aptitudeDefense: number
  aptitudeResistance: number
  aptitudeSpeed: number
}

/** 已学习技能视图。 */
export interface LearnedSkillView {
  skillId: string
  name: string
  element: string
  damageType: string
  effectType: string
  cooldown: number
  /** 装备槽位 1~4，null 表示已学习但未装备。 */
  slot: number | null
  sourceType: string
  /** 技能类型（REV-016）：ACTIVE / PASSIVE。 */
  skillType?: string
  /** 是否特色/专属技能（REV-016）。 */
  signature?: boolean
}

/** 待解锁技能视图。 */
export interface AvailableSkillView {
  skillId: string
  name: string
  element: string
  unlockLevel: number
}

/** 被动技能视图（REV-016：全部自动生效、无携带上限）。 */
export interface PassiveSkillView {
  passiveId: string
  name: string
  unlockLevel: number
  /** 当前等级是否已解锁。 */
  unlocked: boolean
  /** 来源标识：INNATE 自身 / BOOK 技能书 / SPECIAL 特殊（预留）。 */
  source: string
  signature: boolean
}

/** 宠物详情（基础 / 属性 / 技能三标签一次返回）。 */
export interface PetDetail {
  pet: PlayerPetEntity
  species: SpeciesView
  panelStats: PetPanelStats
  learnedSkills: LearnedSkillView[]
  availableSkills: AvailableSkillView[]
  /** 被动技能列表（REV-016）。 */
  passives: PassiveSkillView[]
  /** 种族自身主动技能总数（展示「已掌握 X / N」）。 */
  totalInnateActiveSkills: number
  /** 本次操作新学会的主动技能名称。 */
  newlyLearnedSkillNames: string[]
  /** 新技能因槽位已满未能自动装备（REV-011 提示）。 */
  skillEquipOverflow: boolean
  /** 技能书主动技能装备槽（阶段 10，槽位 5~6）。 */
  bookSkillSlots: LearnedSkillView[]
  /** 已学习的技能书技能列表（阶段 10）。 */
  learnedBookSkills: LearnedSkillView[]
  /** 已学技能书主动技能数量（阶段 10，/10）。 */
  bookSkillLearnCount: number
  expPool: number
  /** 已消耗自由点数（按需求 §20 转换表折算：速度每点次 2 点，其余 1 点）。 */
  allocatedFreePoints: number
  /** 剩余可分配自由点数 = 已获得 - 已消耗。 */
  freePointsAvailable: number
  expToNextLevel: number
}

/** 玩家宠物存档实体（与后端 PlayerPetEntity 对应）。 */
export interface PlayerPetEntity {
  id: number
  saveId: string
  speciesId: string
  nickname: string
  level: number
  capturedLevel: number
  hpAptitude: number
  strengthAptitude: number
  spiritAptitude: number
  defenseAptitude: number
  resistanceAptitude: number
  speedAptitude: number
  freePointHp: number
  freePointStrength: number
  freePointSpirit: number
  freePointDefense: number
  freePointResistance: number
  freePointSpeed: number
  currentHp: number
  isStarter: boolean
  specialAppearance: string | null
  locked: boolean
  favorite: boolean
  capturedMapId: string | null
  battleCount: number
  winCount: number
}

// ==================== 升级预览 ====================

/** 解锁技能信息（REV-013：区分主动/被动）。 */
export interface UnlockedSkill {
  skillId: string
  unlockLevel: number
  /** ACTIVE（主动）/ PASSIVE（被动）。 */
  skillType?: string
  name?: string
}

/** 升级预览结果。 */
export interface LevelUpPreview {
  fromLevel: number
  toLevel: number
  expRequired: number
  pointsGained: number
  skillsUnlocked: UnlockedSkill[]
  beforeStats: PetPanelStats
  afterStats: PetPanelStats
  expPoolAvailable: number
  expPoolSufficient: boolean
}

// ==================== 队伍 ====================

/** 队伍成员视图。 */
export interface TeamMemberView {
  memberId: number
  petId: number
  position: number
  speciesId: string
  nickname: string
  level: number
  currentHp: number
  isStarter: boolean
}

/** 队伍视图。 */
export interface TeamView {
  teamId: number
  name: string
  slot: number
  isActive: boolean
  members: TeamMemberView[]
}

/** 队伍预设视图（阶段 6：5 套预设）。 */
export interface TeamPresetView {
  teamId: number
  slot: number
  name: string
  isActive: boolean
  members: TeamMemberView[]
}

/** 成员条目（前端提交）。 */
export interface TeamMemberEntry {
  petId: number
  position: number
}

/** 宠物装备技能摘要（Bootstrap petSummaries）。 */
export interface EquippedSkillSummary {
  skillId: string
  name: string
  slot: number
}

/** 宠物摘要（Bootstrap：种族信息 + 装备技能，队伍页技能查看用）。 */
export interface PetSummaryView {
  pet: {
    id: number
    speciesId: string
    nickname: string | null
    level: number
    currentHp: number
  }
  speciesName: string
  element: string
  rarity: string
  equippedSkills: EquippedSkillSummary[]
}

// ==================== 背包 ====================

/** 背包道具视图。 */
export interface InventoryItemView {
  itemId: string
  name: string
  description: string
  category: string
  itemType: string
  value: number
  usableOutsideBattle: boolean
  usableInBattle: boolean
  discardable: boolean
  quantity: number
}

/** 背包视图。 */
export interface InventoryView {
  items: InventoryItemView[]
  gold: number
}

/** 使用道具结果。 */
export interface UseItemResult {
  itemId: string
  itemName: string
  petId: number
  beforeHp: number
  afterHp: number
  maxHp: number
  remainingQuantity: number
}

// ==================== 战斗结算 ====================

/** 单个掉落结果。 */
export interface DropResult {
  itemId: string
  name: string
  quantity: number
}

/** 单只宠物 HP 回写明细。 */
export interface PetHpWriteback {
  petId: number
  name: string
  beforeHp: number
  afterHp: number
  maxHp: number
  alive: boolean
}

/** 被捕捉宠物摘要（野生战斗结算，阶段 5）。 */
export interface CapturedPetView {
  petId: number
  speciesId: string
  name: string
  rarity: string
  level: number
  specialAppearance: string | null
  extraSkillIds: string[]
  /** 直接入队时的队伍位置（null = 未入队，留在仓库）。 */
  teamPosition: number | null
}

/** 战斗结算结果。 */
export interface BattleSettlement {
  battleId: string
  winner: string
  playerWon: boolean
  /** 玩家是否逃跑成功（同战败结算）。 */
  fled: boolean
  expGained: number
  goldGained: number
  drops: DropResult[]
  /** 本场被捕捉的宠物列表（野生战斗）。 */
  capturedPets: CapturedPetView[]
  hpWritebacks: PetHpWriteback[]
  /** 战败流程结果（阶段 6；玩家战败且未逃跑时非空）。 */
  defeat?: DefeatView
}
