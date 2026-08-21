import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { ApiResponse } from '../types/api'
import type { CurrentLocationView, WorldView } from '../types/map'

/**
 * 世界图谱 Store（阶段 2）。
 * <p>
 * 面向前端消费 WorldGraph + PlayerKnowledge 过滤后的 World/Region/Map 层级，
 * 以及当前精确位置、受控的知识发现写入与位置保存。
 * 业务裁决（过滤 / 校验 / 迁移）全部由后端 WorldTruthService 完成，Store 只存前端状态。
 * 阶段 3/4 将基于本图谱投影构建世界图 / 区域图 / 小地图，本阶段只提供类型对齐与 API 存取。
 */
export const useWorldStore = defineStore('world', () => {
  /** 世界图谱（按玩家知识过滤）。 */
  const graph = ref<WorldView | null>(null)
  /** 当前精确位置。 */
  const currentLocation = ref<CurrentLocationView | null>(null)
  const loading = ref(false)
  const error = ref('')

  /** 加载世界图谱。 */
  async function loadGraph() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<WorldView>('/api/world')
      graph.value = (res as ApiResponse<WorldView>).data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '加载世界图谱失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 加载当前精确位置（含安全/出生锚点回退）。 */
  async function loadCurrentLocation() {
    try {
      const res = await apiGet<CurrentLocationView>('/api/world/current')
      currentLocation.value = (res as ApiResponse<CurrentLocationView>).data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '加载当前位置失败'
      throw e
    }
  }

  /** 保存当前地图 / 坐标 / 朝向（节流后提交；非法跨图位置由后端拒绝）。 */
  async function updatePosition(payload: {
    mapId: string
    posX: number | null
    posY: number | null
    facing?: string | null
  }) {
    const res = await apiPost<CurrentLocationView>('/api/world/position', {
      mapId: payload.mapId,
      posX: payload.posX,
      posY: payload.posY,
      facing: payload.facing ?? null,
    })
    currentLocation.value = (res as ApiResponse<CurrentLocationView>).data
    return currentLocation.value
  }

  /** 受控写入"已发现/已解锁"知识（校验由后端完成）。 */
  async function discover(type: string, id: string) {
    await apiPost<void>('/api/world/discover', { type, id })
    await loadGraph()
  }

  return {
    graph, currentLocation, loading, error,
    loadGraph, loadCurrentLocation, updatePosition, discover,
  }
})