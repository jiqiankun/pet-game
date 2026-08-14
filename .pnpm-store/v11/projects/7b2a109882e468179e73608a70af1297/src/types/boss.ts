/**
 * Boss 系统类型定义（阶段 7）。
 */

export interface BossInfo {
  bossId: string
  name: string
  mapId: string
  element: string
  recommendedLevel: number
  luckValue: number
  difficulties: DifficultyInfo[]
}

export interface DifficultyInfo {
  difficulty: 'NORMAL' | 'HARD' | 'NIGHTMARE'
  unlocked: boolean
  defeatCount: number
  dropInfo: DropTierInfo[]
}

export interface DropTierInfo {
  rarity: 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
  unlocked: boolean
  items: DropItemInfo[]
}

export interface DropItemInfo {
  itemId: string
  qty: number
  chance: number
  exchangeQty: number
}

export interface AutoChallengeResult {
  totalBattles: number
  wins: number
  losses: number
  totalExp: number
  totalGold: number
  totalDrops: Array<{ itemId: string; qty: number; rarity: string }>
  finalLuck: number
}

/** 已持久化的 Boss 遭遇快照（阶段 13）。 */
export interface BossEncounterUnit {
  unitId: string
  speciesId?: string | null
  name: string
  element: string
  role: string
  level: number
  maxHp: number
  strength: number
  spirit: number
  defense: number
  resistance: number
  speed: number
}

export interface BossEncounterSnapshot {
  snapshotId: number
  gameDifficulty: string
  currentGameDifficulty: string
  difficultyMismatch: boolean
  canReset: boolean
  generatedLevel: number
  playerLevelCap: number
  bossAiLevel: number
  snapshotVersion: number
  locked: boolean
  defeated: boolean
  units: BossEncounterUnit[]
}

// ==================== Boss 挑战目标（阶段 11） ====================

/** 单个 Boss 挑战目标。 */
export interface BossChallengeItem {
  challengeId: string
  type: string
  name: string
  description: string
  value: number
  completed: boolean
  achievementId: string | null
}

/** 某 Boss 的一组挑战目标。 */
export interface BossChallengeGroup {
  bossId: string
  completionTitleId: string | null
  allCompleted: boolean
  challenges: BossChallengeItem[]
}

/** 挑战目标类型中文名。 */
export const CHALLENGE_TYPE_LABELS: Record<string, string> = {
  TURN_LIMIT: '速战速决',
  NO_RECOVERY_ITEM: '不用恢复道具',
  NO_PET_FAINTED: '无宠物倒下',
  MULTI_ELEMENT: '多属性阵容',
}
