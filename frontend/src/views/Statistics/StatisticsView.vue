<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useStatisticsStore } from '../../stores/statistics'
import { COMPLETION_COMPONENT_LABELS } from '../../types/statistics'

const statsStore = useStatisticsStore()

/** 统计键显示配置（顺序 + 中文名 + 是否格式化时长）。 */
const STAT_DEFS: Array<{ key: string; label: string; asTime?: boolean }> = [
  { key: 'PLAY_TIME_SECONDS', label: '游戏时长', asTime: true },
  { key: 'BATTLES_TOTAL', label: '战斗总次数' },
  { key: 'BATTLES_WON', label: '战斗胜利' },
  { key: 'BATTLES_LOST', label: '战斗失败' },
  { key: 'FLED_COUNT', label: '逃跑次数' },
  { key: 'CAPTURES_SUCCESS', label: '捕获成功' },
  { key: 'CAPTURES_FAILED', label: '捕获失败' },
  { key: 'RELEASED_PETS', label: '放生宠物' },
  { key: 'TOTAL_KILLS', label: '累计击败' },
  { key: 'BOSS_DEFEATED', label: 'Boss 击败' },
  { key: 'BOSS_CHALLENGES', label: 'Boss 挑战目标' },
  { key: 'MAX_DAMAGE', label: '最高单次伤害' },
  { key: 'MAX_CRIT_DAMAGE', label: '最高暴击伤害' },
  { key: 'CAPTURE_BALLS_USED', label: '捕捉球消耗' },
  { key: 'GOLD_EARNED', label: '累计获得金币' },
  { key: 'EXP_EARNED', label: '累计获得经验' },
  { key: 'MAX_PET_LEVEL', label: '最高宠物等级' },
  { key: 'MAX_LEVEL_PETS', label: '满级宠物' },
  { key: 'TOTAL_DAMAGE', label: '累计造成伤害' },
  { key: 'TOTAL_HEAL', label: '累计治疗量' },
  { key: 'POKEDEX_RESEARCHED_5', label: '图鉴 Lv.5 种族' },
  { key: 'ELITE_CAPTURED', label: '精英个体捕获' },
  { key: 'SPECIAL_APPEARANCE_CAPTURED', label: '特殊外观捕获' },
]

const componentList = computed(() => {
  if (!statsStore.completion) return []
  return Object.entries(statsStore.completion.components).map(([key, val]) => ({
    key,
    label: COMPLETION_COMPONENT_LABELS[key] ?? key,
    ...val,
  }))
})

function statValue(key: string): number {
  if (!statsStore.overview) return 0
  return statsStore.overview.stats[key] ?? 0
}

/** 秒 → 时长文本。 */
function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) return `${h}小时${m}分`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

function formatNumber(v: number): string {
  return v.toLocaleString('zh-CN')
}

onMounted(async () => {
  await statsStore.loadAll()
})
</script>

<template>
  <div class="statistics-page">
    <h2 class="page-title">我的统计</h2>

    <div v-if="statsStore.loading" class="loading">加载中...</div>
    <div v-if="statsStore.error" class="error">{{ statsStore.error }}</div>

    <!-- 游戏完成度 -->
    <section v-if="statsStore.completion" class="completion-section">
      <div class="completion-header">
        <h3>游戏完成度</h3>
        <span class="completion-percent">{{ statsStore.completion.overall }}%</span>
      </div>
      <div class="completion-bar">
        <div
          class="completion-fill"
          :style="{ width: Math.min(100, statsStore.completion.overall) + '%' }"
        ></div>
      </div>
      <div class="component-list">
        <div v-for="comp in componentList" :key="comp.key" class="component-item">
          <div class="component-label">
            <span>{{ comp.label }}</span>
            <span class="component-contribution">+{{ comp.contribution }}%</span>
          </div>
          <div class="component-bar">
            <div class="component-fill" :style="{ width: Math.min(100, comp.progress * 100) + '%' }"></div>
          </div>
          <div class="component-meta">
            <span>进度 {{ Math.round(comp.progress * 100) }}%</span>
            <span>权重 {{ Math.round(comp.weight * 100) }}%</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 使用最多的宠物 / 技能 -->
    <section class="stats-grid-section">
      <h3>使用最多</h3>
      <div class="most-used-row">
        <div class="most-used-card">
          <span class="most-label">使用最多宠物</span>
          <span class="most-value">{{ statsStore.overview?.mostUsed.mostUsedPet ?? '无' }}</span>
          <span class="most-count">{{ statsStore.overview?.mostUsed.mostUsedPetCount ?? 0 }} 次</span>
        </div>
        <div class="most-used-card">
          <span class="most-label">使用最多技能</span>
          <span class="most-value">{{ statsStore.overview?.mostUsed.mostUsedSkill ?? '无' }}</span>
          <span class="most-count">{{ statsStore.overview?.mostUsed.mostUsedSkillCount ?? 0 }} 次</span>
        </div>
      </div>
    </section>

    <!-- 详细统计 -->
    <section class="stats-grid-section">
      <h3>详细统计</h3>
      <div class="stats-grid">
        <div v-for="def in STAT_DEFS" :key="def.key" class="stat-card">
          <span class="stat-label">{{ def.label }}</span>
          <span class="stat-value">{{ def.asTime ? formatTime(statValue(def.key)) : formatNumber(statValue(def.key)) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.statistics-page {
  padding: 16px;
}

.page-title {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.loading, .error {
  text-align: center;
  padding: 20px;
  color: var(--text-secondary);
}

.error {
  color: #e74c3c;
}

section {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

section h3 {
  font-size: 15px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

/* 完成度 */
.completion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.completion-percent {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
}

.completion-bar {
  height: 12px;
  background: #333;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 16px;
}

.completion-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), #2ecc71);
  border-radius: inherit;
  transition: width 0.4s;
}

.component-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.component-item {
  padding: 10px;
  background: var(--bg-main);
  border-radius: 6px;
}

.component-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}

.component-contribution {
  color: var(--color-primary);
  font-weight: 600;
}

.component-bar {
  height: 6px;
  background: #333;
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}

.component-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: inherit;
}

.component-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-secondary);
}

/* 使用最多 */
.most-used-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.most-used-card {
  padding: 12px;
  background: var(--bg-main);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.most-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.most-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.most-count {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 详细统计 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}

.stat-card {
  padding: 10px;
  background: var(--bg-main);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
}
</style>