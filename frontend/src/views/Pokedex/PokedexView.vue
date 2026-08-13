<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { usePokedexStore } from '../../stores/pokedex'
import type { PokedexDetail, PokedexEntry } from '../../types/pokedex'

const pokedexStore = usePokedexStore()

const selectedSpecies = ref<PokedexDetail | null>(null)
const showDetail = ref(false)

const elementLabels: Record<string, string> = {
  NONE: '无', FIRE: '火', WATER: '水', WOOD: '木', METAL: '金',
  EARTH: '土', WIND: '风', THUNDER: '雷', LIGHT: '光', DARK: '暗',
}

const rarityLabels: Record<string, string> = {
  COMMON: '普通', RARE: '稀有', EPIC: '珍稀', LEGENDARY: '传说',
}

const rarityColors: Record<string, string> = {
  COMMON: '#8b8b8b', RARE: '#4a90d9', EPIC: '#c455e8', LEGENDARY: '#f5a623',
}

const levelLabels = ['Lv.0', 'Lv.1', 'Lv.2', 'Lv.3', 'Lv.4', 'Lv.5']

const statLabels: Record<string, string> = {
  hp: '生命', strength: '力量', spirit: '灵力',
  defense: '防御', resistance: '抗性', speed: '速度',
}

/** 提取所有不重复的属性列表。 */
const availableElements = computed(() => {
  const set = new Set<string>()
  for (const e of pokedexStore.entries) {
    if (e.element) set.add(e.element)
  }
  return Array.from(set)
})

onMounted(async () => {
  await pokedexStore.loadPokedex()
})

async function selectSpecies(entry: PokedexEntry) {
  const detail = await pokedexStore.loadDetail(entry.speciesId)
  if (detail) {
    selectedSpecies.value = detail
    showDetail.value = true
  }
}

function closeDetail() {
  showDetail.value = false
  selectedSpecies.value = null
}

function setFilterLevel(level: number | null) {
  pokedexStore.filterLevel = level
}

function setFilterElement(element: string | null) {
  pokedexStore.filterElement = element
}

/** 根据研究等级返回卡片样式类。 */
function cardClass(entry: PokedexEntry): string {
  if (entry.researchLevel === 0) return 'card-unknown'
  if (entry.caught) return 'card-caught'
  if (entry.seen) return 'card-seen'
  return 'card-unknown'
}
</script>

<template>
  <div class="pokedex-page">
    <h2 class="page-title">宠物图鉴</h2>

    <!-- 统计栏 -->
    <div class="stats-bar">
      <span class="stat-item">发现 <strong>{{ pokedexStore.discoveredCount }}</strong>/{{ pokedexStore.totalCount }}</span>
      <span class="stat-item">捕获 <strong>{{ pokedexStore.caughtCount }}</strong>/{{ pokedexStore.totalCount }}</span>
      <span class="stat-item">完全研究 <strong>{{ pokedexStore.fullyResearchedCount }}</strong>/{{ pokedexStore.totalCount }}</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">研究等级：</span>
        <button
          :class="['filter-btn', { active: pokedexStore.filterLevel === null }]"
          @click="setFilterLevel(null)"
        >全部</button>
        <button
          v-for="lv in [0, 1, 2, 3, 4, 5]"
          :key="lv"
          :class="['filter-btn', { active: pokedexStore.filterLevel === lv }]"
          @click="setFilterLevel(lv)"
        >{{ levelLabels[lv] }}</button>
      </div>
      <div class="filter-group">
        <span class="filter-label">属性：</span>
        <button
          :class="['filter-btn', { active: pokedexStore.filterElement === null }]"
          @click="setFilterElement(null)"
        >全部</button>
        <button
          v-for="el in availableElements"
          :key="el"
          :class="['filter-btn', { active: pokedexStore.filterElement === el }]"
          @click="setFilterElement(el)"
        >{{ elementLabels[el] ?? el }}</button>
      </div>
    </div>

    <div v-if="pokedexStore.loading" class="loading">加载中...</div>
    <div v-if="pokedexStore.error" class="error">{{ pokedexStore.error }}</div>

    <!-- 卡片网格 -->
    <div class="pokedex-grid">
      <div
        v-for="entry in pokedexStore.filteredEntries"
        :key="entry.speciesId"
        :class="['pokedex-card', cardClass(entry)]"
        @click="selectSpecies(entry)"
      >
        <div class="card-header">
          <span class="card-level">{{ levelLabels[entry.researchLevel] }}</span>
          <span v-if="entry.rarity" class="card-rarity" :style="{ color: rarityColors[entry.rarity] ?? '#8b8b8b' }">
            {{ rarityLabels[entry.rarity] ?? entry.rarity }}
          </span>
        </div>
        <div class="card-icon">
          <template v-if="entry.researchLevel === 0">?</template>
          <template v-else>{{ entry.name?.[0] ?? '?' }}</template>
        </div>
        <div class="card-name">
          {{ entry.researchLevel >= 1 ? entry.name : '???' }}
        </div>
        <div class="card-element" v-if="entry.element">
          {{ elementLabels[entry.element] ?? entry.element }}
        </div>
        <div class="card-progress">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: Math.min(100, (entry.researchLevel / 5) * 100) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情面板 -->
    <div v-if="showDetail && selectedSpecies" class="detail-overlay" @click.self="closeDetail">
      <div class="detail-panel">
        <button class="close-btn" @click="closeDetail">✕</button>

        <h3 class="detail-title">
          {{ selectedSpecies.researchLevel >= 1 ? selectedSpecies.name : '???' }}
          <span class="detail-level">{{ levelLabels[selectedSpecies.researchLevel] }}</span>
        </h3>

        <!-- 研究进度条 -->
        <div class="detail-progress">
          <div class="progress-label">
            研究值: {{ selectedSpecies.researchPoints }}
            <span v-if="selectedSpecies.caught" class="badge-caught">已捕获</span>
            <span v-else-if="selectedSpecies.seen" class="badge-seen">已发现</span>
          </div>
          <div class="progress-bar large">
            <div
              class="progress-fill"
              :style="{ width: Math.min(100, (selectedSpecies.researchLevel / 5) * 100) + '%' }"
            ></div>
          </div>
          <div class="level-marks">
            <span v-for="lv in [0,1,2,3,4,5]" :key="lv"
                  :class="['level-mark', { active: selectedSpecies.researchLevel >= lv }]">
              {{ levelLabels[lv] }}
            </span>
          </div>
        </div>

        <!-- Lv.1: 基本信息 -->
        <div v-if="selectedSpecies.researchLevel >= 1" class="info-section">
          <h4>基本信息</h4>
          <div class="info-row">
            <span>属性: {{ elementLabels[selectedSpecies.element ?? ''] ?? selectedSpecies.element }}</span>
          </div>
          <p class="description">{{ selectedSpecies.description }}</p>
        </div>

        <!-- Lv.2: 稀有度 + 捕获率 -->
        <div v-if="selectedSpecies.researchLevel >= 2" class="info-section">
          <h4>捕获信息</h4>
          <div class="info-row">
            <span>稀有度:
              <span :style="{ color: rarityColors[selectedSpecies.rarity ?? ''] ?? '#8b8b8b' }">
                {{ rarityLabels[selectedSpecies.rarity ?? ''] ?? selectedSpecies.rarity }}
              </span>
            </span>
            <span>基础捕获率: {{ ((selectedSpecies.captureRate ?? 0) * 100).toFixed(0) }}%</span>
          </div>
        </div>

        <!-- Lv.3: 技能 + 六维 -->
        <div v-if="selectedSpecies.researchLevel >= 3" class="info-section">
          <h4>成长倾向</h4>
          <div class="base-stats" v-if="selectedSpecies.baseStats">
            <div v-for="(val, key) in selectedSpecies.baseStats" :key="key" class="stat-item">
              <span class="stat-label">{{ statLabels[key] ?? key }}</span>
              <span class="stat-value">{{ val }}</span>
            </div>
          </div>

          <h4>种族技能</h4>
          <div class="skill-list" v-if="selectedSpecies.skills?.length">
            <div v-for="skill in selectedSpecies.skills" :key="skill.skillId" class="skill-item">
              <span class="skill-name">
                {{ skill.skillName }}
                <span v-if="skill.signature" class="badge-sig">特色</span>
              </span>
              <span class="skill-unlock">Lv.{{ skill.unlockLevel }}</span>
            </div>
          </div>

          <h4 v-if="selectedSpecies.passives?.length">种族被动</h4>
          <div class="skill-list" v-if="selectedSpecies.passives?.length">
            <div v-for="p in selectedSpecies.passives" :key="p.passiveId" class="skill-item">
              <span class="skill-name">
                {{ p.passiveName }}
                <span v-if="p.signature" class="badge-sig">特色</span>
              </span>
              <span class="skill-unlock">Lv.{{ p.unlockLevel }}</span>
            </div>
          </div>
        </div>

        <!-- Lv.4: 稀有技能 + 出现区域 -->
        <div v-if="selectedSpecies.researchLevel >= 4" class="info-section">
          <h4>稀有技能池</h4>
          <div class="tag-list" v-if="selectedSpecies.rareSkills?.length">
            <span v-for="rs in selectedSpecies.rareSkills" :key="rs" class="tag rare">{{ rs }}</span>
          </div>
          <span v-else class="no-data">无</span>

          <h4>出现区域</h4>
          <div class="tag-list" v-if="selectedSpecies.encounterRegions?.length">
            <span v-for="r in selectedSpecies.encounterRegions" :key="r" class="tag region">{{ r }}</span>
          </div>
          <span v-else class="no-data">无已知区域</span>
        </div>

        <!-- Lv.5: 历史记录 -->
        <div v-if="selectedSpecies.researchLevel >= 5 && selectedSpecies.history" class="info-section">
          <h4>历史记录</h4>
          <div class="history-grid">
            <div class="history-item">
              <span class="history-label">捕获总数</span>
              <span class="history-value">{{ selectedSpecies.history.totalCaptures }}</span>
            </div>
            <div class="history-item">
              <span class="history-label">击败总数</span>
              <span class="history-value">{{ selectedSpecies.history.totalDefeats }}</span>
            </div>
            <div class="history-item">
              <span class="history-label">精英遭遇</span>
              <span class="history-value">{{ selectedSpecies.history.eliteEncounters }}</span>
            </div>
            <div class="history-item">
              <span class="history-label">特殊外观</span>
              <span class="history-value">{{ selectedSpecies.history.specialAppearances }}</span>
            </div>
            <div class="history-item">
              <span class="history-label">最高综合资质</span>
              <span class="history-value">{{ selectedSpecies.history.bestCombinedAptitude }}</span>
            </div>
          </div>

          <h4>六维最高资质</h4>
          <div class="base-stats">
            <div class="stat-item">
              <span class="stat-label">生命</span>
              <span class="stat-value">{{ selectedSpecies.history.bestHp }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">力量</span>
              <span class="stat-value">{{ selectedSpecies.history.bestStrength }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">灵力</span>
              <span class="stat-value">{{ selectedSpecies.history.bestSpirit }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">防御</span>
              <span class="stat-value">{{ selectedSpecies.history.bestDefense }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">抗性</span>
              <span class="stat-value">{{ selectedSpecies.history.bestResistance }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">速度</span>
              <span class="stat-value">{{ selectedSpecies.history.bestSpeed }}</span>
            </div>
          </div>

          <h4 v-if="selectedSpecies.history.discoveredRareSkills?.length">已发现稀有技能</h4>
          <div class="tag-list" v-if="selectedSpecies.history.discoveredRareSkills?.length">
            <span v-for="rs in selectedSpecies.history.discoveredRareSkills" :key="rs" class="tag rare">{{ rs }}</span>
          </div>
        </div>

        <!-- 进化占位 -->
        <div v-if="selectedSpecies.researchLevel >= 5 && selectedSpecies.evolutionPlaceholder" class="info-section">
          <h4>进化资料</h4>
          <p class="placeholder-text">{{ selectedSpecies.evolutionPlaceholder }}</p>
        </div>

        <!-- 未解锁提示 -->
        <div v-if="selectedSpecies.researchLevel === 0" class="info-section locked">
          <p>??? — 尚未发现此种族</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pokedex-page {
  padding: 16px;
}

.page-title {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.stats-bar {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  padding: 8px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.stat-item {
  font-size: 14px;
  color: var(--text-secondary);
}

.stat-item strong {
  color: var(--color-primary);
  font-size: 16px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-right: 4px;
}

.filter-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-main);
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.15s;
}

.filter-btn:hover {
  border-color: var(--color-primary);
}

.filter-btn.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.loading, .error {
  text-align: center;
  padding: 20px;
  color: var(--text-secondary);
}

.error {
  color: #e74c3c;
}

/* 卡片网格 */
.pokedex-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.pokedex-card {
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  text-align: center;
  transition: transform 0.15s, box-shadow 0.15s;
  border: 2px solid transparent;
}

.pokedex-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-2);
}

.card-unknown {
  background: #2a2a2a;
  color: #666;
  border-color: #333;
}

.card-seen {
  background: var(--bg-card);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-1);
}

.card-caught {
  background: var(--bg-card);
  border-color: var(--color-success, #2ecc71);
  box-shadow: var(--shadow-1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  margin-bottom: 4px;
}

.card-level {
  font-weight: bold;
  color: var(--text-secondary);
}

.card-rarity {
  font-weight: bold;
}

.card-icon {
  font-size: 36px;
  margin: 8px 0;
  line-height: 1;
}

.card-unknown .card-icon {
  color: #555;
}

.card-name {
  font-size: 14px;
  font-weight: bold;
  margin-bottom: 2px;
}

.card-element {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.card-progress {
  margin-top: 4px;
}

.progress-bar {
  height: 4px;
  background: #333;
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar.large {
  height: 8px;
  border-radius: 4px;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: inherit;
  transition: width 0.3s;
}

/* 详情面板 */
.detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.detail-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg, 12px);
  padding: 24px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  position: relative;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 18px;
  cursor: pointer;
  padding: 4px 8px;
}

.close-btn:hover {
  color: var(--text-primary);
}

.detail-title {
  font-size: 20px;
  margin-bottom: 12px;
  color: var(--color-primary);
}

.detail-level {
  font-size: 14px;
  color: var(--text-secondary);
  margin-left: 8px;
}

.detail-progress {
  margin-bottom: 16px;
}

.progress-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.level-marks {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
}

.level-mark {
  color: #555;
}

.level-mark.active {
  color: var(--color-primary);
  font-weight: bold;
}

.badge-caught {
  background: #2ecc71;
  color: #fff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  margin-left: 6px;
}

.badge-seen {
  background: var(--color-primary);
  color: #fff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  margin-left: 6px;
}

/* 信息区块 */
.info-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.info-section h4 {
  font-size: 14px;
  color: var(--color-primary);
  margin-bottom: 8px;
}

.info-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  margin-bottom: 4px;
}

.description {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* 六维基础值 */
.base-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px 12px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 2px 0;
}

.stat-label {
  color: var(--text-secondary);
}

.stat-value {
  font-weight: bold;
  color: var(--text-primary);
}

/* 技能列表 */
.skill-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.skill-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 4px;
}

.skill-name {
  display: flex;
  align-items: center;
  gap: 4px;
}

.skill-unlock {
  font-size: 11px;
  color: var(--text-secondary);
}

.badge-sig {
  font-size: 10px;
  background: var(--color-primary);
  color: #fff;
  padding: 0 4px;
  border-radius: 3px;
}

/* 标签列表 */
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.tag.rare {
  background: rgba(196, 85, 232, 0.2);
  color: #c455e8;
  border: 1px solid rgba(196, 85, 232, 0.3);
}

.tag.region {
  background: rgba(74, 144, 217, 0.2);
  color: #4a90d9;
  border: 1px solid rgba(74, 144, 217, 0.3);
}

.no-data {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 历史记录 */
.history-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.history-item {
  text-align: center;
  padding: 6px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 4px;
}

.history-label {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
}

.history-value {
  display: block;
  font-size: 16px;
  font-weight: bold;
  color: var(--color-primary);
}

.placeholder-text {
  font-size: 13px;
  color: var(--text-secondary);
  font-style: italic;
}

.info-section.locked {
  text-align: center;
  padding: 40px 20px;
  color: #555;
  font-size: 16px;
}
</style>
