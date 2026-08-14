/**
 * 图鉴系统类型定义（阶段 8）。
 */

/** 图鉴列表条目（27 种族之一）。 */
export interface PokedexEntry {
  speciesId: string
  /** Lv.0 时为 null。 */
  name: string | null
  /** Lv.0 时为 null。 */
  element: string | null
  /** Lv.2+ 可见。 */
  rarity: string | null
  /** 研究等级 0~5。 */
  researchLevel: number
  /** 累计研究值。 */
  researchPoints: number
  /** 是否已发现。 */
  seen: boolean
  /** 是否已捕获。 */
  caught: boolean
}

/** 图鉴种族详情（按研究等级逐级填充，未解锁字段为 null）。 */
export interface PokedexDetail extends PokedexEntry {
  // Lv.1+
  description?: string | null
  // Lv.2+
  captureRate?: number | null
  // Lv.3+
  skills?: SkillInfo[] | null
  passives?: PassiveInfo[] | null
  baseStats?: Record<string, number> | null
  // Lv.4+
  rareSkills?: string[] | null
  encounterRegions?: string[] | null
  // Lv.5+
  history?: PokedexHistory | null
  specialAppearanceCount?: number | null
  evolutionPlaceholder?: string | null
}

/** 种族技能信息（Lv.3+ 可见）。 */
export interface SkillInfo {
  skillId: string
  skillName: string
  unlockLevel: number
  signature: boolean
}

/** 种族被动信息（Lv.3+ 可见）。 */
export interface PassiveInfo {
  passiveId: string
  passiveName: string
  unlockLevel: number
  signature: boolean
}

/** 图鉴历史记录（Lv.5+ 可见）。 */
export interface PokedexHistory {
  totalCaptures: number
  totalDefeats: number
  eliteEncounters: number
  specialAppearances: number
  bestCombinedAptitude: number
  bestHp: number
  bestStrength: number
  bestSpirit: number
  bestDefense: number
  bestResistance: number
  bestSpeed: number
  discoveredRareSkills: string[]
}

/** Lv.5 野外识别结果。 */
export interface WildIdentification {
  speciesId: string
  /** 资质预估等级：S/A/B/C/D。 */
  gradeLabel: string
}
