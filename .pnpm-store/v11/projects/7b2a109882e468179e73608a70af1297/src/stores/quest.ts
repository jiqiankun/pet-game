import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type {
  QuestListView,
  QuestDetail,
  QuestCompleteResult,
  DialogueView,
  TutorialStateView,
  ActiveQuestSummary,
  MapChangeView,
} from '../types/quest'

/**
 * 任务 Store（阶段 9）。
 * 管理任务列表/详情、NPC 对话、教学状态、主线摘要。
 */
export const useQuestStore = defineStore('quest', () => {
  const questList = ref<QuestListView | null>(null)
  const loading = ref(false)
  const error = ref('')

  // 对话状态
  const currentDialogue = ref<DialogueView | null>(null)

  // 教学状态
  const tutorialState = ref<TutorialStateView | null>(null)

  /** 加载任务列表。 */
  async function loadQuests() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<QuestListView>('/api/quests')
      questList.value = res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载任务列表失败'
    } finally {
      loading.value = false
    }
  }

  /** 加载任务详情。 */
  async function loadQuestDetail(questId: string): Promise<QuestDetail | null> {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<QuestDetail>(`/api/quests/${questId}`)
      return res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载任务详情失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** 接受任务。 */
  async function acceptQuest(questId: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      await apiPost(`/api/quests/${questId}/accept`)
      await loadQuests()
      return true
    } catch (e: any) {
      error.value = e.message ?? '接受任务失败'
      return false
    } finally {
      loading.value = false
    }
  }

  /** 完成任务。 */
  async function completeQuest(questId: string): Promise<QuestCompleteResult | null> {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<QuestCompleteResult>(`/api/quests/${questId}/complete`)
      await loadQuests()
      return res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '完成任务失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** 三选一奖励选择。 */
  async function chooseReward(questId: string, choiceId: string, optionIndex: number): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      await apiPost(`/api/quests/${questId}/choose-reward`, { choiceId, optionIndex })
      return true
    } catch (e: any) {
      error.value = e.message ?? '选择奖励失败'
      return false
    } finally {
      loading.value = false
    }
  }

  /** NPC 对话。 */
  async function talkNpc(npcId: string): Promise<DialogueView | null> {
    error.value = ''
    try {
      const res = await apiPost<DialogueView>(`/api/npcs/${npcId}/talk`)
      currentDialogue.value = res.data ?? null
      return currentDialogue.value
    } catch (e: any) {
      error.value = e.message ?? 'NPC 对话失败'
      return null
    }
  }

  /** 继续对话。 */
  async function continueDialogue() {
    if (!currentDialogue.value || !currentDialogue.value.hasMore) return
    await talkNpc(currentDialogue.value.npcId)
  }

  /** 关闭对话。 */
  function closeDialogue() {
    currentDialogue.value = null
  }

  /** 加载教学状态。 */
  async function loadTutorial() {
    error.value = ''
    try {
      const res = await apiGet<TutorialStateView>('/api/tutorial')
      tutorialState.value = res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载教学状态失败'
    }
  }

  /** 完成教学步骤。 */
  async function completeTutorialStep(stepId: string): Promise<boolean> {
    error.value = ''
    try {
      await apiPost(`/api/tutorial/${stepId}/complete`)
      await loadTutorial()
      return true
    } catch (e: any) {
      error.value = e.message ?? '完成教学步骤失败'
      return false
    }
  }

  /** 跳过教学。 */
  async function skipTutorial(): Promise<boolean> {
    error.value = ''
    try {
      await apiPost('/api/tutorial/skip')
      await loadTutorial()
      return true
    } catch (e: any) {
      error.value = e.message ?? '跳过教学失败'
      return false
    }
  }

  /** 重置教学提示（阶段 14）。 */
  async function resetTutorial(): Promise<boolean> {
    error.value = ''
    try {
      await apiPost('/api/tutorial/reset')
      await loadTutorial()
      return true
    } catch (e: any) {
      error.value = e.message ?? '重置教学失败'
      return false
    }
  }

  /** 获取主线摘要。 */
  async function getActiveSummary(): Promise<ActiveQuestSummary | null> {
    error.value = ''
    try {
      const res = await apiGet<ActiveQuestSummary>('/api/quests/active-summary')
      return res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载主线摘要失败'
      return null
    }
  }

  /** 获取地图变更列表。 */
  async function getMapChanges(): Promise<MapChangeView[]> {
    error.value = ''
    try {
      const res = await apiGet<MapChangeView[]>('/api/map-changes')
      return res.data ?? []
    } catch (e: any) {
      error.value = e.message ?? '加载地图变更失败'
      return []
    }
  }

  return {
    questList,
    loading,
    error,
    currentDialogue,
    tutorialState,
    loadQuests,
    loadQuestDetail,
    acceptQuest,
    completeQuest,
    chooseReward,
    talkNpc,
    continueDialogue,
    closeDialogue,
    loadTutorial,
    completeTutorialStep,
    skipTutorial,
    resetTutorial,
    getActiveSummary,
    getMapChanges,
  }
})
