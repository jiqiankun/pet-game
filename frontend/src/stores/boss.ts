import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { AutoChallengeResult, BossInfo } from '../types/boss'

/**
 * Boss Store（阶段 7）。
 * 管理 Boss 列表、当前选中 Boss、战斗状态、自动挑战进度。
 */
export const useBossStore = defineStore('boss', () => {
  const bosses = ref<BossInfo[]>([])
  const currentBossId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref('')
  const autoResult = ref<AutoChallengeResult | null>(null)

  /** 加载 Boss 列表。 */
  async function loadBosses() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<BossInfo[]>('/api/bosses')
      bosses.value = res.data ?? []
    } catch (e: any) {
      error.value = e.message ?? '加载 Boss 列表失败'
    } finally {
      loading.value = false
    }
  }

  /** 加载单个 Boss 详情。 */
  async function loadBoss(bossId: string): Promise<BossInfo | null> {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<BossInfo>(`/api/bosses/${bossId}`)
      // 更新列表中对应的条目
      const idx = bosses.value.findIndex(b => b.bossId === bossId)
      if (idx >= 0 && res.data) {
        bosses.value[idx] = res.data
      }
      return res.data ?? null
    } catch (e: any) {
      error.value = e.message ?? '加载 Boss 详情失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** 开始 Boss 战斗。 */
  async function startBossBattle(bossId: string, difficulty: string, seed?: number): Promise<string | null> {
    loading.value = true
    error.value = ''
    try {
      const body: Record<string, unknown> = { difficulty }
      if (seed !== undefined) body.seed = seed
      const res = await apiPost<{ battleId: string }>(`/api/bosses/${bossId}/battle`, body)
      return res.data?.battleId ?? null
    } catch (e: any) {
      error.value = e.message ?? '开始 Boss 战斗失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** 自动挑战。 */
  async function autoChallenge(bossId: string, difficulty: string, mode: string): Promise<AutoChallengeResult | null> {
    loading.value = true
    error.value = ''
    autoResult.value = null
    try {
      const res = await apiPost<AutoChallengeResult>(`/api/bosses/${bossId}/auto`, { difficulty, mode })
      autoResult.value = res.data ?? null
      // 刷新 Boss 列表
      await loadBosses()
      return autoResult.value
    } catch (e: any) {
      error.value = e.message ?? '自动挑战失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** 幸运兑换。 */
  async function exchangeLuck(bossId: string, dropItemId: string): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      await apiPost(`/api/bosses/${bossId}/exchange`, { dropItemId })
      await loadBosses()
      return true
    } catch (e: any) {
      error.value = e.message ?? '幸运兑换失败'
      return false
    } finally {
      loading.value = false
    }
  }

  return {
    bosses,
    currentBossId,
    loading,
    error,
    autoResult,
    loadBosses,
    loadBoss,
    startBossBattle,
    autoChallenge,
    exchangeLuck,
  }
})
