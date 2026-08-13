/**
 * 战斗相关类型（与后端 BattleSnapshot / BattleEvent / BattleAction 对应）。
 * 前端只提交行动意图，不计算任何战斗结果。
 */

/** 单位携带状态的展示视图。 */
export interface UnitStatusView {
  statusId: string
  name: string
  category: 'DOT' | 'CONTROL' | 'DEBUFF' | 'BUFF' | string
  remainingTurns: number
}

/** 战斗单位快照。 */
export interface UnitSnapshot {
  unitId: string
  name: string
  element: string
  level: number
  maxHp: number
  currentHp: number
  shield: number
  strength: number
  spirit: number
  defense: number
  resistance: number
  speed: number
  alive: boolean
  active: boolean
  position: number
  defending: boolean
  charging: boolean
  chargingSkillId: string | null
  chargeRemaining: number
  skillIds: string[]
  cooldowns: Record<string, number>
  statuses: UnitStatusView[]
}

/** 战斗事件。 */
export interface BattleEvent {
  type: string
  round: number
  sourceId?: string | null
  targetId?: string | null
  skillId?: string | null
  statusId?: string | null
  value?: number | null
  critical?: boolean | null
  elementRelation?: string | null
  data?: Record<string, unknown>
}

/** 战斗快照。 */
export interface BattleSnapshot {
  battleId: string
  seed: number
  currentRound: number
  finished: boolean
  winner: 'PLAYER' | 'ENEMY' | null
  playerUnits: UnitSnapshot[]
  enemyUnits: UnitSnapshot[]
  events: BattleEvent[]
}

/** 行动意图（前端提交）。 */
export interface BattleAction {
  type: 'SKILL' | 'DEFEND' | 'SWITCH'
  petId: string
  skillId?: string
  targetId?: string
  switchPetId?: string
}

/** 技能配置（前端展示用）。 */
export interface SkillConfigView {
  id: string
  name: string
  description: string
  element: string
  damageType: string
  effectType: string
  target: string
  baseValue: number
  cooldown: number
  accuracy: number
  chargeTurns: number
}

/** 技能配置根对象。 */
export interface SkillsConfigView {
  configVersion: number
  skills: SkillConfigView[]
}
