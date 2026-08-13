/**
 * 战斗相关类型（与后端 BattleSnapshot / BattleEvent / BattleAction 对应）。
 * 前端只提交行动意图，不计算任何战斗结果。
 */

/** 单位携带状态的展示视图（REV-015：五类模型 + 叠层 + 震慑）。 */
export interface UnitStatusView {
  statusId: string
  name: string
  /** CONTINUOUS / BUFF / DEBUFF / SPECIAL_CONTROL / MARK。 */
  category: 'CONTINUOUS' | 'BUFF' | 'DEBUFF' | 'SPECIAL_CONTROL' | 'MARK' | string
  remainingTurns: number
  /** 当前层数（叠层状态，默认 1）。 */
  stack?: number
  /** 是否捕获震慑（安全捕捉窗口，需求 §142）。 */
  captureStun?: boolean
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
  /** 是否已被捕捉（野生战斗，阶段 5）。 */
  captured: boolean
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
  /** 战斗类型：TEST / WILD / BOSS。 */
  battleType: 'TEST' | 'WILD' | 'BOSS' | string
  seed: number
  currentRound: number
  finished: boolean
  winner: 'PLAYER' | 'ENEMY' | null
  /** 玩家是否逃跑成功（野生战斗，同战败结算）。 */
  fled: boolean
  /** Boss 战斗禁止捕捉（阶段 7）。 */
  uncapturable?: boolean
  playerUnits: UnitSnapshot[]
  enemyUnits: UnitSnapshot[]
  events: BattleEvent[]
}

/** 行动意图（前端提交）。 */
export interface BattleAction {
  type: 'SKILL' | 'DEFEND' | 'SWITCH' | 'CAPTURE' | 'FLEE'
  petId: string
  skillId?: string
  targetId?: string
  switchPetId?: string
  /** CAPTURE 行动使用的捕捉球道具 ID。 */
  itemId?: string
}

/** 捕捉率视图（野生战斗，后端计算）。 */
export interface CaptureRateView {
  unitId: string
  unitName: string
  ballItemId: string
  ballName: string
  rate: number
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
