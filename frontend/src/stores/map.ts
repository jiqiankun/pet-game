import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { ApiResponse } from '../types/api'
import type {
  CampRestView,
  MapEnterView,
  RewardResultView,
  WorldMapView,
} from '../types/map'

/**
 * 地图探索 Store（阶段 6）。
 * <p>
 * 管理当前区域访问状态、大地图视图与本次会话内被击败的野怪刷新点。
 * 业务规则（解锁/刷新/奖励）全部由后端裁定，Store 只保存前端状态。
 */
export const useMapStore = defineStore('map', () => {
  // ---- 状态 ----
  /** 当前区域访问状态（含会话与已消耗对象）。 */
  const currentMap = ref<MapEnterView | null>(null)
  /** 大地图视图。 */
  const worldMap = ref<WorldMapView | null>(null)
  /** 本次会话已被击败的野怪刷新点（战斗胜利后移除，重进区域刷新）。 */
  const defeatedWildIds = ref<string[]>([])
  /** 当前正在进行的地图遭遇对应的野怪刷新点（战斗胜利后标记移除）。 */
  const activeEncounterSpawnId = ref<string | null>(null)
  /** 玩家坐标（会话级表现态：由 MapScene 节流上报，保证 Overlay 关闭后上下文稳定）。 */
  const playerPosition = ref<{ x: number; y: number } | null>(null)
  /** 附近交互对象（MapScene 节流上报，供情境交互层 ContextInteractionPanel 展示动作按钮）。 */
  const nearbyObject = ref<{ type: string; label: string; id: string } | null>(null)
  const loading = ref(false)
  const error = ref('')

  /** 加载当前区域访问状态（无会话时后端补建）。 */
  async function loadCurrentMap() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<MapEnterView>('/api/maps/current')
      const view = (res as ApiResponse<MapEnterView>).data
      // 会话变化（重进/休息刷新）时清空击败记录
      if (!currentMap.value
        || currentMap.value.sessionId !== view.sessionId
        || currentMap.value.mapId !== view.mapId) {
        defeatedWildIds.value = []
      }
      currentMap.value = view
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '加载当前区域失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 加载大地图视图（区域、推荐等级、Boss 占位、营地）。 */
  async function loadWorldMap() {
    try {
      const res = await apiGet<WorldMapView>('/api/maps/world')
      worldMap.value = (res as ApiResponse<WorldMapView>).data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '加载大地图失败'
      throw e
    }
  }

  /** 进入区域（出口移动 / 大地图进入）：生成新会话，触发野怪与采集点刷新。 */
  async function enterRegion(mapId: string, exitId?: string) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<MapEnterView>(`/api/maps/${mapId}/enter`, {
        exitId: exitId ?? null,
      })
      currentMap.value = (res as ApiResponse<MapEnterView>).data
      defeatedWildIds.value = []
      return currentMap.value
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '进入区域失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 营地休息：免费恢复全队 + 激活营地 + 触发地图刷新。 */
  async function restAtCamp(campId: string) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<CampRestView>(`/api/maps/camps/${campId}/rest`)
      const rest = (res as ApiResponse<CampRestView>).data
      // 休息触发刷新：重新加载当前区域状态（新会话）
      await loadCurrentMap()
      return rest
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '营地休息失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 已激活营地间免费传送。 */
  async function teleportToCamp(campId: string) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<MapEnterView>(`/api/maps/camps/${campId}/teleport`)
      currentMap.value = (res as ApiResponse<MapEnterView>).data
      defeatedWildIds.value = []
      return currentMap.value
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '传送失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 采集普通采集点（单次访问内一次性）。 */
  async function gather(gatherId: string) {
    try {
      const res = await apiPost<RewardResultView>(`/api/maps/gathers/${gatherId}/gather`)
      return (res as ApiResponse<RewardResultView>).data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '采集失败'
      throw e
    }
  }

  /** 开启隐藏宝箱（全局一次性）。 */
  async function openChest(chestId: string) {
    try {
      const res = await apiPost<RewardResultView>(`/api/maps/chests/${chestId}/open`)
      return (res as ApiResponse<RewardResultView>).data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '开启宝箱失败'
      throw e
    }
  }

  /** 记录本次会话被击败的野怪刷新点（战斗胜利后调用）。 */
  function markWildDefeated(spawnId: string) {
    if (!defeatedWildIds.value.includes(spawnId)) {
      defeatedWildIds.value.push(spawnId)
    }
  }

  /** 更新玩家坐标（由 MapScene 节流上报）。 */
  function setPlayerPosition(pos: { x: number; y: number }) {
    playerPosition.value = pos
  }

  /** 更新附近交互对象（由 MapScene 节流上报；type 为空串表示无附近对象）。 */
  function setNearbyObject(nearby: { type: string; label: string; id: string }) {
    nearbyObject.value = nearby.type ? nearby : null
  }

  return {
    currentMap, worldMap, defeatedWildIds, activeEncounterSpawnId, playerPosition, nearbyObject, loading, error,
    loadCurrentMap, loadWorldMap, enterRegion, restAtCamp,
    teleportToCamp, gather, openChest, markWildDefeated, setPlayerPosition, setNearbyObject,
  }
})
