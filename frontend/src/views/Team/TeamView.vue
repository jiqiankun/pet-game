<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { useBattleStore } from '../../stores/battle'
import { apiGet, apiPut, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { TeamView, TeamMemberEntry } from '../../types/pet'

const gameStore = useGameStore()
const battleStore = useBattleStore()

const team = ref<TeamView | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')

// 编辑中的成员布局：position → petId
const editSlots = ref<Record<number, number | null>>({
  1: null, 2: null, 3: null, 4: null, 5: null, 6: null,
})

const inBattle = computed(() => battleStore.inBattle)

// 所有可用宠物（从 bootstrap 加载）
const availablePets = computed(() => gameStore.pets)

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
  await loadTeam()
})

async function loadTeam() {
  loading.value = true
  error.value = ''
  try {
    const res = await apiGet<TeamView>('/api/team')
    team.value = (res as ApiResponse<TeamView>).data
    // 初始化编辑槽位
    editSlots.value = { 1: null, 2: null, 3: null, 4: null, 5: null, 6: null }
    for (const m of team.value.members) {
      editSlots.value[m.position] = m.petId
    }
  } catch (e: any) {
    error.value = e.message || '加载队伍失败'
  } finally {
    loading.value = false
  }
}

/** 保存队伍布局。 */
async function saveTeam() {
  if (inBattle.value) return
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
    const res = await apiPut<TeamView>('/api/team/members', { members })
    team.value = (res as ApiResponse<TeamView>).data
    await gameStore.loadBootstrap()
  } catch (e: any) {
    error.value = e instanceof BusinessError ? e.message : (e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 获取宠物 HP。 */
function petHp(petId: number | null): string {
  if (!petId) return ''
  const pet = availablePets.value.find((p) => p.id === petId)
  return pet ? `HP ${pet.currentHp}` : ''
}

/** 移除槽位中的宠物。 */
function clearSlot(pos: number) {
  editSlots.value[pos] = null
}
</script>

<template>
  <div class="team-view">
    <div v-if="inBattle" class="battle-notice">
      战斗中无法编辑队伍，请先结束战斗。
    </div>

    <div v-if="loading" class="loading-text">加载中...</div>

    <template v-if="team">
      <div class="team-header">
        <h2>{{ team.name }}</h2>
        <span class="team-slot-badge">槽位 {{ team.slot }}</span>
      </div>

      <!-- 首发（位置 1~3） -->
      <div class="position-group">
        <h3>首发阵容（位置 1~3）</h3>
        <div class="slot-row">
          <div v-for="pos in 3" :key="pos" class="slot-card starter">
            <div class="slot-pos">位置 {{ pos }}</div>
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
            <div v-if="editSlots[pos]" class="slot-pet-info">
              <span>{{ petHp(editSlots[pos]) }}</span>
              <button class="btn-link" :disabled="inBattle" @click="clearSlot(pos)">移除</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 候补（位置 4~6） -->
      <div class="position-group">
        <h3>候补阵容（位置 4~6）</h3>
        <div class="slot-row">
          <div v-for="pos in [4, 5, 6]" :key="pos" class="slot-card bench">
            <div class="slot-pos">位置 {{ pos }}</div>
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
            <div v-if="editSlots[pos]" class="slot-pet-info">
              <span>{{ petHp(editSlots[pos]) }}</span>
              <button class="btn-link" :disabled="inBattle" @click="clearSlot(pos)">移除</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作 -->
      <div class="team-actions">
        <button class="btn-primary" :disabled="saving || inBattle" @click="saveTeam">
          {{ saving ? '保存中...' : '保存队伍' }}
        </button>
        <button class="btn-secondary" :disabled="inBattle" @click="loadTeam">重置</button>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>
    </template>

    <div v-else-if="!loading" class="empty-text">暂无队伍数据</div>
  </div>
</template>

<style scoped>
.team-view {
  padding: 24px;
  max-width: 760px;
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

.team-slot-badge {
  font-size: 12px;
  background-color: var(--color-secondary);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
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
}

.slot-card.starter {
  border-top: 3px solid var(--color-primary);
}

.slot-card.bench {
  border-top: 3px solid var(--text-secondary);
}

.slot-pos {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.pet-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
}

.pet-select:disabled {
  background-color: #f5f5f5;
}

.slot-pet-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.btn-link {
  background: none;
  border: none;
  color: var(--color-danger);
  cursor: pointer;
  font-size: 11px;
  text-decoration: underline;
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