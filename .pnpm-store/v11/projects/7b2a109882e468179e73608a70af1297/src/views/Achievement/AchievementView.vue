<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAchievementStore } from '../../stores/achievement'
import { ACHIEVEMENT_CATEGORY_LABELS } from '../../types/achievement'

const achievementStore = useAchievementStore()

const categoryColors: Record<string, string> = {
  EXPLORE: '#4a90d9',
  CAPTURE: '#c455e8',
  BREED: '#2ecc71',
  BATTLE: '#e67e22',
  BOSS: '#e74c3c',
  POKEDEX: '#3498db',
  SPECIAL: '#f5a623',
}

const categoryList = computed(() => {
  const keys = Object.keys(achievementStore.categoryCounts)
  return keys
})

onMounted(async () => {
  await achievementStore.loadAchievements()
})

function formatTime(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<template>
  <div class="achievement-page">
    <h2 class="page-title">成就</h2>

    <!-- 总览统计 -->
    <div class="summary-bar">
      <span class="summary-item">
        已解锁 <strong>{{ achievementStore.unlockedCount }}</strong> / {{ achievementStore.totalCount }}
      </span>
      <span class="summary-item">完成率
        <strong>{{ achievementStore.totalCount ? Math.round((achievementStore.unlockedCount / achievementStore.totalCount) * 100) : 0 }}%</strong>
      </span>
    </div>

    <!-- 分类筛选 -->
    <div class="filter-bar">
      <button
        :class="['filter-btn', { active: achievementStore.filterCategory === null }]"
        @click="achievementStore.setCategory(null)"
      >全部</button>
      <button
        v-for="cat in categoryList"
        :key="cat"
        :class="['filter-btn', { active: achievementStore.filterCategory === cat }]"
        :style="{ '--cat-color': categoryColors[cat] ?? '#888' }"
        @click="achievementStore.setCategory(cat)"
      >
        {{ ACHIEVEMENT_CATEGORY_LABELS[cat] ?? cat }}
        ({{ achievementStore.categoryCounts[cat]?.unlocked ?? 0 }}/{{ achievementStore.categoryCounts[cat]?.total ?? 0 }})
      </button>
    </div>

    <!-- 解锁状态筛选 -->
    <div class="filter-bar slim">
      <button
        :class="['filter-btn', { active: achievementStore.filterUnlocked === null }]"
        @click="achievementStore.setUnlockedFilter(null)"
      >全部状态</button>
      <button
        :class="['filter-btn', { active: achievementStore.filterUnlocked === true }]"
        @click="achievementStore.setUnlockedFilter(true)"
      >已解锁</button>
      <button
        :class="['filter-btn', { active: achievementStore.filterUnlocked === false }]"
        @click="achievementStore.setUnlockedFilter(false)"
      >未解锁</button>
    </div>

    <div v-if="achievementStore.loading" class="loading">加载中...</div>
    <div v-if="achievementStore.error" class="error">{{ achievementStore.error }}</div>

    <!-- 成就网格 -->
    <div class="achievement-grid">
      <div
        v-for="ach in achievementStore.filteredAchievements"
        :key="ach.id"
        :class="['achievement-card', { unlocked: ach.unlocked }]"
      >
        <div class="achievement-header">
          <span class="achievement-category" :style="{ background: categoryColors[ach.category] ?? '#888' }">
            {{ ACHIEVEMENT_CATEGORY_LABELS[ach.category] ?? ach.category }}
          </span>
          <span v-if="ach.unlocked" class="badge-unlocked">已解锁</span>
          <span v-else class="badge-locked">未解锁</span>
        </div>
        <div class="achievement-name">{{ ach.name }}</div>
        <div class="achievement-desc">{{ ach.description }}</div>
        <div v-if="ach.unlocked && ach.unlockedAt" class="achievement-time">
          解锁于 {{ formatTime(ach.unlockedAt) }}
        </div>
        <div v-if="ach.titleId" class="achievement-perk">称号：{{ ach.titleId }}</div>
        <div v-if="ach.avatarId" class="achievement-perk">头像：{{ ach.avatarId }}</div>
      </div>
    </div>

    <div v-if="!achievementStore.loading && achievementStore.filteredAchievements.length === 0" class="empty">
      暂无符合条件的成就
    </div>
  </div>
</template>

<style scoped>
.achievement-page {
  padding: 16px;
}

.page-title {
  font-size: 20px;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.summary-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.summary-item {
  font-size: 14px;
  color: var(--text-secondary);
}

.summary-item strong {
  color: var(--color-primary);
  font-size: 16px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
}

.filter-bar.slim {
  margin-bottom: 16px;
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

.empty {
  text-align: center;
  padding: 40px;
  color: var(--text-secondary);
}

.achievement-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.achievement-card {
  padding: 14px;
  border-radius: var(--radius-md);
  background: var(--bg-card);
  border: 2px solid var(--border-color);
  box-shadow: var(--shadow-1);
  transition: transform 0.15s, box-shadow 0.15s;
}

.achievement-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-2);
}

.achievement-card.unlocked {
  border-color: var(--color-success, #2ecc71);
}

.achievement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.achievement-category {
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.badge-unlocked {
  font-size: 11px;
  color: #fff;
  background: var(--color-success, #2ecc71);
  padding: 1px 6px;
  border-radius: 4px;
}

.badge-locked {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-main);
  padding: 1px 6px;
  border-radius: 4px;
}

.achievement-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.achievement-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 8px;
}

.achievement-time {
  font-size: 11px;
  color: var(--text-secondary);
}

.achievement-perk {
  font-size: 12px;
  color: var(--color-primary);
  margin-top: 4px;
}

@media (max-width: 768px) {
  .achievement-view { padding: 8px; }
  .achievement-grid { grid-template-columns: 1fr; }
}
</style>