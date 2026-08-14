/**
 * 成就系统类型定义（阶段 11，需求 §110）。
 * 与后端 AchievementController.listAchievements 返回结构对应。
 */

export interface AchievementItem {
  id: string
  name: string
  description: string
  category: string
  hidden: boolean
  unlocked: boolean
  titleId: string | null
  avatarId: string | null
  /** 解锁时间（ISO 字符串），未解锁时为 null。 */
  unlockedAt: string | null
}

/** 成就分类显示名。 */
export const ACHIEVEMENT_CATEGORY_LABELS: Record<string, string> = {
  EXPLORE: '探索',
  CAPTURE: '捕捉',
  BREED: '培养',
  BATTLE: '战斗',
  BOSS: 'Boss',
  POKEDEX: '图鉴',
  SPECIAL: '特殊',
}