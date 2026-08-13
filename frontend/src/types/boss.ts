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
