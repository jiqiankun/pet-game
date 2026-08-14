import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet } from '../api/client'
import type { CompletionOverview, StatisticsOverview } from '../types/statistics'

/**
 * 玩家统计与游戏完成度 Store（阶段 11）。
 * 管理统计总览与完成度数据。
 */
export const useStatisticsStore = defineStore('statistics', () => {
  const overview = ref<StatisticsOverview | null>(null)
  const completion = ref<CompletionOverview | null>(null)
  const loading = ref(false)
  const error = ref('')

  /** 加载玩家统计总览。 */
  async function loadStatistics() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<StatisticsOverview>('/api/statistics')
      overview.value = res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载统计数据失败'
    } finally {
      loading.value = false
    }
  }

  /** 加载游戏完成度。 */
  async function loadCompletion() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<CompletionOverview>('/api/completion')
      completion.value = res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载完成度失败'
    } finally {
      loading.value = false
    }
  }

  /** 同时加载统计与完成度。 */
  async function loadAll() {
    await Promise.all([loadStatistics(), loadCompletion()])
  }

  return {
    overview,
    completion,
    loading,
    error,
    loadStatistics,
    loadCompletion,
    loadAll,
  }
})