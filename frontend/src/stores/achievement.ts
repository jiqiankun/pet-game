import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiGet } from '../api/client'
import type { AchievementItem } from '../types/achievement'

/**
 * 成就 Store（阶段 11）。
 * 管理成就列表与分类筛选。
 */
export const useAchievementStore = defineStore('achievement', () => {
  const achievements = ref<AchievementItem[]>([])
  const loading = ref(false)
  const error = ref('')
  const filterCategory = ref<string | null>(null)
  const filterUnlocked = ref<boolean | null>(null)

  /** 按分类 / 解锁状态筛选。 */
  const filteredAchievements = computed(() => {
    let list = achievements.value
    if (filterCategory.value !== null) {
      list = list.filter(a => a.category === filterCategory.value)
    }
    if (filterUnlocked.value !== null) {
      list = list.filter(a => a.unlocked === filterUnlocked.value)
    }
    return list
  })

  /** 已解锁成就数。 */
  const unlockedCount = computed(() => achievements.value.filter(a => a.unlocked).length)

  /** 全部成就可展示数（隐藏成就未解锁时后端不返回）。 */
  const totalCount = computed(() => achievements.value.length)

  /** 各分类计数。 */
  const categoryCounts = computed<Record<string, { total: number; unlocked: number }>>(() => {
    const map: Record<string, { total: number; unlocked: number }> = {}
    for (const a of achievements.value) {
      if (!map[a.category]) map[a.category] = { total: 0, unlocked: 0 }
      map[a.category]!.total++
      if (a.unlocked) map[a.category]!.unlocked++
    }
    return map
  })

  /** 加载成就列表。 */
  async function loadAchievements() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<AchievementItem[]>('/api/achievements')
      achievements.value = res.data ?? []
    } catch (e: any) {
      error.value = e.message ?? '加载成就失败'
    } finally {
      loading.value = false
    }
  }

  /** 设置分类筛选。 */
  function setCategory(category: string | null) {
    filterCategory.value = category
  }

  /** 设置解锁状态筛选（null=全部，true=已解锁，false=未解锁）。 */
  function setUnlockedFilter(state: boolean | null) {
    filterUnlocked.value = state
  }

  return {
    achievements,
    loading,
    error,
    filterCategory,
    filterUnlocked,
    filteredAchievements,
    unlockedCount,
    totalCount,
    categoryCounts,
    loadAchievements,
    setCategory,
    setUnlockedFilter,
  }
})