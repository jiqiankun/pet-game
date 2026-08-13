/**
 * 阶段 4 前后端类型定义（与后端 DTO 一一对应）。
 * 前端只提交意图，所有计算在后端完成。
 */

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
}

/** 待解锁技能视图。 */
export interface AvailableSkillView {
  skillId: string
  name: string
  element: string
  unlockLevel: number
}

/** 宠物详情（基础 / 属性 / 技能三标签一次返回）。 */
export interface PetDetail {
  pet: PlayerPetEntity
  species: SpeciesView
  panelStats: PetPanelStats
  learnedSkills: LearnedSkillView[]
  availableSkills: AvailableSkillView[]
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

/** 解锁技能信息。 */
export interface UnlockedSkill {
  skillId: string
  unlockLevel: number
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

/** 成员条目（前端提交）。 */
export interface TeamMemberEntry {
  petId: number
  position: number
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
}
