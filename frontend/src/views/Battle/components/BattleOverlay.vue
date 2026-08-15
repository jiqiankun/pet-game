<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useBattleStore, isActiveAlive } from '../../../stores/battle'
import { useGameStore } from '../../../stores/game'
import { useMapStore } from '../../../stores/map'
import { apiGet } from '../../../api/client'
import type { ApiResponse } from '../../../types/api'
import type { AutoStrategy, BattleEvent, UnitSnapshot } from '../../../types/battle'
import type { InventoryItemView } from '../../../types/pet'
import {
  elementIconUrl,
  itemIconUrl,
  skillTypeIconUrl,
  statusIconUrl,
} from '../../../game-assets'
import {
  captureTier,
  elementRelationMark,
  skillTargetLabel,
} from '../../../utils/labels'

/**
 * 战斗浮层（战斗体验优化，P0）。
 * <p>
 * 全屏覆盖地图/来源页面的战斗 UI，由 battleStore 驱动，前端只提交行动意图。
 * 支持「战斗浮层化」：地图遇敌/Boss 战打开本浮层时底层页面保留、地图暂停，关闭后恢复探索。
 * 本组件不承载任何战斗计算，全部结果由后端 BattleEngine 裁定。
 */

const emit = defineEmits<{ (e: 'close'): void }>()

const battleStore = useBattleStore()
const gameStore = useGameStore()
const mapStore = useMapStore()

// ==================== 基础状态 ====================

const snapshot = computed(() => battleStore.snapshot)
const settlement = computed(() => battleStore.settlement)
const playerActive = computed(() => snapshot.value?.playerUnits.filter(isActiveAlive) ?? [])
const playerBench = computed(() => snapshot.value?.playerUnits.filter((u) => u.alive && !u.active) ?? [])
const enemyActive = computed(() => snapshot.value?.enemyUnits.filter((u) => u.alive && u.active && !u.captured) ?? [])
const isWild = computed(() => snapshot.value?.battleType === 'WILD')
const isBoss = computed(() => snapshot.value?.battleType === 'BOSS')
const uncapturable = computed(() => isBoss.value || snapshot.value?.uncapturable === true)

// ==================== 一级菜单 / 操作状态 ====================

type MenuKey = 'skills' | 'pet' | 'bag' | 'tactical' | 'capture' | null
/** 当前打开的一级子面板。 */
const activeMenu = ref<MenuKey>(null)
/** 当前操作的上场宠物（玩家 HUD 点击卡片切换）。 */
const operatorId = ref<string | null>(null)
/** 选中的技能（待目标/待确认）。 */
const formedSkill = ref<{ petId: string; skillId: string } | null>(null)
/** 单体目标选择状态。 */
const targeting = ref<{ petId: string; skillId: string } | null>(null)

/** 当前操作宠物。 */
const operator = computed<UnitSnapshot | null>(() => {
  const id = operatorId.value
  if (id) return snapshot.value?.playerUnits.find((u) => u.unitId === id) ?? null
  return playerActive.value[0] ?? null
})

/** 首个可攻击的敌方目标（技能克制预览用）。 */
const firstValidEnemy = computed(() => enemyActive.value[0] ?? null)

// ==================== 捕捉 ====================

const captureMode = ref(false)
const selectedBall = ref<InventoryItemView | null>(null)
const captureBalls = ref<InventoryItemView[]>([])

// ==================== 战术面板 ====================

const autoPanelOpen = ref(false)
const autoStrategy = ref<AutoStrategy>('BALANCED')
const autoSwitch = ref(true)
const autoSwitchHpThreshold = ref(25)
const autoUseRecoveryItem = ref(false)
const autoRecoveryHpThreshold = ref(35)
const autoRevive = ref(false)
const captureTargetId = ref<string | null>(null)

const strategyOptions: Array<{ value: AutoStrategy; label: string; desc: string }> = [
  { value: 'BALANCED', label: '均衡', desc: '攻击/治疗/控制平衡，默认推荐' },
  { value: 'AGGRESSIVE', label: '进攻', desc: '尽快结束战斗，强化斩杀与克制打击' },
  { value: 'DEFENSIVE', label: '稳健', desc: '降低死亡风险，提前恢复与换宠' },
  { value: 'CAPTURE', label: '捕捉', desc: '安全削血压至 1HP，避免误杀后捕捉' },
]

// ==================== 速度 / 自动播放 ====================

const battleSpeed = ref<1 | 2 | 3>(1)
const autoPlay = ref(false)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

const speedInterval = computed(() => {
  const map = { 1: 1500, 2: 600, 3: 200 }
  return map[battleSpeed.value]
})

function setSpeed(s: 1 | 2 | 3) {
  battleSpeed.value = s
  if (autoPlay.value) restartAutoPlay()
}

function toggleAutoPlay() {
  autoPlay.value = !autoPlay.value
  if (autoPlay.value) restartAutoPlay()
  else stopAutoPlay()
}

function restartAutoPlay() {
  stopAutoPlay()
  autoPlayTimer = setInterval(async () => {
    if (snapshot.value?.finished || battleStore.loading) {
      stopAutoPlay()
      autoPlay.value = false
      return
    }
    await battleStore.submitActions()
  }, speedInterval.value)
}

function stopAutoPlay() {
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

// ==================== VFX（复用阶段 3/14 素材） ====================

interface ActiveVfx {
  category: 'elemental' | 'combat'
  template: string
  targetId: string | null
  key: number
  durationMs: number
}

const activeVfx = ref<ActiveVfx | null>(null)
let activeVfxTimer: ReturnType<typeof setTimeout> | null = null
let vfxSequence = 0

const elementalTierBySkill: Record<string, 'small' | 'medium' | 'large' | 'ultimate'> = {
  SKILL_FLAME_BURST: 'medium', SKILL_BLAZING_SPIRIT: 'large', SKILL_METEOR_FALL: 'ultimate',
  SKILL_MAGMA_CLASH: 'medium', SKILL_INFERNO: 'large', SKILL_TIDAL_WAVE: 'medium',
  SKILL_TORRENT: 'medium', SKILL_EARTHQUAKE: 'large', SKILL_ROCK_BLAST: 'large',
  SKILL_GALE: 'medium', SKILL_AIR_CUTTER: 'medium', SKILL_THUNDER_STORM: 'large',
  SKILL_THUNDERBOLT: 'medium', SKILL_CHAIN_LIGHTNING: 'large', SKILL_LIGHT_BURST: 'medium',
  SKILL_PRISM: 'medium', SKILL_BLADE_STORM: 'medium',
}

const specialVfxBySkill: Record<string, string> = {
  SKILL_LEAVE_AT_ONE_HP: 'leave_one_hp', SKILL_BOOK_LEAVE_ALIVE: 'leave_one_hp',
  SKILL_ROCK_SHIELD: 'shield', SKILL_GOLDEN_GUARD: 'shield', SKILL_BATTLE_CRY: 'buff_up',
  SKILL_GUARD_ALLY: 'buff_up', SKILL_TAUNT: 'buff_up', SKILL_BOOK_TAUNT: 'buff_up',
  SKILL_ENEMY_HARDEN: 'buff_up', SKILL_BOOK_IRON_DEFENSE: 'buff_up', SKILL_BOOK_AGILITY: 'buff_up',
  SKILL_BOOK_FOCUS_ENERGY: 'buff_up', SKILL_BOOK_COUNTER: 'buff_up', SKILL_BOOK_BULK_UP: 'buff_up',
  SKILL_BOOK_CALM_MIND: 'buff_up', SKILL_ENTANGLE: 'control_bind', SKILL_GUST_TRAP: 'control_bind',
  SKILL_SILENT_FOG: 'control_bind', SKILL_THUNDER_WAVE: 'debuff_down', SKILL_CURSE: 'debuff_down',
  SKILL_BOOK_TOXIC: 'poison', SKILL_BOOK_LIFE_DRAIN: 'life_drain', SKILL_BOOK_SHIELD_BREAK: 'dispel',
  SKILL_BOOK_DISPEL: 'dispel', SKILL_REFORGE: 'buff_up', SKILL_WIND_GRACE: 'buff_up',
  SKILL_LIGHTNING_ROD: 'buff_up', SKILL_GROUP_GUARD: 'buff_up', SKILL_ENERGIZE: 'buff_up',
  SKILL_NIGHT_SHROUD: 'debuff_down', SKILL_MARK_TARGET: 'debuff_down', SKILL_ANTI_HEAL: 'debuff_down',
  SKILL_ELIMINATE: 'dispel',
}

const elementalNames = new Set(['fire', 'thunder', 'water', 'earth', 'wind', 'light', 'metal', 'dark', 'wood'])
const vfxEventPriority = [
  'CAPTURE_ATTEMPT', 'PHASE_TRANSITION', 'STUNNED', 'LIFE_STEAL', 'STATUS_REMOVED',
  'SHIELD_BROKEN', 'SHIELD_CREATED', 'HEAL', 'DAMAGE', 'SKILL_CAST', 'BUFF_APPLIED',
  'DEBUFF_APPLIED', 'STATUS_APPLIED',
]

function resolveSkillVfx(skillId: string | null | undefined, targetId: string | null): ActiveVfx | null {
  if (!skillId) return null
  const special = specialVfxBySkill[skillId]
  if (special) return { category: 'combat', template: special, targetId, key: 0, durationMs: 0 }
  const skill = battleStore.skillIndex[skillId]
  if (!skill) return null
  if (skill.effectType === 'HEAL') {
    return { category: 'combat', template: skill.target === 'ALLY_ALL' ? 'heal_aoe' : 'heal_small', targetId, key: 0, durationMs: 0 }
  }
  if (skill.effectType === 'SHIELD') return { category: 'combat', template: 'shield', targetId, key: 0, durationMs: 0 }
  const element = skill.element.toLowerCase()
  if (!elementalNames.has(element)) return null
  return { category: 'elemental', template: `${element}_${elementalTierBySkill[skillId] ?? 'small'}`, targetId, key: 0, durationMs: 0 }
}

function resolveEventVfx(event: BattleEvent): ActiveVfx | null {
  const targetId = event.targetId ?? event.sourceId ?? null
  switch (event.type) {
    case 'CAPTURE_ATTEMPT': return { category: 'combat', template: 'capture', targetId, key: 0, durationMs: 0 }
    case 'PHASE_TRANSITION': return { category: 'combat', template: 'boss_phase', targetId, key: 0, durationMs: 0 }
    case 'STUNNED': return { category: 'combat', template: 'leave_one_hp', targetId, key: 0, durationMs: 0 }
    case 'LIFE_STEAL': return { category: 'combat', template: 'life_drain', targetId: event.sourceId ?? targetId, key: 0, durationMs: 0 }
    case 'STATUS_REMOVED':
    case 'SHIELD_BROKEN': return { category: 'combat', template: 'dispel', targetId, key: 0, durationMs: 0 }
    case 'SHIELD_CREATED': return { category: 'combat', template: 'shield', targetId, key: 0, durationMs: 0 }
    case 'BUFF_APPLIED': return { category: 'combat', template: 'buff_up', targetId, key: 0, durationMs: 0 }
    case 'DEBUFF_APPLIED': return { category: 'combat', template: 'debuff_down', targetId, key: 0, durationMs: 0 }
    case 'STATUS_APPLIED': {
      if (event.statusId === 'POISON') return { category: 'combat', template: 'poison', targetId, key: 0, durationMs: 0 }
      if (event.statusId === 'REGEN') return { category: 'combat', template: 'buff_up', targetId, key: 0, durationMs: 0 }
      if (event.statusId === 'STEALTH') return { category: 'elemental', template: 'dark_small', targetId, key: 0, durationMs: 0 }
      if (['ROOT', 'SILENCE', 'CAPTURE_STUN'].includes(event.statusId ?? '')) {
        return { category: 'combat', template: 'control_bind', targetId, key: 0, durationMs: 0 }
      }
      return { category: 'combat', template: 'debuff_down', targetId, key: 0, durationMs: 0 }
    }
    case 'DAMAGE':
      if (event.data?.passive === true && typeof event.data.element === 'string') {
        const element = event.data.element.toLowerCase()
        if (elementalNames.has(element)) return { category: 'elemental', template: `${element}_small`, targetId, key: 0, durationMs: 0 }
      }
      return resolveSkillVfx(event.skillId, targetId)
    case 'SKILL_CAST': return resolveSkillVfx(event.skillId, targetId)
    case 'HEAL': return resolveSkillVfx(event.skillId, targetId) ?? { category: 'combat', template: 'heal_small', targetId, key: 0, durationMs: 0 }
    default: return null
  }
}

function playVfxForEvents(events: BattleEvent[]) {
  for (const type of vfxEventPriority) {
    const event = [...events].reverse().find((item) => item.type === type)
    if (!event) continue
    const asset = resolveEventVfx(event)
    if (!asset) continue
    const durationMs = Math.round(600 / battleSpeed.value)
    const key = ++vfxSequence
    activeVfx.value = { ...asset, key, durationMs }
    if (activeVfxTimer) clearTimeout(activeVfxTimer)
    activeVfxTimer = setTimeout(() => {
      if (activeVfx.value?.key === key) activeVfx.value = null
    }, durationMs)
    return
  }
}

function vfxStyle(vfx: ActiveVfx): Record<string, string> {
  return {
    backgroundImage: `url(/assets/fx/${vfx.category}/vfx_${vfx.template}_sheet.png)`,
    '--vfx-duration': `${vfx.durationMs}ms`,
  }
}

// ==================== 伤害飘字 ====================

interface DamageNum {
  key: number
  unitId: string
  text: string
  className: string
}
const damageNums = ref<DamageNum[]>([])
let dnSeq = 0

function spawnDamageNumbers(events: BattleEvent[]) {
  for (const event of events) {
    const targetId = event.targetId
    if (!targetId) continue
    let text = ''
    let className = ''
    switch (event.type) {
      case 'DAMAGE':
        text = event.critical ? `暴击! -${event.value}` : `-${event.value}`
        className = event.critical ? 'dn-crit' : event.elementRelation === 'ADVANTAGE' ? 'dn-advantage' : event.elementRelation === 'DISADVANTAGE' ? 'dn-disadvantage' : ''
        break
      case 'HEAL':
        text = `+${event.value} HP`
        className = 'dn-heal'
        break
      case 'SHIELD_CREATED':
        text = `护盾 +${event.value}`
        className = 'dn-shield'
        break
      case 'SHIELD_BROKEN':
        text = '护盾破碎'
        className = 'dn-shield'
        break
      case 'MISS':
        text = 'MISS'
        className = 'dn-miss'
        break
      case 'STATUS_TICK':
        text = `-${event.value}`
        className = 'dn-dot'
        break
      default:
        continue
    }
    if (!text) continue
    const key = ++dnSeq
    damageNums.value.push({ key, unitId: targetId, text, className })
    setTimeout(() => {
      damageNums.value = damageNums.value.filter((d) => d.key !== key)
    }, 1100)
  }
}

// ==================== 状态详情 ====================

const statusDetail = ref<{ unit: UnitSnapshot; statusId: string } | null>(null)

/** 状态详情的展示条目（避免模板内嵌套取值触发空值告警）。 */
const statusDetailItems = computed(() => {
  const d = statusDetail.value
  if (!d) return []
  return d.unit.statuses.filter((s) => s.statusId === d.statusId)
})

// ==================== 战斗日志折叠 ====================

const battleLogOpen = ref(false)

// ==================== 生命值辅助 ====================

function hpPercent(unit: UnitSnapshot): number {
  return unit.maxHp > 0 ? Math.max(0, Math.min(100, (unit.currentHp / unit.maxHp) * 100)) : 0
}

function cooldownOf(unit: UnitSnapshot, skillId: string): number {
  return unit.cooldowns[skillId] ?? 0
}

function unitArtUrl(unit: UnitSnapshot): string | null {
  if (!unit.artType || !unit.artId) return null
  if (unit.artType === 'BOSS') return `/assets/bosses/portraits/boss_${unit.artId}_portrait.png`
  return `/assets/pets/portraits/pet_${unit.artId}_portrait.png`
}

function levelText(unit: UnitSnapshot): string {
  return unit.effectiveLevel < unit.actualLevel
    ? `Lv.${unit.actualLevel} → ${unit.effectiveLevel}`
    : `Lv.${unit.actualLevel}`
}

// ==================== 技能不可用原因 ====================

function skillUnavailableReason(unit: UnitSnapshot, skillId: string): { blocked: boolean; reason: string } {
  if (battleStore.loading) return { blocked: true, reason: '结算中' }
  if (snapshot.value?.finished) return { blocked: true, reason: '战斗已结束' }
  if (battleStore.autoEnabled) return { blocked: true, reason: '自动战斗中' }
  if (!unit.alive || !unit.active) return { blocked: true, reason: '不在场上' }
  if (unit.charging) return { blocked: true, reason: '蓄力中' }
  const cd = cooldownOf(unit, skillId)
  if (cd > 0) return { blocked: true, reason: `还需 ${cd} 回合` }
  if (unit.statuses.some((s) => s.statusId === 'SILENCE')) return { blocked: true, reason: '被沉默，无法使用技能' }
  const skill = battleStore.skillIndex[skillId]
  if (skill) {
    if (skill.target === 'ENEMY_SINGLE' || skill.target === 'ENEMY_ALL') {
      if (!enemyActive.value.some((e) => e.alive && !e.captured)) return { blocked: true, reason: '没有可攻击的敌人' }
    }
    if (skill.target === 'ALLY_SINGLE' || skill.target === 'ALLY_ALL') {
      if (!snapshot.value?.playerUnits.some((p) => p.alive && p.currentHp < p.maxHp)) {
        return { blocked: true, reason: '没有需要治疗的队友' }
      }
    }
  }
  return { blocked: false, reason: '' }
}

// ==================== 技能克制预览 ====================

/** 技能对首个敌方目标的克制关系标记。 */
function skillRelationMark(skillId: string): { text: string; className: string } | null {
  const skill = battleStore.skillIndex[skillId]
  const enemy = firstValidEnemy.value
  if (!skill || !enemy) return null
  if (skill.element === operator.value?.element) return { text: '本属性', className: 'same-element' }
  return elementRelationMark(battleStore.elementRelation(skill.element, enemy.element))
}

// ==================== 捕捉率 ====================

function captureRateOf(unit: UnitSnapshot): number | null {
  if (!selectedBall.value) return null
  return battleStore.captureRateOf(unit.unitId, selectedBall.value.itemId)
}

// ==================== 行动选择 ====================

function selectOperator(unit: UnitSnapshot) {
  if (!unit.alive || !unit.active) return
  operatorId.value = unit.unitId
}

function openMenu(key: MenuKey) {
  // 关闭其他面板
  if (activeMenu.value === key) {
    activeMenu.value = null
    formedSkill.value = null
    targeting.value = null
    return
  }
  activeMenu.value = key
  formedSkill.value = null
  targeting.value = null
  if (key === 'capture') {
    captureMode.value = true
    loadCaptureBalls()
  } else {
    captureMode.value = false
  }
  if (key === 'bag') {
    loadBattleItems()
  }
}

function closeMenus() {
  activeMenu.value = null
  formedSkill.value = null
  targeting.value = null
  captureMode.value = false
}

/** 点击技能：进入选中/目标选择。 */
function handleSkillClick(skillId: string) {
  if (!operator.value) return
  const reason = skillUnavailableReason(operator.value, skillId)
  if (reason.blocked) return
  const skill = battleStore.skillIndex[skillId]
  if (!skill) return
  if (skill.target === 'ENEMY_SINGLE' || skill.target === 'ALLY_SINGLE') {
    formedSkill.value = null
    targeting.value = { petId: operator.value.unitId, skillId }
  } else {
    formedSkill.value = { petId: operator.value.unitId, skillId }
    targeting.value = null
  }
}

/** 确认无需选目标的技能（全体/自身）。 */
function confirmFormedSkill() {
  if (!formedSkill.value) return
  battleStore.setAction({ type: 'SKILL', petId: formedSkill.value.petId, skillId: formedSkill.value.skillId })
  formedSkill.value = null
  activeMenu.value = null
}

/** 点击敌方单位：捕捉或单体技能目标。 */
function handleEnemyClick(enemy: UnitSnapshot) {
  if (!enemy.alive || !enemy.active || enemy.captured) return
  if (captureMode.value) {
    if (!selectedBall.value) return
    battleStore.setAction({
      type: 'CAPTURE',
      petId: operator.value?.unitId ?? enemy.unitId,
      itemId: selectedBall.value.itemId,
      targetId: enemy.unitId,
    })
    closeMenus()
    return
  }
  if (targeting.value) {
    battleStore.setAction({ type: 'SKILL', petId: targeting.value.petId, skillId: targeting.value.skillId, targetId: enemy.unitId })
    targeting.value = null
    activeMenu.value = null
  }
}

/** 点击己方单位（治疗技能目标选择）。 */
function handleAllyClick(ally: UnitSnapshot) {
  if (!targeting.value) return
  if (!ally.alive) return
  battleStore.setAction({ type: 'SKILL', petId: targeting.value.petId, skillId: targeting.value.skillId, targetId: ally.unitId })
  targeting.value = null
  activeMenu.value = null
}

function cancelFormed() {
  formedSkill.value = null
  targeting.value = null
}

/** 防御当前操作宠物。 */
function doDefend() {
  if (!operator.value) return
  battleStore.setAction({ type: 'DEFEND', petId: operator.value.unitId })
  closeMenus()
}

/** 逃跑（野生战，用户裁决：必定成功、同战败结算）。 */
function handleFlee() {
  if (!operator.value) return
  battleStore.setAction({ type: 'FLEE', petId: operator.value.unitId })
  closeMenus()
}

/** 换宠：收集 SWITCH 行动。 */
function handleSwitch(bench: UnitSnapshot) {
  if (!operator.value) return
  battleStore.setAction({ type: 'SWITCH', petId: operator.value.unitId, switchPetId: bench.unitId })
  closeMenus()
}

/** 战斗背包使用道具：提交 ITEM 行动。 */
function useBattleItem(item: InventoryItemView, target: UnitSnapshot) {
  battleStore.setAction({ type: 'ITEM', petId: operator.value?.unitId ?? target.unitId, itemId: item.itemId, targetId: target.unitId })
  closeMenus()
}

// ==================== 自动战斗 ====================

async function toggleAutoBattle() {
  if (!snapshot.value) return
  try {
    await battleStore.configureAuto(snapshot.value.battleId, {
      enabled: !battleStore.autoEnabled,
      strategy: autoStrategy.value,
      autoSwitch: autoSwitch.value,
      autoSwitchHpThreshold: autoSwitchHpThreshold.value,
      autoUseRecoveryItem: autoUseRecoveryItem.value,
      autoRecoveryHpThreshold: autoRecoveryHpThreshold.value,
      autoRevive: autoRevive.value,
      captureTargetId: captureTargetId.value,
    })
    if (battleStore.autoEnabled && !autoPlay.value && !snapshot.value.finished) {
      autoPlay.value = true
      restartAutoPlay()
    }
    if (!battleStore.autoEnabled) captureTargetId.value = null
  } catch {
    // 错误已写入 store.error
  }
}

/** 仅保存自动战斗策略/开关偏好（保持当前开关状态）。 */
async function saveAutoPreference() {
  if (!snapshot.value) return
  try {
    await battleStore.configureAuto(snapshot.value.battleId, {
      enabled: battleStore.autoEnabled,
      strategy: autoStrategy.value,
      autoSwitch: autoSwitch.value,
      autoSwitchHpThreshold: autoSwitchHpThreshold.value,
      autoUseRecoveryItem: autoUseRecoveryItem.value,
      autoRecoveryHpThreshold: autoRecoveryHpThreshold.value,
      autoRevive: autoRevive.value,
      captureTargetId: captureTargetId.value,
    })
  } catch {
    // 错误已写入 store.error
  }
}

/** 已选行动的展示文案。 */
function actionText(action: { type?: string; skillId?: string } | undefined): string {
  if (!action) return ''
  switch (action.type) {
    case 'SKILL':
      return battleStore.skillName(action.skillId)
    case 'SWITCH':
      return '换宠'
    case 'CAPTURE':
      return '捕捉'
    case 'FLEE':
      return '逃跑'
    case 'ITEM':
      return '使用道具'
    default:
      return '防御'
  }
}

// ==================== 捕捉球加载 ====================

async function loadCaptureBalls() {
  try {
    const res = await apiGet<{ items: InventoryItemView[]; gold: number }>('/api/inventory')
    captureBalls.value = ((res as ApiResponse<{ items: InventoryItemView[] }>).data.items || [])
      .filter((i) => i.itemType === 'CAPTURE_BALL' && i.quantity > 0)
    selectedBall.value = captureBalls.value[0] ?? null
  } catch {
    captureBalls.value = []
  }
}

// ==================== 捕捉去向选择 ====================

const needDestChoice = computed(() => {
  if (!snapshot.value || snapshot.value.battleType !== 'WILD') return false
  return snapshot.value.enemyUnits.some((u) => u.captured) && gameStore.teamMembers.length < 6
})
const joinTeamChoice = ref(false)

async function confirmSettle() {
  await battleStore.settleBattle(joinTeamChoice.value)
}

// ==================== 结算 / 返回 ====================

/** 战斗结束返回：清结算并通知父级关闭浮层。 */
function handleReturn() {
  if (settlement.value) {
    emit('close')
  }
}

// ==================== 生命周期与监听 ====================

onMounted(() => {
  battleStore.loadSkillConfig()
  battleStore.loadElementsConfig()
  battleStore.loadAutoPreference()
  // 默认选中第一个上场宠物
  if (playerActive.value[0]) operatorId.value = playerActive.value[0].unitId
})

onBeforeUnmount(() => {
  stopAutoPlay()
  if (activeVfxTimer) clearTimeout(activeVfxTimer)
})

// 新战斗开始 / 偏好加载完成时同步面板默认值
watch(
  () => [snapshot.value?.battleId, battleStore.autoPreference],
  () => {
    const pref = battleStore.autoPreference
    if (pref) {
      autoStrategy.value = pref.strategy
      autoSwitch.value = pref.autoSwitch
      autoSwitchHpThreshold.value = pref.autoSwitchHpThreshold
      autoUseRecoveryItem.value = pref.autoUseRecoveryItem
      autoRecoveryHpThreshold.value = pref.autoRecoveryHpThreshold
      autoRevive.value = pref.autoRevive
    }
    if (playerActive.value[0]) operatorId.value = playerActive.value[0].unitId
  },
  { immediate: true },
)

// 野生战斗回合变化后实时刷新捕捉率
watch(
  () => snapshot.value?.currentRound,
  () => {
    if (captureMode.value && !snapshot.value?.finished) battleStore.loadCaptureRates()
  },
)

// 事件 → VFX 与伤害飘字
watch(
  () => snapshot.value?.events,
  (events) => {
    if (events?.length) {
      playVfxForEvents(events)
      spawnDamageNumbers(events)
    }
  },
)

// 战斗结束自动停止自动播放；野生胜利标记野怪移除
watch(
  () => snapshot.value?.finished,
  (finished) => {
    if (finished) {
      stopAutoPlay()
      autoPlay.value = false
    }
  },
)

// 结算完成后：野生胜利标记地图野怪移除
watch(settlement, (result) => {
  if (!result) return
  if (result.playerWon && mapStore.activeEncounterSpawnId) {
    mapStore.markWildDefeated(mapStore.activeEncounterSpawnId)
    mapStore.activeEncounterSpawnId = null
  }
  if (!result.playerWon && !result.fled) {
    mapStore.activeEncounterSpawnId = null
  }
})

// ESC 取消当前选择
function onKeydown(ev: KeyboardEvent) {
  if (ev.key === 'Escape') {
    if (targeting.value || formedSkill.value) {
      cancelFormed()
    } else if (activeMenu.value) {
      activeMenu.value = null
    }
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// ==================== 战斗背包（仅战斗内可用物品） ====================

const battleItems = ref<InventoryItemView[]>([])
const bagTarget = ref<UnitSnapshot | null>(null)
const bagItem = ref<InventoryItemView | null>(null)

async function loadBattleItems() {
  try {
    const res = await apiGet<{ items: InventoryItemView[]; gold: number }>('/api/inventory')
    const items = ((res as ApiResponse<{ items: InventoryItemView[] }>).data.items || [])
      .filter((i) => i.usableInBattle && i.quantity > 0)
    battleItems.value = items
  } catch {
    battleItems.value = []
  }
}

/** 打开战斗背包并选择物品。 */
function pickBagItem(item: InventoryItemView) {
  bagItem.value = item
  bagTarget.value = null
}

/** 选择道具目标（我方存活宠物）。 */
function setBagTarget(unit: UnitSnapshot) {
  if (!bagItem.value) return
  bagTarget.value = unit
}

/** 确认使用道具。 */
function confirmUseItem() {
  if (!bagItem.value || !bagTarget.value) return
  useBattleItem(bagItem.value, bagTarget.value)
}

/** 道具可用目标列表（HEAL_HP 需存活掉血；REVIVE 需倒下）。 */
function bagTargets(): UnitSnapshot[] {
  const units = snapshot.value?.playerUnits ?? []
  return units.filter((u) => (bagItem.value?.itemType === 'REVIVE' ? !u.alive : u.alive && u.currentHp < u.maxHp))
}

// ==================== 结算刷新 ====================

const victoryInteractionText = computed(() => {
  const interaction = settlement.value?.victoryInteraction
  if (!interaction) return ''
  const name = interaction.winnerName || '对方'
  return (interaction.text || '').replace(/\{winnerName\}/g, name)
})
</script>

<template>
  <div class="battle-overlay">
    <!-- 未开始（独立模式兜底）：不渲染本体 -->
    <template v-if="snapshot">
      <!--=========== 顶部 HUD ===========-->
      <div class="hud-top">
        <div class="hud-top-left">
          <span class="round-badge">回合 {{ snapshot.currentRound }}</span>
          <span v-if="isWild" class="wild-badge">野生遭遇</span>
          <span v-if="isBoss" class="boss-badge">Boss 战</span>
          <span v-if="snapshot.finished" class="result-badge" :class="snapshot.fled ? 'flee' : snapshot.winner === 'PLAYER' ? 'win' : 'lose'">
            {{ snapshot.fled ? '已逃跑' : snapshot.winner === 'PLAYER' ? '胜利' : '失败' }}
          </span>
        </div>
        <div class="hud-top-right">
          <span class="auto-indicator" :class="{ on: battleStore.autoEnabled }" @click="toggleAutoBattle">
            {{ battleStore.autoEnabled ? '⚡ 自动' : '手动' }}
          </span>
          <div class="speed-control">
            <button v-for="s in ([1, 2, 3] as const)" :key="s" class="speed-btn" :class="{ active: battleSpeed === s }" @click="setSpeed(s)">{{ s }}x</button>
          </div>
          <button v-if="!snapshot.finished" class="auto-btn" :class="{ active: autoPlay }" @click="toggleAutoPlay">
            {{ autoPlay ? '自动中' : '自动' }}
          </button>
          <button class="log-btn" @click="battleLogOpen = !battleLogOpen">日志</button>
        </div>
      </div>

      <!-- 行动顺序条 -->
      <div v-if="battleStore.actionOrder.length" class="order-bar">
        <span class="order-label">行动顺序</span>
        <span v-for="(unitId, idx) in battleStore.actionOrder" :key="unitId" class="order-chip" :class="{ enemy: unitId.startsWith('ENEMY') }">
          {{ idx + 1 }}. {{ battleStore.unitName(unitId) }}
        </span>
      </div>

      <!--=========== 敌方 HUD ===========-->
      <div class="enemy-hud">
        <div
          v-for="unit in snapshot.enemyUnits"
          :key="unit.unitId"
          class="unit-card enemy"
          :class="{
            dead: !unit.alive,
            bench: !unit.active && unit.alive && !unit.captured,
            captured: unit.captured,
            targetable: (targeting !== null || captureMode) && unit.alive && unit.active && !unit.captured,
          }"
          @click="handleEnemyClick(unit)"
        >
          <div v-if="activeVfx?.targetId === unit.unitId" :key="activeVfx.key" class="battle-vfx" :style="vfxStyle(activeVfx)"></div>
          <div class="dn-layer">
            <span v-for="dn in damageNums.filter((d) => d.unitId === unit.unitId)" :key="dn.key" class="damage-num" :class="dn.className">{{ dn.text }}</span>
          </div>
          <img v-if="unitArtUrl(unit)" class="unit-art" :src="unitArtUrl(unit)!" alt="" />
          <div class="unit-name">
            {{ unit.name }}
            <img class="mini-icon" :src="elementIconUrl(unit.element)" alt="" />
            <span class="unit-level">{{ levelText(unit) }}</span>
            <span v-if="unit.elite" class="elite-badge">✨精英</span>
          </div>
          <div class="hp-bar"><div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div></div>
          <div class="unit-hp">{{ unit.currentHp }} / {{ unit.maxHp }}</div>
          <div class="status-row">
            <span v-for="status in unit.statuses" :key="status.statusId" class="status-tag" :class="{ 'capture-stun': status.captureStun }" @click.stop="statusDetail = { unit, statusId: status.statusId }">
              <img :src="statusIconUrl(status.statusId)" alt="" />
              {{ status.remainingTurns }}{{ (status.stack ?? 1) > 1 ? '×' + status.stack : '' }}
            </span>
            <span v-if="unit.charging" class="status-tag charging">蓄力{{ unit.chargeRemaining }}</span>
            <span v-if="unit.defending" class="status-tag defending">防御</span>
            <span v-if="unit.captured" class="status-tag captured-tag">已捕捉</span>
            <span v-if="captureMode && unit.alive && unit.active && !unit.captured && captureRateOf(unit) !== null" class="capture-tier" :style="{ color: captureTier(captureRateOf(unit)!).color }">
              {{ captureTier(captureRateOf(unit)!).label }}
            </span>
            <span v-if="unit.statuses.some((s) => s.captureStun)" class="status-tag safe-window">
              <img :src="statusIconUrl('CAPTURE_STUN')" alt="" />可捕捉
            </span>
          </div>
        </div>
      </div>

      <!--=========== 中部：操作提示条 ===========-->
      <div v-if="targeting || formedSkill || captureMode" class="prompt-bar">
        <template v-if="targeting">
          <span class="prompt-text">选择技能目标（{{ battleStore.skillName(targeting.skillId) }}）</span>
          <button class="btn-link" @click="cancelFormed">取消</button>
        </template>
        <template v-else-if="formedSkill">
          <span class="prompt-text">
            使用 {{ battleStore.skillName(formedSkill.skillId) }}
            <span v-if="skillRelationMark(formedSkill.skillId)" class="relation-mark" :class="skillRelationMark(formedSkill.skillId)!.className">
              {{ skillRelationMark(formedSkill.skillId)!.text }}
            </span>
          </span>
          <button class="btn-primary small" @click="confirmFormedSkill">确认</button>
          <button class="btn-link" @click="cancelFormed">取消</button>
        </template>
        <template v-else-if="captureMode">
          <span class="prompt-text">选择捕捉球，然后点击目标</span>
          <button
            v-for="ball in captureBalls"
            :key="ball.itemId"
            class="ball-btn"
            :class="{ selected: selectedBall?.itemId === ball.itemId }"
            @click="selectedBall = ball"
          >
            <img class="ball-icon" :src="itemIconUrl(ball.itemId)" alt="" />{{ ball.name }} ×{{ ball.quantity }}
          </button>
          <button class="btn-link" @click="closeMenus">取消</button>
        </template>
      </div>

      <!--=========== 我方 HUD（点击选择操作宠物） ===========-->
      <div class="player-hud">
        <div
          v-for="unit in snapshot.playerUnits"
          :key="unit.unitId"
          class="unit-card player"
          :class="{
            dead: !unit.alive,
            bench: !unit.active && unit.alive,
            operator: operator?.unitId === unit.unitId,
            targetable: targeting !== null && unit.alive,
          }"
          @click="targeting ? handleAllyClick(unit) : selectOperator(unit)"
        >
          <div v-if="activeVfx?.targetId === unit.unitId" :key="activeVfx.key" class="battle-vfx" :style="vfxStyle(activeVfx)"></div>
          <div class="dn-layer">
            <span v-for="dn in damageNums.filter((d) => d.unitId === unit.unitId)" :key="dn.key" class="damage-num" :class="dn.className">{{ dn.text }}</span>
          </div>
          <img v-if="unitArtUrl(unit)" class="unit-art" :src="unitArtUrl(unit)!" alt="" />
          <div class="unit-name">
            {{ unit.name }}
            <img class="mini-icon" :src="elementIconUrl(unit.element)" alt="" />
            <span class="unit-level">{{ levelText(unit) }}</span>
          </div>
          <div class="hp-bar"><div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div></div>
          <div class="unit-hp">{{ unit.currentHp }} / {{ unit.maxHp }}</div>
          <div class="status-row">
            <span v-for="status in unit.statuses" :key="status.statusId" class="status-tag" :class="{ 'capture-stun': status.captureStun }" @click.stop="statusDetail = { unit, statusId: status.statusId }">
              <img :src="statusIconUrl(status.statusId)" alt="" />
              {{ status.remainingTurns }}{{ (status.stack ?? 1) > 1 ? '×' + status.stack : '' }}
            </span>
            <span v-if="unit.charging" class="status-tag charging">蓄力{{ unit.chargeRemaining }}</span>
            <span v-if="unit.defending" class="status-tag defending">防御</span>
          </div>
          <div v-if="battleStore.getAction(unit.unitId)" class="chosen-action">
            {{ actionText(battleStore.getAction(unit.unitId)) }}
          </div>
        </div>
      </div>

      <!--=========== 底部控制栏 ===========-->
      <div v-if="!snapshot.finished" class="control-bar">
        <button class="ctrl-btn" :class="{ active: activeMenu === 'skills' }" @click="openMenu('skills')">⚔ 攻击</button>
        <button class="ctrl-btn" :class="{ active: activeMenu === 'pet' }" @click="openMenu('pet')">🐾 宠物</button>
        <button class="ctrl-btn" :class="{ active: activeMenu === 'bag' }" @click="openMenu('bag')">🎒 背包</button>
        <button class="ctrl-btn" :class="{ active: activeMenu === 'tactical' }" @click="openMenu('tactical')">⚙ 战术</button>
        <button v-if="isWild && !uncapturable" class="ctrl-btn capture" :class="{ active: activeMenu === 'capture' }" @click="openMenu('capture')">🔮 捕捉</button>
        <button class="ctrl-btn end" :disabled="battleStore.loading" @click="battleStore.submitActions()">
          {{ battleStore.loading ? '结算中...' : '结束回合' }}
        </button>
      </div>

      <!--=========== 技能面板 ===========-->
      <div v-if="activeMenu === 'skills' && operator" class="sheet-panel">
        <div class="sheet-head">
          <span class="sheet-title">技能 · {{ operator.name }}</span>
          <button class="btn-link" @click="closeMenus">关闭</button>
        </div>
        <div class="skill-grid">
          <button
            v-for="skillId in operator.skillIds"
            :key="skillId"
            class="skill-btn"
            :class="{ disabled: skillUnavailableReason(operator, skillId).blocked }"
            :disabled="skillUnavailableReason(operator, skillId).blocked || battleStore.loading"
            @click="handleSkillClick(skillId)"
          >
            <span class="skill-inner">
              <img class="skill-icon" :src="skillTypeIconUrl(battleStore.skillIndex[skillId] ?? { skillId })" alt="" />
              <span class="skill-info">
                <span class="skill-name">
                  {{ battleStore.skillName(skillId) }}
                  <span v-if="skillRelationMark(skillId)" class="relation-mark" :class="skillRelationMark(skillId)!.className">{{ skillRelationMark(skillId)!.text }}</span>
                </span>
                <span class="skill-desc">{{ battleStore.skillIndex[skillId]?.description ?? '' }}</span>
                <span class="skill-meta">
                  <span v-if="skillTargetLabel(battleStore.skillIndex[skillId]?.target)" class="meta-item">{{ skillTargetLabel(battleStore.skillIndex[skillId]?.target) }}</span>
                  <span v-if="cooldownOf(operator, skillId) > 0" class="meta-item cd">CD{{ cooldownOf(operator, skillId) }}</span>
                  <span v-else class="meta-item ready">可用</span>
                  <span v-if="skillUnavailableReason(operator, skillId).blocked" class="meta-item blocked">{{ skillUnavailableReason(operator, skillId).reason }}</span>
                </span>
              </span>
            </span>
          </button>
        </div>
      </div>

      <!--=========== 换宠面板 ===========-->
      <div v-if="activeMenu === 'pet'" class="sheet-panel">
        <div class="sheet-head">
          <span class="sheet-title">换宠</span>
          <button class="btn-link" @click="closeMenus">关闭</button>
        </div>
        <div class="switch-list">
          <div v-for="bench in playerBench" :key="bench.unitId" class="switch-item" @click="handleSwitch(bench)">
            <img v-if="unitArtUrl(bench)" class="switch-art" :src="unitArtUrl(bench)!" alt="" />
            <div class="switch-info">
              <div class="switch-name">{{ bench.name }}</div>
              <div class="hp-bar"><div class="hp-fill" :style="{ width: hpPercent(bench) + '%' }"></div></div>
              <div class="unit-hp">{{ bench.currentHp }} / {{ bench.maxHp }}</div>
            </div>
            <button class="btn-primary small">换上</button>
          </div>
          <p v-if="playerBench.length === 0" class="sheet-empty">没有可换上场的候补宠物</p>
        </div>
      </div>

      <!--=========== 战斗背包面板 ===========-->
      <div v-if="activeMenu === 'bag'" class="sheet-panel">
        <div class="sheet-head">
          <span class="sheet-title">战斗背包</span>
          <button class="btn-link" @click="closeMenus">关闭</button>
        </div>
        <div v-if="!bagItem" class="bag-list">
          <div v-for="item in battleItems" :key="item.itemId" class="bag-item" @click="pickBagItem(item)">
            <img class="bag-icon" :src="itemIconUrl(item.itemId)" alt="" />
            <div class="bag-info">
              <div class="bag-name">{{ item.name }} ×{{ item.quantity }}</div>
              <div class="bag-desc">{{ item.description }}</div>
            </div>
          </div>
          <p v-if="battleItems.length === 0" class="sheet-empty">战斗内没有可用道具（恢复/复苏类）</p>
        </div>
        <div v-else class="bag-target">
          <div class="bag-item-head">
            <span>『{{ bagItem.name }}』选择目标</span>
            <button class="btn-link" @click="bagItem = null">返回</button>
          </div>
          <div class="bag-target-list">
            <div
              v-for="unit in bagTargets()"
              :key="unit.unitId"
              class="bag-target-item"
              :class="{ selected: bagTarget?.unitId === unit.unitId }"
              @click="setBagTarget(unit)"
            >
              <span>{{ unit.name }} <span v-if="!unit.alive" class="dead-text">（倒下）</span></span>
              <span class="unit-hp">{{ unit.currentHp }} / {{ unit.maxHp }}</span>
            </div>
            <p v-if="bagTargets().length === 0" class="sheet-empty">没有符合条件的道具目标</p>
          </div>
          <button class="btn-primary small" :disabled="!bagTarget" @click="confirmUseItem">确认使用</button>
        </div>
      </div>

      <!--=========== 战术面板 ===========-->
      <div v-if="activeMenu === 'tactical'" class="sheet-panel">
        <div class="sheet-head">
          <span class="sheet-title">战术</span>
          <button class="btn-link" @click="closeMenus">关闭</button>
        </div>
        <div class="tactical-grid">
          <button class="tact-btn" @click="doDefend">🛡 防御{{ operator ? ' · ' + operator.name : '' }}</button>
          <button v-if="isWild && !uncapturable" class="tact-btn flee" @click="handleFlee">🏃 逃跑</button>
        </div>
        <div class="auto-section">
          <button class="auto-toggle" :class="{ active: battleStore.autoEnabled }" :disabled="battleStore.loading" @click="toggleAutoBattle">
            {{ battleStore.autoEnabled ? '■ 停止自动战斗' : '▶ 自动战斗' }}
          </button>
          <span v-if="battleStore.autoEnabled" class="auto-strategy-tag">{{ strategyOptions.find((s) => s.value === autoStrategy)?.label }}策略</span>
          <button class="btn-link" @click="autoPanelOpen = !autoPanelOpen">{{ autoPanelOpen ? '收起设置' : '策略设置' }}</button>
        </div>
        <div v-if="autoPanelOpen" class="auto-settings">
          <div class="setting-row">
            <span class="setting-label">策略预设</span>
            <div class="strategy-options">
              <label v-for="opt in strategyOptions" :key="opt.value" class="strategy-option" :class="{ active: autoStrategy === opt.value }">
                <input v-model="autoStrategy" type="radio" name="autoStrategy" :value="opt.value" />
                <span class="strategy-name">{{ opt.label }}</span>
                <span class="strategy-desc">{{ opt.desc }}</span>
              </label>
            </div>
          </div>
          <div v-if="autoStrategy === 'CAPTURE'" class="setting-row">
            <span class="setting-label">捕捉目标</span>
            <select v-model="captureTargetId" class="setting-select">
              <option :value="null">自动选择（最低 HP 可捕捉敌人）</option>
              <option v-for="enemy in snapshot.enemyUnits.filter((u) => u.alive && !u.captured)" :key="enemy.unitId" :value="enemy.unitId">{{ enemy.name }} Lv.{{ enemy.level }}</option>
            </select>
          </div>
          <div class="setting-row">
            <label class="setting-check">
              <input v-model="autoSwitch" type="checkbox" />自动换宠（HP 低于 <input v-model.number="autoSwitchHpThreshold" type="number" min="5" max="80" class="threshold-input" />% 时考虑）
            </label>
          </div>
          <div class="setting-row">
            <label class="setting-check">
              <input v-model="autoUseRecoveryItem" type="checkbox" />自动使用恢复道具（HP 低于 <input v-model.number="autoRecoveryHpThreshold" type="number" min="5" max="80" class="threshold-input" />% 时使用）
            </label>
          </div>
          <div class="setting-actions">
            <button class="btn-secondary small" :disabled="battleStore.loading" @click="saveAutoPreference">保存设置</button>
            <span class="setting-hint">消耗型道具默认关闭，不会静默消耗资源</span>
          </div>
        </div>
      </div>

      <!--=========== 战斗日志 ===========-->
      <div v-if="battleLogOpen" class="event-log">
        <div class="log-list">
          <p v-for="(line, index) in battleStore.eventLog" :key="index" class="log-line">{{ line }}</p>
        </div>
      </div>

      <p v-if="battleStore.error" class="error-text">{{ battleStore.error }}</p>

      <!--=========== 捕捉去向选择 ===========-->
      <div v-if="snapshot.finished && needDestChoice && !settlement" class="result-overlay">
        <div class="result-card">
          <h3>捕捉成功！</h3>
          <p class="dest-desc">队伍当前 {{ gameStore.teamMembers.length }}/6 只，被捕捉的宠物可以直接加入队伍，也可以留在仓库。</p>
          <label class="dest-checkbox">
            <input v-model="joinTeamChoice" type="checkbox" />捕捉后直接加入队伍
          </label>
          <button class="btn-primary" :disabled="battleStore.loading" @click="confirmSettle">
            {{ battleStore.loading ? '结算中...' : '确认结算' }}
          </button>
        </div>
      </div>

      <!--=========== 结算面板 ===========-->
      <div v-if="settlement" class="result-overlay">
        <div class="result-card">
          <h3>战斗结算</h3>
          <div class="settlement-summary">
            <span v-if="settlement.fled" class="reward-item none">逃跑成功，无奖励</span>
            <template v-else>
              <span v-if="settlement.playerWon" class="reward-item exp">经验 +{{ settlement.expGained }}</span>
              <span v-if="settlement.playerWon" class="reward-item gold">金币 +{{ settlement.goldGained }}</span>
              <span v-if="!settlement.playerWon" class="reward-item none">无奖励（战败零惩罚）</span>
            </template>
          </div>
          <div v-if="settlement.defeat" class="defeat-section">
            <div class="defeat-message">“{{ settlement.defeat.message }}”</div>
            <div class="defeat-detail">你被送回了恢复点，队伍 {{ settlement.defeat.healedPets }} 只宠物已全部恢复。未损失任何金币、经验与物品。</div>
          </div>
          <div v-if="settlement.victoryInteraction" class="victory-interaction-section">
            <div class="victory-interaction-speaker">
              {{ settlement.victoryInteraction.winnerName || '对方' }}
              <span v-if="settlement.victoryInteraction.winnerType === 'BOSS'" class="tag boss">BOSS</span>
              <span v-else-if="settlement.victoryInteraction.winnerType === 'WILD_PET'" class="tag wild">野生</span>
            </div>
            <div v-if="settlement.victoryInteraction.cry" class="victory-interaction-cry">{{ settlement.victoryInteraction.cry }}</div>
            <div class="victory-interaction-text">{{ victoryInteractionText }}</div>
          </div>
          <div v-if="settlement.capturedPets.length" class="captured-section">
            <span class="captured-label">捕捉成功：</span>
            <div v-for="cp in settlement.capturedPets" :key="cp.petId" class="captured-item">
              {{ cp.name }} Lv.{{ cp.level }}（{{ rarityLabel(cp.rarity) }}）
              <span v-if="cp.specialAppearance" class="tag special">特殊外观</span>
              <span v-if="cp.extraSkillIds.length" class="tag rare-skill">稀有技能</span>
              <span v-if="cp.teamPosition" class="tag team">已入队 · 位置 {{ cp.teamPosition }}</span>
              <span v-else class="tag storage">已进仓库</span>
            </div>
          </div>
          <div v-if="settlement.drops.length" class="drops-section">
            <span class="drops-label">掉落道具：</span>
            <span v-for="drop in settlement.drops" :key="drop.itemId" class="drop-tag">{{ drop.name }} ×{{ drop.quantity }}</span>
          </div>
          <div class="hp-writeback-section">
            <span class="writeback-label">宠物 HP 回写：</span>
            <div v-for="wb in settlement.hpWritebacks" :key="wb.petId" class="writeback-item">
              {{ wb.name }}：{{ wb.beforeHp }} → {{ wb.afterHp }}<span v-if="!wb.alive" class="dead-tag">倒下</span>
            </div>
          </div>
          <button class="btn-primary" :disabled="battleStore.loading" @click="handleReturn">返回</button>
        </div>
      </div>

      <!--=========== 状态详情浮层 ===========-->
      <div v-if="statusDetail" class="status-detail-mask" @click.self="statusDetail = null">
        <div class="status-detail-card">
          <h4>{{ statusDetail.unit.name }} · 状态详情</h4>
          <div
            v-for="status in statusDetailItems"
            :key="status.statusId"
            class="status-detail-item"
          >
            <img :src="statusIconUrl(status.statusId)" alt="" />
            <div>
              <div class="sd-name">{{ status.name }}</div>
              <div class="sd-category">{{ status.category }}</div>
              <div class="sd-turns">剩余 {{ status.remainingTurns }} 回合{{ (status.stack ?? 1) > 1 ? ' · 叠层 ' + status.stack : '' }}</div>
            </div>
          </div>
          <button class="btn-primary small" @click="statusDetail = null">关闭</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
import { rarityLabel } from '../../../utils/labels'
export default { name: 'BattleOverlay' }
</script>

<style scoped>
.battle-overlay {
  position: fixed;
  inset: 0;
  z-index: 500;
  display: flex;
  flex-direction: column;
  background: linear-gradient(rgba(20, 26, 34, 0.92), rgba(20, 26, 34, 0.92)),
    url(/assets/backgrounds/battle_bg_meadow.png);
  background-size: cover;
  background-position: center;
  color: #e8eef7;
  padding: 12px;
  box-sizing: border-box;
  overflow-y: auto;
}

/* ===== 顶部 HUD ===== */
.hud-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.hud-top-left, .hud-top-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.round-badge { background-color: var(--color-primary); color: #fff; padding: 4px 12px; border-radius: 12px; font-size: 14px; }
.wild-badge { background-color: #e8f5e9; color: #2e7d32; padding: 4px 10px; border-radius: 12px; font-size: 12px; }
.boss-badge { background-color: #fde0dc; color: #b71c1c; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.result-badge { padding: 4px 12px; border-radius: 12px; font-size: 14px; color: #fff; }
.result-badge.win { background-color: #7ED321; }
.result-badge.lose { background-color: #d32f2f; }
.result-badge.flee { background-color: #8e8e93; }
.auto-indicator { font-size: 12px; padding: 3px 10px; border-radius: 10px; background: #2a2f3a; color: #8e8e93; cursor: pointer; }
.auto-indicator.on { background: rgba(56, 161, 105, 0.2); color: #7ed321; }
.speed-control { display: flex; gap: 4px; }
.speed-btn { padding: 2px 8px; border: 1px solid #3a4150; border-radius: 4px; background: #2a2f3a; color: #cbd5e0; cursor: pointer; font-size: 12px; }
.speed-btn.active { background-color: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.auto-btn { padding: 2px 10px; border: 1px solid var(--color-secondary, #6c757d); border-radius: 4px; background: #2a2f3a; color: #cbd5e0; cursor: pointer; font-size: 12px; }
.auto-btn.active { background-color: var(--color-success, #38a169); color: #fff; }
.log-btn { padding: 2px 10px; border: 1px solid #3a4150; border-radius: 4px; background: #2a2f3a; color: #cbd5e0; cursor: pointer; font-size: 12px; }

.order-bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 4px 10px; margin-bottom: 8px; background: #2a2f3a; border-radius: 6px; font-size: 12px; }
.order-label { color: #8e8e93; }
.order-chip { padding: 2px 8px; border-radius: 10px; background: #2b3a55; color: #9db8e8; }
.order-chip.enemy { background: #55302b; color: #e8a49b; }

/* ===== 单位卡片 ===== */
.enemy-hud { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-bottom: 8px; }
.player-hud { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin-top: 8px; }
.unit-card {
  position: relative;
  padding: 8px;
  border-radius: 10px;
  background: rgba(42, 47, 58, 0.85);
  border: 1px solid #3a4150;
  min-width: 150px;
  flex: 1;
  max-width: 220px;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.unit-card .unit-art { display: block; width: 100%; max-height: 90px; object-fit: contain; margin-bottom: 6px; border-radius: 6px; }
.unit-card.dead { opacity: 0.45; }
.unit-card.bench { opacity: 0.7; border-style: dashed; }
.unit-card.captured { opacity: 0.5; border-color: #2e7d32; }
.unit-card.targetable { cursor: pointer; border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(74, 144, 217, 0.4); }
.unit-card.player.operator { border-color: #7ed321; box-shadow: 0 0 0 2px rgba(126, 211, 33, 0.4); }
.unit-card.player { cursor: pointer; }
.unit-name { font-weight: 600; font-size: 13px; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.mini-icon { width: 14px; height: 14px; object-fit: contain; }
.unit-level { font-size: 11px; color: #8e8e93; }
.elite-badge { font-size: 10px; padding: 1px 6px; border-radius: 8px; background: linear-gradient(135deg, #f6d365, #fda085); color: #7a4a00; font-weight: 700; }
.hp-bar { height: 8px; background: #1a1f2a; border-radius: 4px; overflow: hidden; margin-bottom: 4px; }
.hp-fill { height: 100%; background-color: #7ED321; transition: width 0.3s; }
.unit-hp { font-size: 11px; color: #8e8e93; margin-bottom: 4px; }
.status-row { display: flex; flex-wrap: wrap; gap: 4px; min-height: 18px; }
.status-tag { display: inline-flex; align-items: center; gap: 2px; font-size: 10px; background: #2a2f3a; border: 1px solid #3a4150; padding: 1px 5px; border-radius: 4px; color: #cbd5e0; cursor: pointer; }
.status-tag img { width: 12px; height: 12px; object-fit: contain; }
.status-tag.charging { background: #fff3cd; color: #856404; }
.status-tag.defending { background: #d1ecf1; color: #0c5460; }
.status-tag.capture-stun { background: #e2d9f3; color: #4a2d7a; }
.status-tag.safe-window { background: #d4edda; color: #155724; }
.status-tag.captured-tag { background: #2e7d32; color: #fff; }
.capture-tier { font-size: 11px; font-weight: 700; padding: 1px 6px; border-radius: 4px; background: rgba(0,0,0,0.3); }
.chosen-action { margin-top: 4px; font-size: 11px; color: #7ed321; background: rgba(126, 211, 33, 0.1); padding: 2px 6px; border-radius: 4px; }

/* ===== 伤害飘字 ===== */
.dn-layer { position: absolute; top: 0; left: 0; right: 0; pointer-events: none; z-index: 5; }
.damage-num {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  font-weight: 700;
  font-size: 18px;
  color: #fff;
  text-shadow: 1px 1px 3px rgba(0,0,0,0.8);
  animation: dn-float 1.1s ease-out forwards;
}
.damage-num.dn-crit { color: #ffd54f; font-size: 24px; }
.damage-num.dn-advantage { color: #ff8a65; }
.damage-num.dn-disadvantage { color: #90a4ae; }
.damage-num.dn-heal { color: #7ed321; }
.damage-num.dn-shield { color: #4fc3f7; }
.damage-num.dn-dot { color: #a5d6a7; }
.damage-num.dn-miss { color: #b0bec5; }
@keyframes dn-float {
  0% { opacity: 0; transform: translate(-50%, 10px); }
  15% { opacity: 1; }
  100% { opacity: 0; transform: translate(-50%, -46px); }
}

/* ===== VFX ===== */
.battle-vfx {
  position: absolute; z-index: 4; top: 45%; left: 50%; width: 110px; height: 110px;
  pointer-events: none; background-repeat: no-repeat; background-size: 400% 100%;
  animation: battle-vfx-frames var(--vfx-duration) steps(4, end) forwards;
  transform: translate(-50%, -50%);
}
@keyframes battle-vfx-frames { from { background-position: 0 0; } to { background-position: -440px 0; } }

/* ===== 提示条 ===== */
.prompt-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  background: rgba(255, 243, 205, 0.95); color: #856404; border-radius: 8px;
  padding: 8px 12px; margin: 8px 0; font-size: 13px;
}
.prompt-text { font-weight: 600; }
.relation-mark { font-size: 11px; padding: 1px 6px; border-radius: 4px; margin-left: 4px; }
.relation-mark.advantage { background: #ff8a65; color: #fff; }
.relation-mark.disadvantage { background: #90a4ae; color: #fff; }
.relation-mark.same-element { background: #7ed321; color: #1a1f2a; }

/* ===== 底部控制栏 ===== */
.control-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: auto; padding: 10px 0;
}
.ctrl-btn {
  flex: 1; min-width: 72px; padding: 12px 8px; border: none; border-radius: 10px;
  background: #2a2f3a; color: #e8eef7; font-size: 14px; font-weight: 600; cursor: pointer;
  transition: background 0.15s, transform 0.1s;
}
.ctrl-btn:active { transform: scale(0.97); }
.ctrl-btn.active { background: var(--color-primary); color: #fff; }
.ctrl-btn.capture { background: #2e7d32; color: #fff; }
.ctrl-btn.end { background: #7ed321; color: #1a1f2a; }
.ctrl-btn.end:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 底部面板（Bottom Sheet） ===== */
.sheet-panel {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 560;
  background: #1f2430; border-radius: 16px 16px 0 0; padding: 14px;
  max-height: 62vh; overflow-y: auto; box-shadow: 0 -6px 24px rgba(0,0,0,0.4);
}
.sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sheet-title { font-size: 15px; font-weight: 700; }
.sheet-empty { color: #8e8e93; font-size: 13px; padding: 12px; text-align: center; }
.btn-link { background: none; border: none; color: #4a90d9; cursor: pointer; text-decoration: underline; font-size: 13px; }

/* 技能面板 */
.skill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.skill-btn {
  display: block; text-align: left; padding: 8px; border: 1px solid #3a4150; border-radius: 8px;
  background: #2a2f3a; color: #e8eef7; cursor: pointer; transition: border-color 0.15s;
}
.skill-btn:hover:not(:disabled) { border-color: var(--color-primary); }
.skill-btn.disabled { opacity: 0.5; cursor: not-allowed; }
.skill-inner { display: flex; gap: 8px; align-items: flex-start; }
.skill-icon { width: 34px; height: 34px; object-fit: contain; }
.skill-info { flex: 1; }
.skill-name { font-weight: 600; font-size: 13px; display: flex; align-items: center; flex-wrap: wrap; }
.skill-desc { font-size: 11px; color: #8e8e93; display: block; margin: 2px 0; line-height: 1.4; }
.skill-meta { display: flex; gap: 6px; flex-wrap: wrap; font-size: 11px; }
.meta-item { padding: 1px 6px; border-radius: 4px; background: #3a4150; }
.meta-item.cd { background: #55302b; color: #e8a49b; }
.meta-item.ready { background: rgba(126, 211, 33, 0.15); color: #7ed321; }
.meta-item.blocked { background: #55302b; color: #ffb4a2; }

/* 换宠 */
.switch-list { display: flex; flex-direction: column; gap: 8px; }
.switch-item { display: flex; align-items: center; gap: 10px; padding: 8px; border: 1px solid #3a4150; border-radius: 8px; background: #2a2f3a; cursor: pointer; }
.switch-art { width: 48px; height: 48px; object-fit: contain; }
.switch-info { flex: 1; }
.switch-name { font-weight: 600; font-size: 13px; margin-bottom: 2px; }

/* 战斗背包 */
.bag-list { display: flex; flex-direction: column; gap: 8px; }
.bag-item { display: flex; align-items: center; gap: 10px; padding: 8px; border: 1px solid #3a4150; border-radius: 8px; background: #2a2f3a; cursor: pointer; }
.bag-icon { width: 32px; height: 32px; object-fit: contain; }
.bag-info { flex: 1; }
.bag-name { font-weight: 600; font-size: 13px; }
.bag-desc { font-size: 11px; color: #8e8e93; }
.bag-target .bag-item-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }
.bag-target-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.bag-target-item { display: flex; justify-content: space-between; align-items: center; padding: 8px; border: 1px solid #3a4150; border-radius: 6px; background: #2a2f3a; font-size: 13px; cursor: pointer; }
.bag-target-item.selected { border-color: var(--color-primary); }
.dead-text { color: #ff8a65; }

/* 战术 */
.tactical-grid { display: flex; gap: 8px; margin-bottom: 10px; }
.tact-btn { flex: 1; padding: 12px; border: none; border-radius: 8px; background: #4A90D9; color: #fff; font-weight: 600; cursor: pointer; }
.tact-btn.flee { background: #b87800; }
.auto-section { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.auto-toggle { padding: 8px 18px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; font-weight: 600; cursor: pointer; }
.auto-toggle.active { background: var(--color-danger, #e53e3e); }
.auto-strategy-tag { font-size: 12px; padding: 2px 10px; border-radius: 10px; background: rgba(56, 161, 105, 0.2); color: #7ed321; font-weight: 600; }
.auto-settings { display: flex; flex-direction: column; gap: 8px; border-top: 1px dashed #3a4150; padding-top: 8px; }
.setting-row { font-size: 13px; }
.setting-label { font-weight: 600; margin-right: 8px; }
.strategy-options { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 6px; margin-top: 4px; }
.strategy-option { display: flex; flex-direction: column; gap: 2px; padding: 6px 10px; border: 1px solid #3a4150; border-radius: 6px; cursor: pointer; }
.strategy-option.active { border-color: var(--color-primary); background: rgba(74, 144, 217, 0.1); }
.strategy-option input { display: none; }
.strategy-name { font-weight: 600; font-size: 13px; }
.strategy-desc { font-size: 11px; color: #8e8e93; }
.setting-check { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.threshold-input { width: 52px; padding: 2px 4px; border: 1px solid #3a4150; border-radius: 4px; background: #2a2f3a; color: #e8eef7; }
.setting-select { padding: 4px 8px; background: #2a2f3a; color: #e8eef7; border: 1px solid #3a4150; border-radius: 4px; }
.setting-actions { display: flex; align-items: center; gap: 10px; }
.setting-hint { font-size: 11px; color: #8e8e93; }

/* 通用按钮 */
.btn-primary { padding: 10px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 8px; font-size: 14px; cursor: pointer; }
.btn-primary.small { padding: 8px 16px; font-size: 13px; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary.small { padding: 6px 16px; background: #3a4150; color: #e8eef7; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; }
.ball-btn { display: inline-flex; align-items: center; padding: 4px 10px; font-size: 12px; border: 1px solid #2e7d32; background: #fff; color: #2e7d32; border-radius: 6px; cursor: pointer; }
.ball-btn.selected { background: #2e7d32; color: #fff; }
.ball-icon { width: 18px; height: 18px; object-fit: contain; margin-right: 3px; }
.error-text { margin-top: 10px; color: #ff8a65; font-size: 13px; text-align: center; }

/* 事件日志 */
.event-log { margin-top: 8px; background: #2a2f3a; border-radius: 8px; padding: 10px; }
.log-list { max-height: 160px; overflow-y: auto; display: flex; flex-direction: column-reverse; }
.log-line { font-size: 12px; padding: 2px 0; color: #cbd5e0; }

/* 结算 / 结果浮层 */
.result-overlay { position: fixed; inset: 0; z-index: 600; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; }
.result-card { background: #1f2430; border-radius: 12px; padding: 20px; width: 440px; max-width: 92vw; max-height: 80vh; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.result-card h3 { font-size: 17px; color: #7ed321; }
.settlement-summary { display: flex; gap: 12px; flex-wrap: wrap; }
.reward-item { font-size: 14px; font-weight: 600; padding: 4px 12px; border-radius: 12px; }
.reward-item.exp { background: #2b3a55; color: #9db8e8; }
.reward-item.gold { background: #55431f; color: #f5c04a; }
.reward-item.none { color: #8e8e93; }
.defeat-section { background: #55431f; border-radius: 8px; padding: 10px 12px; }
.defeat-message { font-size: 14px; color: #f5c04a; font-weight: 600; margin-bottom: 4px; }
.defeat-detail { font-size: 12px; color: #d8c08a; line-height: 1.6; }
.victory-interaction-section { padding: 10px 12px; border-radius: 8px; background: #2a2f3a; border-left: 4px solid #b08968; }
.victory-interaction-speaker { font-size: 13px; font-weight: 700; color: #d8c08a; display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.victory-interaction-cry { font-size: 14px; color: #b08968; font-style: italic; margin-bottom: 3px; }
.victory-interaction-text { font-size: 13px; color: #cbd5e0; line-height: 1.6; }
.tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; }
.tag.boss { background: #6a1b9a; color: #fff; }
.tag.wild { background: #2e7d32; color: #fff; }
.tag.special { background: #4a2d7a; color: #fff; }
.tag.rare-skill { background: #55431f; color: #f5c04a; }
.tag.team { background: #2b3a55; color: #9db8e8; }
.tag.storage { background: #3a4150; color: #cbd5e0; }
.captured-section { font-size: 13px; }
.captured-item { padding: 2px 0; }
.drops-section { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; }
.drops-label { color: #8e8e93; }
.drop-tag { background: #2e7d32; color: #c8f7dc; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.hp-writeback-section { font-size: 12px; color: #8e8e93; }
.writeback-label { display: block; margin-bottom: 4px; }
.writeback-item { padding: 2px 0; }
.dead-tag { color: #ff8a65; font-size: 11px; margin-left: 4px; }
.dest-checkbox { display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; }
.dest-desc { font-size: 13px; color: #8e8e93; }

/* 状态详情 */
.status-detail-mask { position: fixed; inset: 0; z-index: 620; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; }
.status-detail-card { background: #1f2430; border-radius: 12px; padding: 18px; width: 320px; max-width: 90vw; display: flex; flex-direction: column; gap: 10px; }
.status-detail-card h4 { font-size: 15px; }
.status-detail-item { display: flex; gap: 10px; align-items: flex-start; }
.status-detail-item img { width: 32px; height: 32px; object-fit: contain; }
.sd-name { font-weight: 700; font-size: 14px; }
.sd-category { font-size: 12px; color: #8e8e93; }
.sd-turns { font-size: 12px; color: #9db8e8; }

/* 响应式 */
@media (max-width: 768px) {
  .battle-overlay { padding: 8px; }
  .unit-card { min-width: 130px; max-width: 170px; }
  .ctrl-btn { min-width: 60px; padding: 10px 4px; font-size: 12px; }
  .skill-grid { grid-template-columns: 1fr; }
  .prompt-bar { flex-direction: column; align-items: stretch; }
}
@media (prefers-reduced-motion: reduce) {
  .battle-vfx { animation-duration: 0.01ms; }
  .damage-num { animation: none; }
}
</style>