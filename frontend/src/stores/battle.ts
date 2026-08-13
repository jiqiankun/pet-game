import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { ApiResponse } from '../types/api'
import type {
  BattleAction,
  BattleEvent,
  BattleSnapshot,
  SkillConfigView,
  SkillsConfigView,
  UnitSnapshot,
} from '../types/battle'

/**
 * 战斗 Store（阶段 3）。
 * 管理战斗快照、技能配置缓存、事件日志与行动意图收集。
 * 前端只提交行动意图，全部结算由后端 BattleEngine 完成。
 */
export const useBattleStore = defineStore('battle', () => {
  // ---- 状态 ----
  const snapshot = ref<BattleSnapshot | null>(null)
  const skillIndex = ref<Record<string, SkillConfigView>>({})
  const eventLog = ref<string[]>([])
  const pendingActions = ref<BattleAction[]>([])
  const actionOrder = ref<string[]>([])
  const loading = ref(false)
  const error = ref('')

  const inBattle = computed(() => snapshot.value !== null && !snapshot.value.finished)

  /** 全部单位名称索引（事件日志翻译用）。 */
  const unitNames = computed<Record<string, string>>(() => {
    const map: Record<string, string> = {}
    if (snapshot.value) {
      for (const unit of [...snapshot.value.playerUnits, ...snapshot.value.enemyUnits]) {
        map[unit.unitId] = unit.name
      }
    }
    return map
  })

  /** 加载技能配置（展示名称/描述/冷却）。 */
  async function loadSkillConfig() {
    try {
      const res = await apiGet<SkillsConfigView>('/api/game/config/skills')
      const data = (res as ApiResponse<SkillsConfigView>).data
      const index: Record<string, SkillConfigView> = {}
      for (const skill of data.skills || []) {
        index[skill.id] = skill
      }
      skillIndex.value = index
    } catch (e: any) {
      // 技能配置加载失败不阻塞战斗，按钮降级显示技能 ID
      console.warn('技能配置加载失败', e)
    }
  }

  /** 开始测试战斗。 */
  async function startTestBattle(seed?: number) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<BattleSnapshot>('/api/battles', {
        type: 'TEST_BATTLE',
        seed: seed ?? null,
      })
      snapshot.value = (res as ApiResponse<BattleSnapshot>).data
      pendingActions.value = []
      actionOrder.value = []
      eventLog.value = [`战斗开始（种子 ${snapshot.value.seed}）`]
      appendEvents(snapshot.value.events)
    } catch (e: any) {
      error.value = e.message || '战斗创建失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 提交行动意图并结算回合。 */
  async function submitActions() {
    if (!snapshot.value || snapshot.value.finished) return
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<BattleSnapshot>(
        `/api/battles/${snapshot.value.battleId}/actions`,
        { actions: pendingActions.value },
      )
      snapshot.value = (res as ApiResponse<BattleSnapshot>).data
      pendingActions.value = []
      appendEvents(snapshot.value.events)
      if (snapshot.value.finished) {
        eventLog.value.push(
          snapshot.value.winner === 'PLAYER' ? '战斗胜利！' : '战斗失败……',
        )
      }
    } catch (e: any) {
      error.value = e.message || '行动提交失败'
    } finally {
      loading.value = false
    }
  }

  /** 收集单个单位的行动意图（同一单位重复设置时覆盖）。 */
  function setAction(action: BattleAction) {
    pendingActions.value = pendingActions.value.filter((a) => a.petId !== action.petId)
    pendingActions.value.push(action)
  }

  /** 查询单位已收集的行动。 */
  function getAction(petId: string): BattleAction | undefined {
    return pendingActions.value.find((a) => a.petId === petId)
  }

  /** 退出战斗（仅清理前端状态，内存战斗由后端自然丢弃）。 */
  function leaveBattle() {
    snapshot.value = null
    pendingActions.value = []
    actionOrder.value = []
    eventLog.value = []
    error.value = ''
  }

  // ---- 事件日志 ----

  function skillName(skillId: string | null | undefined): string {
    if (!skillId) return ''
    return skillIndex.value[skillId]?.name ?? skillId
  }

  function unitName(unitId: string | null | undefined): string {
    if (!unitId) return ''
    return unitNames.value[unitId] ?? unitId
  }

  function appendEvents(events: BattleEvent[]) {
    for (const event of events) {
      if (event.type === 'ACTION_ORDER') {
        const order = event.data?.order
        if (Array.isArray(order)) {
          actionOrder.value = order.map(String)
        }
        continue
      }
      const text = describeEvent(event)
      if (text) {
        eventLog.value.push(text)
      }
    }
    // 限制日志长度
    if (eventLog.value.length > 300) {
      eventLog.value = eventLog.value.slice(-300)
    }
  }

  /** 事件 → 日志文本（仅展示，不参与任何计算）。 */
  function describeEvent(event: BattleEvent): string {
    const source = unitName(event.sourceId)
    const target = unitName(event.targetId)
    const skill = skillName(event.skillId)
    switch (event.type) {
      case 'TURN_STARTED':
        return `—— 回合 ${event.round} ——`
      case 'ACTION_ORDER':
        return ''
      case 'SKILL_CAST':
        return `${source} 使用 ${skill}`
      case 'DAMAGE': {
        const relation =
          event.elementRelation === 'ADVANTAGE' ? '（克制）'
          : event.elementRelation === 'DISADVANTAGE' ? '（被克）'
          : ''
        const crit = event.critical ? '（暴击）' : ''
        return `${source} 对 ${target} 造成 ${event.value} 点伤害${relation}${crit}`
      }
      case 'CRITICAL':
        return ''
      case 'MISS':
        return `${source} 的攻击落空了`
      case 'HEAL':
        return `${source} 为 ${target} 恢复 ${event.value} 点生命`
      case 'SHIELD_CREATED':
        return `${target} 获得 ${event.value} 点护盾`
      case 'DEFEND':
        return event.data?.reason === 'SILENCE'
          ? `${source} 被沉默，只能防御`
          : `${source} 进入防御姿态`
      case 'CHARGING':
        return `${source} 开始蓄力 ${skill}`
      case 'BUFF_APPLIED':
        return `${target} 获得增益：${event.statusId}`
      case 'STATUS_APPLIED':
        return `${target} 陷入状态：${event.statusId}`
      case 'DEBUFF_APPLIED':
        return `${target} 受到减益：${event.statusId}`
      case 'STATUS_TICK':
        return `${target} 受到 ${event.statusId} 持续伤害 ${event.value} 点`
      case 'STATUS_EXPIRED':
        return `${target} 的 ${event.statusId} 效果结束`
      case 'ACTION_SKIPPED':
        return `${source} 无法行动`
      case 'PET_SWITCHED':
        return `${source} 下场，${unitName(event.data?.inId as string | undefined)} 上场`
      case 'PET_DEFEATED':
        return `${target} 失去战斗能力`
      case 'PET_REPLACED':
        return `${target} 补位上场`
      case 'PASSIVE_TRIGGERED':
        return `${source} 触发被动`
      case 'BATTLE_ENDED':
        return `战斗结束，胜方：${event.data?.winner === 'PLAYER' ? '玩家' : '敌方'}`
      case 'TURN_ENDED':
        return ''
      default:
        return ''
    }
  }

  return {
    snapshot,
    skillIndex,
    eventLog,
    pendingActions,
    actionOrder,
    loading,
    error,
    inBattle,
    loadSkillConfig,
    startTestBattle,
    submitActions,
    setAction,
    getAction,
    leaveBattle,
    skillName,
    unitName,
  }
})

/** 辅助：判断单位是否存活上场。 */
export function isActiveAlive(unit: UnitSnapshot): boolean {
  return unit.alive && unit.active
}
