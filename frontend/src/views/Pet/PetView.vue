<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { useBattleStore } from '../../stores/battle'
import { apiGet, apiPost, BusinessError } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import type { PetDetail, LevelUpPreview } from '../../types/pet'

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
const activeTab = ref<'basic' | 'stats' | 'skills'>('basic')

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
            <button class="tab-btn" :class="{ active: activeTab === 'basic' }" @click="activeTab = 'basic'">基础</button>
            <button class="tab-btn" :class="{ active: activeTab === 'stats' }" @click="activeTab = 'stats'">属性</button>
            <button class="tab-btn" :class="{ active: activeTab === 'skills' }" @click="activeTab = 'skills'">技能</button>
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
                  <span>解锁技能：</span>
                  <span v-for="s in preview.skillsUnlocked" :key="s.skillId" class="unlock-tag">{{ s.skillId }} (Lv.{{ s.unlockLevel }})</span>
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

          <!-- 技能标签 -->
          <div v-if="activeTab === 'skills'" class="tab-content">
            <!-- 装备槽位 -->
            <div class="skill-slots">
              <h4>装备槽位（1~4）</h4>
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

            <!-- 已学习技能 -->
            <div class="skill-list-section">
              <h4>已学习技能</h4>
              <div v-if="detail.learnedSkills.length === 0" class="empty-text">暂无已学习技能</div>
              <div v-for="skill in detail.learnedSkills" :key="skill.skillId" class="skill-card">
                <div class="skill-info">
                  <span class="skill-name">{{ skill.name }}</span>
                  <span class="skill-element">{{ skill.element }}</span>
                  <span class="skill-type">{{ skill.damageType }} / {{ skill.effectType }}</span>
                  <span v-if="skill.cooldown > 0" class="skill-cd">CD {{ skill.cooldown }}</span>
                  <span class="skill-slot-tag" :class="{ equipped: skill.slot }">{{ slotLabel(skill.slot) }}</span>
                </div>
                <div v-if="!skill.slot" class="skill-equip-row">
                  <button v-for="slot in 4" :key="slot" class="btn-action small" :disabled="actionLoading || inBattle" @click="doEquip(skill.skillId, slot)">装到槽{{ slot }}</button>
                </div>
              </div>
            </div>

            <!-- 待解锁技能 -->
            <div v-if="detail.availableSkills.length > 0" class="skill-list-section">
              <h4>待解锁技能</h4>
              <div v-for="skill in detail.availableSkills" :key="skill.skillId" class="skill-card locked">
                <div class="skill-info">
                  <span class="skill-name">{{ skill.name }}</span>
                  <span class="skill-element">{{ skill.element }}</span>
                  <span class="unlock-level">Lv.{{ skill.unlockLevel }} 解锁</span>
                </div>
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

.skill-element {
  font-size: 11px;
  color: var(--color-primary);
  background-color: rgba(74, 144, 217, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
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

@media (max-width: 768px) {
  .pet-layout {
    grid-template-columns: 1fr;
  }
}
</style>