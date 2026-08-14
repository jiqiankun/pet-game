import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { PokedexDetail, PokedexEntry, WildIdentification } from '../types/pokedex'

/**
 * 图鉴 Store（阶段 8）。
 * 管理图鉴列表、种族详情、筛选状态。
 */
export const usePokedexStore = defineStore('pokedex', () => {
  const entries = ref<PokedexEntry[]>([])
  const currentDetail = ref<PokedexDetail | null>(null)
  const filterLevel = ref<number | null>(null)
  const filterElement = ref<string | null>(null)
  const loading = ref(false)
  const error = ref('')

  /** 按研究等级 + 属性筛选的图鉴列表。 */
  const filteredEntries = computed(() => {
    let list = entries.value
    if (filterLevel.value !== null) {
      list = list.filter(e => e.researchLevel === filterLevel.value)
    }
    if (filterElement.value !== null) {
      list = list.filter(e => e.element === filterElement.value)
    }
    return list
  })

  /** 已发现种族数。 */
  const discoveredCount = computed(() => entries.value.filter(e => e.seen).length)

  /** 已捕获种族数。 */
  const caughtCount = computed(() => entries.value.filter(e => e.caught).length)

  /** 完全研究（Lv.5）种族数。 */
  const fullyResearchedCount = computed(() => entries.value.filter(e => e.researchLevel >= 5).length)

  /** 种族总数。 */
  const totalCount = computed(() => entries.value.length)

  /** 加载全量图鉴列表。 */
  async function loadPokedex() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<PokedexEntry[]>('/api/pokedex')
      entries.value = res.data ?? []
    } catch (e: any) {
      error.value = e.message ?? '加载图鉴失败'
    } finally {
      loading.value = false
    }
  }

  /** 加载种族详情。 */
  async function loadDetail(speciesId: string): Promise<PokedexDetail | null> {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<PokedexDetail>(`/api/pokedex/${speciesId}`)
      currentDetail.value = res.data ?? null
      return currentDetail.value
    } catch (e: any) {
      error.value = e.message ?? '加载图鉴详情失败'
      return null
    } finally {
      loading.value = false
    }
  }

  /** Lv.5 野外识别。 */
  async function identifyWild(speciesId: string, aptitudes: number[]): Promise<WildIdentification | null> {
    try {
      const res = await apiPost<WildIdentification>(`/api/pokedex/${speciesId}/identify`, { aptitudes })
      return res.data ?? null
    } catch {
      return null
    }
  }

  return {
    entries,
    currentDetail,
    filterLevel,
    filterElement,
    loading,
    error,
    filteredEntries,
    discoveredCount,
    caughtCount,
    fullyResearchedCount,
    totalCount,
    loadPokedex,
    loadDetail,
    identifyWild,
  }
})
