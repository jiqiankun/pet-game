<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { useBattleStore } from '../../stores/battle'
import { apiGet, apiPut, apiPost, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type {
  TeamPresetView,
  TeamMemberEntry,
  PetSummaryView,
} from '../../types/pet'

/**
 * 队伍页（阶段 6 完善）：5 套预设、切换、拖拽调整、技能查看、快速打开宠物详情。
 * 编辑结果整体提交 PUT /api/team/members（可指定预设）；战斗中禁止编辑与切换。
 */
const gameStore = useGameStore()
const battleStore = useBattleStore()

const presets = ref<TeamPresetView[]>([])
const selectedTeamId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')

// 编辑中的成员布局：position → petId（针对当前选中预设）
const editSlots = ref<Record<number, number | null>>({
  1: null, 2: null, 3: null, 4: null, 5: null, 6: null,
})

// 拖拽状态
const dragging = ref<{ fromPos: number; petId: number } | null>(null)
const dragOverPos = ref<number | null>(null)

const inBattle = computed(() => battleStore.inBattle)
const availablePets = computed(() => gameStore.pets)
const selectedPreset = computed(
  () => presets.value.find((p) => p.teamId === selectedTeamId.value) ?? null,
)

// 已分配到槽位的宠物 ID 集合（用于禁用重复选择）
const assignedPetIds = computed(() => {
  const ids = new Set<number>()
  for (const pos of [1, 2, 3, 4, 5, 6]) {
    const pid = editSlots.value[pos]
    if (pid) ids.add(pid)
  }
  return ids
})

onMounted(async () => {
  await gameStore.loadBootstrap()
  await loadPresets(true)
})

/** 加载全部预设；firstLoad 时默认选中激活预设。 */
async function loadPresets(firstLoad = false) {
  loading.value = true
  error.value = ''
  try {
    const res = await apiGet<TeamPresetView[]>('/api/team/presets')
    presets.value = (res as ApiResponse<TeamPresetView[]>).data
    if (firstLoad || selectedTeamId.value === null) {
      const active = presets.value.find((p) => p.isActive) ?? presets.value[0]
      selectedTeamId.value = active ? active.teamId : null
    }
    syncEditSlots()
  } catch (e: unknown) {
    error.value = e instanceof BusinessError ? e.message : '加载队伍失败'
  } finally {
    loading.value = false
  }
}

/** 根据选中预设初始化编辑槽位。 */
function syncEditSlots() {
  editSlots.value = { 1: null, 2: null, 3: null, 4: null, 5: null, 6: null }
  if (selectedPreset.value) {
    for (const m of selectedPreset.value.members) {
      editSlots.value[m.position] = m.petId
    }
  }
}

function selectPreset(preset: TeamPresetView) {
  selectedTeamId.value = preset.teamId
  syncEditSlots()
}

/** 激活选中预设（战斗中后端拒绝）。 */
async function activateSelected() {
  if (!selectedPreset.value || selectedPreset.value.isActive || inBattle.value) return
  error.value = ''
  try {
    const res = await apiPost<TeamPresetView[]>(
      `/api/team/presets/${selectedPreset.value.teamId}/activate`,
    )
    presets.value = (res as ApiResponse<TeamPresetView[]>).data
    await gameStore.loadBootstrap()
  } catch (e: unknown) {
    error.value = e instanceof BusinessError ? e.message : '切换预设失败'
  }
}

/** 保存当前预设布局。 */
async function saveTeam() {
  if (inBattle.value || !selectedPreset.value) return
  saving.value = true
  error.value = ''
  try {
    const members: TeamMemberEntry[] = []
    for (const pos of [1, 2, 3, 4, 5, 6]) {
      const pid = editSlots.value[pos]
      if (pid) {
        members.push({ petId: pid, position: pos })
      }
    }
    await apiPut('/api/team/members', { teamId: selectedPreset.value.teamId, members })
    await loadPresets()
    await gameStore.loadBootstrap()
  } catch (e: unknown) {
    error.value = e instanceof BusinessError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

// ==================== 拖拽调整 ====================

function onDragStart(pos: number) {
  const petId = editSlots.value[pos]
  if (!petId || inBattle.value) return
  dragging.value = { fromPos: pos, petId }
}

function onDragOver(pos: number, event: DragEvent) {
  if (dragging.value && pos !== dragging.value.fromPos) {
    event.preventDefault()
    dragOverPos.value = pos
  }
}

function onDrop(pos: number) {
  if (!dragging.value || inBattle.value) {
    dragging.value = null
    dragOverPos.value = null
    return
  }
  const from = dragging.value.fromPos
  if (from !== pos) {
    const displaced = editSlots.value[pos]
    editSlots.value[pos] = dragging.value.petId
    editSlots.value[from] = displaced ?? null // 交换位置
  }
  dragging.value = null
  dragOverPos.value = null
}

function onDragEnd() {
  dragging.value = null
  dragOverPos.value = null
}

// ==================== 展示辅助 ====================

function petName(petId: number | null): string {
  if (!petId) return ''
  const pet = availablePets.value.find((p) => p.id === petId)
  if (!pet) return String(petId)
  const summary = gameStore.petSummaries.find(
    (s: PetSummaryView) => s.pet.id === petId,
  )
  const speciesName = summary?.speciesName ?? pet.speciesId
  return pet.nickname ? `${pet.nickname}（${speciesName}）` : speciesName
}

function petHp(petId: number | null): string {
  if (!petId) return ''
  const pet = availablePets.value.find((p) => p.id === petId)
  return pet ? `HP ${pet.currentHp}` : ''
}

function petLevel(petId: number | null): string {
  if (!petId) return ''
  const pet = availablePets.value.find((p) => p.id === petId)
  return pet ? `Lv.${pet.level}` : ''
}

function petSkills(petId: number | null): string[] {
  if (!petId) return []
  const summary = gameStore.petSummaries.find(
    (s: PetSummaryView) => s.pet.id === petId,
  )
  return (summary?.equippedSkills ?? []).map((s) => s.name)
}

/** 移除槽位中的宠物。 */
function clearSlot(pos: number) {
  editSlots.value[pos] = null
}
</script>

<template>
  <div class="team-view">
    <div v-if="inBattle" class="battle-notice">
      战斗中无法编辑队伍或切换预设，请先结束战斗。
    </div>

    <div v-if="loading" class="loading-text">加载中...</div>

    <template v-if="presets.length > 0">
      <!-- 预设切换（阶段 6：5 套预设） -->
      <div class="preset-tabs">
        <button
          v-for="preset in presets"
          :key="preset.teamId"
          class="preset-tab"
          :class="{ selected: preset.teamId === selectedTeamId }"
          @click="selectPreset(preset)"
        >
          预设 {{ preset.slot }}
          <span v-if="preset.isActive" class="active-mark">激活中</span>
          <span class="member-count">{{ preset.members.length }}/6</span>
        </button>
      </div>

      <div class="team-header">
        <h2>{{ selectedPreset?.name ?? '' }}</h2>
        <button
          v-if="selectedPreset && !selectedPreset.isActive"
          class="btn-activate"
          :disabled="inBattle"
          @click="activateSelected"
        >
          激活此预设
        </button>
        <span v-else class="active-badge">当前激活预设</span>
      </div>

      <!-- 首发（位置 1~3） -->
      <div class="position-group">
        <h3>首发阵容（位置 1~3）</h3>
        <div class="slot-row">
          <div
            v-for="pos in 3"
            :key="pos"
            class="slot-card starter"
            :class="{ 'drag-over': dragOverPos === pos }"
            @dragover="onDragOver(pos, $event)"
            @drop="onDrop(pos)"
          >
            <div class="slot-pos">位置 {{ pos }}（首发）</div>
            <template v-if="editSlots[pos]">
              <div
                class="pet-chip"
                draggable="true"
                @dragstart="onDragStart(pos)"
                @dragend="onDragEnd"
              >
                <div class="pet-line">
                  <span class="pet-name">{{ petName(editSlots[pos]) }}</span>
                  <span class="pet-sub">{{ petLevel(editSlots[pos]) }} · {{ petHp(editSlots[pos]) }}</span>
                </div>
                <div v-if="petSkills(editSlots[pos]).length" class="pet-skills">
                  技能：{{ petSkills(editSlots[pos]).join(' / ') }}
                </div>
                <div class="pet-actions">
                  <RouterLink to="/pets" class="btn-link">详情</RouterLink>
                  <button class="btn-link danger" :disabled="inBattle" @click="clearSlot(pos)">移除</button>
                </div>
              </div>
            </template>
            <select
              v-model="editSlots[pos]"
              class="pet-select"
              :disabled="inBattle"
            >
              <option :value="null">— 空 —</option>
              <option
                v-for="pet in availablePets"
                :key="pet.id"
                :value="pet.id"
                :disabled="assignedPetIds.has(pet.id) && editSlots[pos] !== pet.id"
              >
                {{ pet.nickname || pet.speciesId }} (Lv.{{ pet.level }})
              </option>
            </select>
          </div>
        </div>
      </div>

      <!-- 候补（位置 4~6） -->
      <div class="position-group">
        <h3>候补阵容（位置 4~6）</h3>
        <div class="slot-row">
          <div
            v-for="pos in [4, 5, 6]"
            :key="pos"
            class="slot-card bench"
            :class="{ 'drag-over': dragOverPos === pos }"
            @dragover="onDragOver(pos, $event)"
            @drop="onDrop(pos)"
          >
            <div class="slot-pos">位置 {{ pos }}（候补）</div>
            <template v-if="editSlots[pos]">
              <div
                class="pet-chip"
                draggable="true"
                @dragstart="onDragStart(pos)"
                @dragend="onDragEnd"
              >
                <div class="pet-line">
                  <span class="pet-name">{{ petName(editSlots[pos]) }}</span>
                  <span class="pet-sub">{{ petLevel(editSlots[pos]) }} · {{ petHp(editSlots[pos]) }}</span>
                </div>
                <div v-if="petSkills(editSlots[pos]).length" class="pet-skills">
                  技能：{{ petSkills(editSlots[pos]).join(' / ') }}
                </div>
                <div class="pet-actions">
                  <RouterLink to="/pets" class="btn-link">详情</RouterLink>
                  <button class="btn-link danger" :disabled="inBattle" @click="clearSlot(pos)">移除</button>
                </div>
              </div>
            </template>
            <select
              v-model="editSlots[pos]"
              class="pet-select"
              :disabled="inBattle"
            >
              <option :value="null">— 空 —</option>
              <option
                v-for="pet in availablePets"
                :key="pet.id"
                :value="pet.id"
                :disabled="assignedPetIds.has(pet.id) && editSlots[pos] !== pet.id"
              >
                {{ pet.nickname || pet.speciesId }} (Lv.{{ pet.level }})
              </option>
            </select>
          </div>
        </div>
      </div>

      <p class="drag-hint">提示：可拖拽宠物卡片到其他槽位交换位置；下拉框也可调整。</p>

      <!-- 操作 -->
      <div class="team-actions">
        <button class="btn-primary" :disabled="saving || inBattle" @click="saveTeam">
          {{ saving ? '保存中...' : '保存队伍' }}
        </button>
        <button class="btn-secondary" :disabled="inBattle" @click="loadPresets()">重置</button>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>
    </template>

    <div v-else-if="!loading" class="empty-text">暂无队伍数据</div>
  </div>
</template>

<style scoped>
.team-view {
  padding: 24px;
  max-width: 860px;
  margin: 0 auto;
}

.battle-notice {
  background-color: #fff3cd;
  color: #856404;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 12px;
}

.preset-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.preset-tab {
  padding: 8px 14px;
  border: 1px solid #d5dbe3;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}

.preset-tab.selected {
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}

.active-mark {
  font-size: 10px;
  background-color: #2e7d32;
  color: #fff;
  padding: 1px 6px;
  border-radius: 4px;
}

.member-count {
  font-size: 11px;
  color: var(--text-secondary);
}

.team-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.team-header h2 {
  font-size: 20px;
  color: var(--color-primary);
}

.btn-activate {
  padding: 6px 16px;
  background-color: var(--color-secondary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
}

.btn-activate:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.active-badge {
  font-size: 12px;
  background-color: #2e7d32;
  color: #fff;
  padding: 2px 10px;
  border-radius: var(--radius-sm);
}

.position-group {
  margin-bottom: 24px;
}

.position-group h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.slot-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.slot-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
  min-height: 110px;
}

.slot-card.starter {
  border-top: 3px solid var(--color-primary);
}

.slot-card.bench {
  border-top: 3px solid var(--text-secondary);
}

.slot-card.drag-over {
  outline: 2px dashed var(--color-primary);
}

.slot-pos {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.pet-chip {
  border: 1px solid #e0e5ec;
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 8px;
  cursor: grab;
  background-color: var(--bg-main);
}

.pet-chip:active {
  cursor: grabbing;
}

.pet-line {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 6px;
}

.pet-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.pet-sub {
  font-size: 11px;
  color: var(--text-secondary);
}

.pet-skills {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.5;
}

.pet-actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}

.pet-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 12px;
}

.pet-select:disabled {
  background-color: #f5f5f5;
}

.btn-link {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 11px;
  text-decoration: underline;
  padding: 0;
}

.btn-link.danger {
  color: var(--color-danger);
}

.drag-hint {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.team-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.btn-primary {
  padding: 10px 28px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 15px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 28px;
  background-color: var(--bg-main);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md);
  font-size: 15px;
  cursor: pointer;
}

.loading-text {
  text-align: center;
  padding: 60px 0;
  color: var(--text-secondary);
}

.empty-text {
  text-align: center;
  padding: 60px 0;
  color: var(--text-secondary);
}

.error-text {
  margin-top: 12px;
  color: #d32f2f;
  font-size: 14px;
}

@media (max-width: 768px) {
  .slot-row {
    grid-template-columns: 1fr;
  }
}
</style>
