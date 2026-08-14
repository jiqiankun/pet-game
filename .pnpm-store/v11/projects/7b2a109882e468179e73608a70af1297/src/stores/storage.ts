import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost, apiPut } from '../api/client'
import type { ApiResponse } from '../types/api'
import type {
  ReleasePreview,
  ReleaseResult,
  StoragePetView,
  StorageQueryRequest,
} from '../types/storage'

/**
 * 宠物仓库 Store（阶段 5）。
 * 管理仓库列表查询（筛选/排序）、昵称/锁定/收藏、放生预览与放生。
 * 全部规则计算（礼物点数、保护判定）由后端完成，前端只展示与提交意图。
 */
export const useStorageStore = defineStore('storage', () => {
  const pets = ref<StoragePetView[]>([])
  const loading = ref(false)
  const error = ref('')
  /** 最近一次放生结果（礼物汇总展示用）。 */
  const lastReleaseResult = ref<ReleaseResult | null>(null)

  /** 查询仓库（筛选 + 排序）。 */
  async function loadStorage(query: StorageQueryRequest) {
    loading.value = true
    error.value = ''
    try {
      const params = new URLSearchParams()
      for (const [key, value] of Object.entries(query)) {
        if (value === undefined || value === null || value === '') continue
        params.set(key, String(value))
      }
      const qs = params.toString()
      const res = await apiGet<StoragePetView[]>(`/api/storage/pets${qs ? '?' + qs : ''}`)
      pets.value = (res as ApiResponse<StoragePetView[]>).data
    } catch (e: any) {
      error.value = e.message || '仓库查询失败'
    } finally {
      loading.value = false
    }
  }

  /** 设置昵称（空 = 清除）。 */
  async function setNickname(petId: number, nickname: string) {
    const res = await apiPut<StoragePetView>(`/api/storage/pets/${petId}/nickname`, { nickname })
    return (res as ApiResponse<StoragePetView>).data
  }

  /** 设置锁定状态。 */
  async function setLocked(petId: number, value: boolean) {
    const res = await apiPut<StoragePetView>(`/api/storage/pets/${petId}/locked`, { value })
    return (res as ApiResponse<StoragePetView>).data
  }

  /** 设置收藏状态。 */
  async function setFavorite(petId: number, value: boolean) {
    const res = await apiPut<StoragePetView>(`/api/storage/pets/${petId}/favorite`, { value })
    return (res as ApiResponse<StoragePetView>).data
  }

  /** 放生预览：保护原因、礼物点数与额外警告（前端二次确认依据）。 */
  async function previewRelease(petIds: number[]) {
    const res = await apiPost<ReleasePreview>('/api/storage/release-preview', { petIds })
    return (res as ApiResponse<ReleasePreview>).data
  }

  /** 执行放生（单只/批量）：后端自动排除受保护宠物并汇总礼物。 */
  async function releasePets(petIds: number[]) {
    error.value = ''
    try {
      const res = await apiPost<ReleaseResult>('/api/storage/release', { petIds })
      lastReleaseResult.value = (res as ApiResponse<ReleaseResult>).data
      return lastReleaseResult.value
    } catch (e: any) {
      error.value = e.message || '放生失败'
      throw e
    }
  }

  function clearReleaseResult() {
    lastReleaseResult.value = null
  }

  return {
    pets,
    loading,
    error,
    lastReleaseResult,
    loadStorage,
    setNickname,
    setLocked,
    setFavorite,
    previewRelease,
    releasePets,
    clearReleaseResult,
  }
})
