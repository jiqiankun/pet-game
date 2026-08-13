import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api/client'
import type { ApiResponse } from '../types/api'
import type {
  BattleAction,
  BattleEvent,
  BattleSnapshot,
  CaptureRateView,
  SkillConfigView,
  SkillsConfigView,
  UnitSnapshot,
} from '../types/battle'
import type { BattleSettlement } from '../types/pet'

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
  /** 战斗结算结果（战斗结束后调用 settle 获得）。 */
  const settlement = ref<BattleSettlement | null>(null)
  /** 野生战斗捕捉率（后端计算，选择捕捉球时展示）。 */
  const captureRates = ref<CaptureRateView[]>([])

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

  /** 开始野生战斗（阶段 5：可捕捉可逃跑的简化遭遇入口）。 */
  async function startWildBattle(seed?: number) {
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<BattleSnapshot>('/api/wild/battles', {
        seed: seed ?? null,
      })
      snapshot.value = (res as ApiResponse<BattleSnapshot>).data
      pendingActions.value = []
      actionOrder.value = []
      captureRates.value = []
      settlement.value = null
      eventLog.value = [`遭遇野生宠物！（种子 ${snapshot.value.seed}）`]
      appendEvents(snapshot.value.events)
    } catch (e: any) {
      error.value = e.message || '野生遭遇创建失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 加载捕捉率（后端实时计算：目标状态变化后需重新加载）。 */
  async function loadCaptureRates() {
    if (!snapshot.value || snapshot.value.battleType !== 'WILD') {
      captureRates.value = []
      return
    }
    try {
      const res = await apiGet<CaptureRateView[]>(
        `/api/wild/battles/${snapshot.value.battleId}/capture-rates`,
      )
      captureRates.value = (res as ApiResponse<CaptureRateView[]>).data
    } catch (e: any) {
      // 捕捉率加载失败不阻断战斗，按钮降级为不显示概率
      console.warn('捕捉率加载失败', e)
      captureRates.value = []
    }
  }

  /** 指定单位×指定捕捉球的捕捉率（展示用，计算仍在后端）。 */
  function captureRateOf(unitId: string, ballItemId: string): number | null {
    const found = captureRates.value.find(
      (r) => r.unitId === unitId && r.ballItemId === ballItemId,
    )
    return found ? found.rate : null
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
          snapshot.value.fled
            ? '逃跑成功，战斗结束。'
            : snapshot.value.winner === 'PLAYER'
              ? '战斗胜利！'
              : '战斗失败……',
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
    settlement.value = null
    captureRates.value = []
    error.value = ''
  }

  /**
   * 战斗结算（阶段 4；阶段 5 扩展捕捉去向）。
   * 战斗结束后调用后端 settle 接口，落库 HP 回写、经验/金币/掉落发放；
   * 野生战斗另含捕捉落库与捕捉球扣除。
   * joinTeam=true 且队伍未满 6 只时，被捕捉宠物直接入队（需求 §48）。
   */
  async function settleBattle(joinTeam = false) {
    if (!snapshot.value || !snapshot.value.finished) return
    if (settlement.value) return  // 已结算，避免重复调用
    loading.value = true
    error.value = ''
    try {
      const res = await apiPost<BattleSettlement>(
        `/api/battles/${snapshot.value.battleId}/settle`,
        { joinTeam },
      )
      settlement.value = (res as ApiResponse<BattleSettlement>).data
    } catch (e: any) {
      error.value = e.message || '战斗结算失败'
    } finally {
      loading.value = false
    }
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
      case 'CAPTURE_ATTEMPT': {
        const rate = event.data?.rate
        const rateText = typeof rate === 'number' ? `（成功率 ${(rate * 100).toFixed(1)}%）` : ''
        return `${source} 向 ${target} 投出捕捉球${rateText}`
      }
      case 'CAPTURE_SUCCESS':
        return `捕捉成功！${target} 加入了队伍`
      case 'CAPTURE_FAIL':
        return `可恶！${target} 从捕捉球中逃了出来`
      case 'FLEE_SUCCESS':
        return `${source} 成功逃离了战斗`
      case 'FLEE_FAIL':
        return `${source} 逃跑失败！`
      case 'PASSIVE_TRIGGERED':
        return `${source} 触发被动`
      case 'BATTLE_ENDED':
        if (event.data?.winner === 'FLEE') return '战斗结束（逃跑）'
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
    settlement,
    captureRates,
    loadSkillConfig,
    startTestBattle,
    startWildBattle,
    loadCaptureRates,
    captureRateOf,
    submitActions,
    setAction,
    getAction,
    leaveBattle,
    settleBattle,
    skillName,
    unitName,
  }
})

/** 辅助：判断单位是否存活上场。 */
export function isActiveAlive(unit: UnitSnapshot): boolean {
  return unit.alive && unit.active
}
