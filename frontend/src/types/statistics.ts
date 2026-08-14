/**
 * 玩家统计与游戏完成度类型定义（阶段 11，需求 §111/§112）。
 * 与后端 StatisticsController / CompletionController 返回结构对应。
 */

/** 玩家统计总览。 */
export interface StatisticsOverview {
  /** 全部统计键值。 */
  stats: Record<string, number>
  /** 使用最多的宠物/技能。 */
  mostUsed: {
    mostUsedPet: string | null
    mostUsedPetCount: string
    mostUsedSkill: string | null
    mostUsedSkillCount: string
  }
}

/** 完成度分项。 */
export interface CompletionComponent {
  /** 权重（0~1 之和）。 */
  weight: number
  /** 单项进度（0~1）。 */
  progress: number
  /** 权重贡献（已计入总完成度）。 */
  contribution: number
}

/** 游戏完成度总览。 */
export interface CompletionOverview {
  /** 总完成度（0~100）。 */
  overall: number
  components: Record<string, CompletionComponent>
}

/** 完成度分项中文名。 */
export const COMPLETION_COMPONENT_LABELS: Record<string, string> = {
  main: '主线',
  region: '区域',
  discovery: '宠物发现',
  capture: '宠物捕获',
  research: '图鉴研究',
  boss: 'Boss',
  hiddenRegion: '隐藏区域',
  sideQuest: '主要支线',
}