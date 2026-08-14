<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { useBattleStore } from '../../stores/battle'
import { apiGet, apiPost, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { PetDetail, LevelUpPreview } from '../../types/pet'
import { elementIconUrl, skillTypeIconUrl } from '../../game-assets'

const gameStore = useGameStore()
const battleStore = useBattleStore()

// 选中的宠物详情
const detail = ref<PetDetail | null>(null)
const loading = ref(false)
const error = ref('')
const actionLoading = ref(false)
const actionError = ref('')

// 升级预览
const preview = ref<LevelUpPreview | null>(null)
const previewTargetLevel = ref<number | null>(null)

// 加点输入
const allocateStat = ref('')
const allocatePoints = ref(1)

// 自定义升级输入
const customExp = ref(0)
const targetLevelInput = ref(1)

// 标签页
const activeTab = ref<'basic' | 'stats' | 'skills' | 'build' | 'history'>('basic')

// 技能书管理（阶段 10）
const learnBookItemId = ref('')
const learnForgetSkillId = ref<string | null>(null)

// 推荐 Build（阶段 10）
const builds = ref<BuildRecommendation[]>([])
const buildsLoaded = ref(false)

const inBattle = computed(() => battleStore.inBattle)

const STAT_KEYS = ['HP', 'STRENGTH', 'SPIRIT', 'DEFENSE', 'RESISTANCE', 'SPEED'] as const
const STAT_LABELS: Record<string, string> = {
  HP: '生命', STRENGTH: '力量', SPIRIT: '灵力',
  DEFENSE: '防御', RESISTANCE: '抗性', SPEED: '速度',
}

onMounted(async () => {
  await gameStore.loadBootstrap()
  if (gameStore.pets.length > 0) {
    await loadDetail(gameStore.pets[0].id)
  }
})

async function loadDetail(petId: number) {
  loading.value = true
  error.value = ''
  detail.value = null
  preview.value = null
  try {
    const res = await apiGet<PetDetail>(`/api/pets/${petId}`)
    detail.value = (res as ApiResponse<PetDetail>).data
    activeTab.value = 'basic'
    buildsLoaded.value = false
    builds.value = []
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 加载升级预览。 */
async function loadPreview(toLevel: number) {
  if (!detail.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiGet<LevelUpPreview>(
      `/api/pets/${detail.value.pet.id}/level-up/preview?to=${toLevel}`,
    )
    preview.value = (res as ApiResponse<LevelUpPreview>).data
    previewTargetLevel.value = toLevel
  } catch (e: any) {
    actionError.value = e.message || '预览失败'
    preview.value = null
  } finally {
    actionLoading.value = false
  }
}

/** 执行升级。 */
async function doLevelUp(mode: string, targetLevel?: number, exp?: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/level-up`,
      { mode, targetLevel: targetLevel ?? null, exp: exp ?? null },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
    preview.value = null
    // 刷新首页数据（经验池已扣减）
    await gameStore.loadBootstrap()
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '升级失败')
  } finally {
    actionLoading.value = false
  }
}

/** 加点。 */
async function doAllocate() {
  if (!detail.value || !allocateStat.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/allocate-points`,
      { stat: allocateStat.value, points: allocatePoints.value },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
    await gameStore.loadBootstrap()
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '加点失败')
  } finally {
    actionLoading.value = false
  }
}

/** 洗点。 */
async function doReset() {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/reset-points`,
    )
    detail.value = (res as ApiResponse<PetDetail>).data
    await gameStore.loadBootstrap()
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '洗点失败')
  } finally {
    actionLoading.value = false
  }
}

/** 装备技能到槽位。 */
async function doEquip(skillId: string, slot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/skills/equip`,
      { skillId, slot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '装备失败')
  } finally {
    actionLoading.value = false
  }
}

/** 卸下技能。 */
async function doUnequip(slot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/skills/unequip`,
      { slot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '卸下失败')
  } finally {
    actionLoading.value = false
  }
}

/** 稀有度标签颜色。 */
function rarityClass(rarity: string): string {
  return `rarity-${rarity?.toLowerCase()}`
}

/** 格式化槽位显示。 */
function slotLabel(slot: number | null): string {
  return slot ? `槽${slot}` : '未装备'
}

/** 当前已装备的主动技能数（REV-017）。 */
function equippedCount(): number {
  if (!detail.value) return 0
  return detail.value.learnedSkills.filter(s => s.slot != null).length
}

/** 被动来源标识（REV-017）。 */
function sourceLabel(source: string): string {
  if (source === 'BOOK') return '📖技能书'
  if (source === 'SPECIAL') return '✨特殊'
  return '自身'
}

/** 解锁技能显示名（REV-013：优先名称，兼容旧数据）。 */
function u2label(s: { skillId: string; name?: string }): string {
  return s.name || s.skillId
}

// ==================== 技能书管理（阶段 10） ====================

interface BuildRecommendation {
  name: string
  description: string
  statPriority: string[]
  skillPriority: string[]
}

/** 使用技能书学习技能。 */
async function doLearnBook() {
  if (!detail.value || !learnBookItemId.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/learn-skill-book`,
      { itemId: learnBookItemId.value, forgetSkillId: learnForgetSkillId.value },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
    learnBookItemId.value = ''
    learnForgetSkillId.value = null
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '学习失败')
  } finally {
    actionLoading.value = false
  }
}

/** 遗忘技能书主动技能。 */
async function doForgetBook(skillId: string) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/forget-book-skill`,
      { skillId },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '遗忘失败')
  } finally {
    actionLoading.value = false
  }
}

/** 装备技能书技能到槽位 5~6。 */
async function doEquipBook(skillId: string, bookSlot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/equip-book-skill`,
      { skillId, bookSlot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '装备失败')
  } finally {
    actionLoading.value = false
  }
}

/** 卸下技能书槽位。 */
async function doUnequipBook(bookSlot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/unequip-book-skill`,
      { bookSlot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '卸下失败')
  } finally {
    actionLoading.value = false
  }
}

/** 启用被动技能书被动到槽位 7~8（阶段 14「已学习 ≠ 当前生效」）。 */
async function doEquipBookPassive(passiveId: string, bookSlot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/equip-book-passive`,
      { skillId: passiveId, bookSlot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '启用失败')
  } finally {
    actionLoading.value = false
  }
}

/** 卸下（停用）被动技能书槽位，立即失效。 */
async function doUnequipBookPassive(bookSlot: number) {
  if (!detail.value || inBattle.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const res = await apiPost<PetDetail>(
      `/api/pets/${detail.value.pet.id}/unequip-book-passive`,
      { bookSlot },
    )
    detail.value = (res as ApiResponse<PetDetail>).data
  } catch (e: any) {
    actionError.value = e instanceof BusinessError ? e.message : (e.message || '停用失败')
  } finally {
    actionLoading.value = false
  }
}

/** 加载推荐 Build（阶段 10，纯展示）。 */
async function loadBuilds() {
  if (!detail.value || buildsLoaded.value) return
  try {
    const res = await apiGet<BuildRecommendation[]>(
      `/api/pets/${detail.value.pet.id}/build-recommendations`,
    )
    builds.value = (res as ApiResponse<BuildRecommendation[]>).data ?? []
    buildsLoaded.value = true
  } catch {
    builds.value = []
  }
}

function switchTab(tab: 'basic' | 'stats' | 'skills' | 'build' | 'history') {
  activeTab.value = tab
  if (tab === 'build') loadBuilds()
}

/** 捕获地点显示（无记录则显示未知）。 */
function capturedLocationLabel(mapId: string | null): string {
  return mapId || '未知地点'
}

/** 格式化小数安全加法。 */
function nz(v: number | null | undefined): number {
  return v ?? 0
}

/** 格式化捕获日期。 */
function formatDate(iso: string | null): string {
  if (!iso) return '未知'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<template>
  <div class="pet-view">
    <!-- 战斗中禁用操作提示 -->
    <div v-if="inBattle" class="battle-notice">
      战斗中无法进行培养操作，请先结束战斗。
    </div>

    <div class="pet-layout">
      <!-- 左侧：宠物列表 -->
      <div class="pet-list-panel">
        <h3>宠物列表</h3>
        <div v-if="loading && !detail" class="loading-text">加载中...</div>
        <div v-else-if="gameStore.pets.length === 0" class="empty-text">暂无宠物</div>
        <div
          v-for="pet in gameStore.pets"
          :key="pet.id"
          class="pet-list-item"
          :class="{ active: detail?.pet.id === pet.id }"
          @click="loadDetail(pet.id)"
        >
          <div class="list-pet-name">{{ pet.nickname || pet.speciesId }}</div>
          <div class="list-pet-info">
            <span class="list-level">Lv.{{ pet.level }}</span>
            <span class="list-hp">HP {{ pet.currentHp }}</span>
            <span v-if="pet.isStarter" class="starter-tag">初始</span>
          </div>
        </div>
      </div>

      <!-- 右侧：详情面板 -->
      <div class="pet-detail-panel">
        <p v-if="error" class="error-text">{{ error }}</p>
        <div v-if="loading" class="loading-text">加载中...</div>

        <template v-if="detail">
          <!-- 标签切换 -->
          <div class="tab-bar">
            <button class="tab-btn" :class="{ active: activeTab === 'basic' }" @click="switchTab('basic')">基础</button>
            <button class="tab-btn" :class="{ active: activeTab === 'stats' }" @click="switchTab('stats')">属性</button>
            <button class="tab-btn" :class="{ active: activeTab === 'skills' }" @click="switchTab('skills')">技能</button>
            <button class="tab-btn" :class="{ active: activeTab === 'build' }" @click="switchTab('build')">推荐方案</button>
            <button class="tab-btn" :class="{ active: activeTab === 'history' }" @click="switchTab('history')">记录</button>
          </div>

          <!-- 基础标签 -->
          <div v-if="activeTab === 'basic'" class="tab-content">
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">昵称</span>
                <span class="info-value">{{ detail.pet.nickname }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">种族</span>
                <span class="info-value">{{ detail.species.name }} ({{ detail.pet.speciesId }})</span>
              </div>
              <div class="info-item">
                <span class="info-label">属性</span>
                <span class="info-value">{{ detail.species.element }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">稀有度</span>
                <span class="info-value" :class="rarityClass(detail.species.rarity)">{{ detail.species.rarity }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">等级</span>
                <span class="info-value">Lv.{{ detail.pet.level }} / 50</span>
              </div>
              <div class="info-item">
                <span class="info-label">捕获等级</span>
                <span class="info-value">Lv.{{ detail.pet.capturedLevel }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">HP</span>
                <span class="info-value">{{ detail.pet.currentHp }} / {{ detail.panelStats.maxHp }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">战斗次数</span>
                <span class="info-value">{{ detail.pet.battleCount }} (胜 {{ detail.pet.winCount }})</span>
              </div>
            </div>
            <p v-if="detail.species.description" class="species-desc">{{ detail.species.description }}</p>
          </div>

          <!-- 属性标签 -->
          <div v-if="activeTab === 'stats'" class="tab-content">
            <!-- 经验池与自由点数 -->
            <div class="resource-bar">
              <div class="resource-item">
                <span class="resource-label">经验池</span>
                <span class="resource-value">{{ detail.expPool }}</span>
                <span v-if="detail.expToNextLevel > 0" class="resource-hint">下一级需 {{ detail.expToNextLevel }}</span>
                <span v-else class="resource-hint">已达等级上限</span>
              </div>
              <div class="resource-item">
                <span class="resource-label">可分配点数</span>
                <span class="resource-value">{{ detail.freePointsAvailable }}</span>
                <span class="resource-hint">已用 {{ detail.allocatedFreePoints }}</span>
              </div>
            </div>

            <!-- 属性分解表 -->
            <table class="stat-table">
              <thead>
                <tr>
                  <th>维度</th><th>基础</th><th>成长</th><th>资质</th><th>加点</th><th>合计</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="key in STAT_KEYS" :key="key">
                  <td class="stat-name">{{ STAT_LABELS[key] }}</td>
                  <td>{{ detail.panelStats.breakdowns[key]?.base ?? 0 }}</td>
                  <td>{{ detail.panelStats.breakdowns[key]?.growth ?? 0 }}</td>
                  <td>{{ detail.panelStats.breakdowns[key]?.aptBonus ?? 0 }}</td>
                  <td>{{ detail.panelStats.breakdowns[key]?.freeBonus ?? 0 }}</td>
                  <td class="stat-total">{{ detail.panelStats.breakdowns[key]?.total ?? 0 }}</td>
                </tr>
              </tbody>
            </table>

            <!-- 升级区域 -->
            <div class="action-section">
              <h4>升级</h4>
              <div class="upgrade-buttons">
                <button class="btn-action" :disabled="actionLoading || inBattle || detail.expToNextLevel === 0" @click="doLevelUp('ONE')">升 1 级</button>
                <button class="btn-action" :disabled="actionLoading || inBattle || detail.expToNextLevel === 0" @click="doLevelUp('FIVE')">升 5 级</button>
                <button class="btn-action" :disabled="actionLoading || inBattle || detail.expToNextLevel === 0" @click="doLevelUp('TO_CAP')">升到上限</button>
              </div>
              <div class="upgrade-custom">
                <button class="btn-action" :disabled="actionLoading || inBattle" @click="loadPreview(targetLevelInput)">预览升到 Lv.{{ targetLevelInput }}</button>
                <input v-model.number="targetLevelInput" type="number" min="1" max="50" class="input-small" />
              </div>
              <div class="upgrade-custom">
                <button class="btn-action" :disabled="actionLoading || inBattle" @click="doLevelUp('CUSTOM_EXP', undefined, customExp)">投入经验升级</button>
                <input v-model.number="customExp" type="number" min="1" class="input-small" />
              </div>

              <!-- 升级预览结果 -->
              <div v-if="preview" class="preview-panel">
                <h4>Lv.{{ preview.fromLevel }} → Lv.{{ preview.toLevel }}</h4>
                <p class="preview-info">所需经验：{{ preview.expRequired }}（{{ preview.expPoolSufficient ? '经验充足' : '经验不足' }}）</p>
                <p class="preview-info">获得点数：{{ preview.pointsGained }}</p>
                <div v-if="preview.skillsUnlocked.length" class="preview-skills">
                  <span>新解锁主动技能：</span>
                  <span v-for="s in preview.skillsUnlocked.filter(u => u.skillType !== 'PASSIVE')" :key="s.skillId" class="unlock-tag">{{ u2label(s) }} (Lv.{{ s.unlockLevel }})</span>
                  <span v-if="!preview.skillsUnlocked.some(u => u.skillType !== 'PASSIVE')" class="empty-text">无</span>
                </div>
                <div v-if="preview.skillsUnlocked.some(u => u.skillType === 'PASSIVE')" class="preview-skills">
                  <span>新解锁被动技能（自动生效）：</span>
                  <span v-for="s in preview.skillsUnlocked.filter(u => u.skillType === 'PASSIVE')" :key="s.skillId" class="unlock-tag passive">{{ u2label(s) }} (Lv.{{ s.unlockLevel }})</span>
                </div>
              </div>
            </div>

            <!-- 加点区域 -->
            <div class="action-section">
              <h4>自由加点</h4>
              <p class="hint-text">HP/力量/灵力/防御/抗性 = 1 点/次；速度 = 2 点/次</p>
              <div class="allocate-row">
                <select v-model="allocateStat" class="select-stat">
                  <option value="">选择维度</option>
                  <option v-for="key in STAT_KEYS" :key="key" :value="key">{{ STAT_LABELS[key] }}{{ key === 'SPEED' ? ' (2点/次)' : '' }}</option>
                </select>
                <input v-model.number="allocatePoints" type="number" min="1" class="input-small" />
                <button class="btn-action" :disabled="actionLoading || inBattle || !allocateStat || detail.freePointsAvailable <= 0" @click="doAllocate">加点</button>
              </div>
              <button class="btn-action reset" :disabled="actionLoading || inBattle || detail.allocatedFreePoints === 0" @click="doReset">洗点（免费）</button>
            </div>
          </div>

          <!-- 技能标签（REV-017：自身主动 / 被动 两区；技能书区属阶段 10） -->
          <div v-if="activeTab === 'skills'" class="tab-content">
            <!-- 新技能提示（REV-011） -->
            <div v-if="detail.newlyLearnedSkillNames && detail.newlyLearnedSkillNames.length" class="battle-notice">
              已学习新的主动技能：{{ detail.newlyLearnedSkillNames.join('、') }}
              <template v-if="detail.skillEquipOverflow">，槽位已满，请调整战斗技能。</template>
            </div>

            <!-- 自身主动技能 -->
            <div class="skill-slots">
              <h4>自身主动技能（已掌握 {{ detail.learnedSkills.length }} / {{ detail.totalInnateActiveSkills ?? detail.learnedSkills.length }} · 装备 {{ equippedCount() }} / 4）</h4>
              <div class="slot-grid">
                <div v-for="slot in 4" :key="slot" class="slot-card">
                  <div class="slot-header">槽 {{ slot }}</div>
                  <template v-if="detail.learnedSkills.find(s => s.slot === slot)">
                    <div class="slot-skill-name">{{ detail.learnedSkills.find(s => s.slot === slot)?.name }}</div>
                    <button class="btn-link" :disabled="actionLoading || inBattle" @click="doUnequip(slot)">卸下</button>
                  </template>
                  <div v-else class="slot-empty">空</div>
                </div>
              </div>
            </div>

            <!-- 已学习技能库（主动） -->
            <div class="skill-list-section">
              <h4>技能库（非战斗状态可自由装配/替换）</h4>
              <div v-if="detail.learnedSkills.length === 0" class="empty-text">暂无已学习技能</div>
              <div v-for="skill in detail.learnedSkills" :key="skill.skillId" class="skill-card">
                <div class="skill-info">
                  <img class="skill-art-icon" :src="skillTypeIconUrl(skill)" alt="" />
                  <span class="skill-name">{{ skill.name }}<span v-if="skill.signature" class="skill-type">★特色</span></span>
                  <span class="skill-element"><img v-if="skill.element !== 'NONE'" :src="elementIconUrl(skill.element)" alt="" />{{ skill.element }}</span>
                  <span class="skill-type">{{ skill.damageType }} / {{ skill.effectType }}</span>
                  <span v-if="skill.cooldown > 0" class="skill-cd">CD {{ skill.cooldown }}</span>
                  <span v-if="skill.sourceType === 'SKILL_BOOK'" class="skill-type">技能书</span>
                  <span class="skill-slot-tag" :class="{ equipped: skill.slot }">{{ slotLabel(skill.slot) }}</span>
                </div>
                <div v-if="!skill.slot" class="skill-equip-row">
                  <button v-for="slot in 4" :key="slot" class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquip(skill.skillId, slot)">装到槽{{ slot }}</button>
                </div>
              </div>
            </div>

            <!-- 被动技能（阶段 14：区分固有被动与技能书被动） -->
            <div class="skill-list-section">
              <h4>被动技能（固有自动生效 · 技能书最多启用 2 个）</h4>

              <!-- 固有被动（自动生效，不可卸下） -->
              <div class="passive-sub" style="margin-bottom: 10px">
                <h5 style="margin: 6px 0 4px; font-size: 13px; color: var(--text-secondary)">固有被动（自动生效）</h5>
                <div v-if="!(detail.passives ?? []).some(p => p.source !== 'BOOK')" class="empty-text">暂无固有被动</div>
                <div v-for="p in detail.passives.filter(p => p.source !== 'BOOK')" :key="p.passiveId" class="skill-card" :class="{ locked: !p.unlocked }">
                  <div class="skill-info">
                    <img class="skill-art-icon" :src="skillTypeIconUrl(p)" alt="" />
                    <span class="skill-name">{{ p.name }}<span v-if="p.signature" class="skill-type">★特色</span></span>
                    <span class="skill-type">{{ sourceLabel(p.source) }}</span>
                    <span v-if="p.unlocked" class="skill-slot-tag equipped">自动生效</span>
                    <span v-else class="unlock-level">Lv.{{ p.unlockLevel }} 解锁</span>
                  </div>
                </div>
              </div>

              <!-- 被动技能书槽（槽位 7~8） -->
              <div class="slot-grid" style="margin: 6px 0">
                <div v-for="bSlot in [7, 8]" :key="bSlot" class="slot-card book-slot">
                  <div class="slot-header">被动槽 {{ bSlot - 6 }}</div>
                  <template v-if="(detail.passives ?? []).find(p => p.source === 'BOOK' && p.slot === bSlot)">
                    <div class="slot-skill-name">{{ detail.passives.find(p => p.source === 'BOOK' && p.slot === bSlot)?.name }}</div>
                    <button class="btn-link" :disabled="actionLoading || inBattle" @click="doUnequipBookPassive(bSlot)">停用</button>
                  </template>
                  <div v-else class="slot-empty">空</div>
                </div>
              </div>

              <!-- 已学习被动技能书（已启用 2/2，可启用/停用） -->
              <h5 style="margin: 6px 0 4px; font-size: 13px; color: var(--text-secondary)">
                已学习被动技能书（已启用 {{ (detail.passives ?? []).filter(p => p.source === 'BOOK' && p.slot).length }} / 2）
              </h5>
              <div v-if="!(detail.passives ?? []).some(p => p.source === 'BOOK')" class="empty-text">暂无被动技能书（可在商店购买）</div>
              <div v-for="p in detail.passives.filter(p => p.source === 'BOOK')" :key="p.passiveId" class="skill-card">
                <div class="skill-info">
                  <img class="skill-art-icon" :src="skillTypeIconUrl(p)" alt="" />
                  <span class="skill-name">{{ p.name }}</span>
                  <span class="skill-type">📖技能书</span>
                  <span class="skill-slot-tag" :class="{ equipped: p.slot }">{{ p.slot ? `已启用（被动槽${p.slot - 6}）` : '已学习' }}</span>
                </div>
                <div class="skill-actions">
                  <button v-if="!p.slot" class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquipBookPassive(p.passiveId, 7)">启用①</button>
                  <button v-if="!p.slot" class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquipBookPassive(p.passiveId, 8)">启用②</button>
                </div>
              </div>
            </div>

            <!-- 待解锁技能（主动） -->
            <div v-if="detail.availableSkills.length > 0" class="skill-list-section">
              <h4>待解锁技能</h4>
              <div v-for="skill in detail.availableSkills" :key="skill.skillId" class="skill-card locked">
                <div class="skill-info">
                  <img class="skill-art-icon" :src="skillTypeIconUrl(skill)" alt="" />
                  <span class="skill-name">{{ skill.name }}</span>
                  <span class="skill-element"><img v-if="skill.element !== 'NONE'" :src="elementIconUrl(skill.element)" alt="" />{{ skill.element }}</span>
                  <span class="unlock-level">Lv.{{ skill.unlockLevel }} 解锁</span>
                </div>
              </div>
            </div>

            <!-- 技能书主动技能区（阶段 10） -->
            <div class="skill-list-section">
              <h4>技能书主动技能（学习 {{ detail.bookSkillLearnCount ?? 0 }} / 10 · 携带 {{ (detail.bookSkillSlots ?? []).filter(s => s.slot).length }} / 2）</h4>

              <!-- 技能书装备槽（槽位 5~6） -->
              <div class="slot-grid">
                <div v-for="bSlot in [5, 6]" :key="bSlot" class="slot-card book-slot">
                  <div class="slot-header">书槽 {{ bSlot }}</div>
                  <template v-if="(detail.bookSkillSlots ?? []).find(s => s.slot === bSlot)">
                    <div class="slot-skill-name">{{ detail.bookSkillSlots.find(s => s.slot === bSlot)?.name }}</div>
                    <button class="btn-link" :disabled="actionLoading || inBattle" @click="doUnequipBook(bSlot)">卸下</button>
                  </template>
                  <div v-else class="slot-empty">空</div>
                </div>
              </div>

              <!-- 学习技能书 -->
              <div class="book-learn-row">
                <input v-model="learnBookItemId" type="text" class="input-small wide" placeholder="技能书道具 ID（如 ITEM_SKILL_BOOK_LEAVE_ALIVE）" />
                <select v-if="detail.bookSkillLearnCount >= 10" v-model="learnForgetSkillId" class="select-stat">
                  <option :value="null">选择要遗忘的技能（已满 10）</option>
                  <option v-for="s in detail.learnedBookSkills" :key="s.skillId" :value="s.skillId">{{ s.name }}</option>
                </select>
                <button class="btn-action" :disabled="actionLoading || inBattle || !learnBookItemId" @click="doLearnBook">学习技能书</button>
              </div>

              <!-- 已学技能书列表 -->
              <div v-if="!(detail.learnedBookSkills ?? []).length" class="empty-text">暂无已学技能书技能（可在商店购买技能书）</div>
              <div v-for="skill in detail.learnedBookSkills" :key="skill.skillId" class="skill-card">
                <div class="skill-info">
                  <img class="skill-art-icon" :src="skillTypeIconUrl(skill)" alt="" />
                  <span class="skill-name">{{ skill.name }}</span>
                  <span class="skill-element"><img v-if="skill.element !== 'NONE'" :src="elementIconUrl(skill.element)" alt="" />{{ skill.element }}</span>
                  <span class="skill-type">{{ skill.damageType }} / {{ skill.effectType }}</span>
                  <span v-if="skill.cooldown > 0" class="skill-cd">CD {{ skill.cooldown }}</span>
                  <span class="skill-type">技能书</span>
                  <span class="skill-slot-tag" :class="{ equipped: skill.slot }">{{ skill.slot ? `书槽${skill.slot}` : '未装备' }}</span>
                </div>
                <div class="skill-equip-row">
                  <template v-if="!skill.slot">
                    <button class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquipBook(skill.skillId, 5)">装到书槽5</button>
                    <button class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquipBook(skill.skillId, 6)">装到书槽6</button>
                  </template>
                  <button class="btn-action small danger" :disabled="actionLoading || inBattle" @click="doForgetBook(skill.skillId)">遗忘</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 推荐方案标签（阶段 10，纯展示） -->
          <div v-if="activeTab === 'build'" class="tab-content">
            <h4>推荐 Build 方案</h4>
            <p class="hint-text">按种族推荐的加点与技能组合，仅供参考，不修改实际数据。</p>
            <div v-if="!buildsLoaded" class="loading-text">加载中...</div>
            <div v-else-if="builds.length === 0" class="empty-text">该种族暂无推荐方案</div>
            <div v-for="build in builds" :key="build.name" class="build-card">
              <div class="build-header">
                <span class="build-name">{{ build.name }}</span>
              </div>
              <p class="build-desc">{{ build.description }}</p>
              <div class="build-row">
                <span class="build-label">加点优先级：</span>
                <span v-for="(stat, i) in build.statPriority" :key="stat" class="stat-tag">
                  {{ i + 1 }}. {{ STAT_LABELS[stat] ?? stat }}
                </span>
              </div>
              <div class="build-row">
                <span class="build-label">推荐技能：</span>
                <span v-for="skillId in build.skillPriority" :key="skillId" class="skill-tag">{{ skillId }}</span>
              </div>
            </div>
          </div>

          <!-- 记录标签（阶段 11 / 需求 §113） -->
          <div v-if="activeTab === 'history'" class="tab-content">
            <h4>个人履历</h4>
            <p class="hint-text">仅作记录展示，不增加属性、不反向影响战斗数值。</p>
            <div class="history-grid">
              <div class="history-item">
                <span class="history-label">捕获日期</span>
                <span class="history-value small">{{ detail.pet.capturedAt ? formatDate(detail.pet.capturedAt) : '未知' }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">捕获地点</span>
                <span class="history-value small">{{ capturedLocationLabel(detail.pet.capturedMapId) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">捕获等级</span>
                <span class="history-value small">Lv.{{ nz(detail.pet.capturedLevel) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">战斗 / 胜利</span>
                <span class="history-value small">{{ nz(detail.pet.battleCount) }} / {{ nz(detail.pet.winCount) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">累计击败</span>
                <span class="history-value small">{{ nz(detail.pet.killCount) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">捕捉辅助</span>
                <span class="history-value small">{{ nz(detail.pet.captureAssistCount) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">Boss 参与 / 胜利</span>
                <span class="history-value small">{{ nz(detail.pet.bossBattleCount) }} / {{ nz(detail.pet.bossWinCount) }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">累计造成伤害</span>
                <span class="history-value small">{{ nz(detail.pet.totalDamage).toLocaleString('zh-CN') }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">累计承受伤害</span>
                <span class="history-value small">{{ nz(detail.pet.totalDamageTaken).toLocaleString('zh-CN') }}</span>
              </div>
              <div class="history-item">
                <span class="history-label">累计治疗量</span>
                <span class="history-value small">{{ nz(detail.pet.totalHeal).toLocaleString('zh-CN') }}</span>
              </div>
            </div>
          </div>

          <p v-if="actionError" class="error-text">{{ actionError }}</p>
        </template>

        <div v-else-if="!loading && !error" class="empty-text">选择左侧宠物查看详情</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pet-view {
  padding: 24px;
  max-width: 960px;
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

.pet-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
}

.pet-list-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
}

.pet-list-panel h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.pet-list-item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background-color 0.2s;
}

.pet-list-item:hover {
  background-color: var(--bg-main);
}

.pet-list-item.active {
  background-color: rgba(74, 144, 217, 0.15);
  border-left: 3px solid var(--color-primary);
}

.list-pet-name {
  font-weight: 500;
  font-size: 13px;
}

.list-pet-info {
  display: flex;
  gap: 6px;
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.starter-tag {
  color: var(--color-secondary);
}

.pet-detail-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-1);
  min-height: 400px;
}

.tab-bar {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #eee;
  margin-bottom: 16px;
}

.tab-btn {
  padding: 8px 16px;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.info-value {
  font-weight: 500;
  font-size: 13px;
}

.rarity-common { color: var(--rarity-common); }
.rarity-rare { color: var(--rarity-rare); }
.rarity-epic { color: var(--rarity-epic); }
.rarity-legend { color: var(--rarity-legend); }

.species-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-top: 12px;
}

.resource-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background-color: var(--bg-main);
  border-radius: 6px;
}

.resource-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.resource-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
}

.resource-hint {
  font-size: 11px;
  color: var(--text-secondary);
}

.stat-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  font-size: 13px;
}

.stat-table th {
  text-align: left;
  padding: 6px;
  color: var(--text-secondary);
  border-bottom: 2px solid #eee;
  font-size: 12px;
}

.stat-table td {
  padding: 6px;
  border-bottom: 1px solid #f5f5f5;
}

.stat-name {
  font-weight: 500;
}

.stat-total {
  font-weight: 600;
  color: var(--color-primary);
}

.action-section {
  margin-bottom: 20px;
  padding: 12px;
  background-color: var(--bg-main);
  border-radius: 6px;
}

.action-section h4 {
  font-size: 14px;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.upgrade-buttons {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.upgrade-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.input-small {
  width: 80px;
  padding: 4px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
}

/* 技能书与推荐 Build（阶段 10） */
.input-small.wide {
  width: 280px;
}

.book-learn-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
  flex-wrap: wrap;
}

.book-slot {
  border-color: #c9a86a;
}

.btn-action.small.danger {
  background-color: #e53e3e;
  color: #fff;
}

.build-card {
  background-color: var(--bg-card);
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-md);
  padding: 14px 16px;
  margin-bottom: 12px;
}

.build-name {
  font-weight: 700;
  font-size: 15px;
  color: var(--color-primary);
}

.build-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 6px 0 10px;
}

.build-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.build-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background-color: rgba(74, 144, 217, 0.12);
  color: var(--color-primary);
  font-weight: 600;
}

.skill-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background-color: #f1f5f9;
  color: var(--text-secondary);
}

.select-stat {
  padding: 4px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
}

.allocate-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.btn-action {
  padding: 6px 14px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-action.small {
  padding: 4px 10px;
  font-size: 12px;
}

.btn-action.reset {
  background-color: var(--color-warning);
}

.preview-panel {
  margin-top: 10px;
  padding: 10px;
  background-color: var(--bg-card);
  border-radius: 6px;
  border-left: 3px solid var(--color-primary);
}

.preview-panel h4 {
  margin-bottom: 4px;
}

.preview-info {
  font-size: 12px;
  color: var(--text-secondary);
}

.preview-skills {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 12px;
  margin-top: 4px;
}

.unlock-tag {
  background-color: #fff3cd;
  color: #856404;
  padding: 2px 6px;
  border-radius: 4px;
}

.hint-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.btn-link {
  background: none;
  border: none;
  color: var(--color-danger);
  cursor: pointer;
  font-size: 12px;
  text-decoration: underline;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.slot-card {
  padding: 8px;
  background-color: var(--bg-main);
  border-radius: 6px;
  text-align: center;
  min-height: 60px;
}

.slot-header {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.slot-skill-name {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 4px;
}

.slot-empty {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 32px;
}

.skill-card {
  padding: 8px;
  border: 1px solid #eee;
  border-radius: 6px;
  margin-bottom: 6px;
}

.skill-card.locked {
  opacity: 0.6;
}

.skill-info {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
}

.skill-name {
  font-weight: 500;
}

.skill-art-icon {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.skill-element {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--color-primary);
  background-color: rgba(74, 144, 217, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
}

.skill-element img {
  width: 14px;
  height: 14px;
  object-fit: contain;
}

.skill-type {
  font-size: 11px;
  color: var(--text-secondary);
}

.skill-cd {
  font-size: 11px;
  color: var(--color-warning);
}

.skill-slot-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background-color: #eee;
  color: var(--text-secondary);
}

.skill-slot-tag.equipped {
  background-color: var(--color-success);
  color: #fff;
}

.skill-equip-row {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.empty-text {
  color: var(--text-secondary);
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}

.loading-text {
  text-align: center;
  padding: 40px 0;
  color: var(--text-secondary);
}

.error-text {
  color: #d32f2f;
  font-size: 13px;
  margin-top: 8px;
}

/* 宠物履历（阶段 11 / 需求 §113） */
.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.history-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  background-color: var(--bg-main);
  border-radius: 6px;
}

.history-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.history-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
}

.history-value.small {
  font-size: 14px;
}

@media (max-width: 768px) {
  .pet-layout {
    grid-template-columns: 1fr;
  }
}
</style>
