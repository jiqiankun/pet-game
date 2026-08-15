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
  /** 真实等级（高难 Boss 战可能高于有效等级）。 */
  actualLevel: number
  /** 本场参与战斗计算的等级。 */
  effectiveLevel: number
  /** 展示资源类型：PET=宠物、BOSS=Boss 核心、null=无资源（测试敌人）。 */
  artType?: 'PET' | 'BOSS' | null
  /** 展示资源 ID：PET 对应 speciesId，BOSS 对应 Boss ID；无资源时为 null。 */
  artId?: string | null
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
  /** 是否精英个体（阶段 10）。 */
  elite: boolean
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
  gameDifficulty?: string | null
  bossSnapshotId?: number | null
  playerLevelCap?: number | null
  playerUnits: UnitSnapshot[]
  enemyUnits: UnitSnapshot[]
  events: BattleEvent[]
  /** 伤害明细调试开关（阶段 14）。 */
  debugDamage?: boolean
  /** 本次战斗已录制的随机数序列（debugDamage 开启时返回）。 */
  debugRandomDraws?: string[]
}

/** 行动意图（前端提交）。 */
export interface BattleAction {
  type: 'SKILL' | 'DEFEND' | 'SWITCH' | 'CAPTURE' | 'FLEE' | 'ITEM'
  petId: string
  skillId?: string
  targetId?: string
  switchPetId?: string
  /** CAPTURE / ITEM 行动使用的道具 ID。 */
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
  /** 稀有度：NORMAL / RARE / EXCLUSIVE（可选）。 */
  rarity?: string
  /** 技能来源：INNATE（自身，展示用，可选）。 */
  source?: string
  /** 技能类型：ACTIVE / PASSIVE（展示用，可选）。 */
  skillType?: string
  damageType: string
  effectType: string
  target: string
  baseValue: number
  cooldown: number
  accuracy: number
  chargeTurns: number
  /** 命中率描述（可选，前端用于展示）。 */
  maxUsesPerBattle?: number
}

/** 技能配置根对象。 */
export interface SkillsConfigView {
  configVersion: number
  skills: SkillConfigView[]
}

/** 自动战斗策略（阶段 10）。 */
export type AutoStrategy = 'BALANCED' | 'AGGRESSIVE' | 'DEFENSIVE' | 'CAPTURE'

/** 自动战斗配置请求（阶段 10；null 字段表示不修改）。 */
export interface ConfigureAutoRequest {
  enabled: boolean
  strategy?: AutoStrategy | null
  autoSwitch?: boolean | null
  autoSwitchHpThreshold?: number | null
  autoUseRecoveryItem?: boolean | null
  autoRecoveryHpThreshold?: number | null
  autoRevive?: boolean | null
  captureTargetId?: string | null
}

/** 玩家自动战斗偏好（GET /api/battles/auto-preference）。 */
export interface AutoPreference {
  strategy: AutoStrategy
  autoSwitch: boolean
  autoSwitchHpThreshold: number
  autoUseRecoveryItem: boolean
  autoRecoveryHpThreshold: number
  autoRevive: boolean
}
