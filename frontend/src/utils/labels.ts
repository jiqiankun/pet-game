/**
 * 全站统一枚举 → 中文映射工具。
 *
 * 项目规范（见 docs/development/FRONTEND_STANDARDS.md）：所有从后端返回的枚举值
 * （属性、稀有度、伤害类型、效果类型、技能类型、技能来源等）在页面展示时一律
 * 通过本文件转换中文，禁止在各页面内重复定义映射表，禁止直接展示枚举原文。
 */

// ==================== 属性（Element） ====================

/** 属性枚举 → 中文。 */
export const ELEMENT_LABELS: Record<string, string> = {
  NONE: '无',
  METAL: '金',
  WOOD: '木',
  WATER: '水',
  FIRE: '火',
  EARTH: '土',
  WIND: '风',
  THUNDER: '雷',
  LIGHT: '光',
  DARK: '暗',
}

/** 属性枚举 → 主题色（用于标签/图标着色）。 */
export const ELEMENT_COLORS: Record<string, string> = {
  METAL: '#6b7b8c',
  WOOD: '#43a047',
  WATER: '#4a90d9',
  FIRE: '#e53935',
  EARTH: '#8d6e63',
  WIND: '#26a69a',
  THUNDER: '#f5a623',
  LIGHT: '#fdd835',
  DARK: '#5e35b1',
  NONE: '#9e9e9e',
}

/** 属性枚举 → 中文，未知值回退原文。 */
export function elementLabel(e: string | null | undefined): string {
  if (!e) return '无'
  return ELEMENT_LABELS[e] ?? e
}

// ==================== 稀有度（Rarity） ====================

/** 稀有度枚举 → 中文。 */
export const RARITY_LABELS: Record<string, string> = {
  COMMON: '普通',
  RARE: '稀有',
  EPIC: '珍稀',
  LEGENDARY: '传说',
}

/** 稀有度枚举 → 主题色。 */
export const RARITY_COLORS: Record<string, string> = {
  COMMON: '#8b8b8b',
  RARE: '#4a90d9',
  EPIC: '#c455e8',
  LEGENDARY: '#f5a623',
}

/** 稀有度枚举 → 中文，未知值回退原文。 */
export function rarityLabel(r: string | null | undefined): string {
  if (!r) return '未知'
  return RARITY_LABELS[r] ?? r
}

/** 稀有度枚举 → 主题色，未知值返回默认灰。 */
export function rarityColor(r: string | null | undefined): string {
  if (!r) return RARITY_COLORS.COMMON as string
  return RARITY_COLORS[r] ?? (RARITY_COLORS.COMMON as string)
}

// ==================== 伤害类型（DamageType） ====================

/** 伤害类型 → 中文。 */
export const DAMAGE_TYPE_LABELS: Record<string, string> = {
  PHYSICAL: '物理',
  MAGICAL: '魔法',
  NONE: '—',
}

/** 伤害类型 → 中文，未知值回退原文。 */
export function damageTypeLabel(d: string | null | undefined): string {
  if (!d) return '—'
  return DAMAGE_TYPE_LABELS[d] ?? d
}

// ==================== 效果类型（EffectType） ====================

/** 效果类型 → 中文。 */
export const EFFECT_TYPE_LABELS: Record<string, string> = {
  DAMAGE: '伤害',
  HEAL: '治疗',
  SHIELD: '护盾',
  NONE: '—',
  SURVIVE_LETHAL: '留生一击',
  APPLY_STATUS_ALLY_ALL: '施加状态（全体）',
  APPLY_STATUS_SELF: '施加状态（自身）',
  DAMAGE_ENEMY_RANDOM: '随机伤害',
  HEAL_SELF: '自愈',
  REDUCE_PHYSICAL_DAMAGE: '减物理伤害',
}

/** 效果类型 → 中文，未知值回退原文。 */
export function effectTypeLabel(e: string | null | undefined): string {
  if (!e) return '—'
  return EFFECT_TYPE_LABELS[e] ?? e
}

// ==================== 技能类型（SkillType） ====================

/** 技能类型 → 中文。 */
export const SKILL_TYPE_LABELS: Record<string, string> = {
  ACTIVE: '主动',
  PASSIVE: '被动',
}

/** 技能类型 → 中文，未知值回退原文。 */
export function skillTypeLabel(t: string | null | undefined): string {
  if (!t) return '主动'
  return SKILL_TYPE_LABELS[t] ?? t
}

// ==================== 技能来源（SourceType / Source） ====================

/** 技能来源 → 中文。 */
export const SOURCE_LABELS: Record<string, string> = {
  INNATE: '自身',
  BOOK: '技能书',
  SPECIAL: '特殊',
  EVOLUTION: '进化',
  SKILL_BOOK: '技能书',
}

/** 技能来源 → 中文，未知值回退原文。 */
export function sourceLabel(s: string | null | undefined): string {
  if (!s) return '自身'
  return SOURCE_LABELS[s] ?? s
}

// ==================== 六维属性（Stat） ====================

/** 六维属性 key → 中文。 */
export const STAT_LABELS: Record<string, string> = {
  HP: '生命',
  STRENGTH: '力量',
  SPIRIT: '灵力',
  DEFENSE: '防御',
  RESISTANCE: '抗性',
  SPEED: '速度',
}

// ==================== 捕捉档位（战斗体验优化） ====================

/** 捕捉率 → 模糊档位（前端映射，不改变后端精确数值接口）。 */
export function captureTier(rate: number): { label: string; color: string } {
  if (rate < 0.1) return { label: '极难', color: '#c0392b' }
  if (rate < 0.25) return { label: '困难', color: '#e67e22' }
  if (rate < 0.5) return { label: '普通', color: '#8e8e93' }
  if (rate < 0.75) return { label: '较容易', color: '#2e7d32' }
  return { label: '很容易', color: '#1e8449' }
}

// ==================== 属性克制关系（战斗体验优化） ====================

/** 克制关系 → 展示标记。 */
export function elementRelationMark(relation: string | null | undefined): { text: string; className: string } | null {
  switch (relation) {
    case 'ADVANTAGE':
      return { text: '效果拔群', className: 'advantage' }
    case 'DISADVANTAGE':
      return { text: '效果较弱', className: 'disadvantage' }
    default:
      return null
  }
}

/** 技能目标类型 → 中文提示。 */
export function skillTargetLabel(target: string | null | undefined): string {
  switch (target) {
    case 'ENEMY_SINGLE': return '单体敌方'
    case 'ENEMY_ALL': return '全体敌方'
    case 'ALLY_SINGLE': return '单体己方'
    case 'ALLY_ALL': return '全体己方'
    case 'SELF': return '自身'
    default: return target ?? ''
  }
}