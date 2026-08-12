import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost, BusinessError } from '../api/client'
import type { ApiResponse } from '../types/api'

/**
 * 游戏状态 Store。
 * 管理玩家存档状态、Bootstrap 数据、新游戏创建等。
 */
export const useGameStore = defineStore('game', () => {
  // ---- 状态 ----
  const hasSave = ref<boolean | null>(null) // null = 未检查
  const player = ref<any>(null)
  const pets = ref<any[]>([])
  const activeTeam = ref<any>(null)
  const teamMembers = ref<any[]>([])
  const gameVersion = ref('')
  const saveVersion = ref(0)
  const developerMode = ref(false)
  const loading = ref(false)
  const error = ref('')

  /**
   * 检查存档状态。
   */
  async function checkSaveStatus() {
    try {
      const res = await apiGet<{ hasSave: boolean }>('/api/game/save-status')
      hasSave.value = (res as ApiResponse<{ hasSave: boolean }>).data.hasSave
    } catch (e) {
      hasSave.value = false
    }
  }

  /**
   * 加载 Bootstrap 数据。
   */
  async function loadBootstrap() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<any>('/api/game/bootstrap')
      const data = (res as ApiResponse<any>).data
      player.value = data.player
      pets.value = data.pets || []
      activeTeam.value = data.activeTeam
      teamMembers.value = data.teamMembers || []
      gameVersion.value = data.gameVersion
      saveVersion.value = data.saveVersion
      developerMode.value = data.developerMode
      hasSave.value = true
    } catch (e: any) {
      if (e instanceof BusinessError && e.code === 'NO_SAVE') {
        hasSave.value = false
      } else {
        error.value = e.message || '加载失败'
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建新游戏。
   */
  async function createNewGame(playerName: string, avatarId: string, petChoiceId: string) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<any>('/api/game/new-game', {
        playerName,
        avatarId,
        petChoiceId,
      })
      const data = (res as ApiResponse<any>).data
      player.value = data.player
      pets.value = data.pets || []
      activeTeam.value = data.activeTeam
      teamMembers.value = data.teamMembers || []
      gameVersion.value = data.gameVersion
      saveVersion.value = data.saveVersion
      developerMode.value = data.developerMode
      hasSave.value = true
    } catch (e: any) {
      error.value = e.message || '创建失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 手动保存。
   */
  async function manualSave() {
    try {
      await apiPost('/api/game/save')
    } catch (e: any) {
      error.value = e.message || '保存失败'
    }
  }

  return {
    hasSave, player, pets, activeTeam, teamMembers,
    gameVersion, saveVersion, developerMode, loading, error,
    checkSaveStatus, loadBootstrap, createNewGame, manualSave,
  }
})
