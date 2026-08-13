/** 任务系统类型定义（阶段 9）。 */

/** 任务摘要。 */
export interface QuestSummary {
  questId: string
  name: string
  type: 'MAIN' | 'SIDE' | 'HIDDEN'
  description: string
  regionId: string | null
  status: 'AVAILABLE' | 'ACTIVE' | 'COMPLETED' | 'LOCKED'
  hidden: boolean
}

/** 任务列表视图。 */
export interface QuestListView {
  mainQuests: QuestSummary[]
  sideQuests: QuestSummary[]
  hiddenQuests: QuestSummary[]
}

/** 任务目标视图。 */
export interface ObjectiveInfo {
  objectiveId: string
  type: string
  description: string
  targetCount: number
  progress: number
  completed: boolean
}

/** 奖励条目。 */
export interface RewardEntryInfo {
  type: 'GOLD' | 'EXP' | 'ITEM' | 'SKILL_BOOK'
  itemId: string | null
  quantity: number
}

/** 三选一奖励组。 */
export interface ChoiceGroupInfo {
  choiceId: string
  options: RewardEntryInfo[]
}

/** 奖励预览。 */
export interface RewardPreview {
  fixed: RewardEntryInfo[]
  choices: ChoiceGroupInfo[]
}

/** 赠送宠物预览。 */
export interface GiftPetPreview {
  speciesId: string
  speciesName: string
  level: number
  source: string
}

/** 地图变更配置。 */
export interface MapChangeConfig {
  changeId: string
  changeType: string
  regionId: string
  description: string
  objectId: string | null
}

/** 任务详情。 */
export interface QuestDetail {
  questId: string
  name: string
  type: string
  description: string
  regionId: string | null
  status: string
  hidden: boolean
  rewardChosen: boolean
  objectives: ObjectiveInfo[]
  rewards: RewardPreview | null
  mapChanges: MapChangeConfig[]
  giftPet: GiftPetPreview | null
}

/** 任务完成结果。 */
export interface QuestCompleteResult {
  questId: string
  name: string
  goldGained: number
  expGained: number
  itemsGained: { itemId: string; quantity: number }[]
  unlockedRegions: string[]
  activatedMapChanges: string[]
  giftPet: { petId: number; speciesId: string; speciesName: string; level: number; source: string } | null
  storyCompleted: boolean
}

/** NPC 对话视图。 */
export interface DialogueView {
  npcId: string
  npcName: string
  nodeId: string
  text: string
  hasMore: boolean
  dialogueCount: number
}

/** 教学步骤视图。 */
export interface TutorialStepView {
  stepId: string
  name: string
  description: string
  order: number
  skippable: boolean
  completed: boolean
  skipped: boolean
}

/** 教学状态。 */
export interface TutorialStateView {
  steps: TutorialStepView[]
  allCompleted: boolean
  completedCount: number
  totalCount: number
}

/** 主线任务摘要。 */
export interface ActiveQuestSummary {
  questId: string
  name: string
  description: string
  regionId: string | null
  currentObjectiveDescription: string | null
  currentProgress: number
  currentTarget: number
}

/** 地图变更视图。 */
export interface MapChangeView {
  changeId: string
  changeType: string
  regionId: string
  description: string
  objectId: string | null
  activatedAt: string
}
