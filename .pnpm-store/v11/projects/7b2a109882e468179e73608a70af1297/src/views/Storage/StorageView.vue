<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useStorageStore } from '../../stores/storage'
import { useGameStore } from '../../stores/game'
import type { ReleasePreview, StoragePetView } from '../../types/storage'

const storageStore = useStorageStore()
const gameStore = useGameStore()

// ---- 筛选与排序条件 ----
const query = reactive({
  name: '',
  element: '',
  rarity: '',
  levelMin: null as number | null,
  levelMax: null as number | null,
  aptitudeMin: null as number | null,
  hasRareSkill: false,
  hasSpecialAppearance: false,
  favoriteOnly: false,
  lockedOnly: false,
  inTeam: '' as '' | 'true' | 'false',
  sortBy: 'CAPTURED_AT',
  sortDirection: 'DESC',
})

const ELEMENTS = ['METAL', 'WOOD', 'WATER', 'FIRE', 'EARTH', 'WIND', 'THUNDER', 'LIGHT', 'DARK']
const RARITIES = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY']
const RARITY_NAMES: Record<string, string> = {
  COMMON: '普通',
  RARE: '稀有',
  EPIC: '珍稀',
  LEGENDARY: '传说',
}
const BLOCK_REASON_NAMES: Record<string, string> = {
  LOCKED: '已锁定',
  FAVORITE: '已收藏',
  IN_TEAM: '在队伍中',
}
const WARNING_NAMES: Record<string, string> = {
  HIGH_RARITY: '珍稀/传说宠物',
  HIGH_APTITUDE: '高资质',
  RARE_SKILL: '携带稀有技能',
  SPECIAL_APPEARANCE: '特殊外观',
}

// ---- 批量选择与放生流程 ----
const selectedIds = ref<number[]>([])
const releasePreview = ref<ReleasePreview | null>(null)
const nicknameEditing = ref<{ petId: number; value: string } | null>(null)
const localError = ref('')

onMounted(() => {
  loadStorage()
})

async function loadStorage() {
  await storageStore.loadStorage({
    name: query.name || undefined,
    element: query.element || undefined,
    rarity: query.rarity || undefined,
    levelMin: query.levelMin,
    levelMax: query.levelMax,
    aptitudeMin: query.aptitudeMin,
    hasRareSkill: query.hasRareSkill || null,
    hasSpecialAppearance: query.hasSpecialAppearance || null,
    favoriteOnly: query.favoriteOnly || null,
    lockedOnly: query.lockedOnly || null,
    inTeam: query.inTeam === '' ? null : query.inTeam === 'true',
    sortBy: query.sortBy,
    sortDirection: query.sortDirection,
  })
}

function resetFilters() {
  query.name = ''
  query.element = ''
  query.rarity = ''
  query.levelMin = null
  query.levelMax = null
  query.aptitudeMin = null
  query.hasRareSkill = false
  query.hasSpecialAppearance = false
  query.favoriteOnly = false
  query.lockedOnly = false
  query.inTeam = ''
  loadStorage()
}

function displayName(pet: StoragePetView): string {
  return pet.nickname ? `${pet.nickname}（${pet.speciesName}）` : pet.speciesName
}

// ---- 昵称 / 锁定 / 收藏 ----

function startNickname(pet: StoragePetView) {
  nicknameEditing.value = { petId: pet.petId, value: pet.nickname ?? '' }
}

async function saveNickname() {
  if (!nicknameEditing.value) return
  try {
    localError.value = ''
    await storageStore.setNickname(nicknameEditing.value.petId, nicknameEditing.value.value)
    nicknameEditing.value = null
    await loadStorage()
  } catch (e: any) {
    localError.value = e.message || '昵称设置失败'
  }
}

async function toggleLocked(pet: StoragePetView) {
  try {
    localError.value = ''
    await storageStore.setLocked(pet.petId, !pet.locked)
    await loadStorage()
  } catch (e: any) {
    localError.value = e.message || '操作失败'
  }
}

async function toggleFavorite(pet: StoragePetView) {
  try {
    localError.value = ''
    await storageStore.setFavorite(pet.petId, !pet.favorite)
    await loadStorage()
  } catch (e: any) {
    localError.value = e.message || '操作失败'
  }
}

// ---- 放生流程（预览 → 二次确认 → 执行） ----

function toggleSelect(petId: number) {
  const idx = selectedIds.value.indexOf(petId)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(petId)
  }
}

/** 打开放生预览（单只或批量）。 */
async function openReleasePreview(petIds: number[]) {
  localError.value = ''
  releasePreview.value = null
  try {
    releasePreview.value = await storageStore.previewRelease(petIds)
  } catch (e: any) {
    localError.value = e.message || '放生预览失败'
  }
}

function confirmRelease() {
  if (!releasePreview.value) return
  const ids = releasePreview.value.pets.map((p) => p.petId)
  return doRelease(ids)
}

async function doRelease(petIds: number[]) {
  try {
    localError.value = ''
    await storageStore.releasePets(petIds)
    releasePreview.value = null
    selectedIds.value = []
    await loadStorage()
    await gameStore.loadBootstrap()
  } catch (e: any) {
    localError.value = e.message || '放生失败'
  }
}

function closeReleaseResult() {
  storageStore.clearReleaseResult()
}

function giftName(type: string, itemId: string | null): string {
  if (type === 'GOLD') return '金币'
  if (type === 'EXP') return '经验'
  return itemId ?? '道具'
}
</script>

<template>
  <div class="storage-view">
    <h2 class="page-title">宠物仓库</h2>
    <p class="page-desc">不限容量；筛选排序浏览，锁定/收藏保护，放生会获得临别礼物。</p>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <input v-model="query.name" type="text" class="filter-input" placeholder="名称/昵称搜索" @keyup.enter="loadStorage" />
      <select v-model="query.element" class="filter-select">
        <option value="">全部属性</option>
        <option v-for="el in ELEMENTS" :key="el" :value="el">{{ el }}</option>
      </select>
      <select v-model="query.rarity" class="filter-select">
        <option value="">全部稀有度</option>
        <option v-for="r in RARITIES" :key="r" :value="r">{{ RARITY_NAMES[r] }}</option>
      </select>
      <input v-model.number="query.levelMin" type="number" class="filter-input small" placeholder="等级下限" />
      <input v-model.number="query.levelMax" type="number" class="filter-input small" placeholder="等级上限" />
      <input v-model.number="query.aptitudeMin" type="number" class="filter-input small" placeholder="综合资质下限" />
      <select v-model="query.inTeam" class="filter-select">
        <option value="">全部（在队与否）</option>
        <option value="true">仅在队伍</option>
        <option value="false">仅在仓库</option>
      </select>
      <select v-model="query.sortBy" class="filter-select">
        <option value="CAPTURED_AT">按捕获时间</option>
        <option value="LEVEL">按等级</option>
        <option value="RARITY">按稀有度</option>
        <option value="APTITUDE">按综合资质</option>
      </select>
      <select v-model="query.sortDirection" class="filter-select small">
        <option value="DESC">降序</option>
        <option value="ASC">升序</option>
      </select>
      <label class="filter-check"><input v-model="query.hasRareSkill" type="checkbox" />稀有技能</label>
      <label class="filter-check"><input v-model="query.hasSpecialAppearance" type="checkbox" />特殊外观</label>
      <label class="filter-check"><input v-model="query.favoriteOnly" type="checkbox" />收藏</label>
      <label class="filter-check"><input v-model="query.lockedOnly" type="checkbox" />锁定</label>
      <button class="btn-primary small" :disabled="storageStore.loading" @click="loadStorage">查询</button>
      <button class="btn-secondary" @click="resetFilters">重置</button>
    </div>

    <!-- 批量操作条 -->
    <div v-if="selectedIds.length > 0" class="batch-bar">
      已选 {{ selectedIds.length }} 只
      <button class="btn-danger small" @click="openReleasePreview(selectedIds)">批量放生</button>
      <button class="btn-link" @click="selectedIds = []">取消选择</button>
      <span class="batch-hint">锁定/收藏/在队宠物将被自动排除</span>
    </div>

    <p v-if="localError" class="error-text">{{ localError }}</p>
    <p v-if="storageStore.error" class="error-text">{{ storageStore.error }}</p>

    <!-- 放生预览（二次确认） -->
    <div v-if="releasePreview" class="release-preview-panel">
      <h3>放生确认</h3>
      <div v-for="info in releasePreview.pets" :key="info.petId" class="preview-item">
        <span class="preview-name">{{ info.name }}</span>
        <span v-if="info.releasable" class="preview-points">礼物点数 {{ info.giftPoints }}</span>
        <span v-else class="preview-block">
          不可放生：{{ info.blockReasons.map((r) => BLOCK_REASON_NAMES[r] ?? r).join('、') }}
        </span>
        <span v-if="info.warningReasons.length" class="preview-warning">
          ⚠ {{ info.warningReasons.map((w) => WARNING_NAMES[w] ?? w).join('、') }}
        </span>
      </div>
      <div class="preview-total">可放生礼物点数合计：{{ releasePreview.totalGiftPoints }}</div>
      <div class="preview-actions">
        <button class="btn-danger" :disabled="releasePreview.totalGiftPoints <= 0" @click="confirmRelease">
          确认放生
        </button>
        <button class="btn-secondary" @click="releasePreview = null">取消</button>
      </div>
    </div>

    <!-- 放生结果（礼物汇总） -->
    <div v-if="storageStore.lastReleaseResult" class="release-result-panel">
      <h3>临别礼物</h3>
      <p class="result-summary">
        放生 {{ storageStore.lastReleaseResult.released.length }} 只
        <template v-if="storageStore.lastReleaseResult.skipped.length">
          ，排除 {{ storageStore.lastReleaseResult.skipped.length }} 只（受保护/在队）
        </template>
        ，礼物总点数 {{ storageStore.lastReleaseResult.totalGiftPoints }}
      </p>
      <div class="gift-list">
        <span v-for="(gift, idx) in storageStore.lastReleaseResult.gifts" :key="idx" class="gift-tag">
          {{ giftName(gift.type, gift.itemId) }} ×{{ gift.quantity }}
        </span>
      </div>
      <button class="btn-secondary" @click="closeReleaseResult">关闭</button>
    </div>

    <!-- 仓库列表 -->
    <div v-if="storageStore.loading" class="loading-text">加载中...</div>
    <div v-else-if="storageStore.pets.length === 0" class="empty-text">仓库中没有符合条件的宠物</div>
    <div v-else class="pet-grid">
      <div v-for="pet in storageStore.pets" :key="pet.petId" class="pet-card">
        <div class="pet-head">
          <label class="select-check">
            <input
              type="checkbox"
              :checked="selectedIds.includes(pet.petId)"
              @change="toggleSelect(pet.petId)"
            />
          </label>
          <img class="pet-icon" :src="`/assets/pets/icons/pet_${pet.speciesId}_icon_64.png`" :alt="pet.speciesName" />
          <span class="pet-name">{{ displayName(pet) }}</span>
          <span class="pet-rarity" :class="pet.rarity.toLowerCase()">{{ RARITY_NAMES[pet.rarity] ?? pet.rarity }}</span>
        </div>
        <div class="pet-info">
          <span>{{ pet.element }} · Lv.{{ pet.level }}</span>
          <span>综合资质 {{ pet.aptitudeTotal }}（均值 {{ pet.aptitudeAverage }}）</span>
          <span>HP {{ pet.currentHp }}</span>
        </div>
        <div class="pet-tags">
          <span v-if="pet.inTeam" class="tag team">在队伍</span>
          <span v-if="pet.locked" class="tag locked">锁定</span>
          <span v-if="pet.favorite" class="tag favorite">收藏</span>
          <span v-if="pet.starter" class="tag starter">初始伙伴</span>
          <span v-if="pet.specialAppearance" class="tag special">特殊外观</span>
          <span v-if="pet.rareSkillIds.length" class="tag rare-skill">稀有技能</span>
        </div>

        <!-- 昵称编辑 -->
        <div v-if="nicknameEditing?.petId === pet.petId" class="nickname-row">
          <input v-model="nicknameEditing.value" type="text" maxlength="12" placeholder="昵称（最长 12 字符）" />
          <button class="btn-primary small" @click="saveNickname">保存</button>
          <button class="btn-link" @click="nicknameEditing = null">取消</button>
        </div>

        <div class="pet-actions">
          <button class="btn-secondary small" @click="startNickname(pet)">昵称</button>
          <button class="btn-secondary small" @click="toggleLocked(pet)">{{ pet.locked ? '解锁' : '锁定' }}</button>
          <button class="btn-secondary small" @click="toggleFavorite(pet)">{{ pet.favorite ? '取消收藏' : '收藏' }}</button>
          <button class="btn-danger small" :disabled="pet.locked || pet.favorite || pet.inTeam" @click="openReleasePreview([pet.petId])">
            放生
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.storage-view {
  padding: 8px;
  max-width: 1100px;
  margin: 0 auto;
}

.page-title {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 4px;
}

.page-desc {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 16px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
  margin-bottom: 12px;
}

.filter-input {
  padding: 6px 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
  width: 140px;
}

.filter-input.small { width: 90px; }

.filter-select {
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
}

.filter-select.small { width: 76px; }

.filter-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: #fff5e0;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 13px;
}

.batch-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.error-text {
  color: #d32f2f;
  font-size: 13px;
  margin-bottom: 8px;
}

.release-preview-panel,
.release-result-panel {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-1);
  margin-bottom: 12px;
  border-left: 4px solid #d32f2f;
}

.release-result-panel {
  border-left-color: #2e7d32;
}

.release-preview-panel h3,
.release-result-panel h3 {
  font-size: 15px;
  margin-bottom: 10px;
}

.preview-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  padding: 4px 0;
}

.preview-name { font-weight: 600; }
.preview-points { color: #2e7d32; }
.preview-block { color: #8e8e93; }
.preview-warning { color: #b87800; }

.preview-total {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.preview-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.result-summary {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.gift-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.gift-tag {
  background-color: #e8f5e9;
  color: #2e7d32;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.loading-text,
.empty-text {
  text-align: center;
  color: var(--text-secondary);
  padding: 32px 0;
}

.pet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.pet-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-1);
}

.pet-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.pet-name {
  font-weight: 600;
  flex: 1;
}

.pet-icon {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.pet-rarity {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: #f0f0f0;
  color: #555;
}

.pet-rarity.rare { background-color: #e8f1ff; color: #2b5fa8; }
.pet-rarity.epic { background-color: #f3e5f5; color: #7b1fa2; }
.pet-rarity.legendary { background-color: #fff3cd; color: #856404; }

.pet-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.pet-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 20px;
  margin-bottom: 6px;
}

.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}

.tag.team { background-color: #e8f1ff; color: #2b5fa8; }
.tag.locked { background-color: #ffecec; color: #a83a2b; }
.tag.favorite { background-color: #fff5e0; color: #b87800; }
.tag.starter { background-color: #f0f0f0; color: #555; }
.tag.special { background-color: #f3e5f5; color: #7b1fa2; }
.tag.rare-skill { background-color: #fff3cd; color: #856404; }

.nickname-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.nickname-row input {
  flex: 1;
  padding: 5px 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 13px;
}

.pet-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.btn-primary {
  padding: 8px 20px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.btn-primary.small {
  padding: 5px 12px;
  font-size: 12px;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 8px 16px;
  background-color: #f0f0f0;
  color: var(--text-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-secondary.small {
  padding: 5px 10px;
  font-size: 12px;
}

.btn-danger {
  padding: 8px 16px;
  background-color: #d32f2f;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-danger.small {
  padding: 5px 10px;
  font-size: 12px;
}

.btn-danger:disabled {
  background-color: #bbb;
  cursor: not-allowed;
}

.btn-link {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  text-decoration: underline;
  font-size: 13px;
}

@media (max-width: 768px) {
  .storage-view { padding: 4px; }
  .filter-bar { flex-direction: column; align-items: stretch; }
  .filter-input, .filter-select { width: 100%; }
  .filter-input.small, .filter-select.small { width: 100%; }
  .pet-grid { grid-template-columns: 1fr; }
}
</style>
