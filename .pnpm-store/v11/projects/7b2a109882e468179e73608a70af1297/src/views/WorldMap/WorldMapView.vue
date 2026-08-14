<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMapStore } from '../../stores/map'
import type { CampView, RegionView } from '../../types/map'

/**
 * 大地图页面雏形（阶段 6，需求 §116）。
 * 展示：区域、推荐等级、Boss 状态（占位）、营地、传送；激活营地可快速传送。
 */
const router = useRouter()
const mapStore = useMapStore()
const busyKey = ref('')
const error = ref('')
const regionThumbnailByMapId: Record<string, string> = {
  MAP_START_VILLAGE: 'village',
  MAP_AREA_MEADOW: 'meadow',
  MAP_AREA_FOREST: 'forest',
  MAP_AREA_WATERS: 'waters',
  MAP_AREA_THUNDER: 'thunder',
  MAP_AREA_RUINS: 'ruins',
}

function regionThumbnailUrl(mapId: string): string {
  const region = regionThumbnailByMapId[mapId] ?? 'meadow'
  return `/assets/backgrounds/region_${region}_thumb.png`
}

onMounted(async () => {
  try {
    await mapStore.loadWorldMap()
  } catch (e) {
    error.value = mapStore.error || '加载大地图失败'
  }
})

/** 进入区域（落到默认出生点）。 */
async function enterRegion(region: RegionView) {
  if (busyKey.value) return
  busyKey.value = `enter_${region.mapId}`
  error.value = ''
  try {
    await mapStore.enterRegion(region.mapId)
    router.push('/explore')
  } catch (e) {
    error.value = mapStore.error || '进入区域失败'
  } finally {
    busyKey.value = ''
  }
}

/** 已激活营地传送。 */
async function teleport(camp: CampView) {
  if (busyKey.value) return
  busyKey.value = `tp_${camp.campId}`
  error.value = ''
  try {
    await mapStore.teleportToCamp(camp.campId)
    router.push('/explore')
  } catch (e) {
    error.value = mapStore.error || '传送失败'
  } finally {
    busyKey.value = ''
  }
}

function goExplore() {
  router.push('/explore')
}
</script>

<template>
  <div class="world-map-view">
    <div class="wm-header">
      <h2>大地图</h2>
      <button class="btn-secondary" @click="goExplore">返回探索</button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="!mapStore.worldMap" class="loading-text">加载中...</div>

    <div v-else class="region-grid">
      <div
        v-for="region in mapStore.worldMap.regions"
        :key="region.mapId"
        class="region-card"
        :class="{ current: region.current, locked: !region.unlocked }"
      >
        <img class="region-thumb" :src="regionThumbnailUrl(region.mapId)" :alt="`${region.name}缩略图`" />
        <div class="region-title">
          <span class="region-name">{{ region.name }}</span>
          <span v-if="region.current" class="tag tag-current">当前</span>
          <span v-if="!region.unlocked" class="tag tag-locked">未解锁</span>
        </div>
        <div class="region-meta">
          <span>类型：{{ region.type === 'BASE' ? '起始据点' : '主要区域' }}</span>
          <span>推荐等级：Lv.{{ region.recommendedLevel }}</span>
          <span>Boss：{{ region.bossStatus === 'NOT_OPEN' ? '未开放（阶段 7）' : region.bossStatus }}</span>
        </div>

        <div v-if="region.camps.length > 0" class="camp-list">
          <div v-for="camp in region.camps" :key="camp.campId" class="camp-row">
            <span class="camp-name">
              营地：{{ camp.name }}
              <span v-if="camp.activated" class="tag tag-active">已激活</span>
            </span>
            <button
              v-if="camp.activated"
              class="btn-teleport"
              :disabled="busyKey === `tp_${camp.campId}`"
              @click="teleport(camp)"
            >
              传送
            </button>
          </div>
        </div>

        <div class="region-actions">
          <button
            class="btn-primary"
            :disabled="!region.unlocked || busyKey !== ''"
            @click="enterRegion(region)"
          >
            {{ region.current ? '回到此区域' : '进入区域' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.world-map-view {
  max-width: 900px;
  margin: 0 auto;
}

.wm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.wm-header h2 {
  font-size: 20px;
  color: var(--color-primary);
}

.btn-secondary {
  padding: 8px 20px;
  background-color: var(--bg-main);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
}

.region-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.region-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
  padding: 16px;
  border: 2px solid transparent;
}

.region-thumb {
  display: block;
  width: calc(100% + 32px);
  height: 132px;
  margin: -16px -16px 12px;
  object-fit: cover;
  border-radius: calc(var(--radius-md) - 2px) calc(var(--radius-md) - 2px) 0 0;
}

.region-card.current {
  border-color: var(--color-primary);
}

.region-card.locked {
  opacity: 0.55;
}

.region-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.region-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  color: #fff;
}

.tag-current {
  background-color: var(--color-primary);
}

.tag-locked {
  background-color: #8a8f98;
}

.tag-active {
  background-color: #2e7d32;
}

.region-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.camp-list {
  margin-bottom: 10px;
}

.camp-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-primary);
  padding: 4px 0;
}

.camp-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-teleport {
  padding: 4px 14px;
  background-color: #2e7d32;
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
}

.btn-teleport:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.region-actions {
  display: flex;
  justify-content: flex-end;
}

.btn-primary {
  padding: 8px 20px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-text {
  text-align: center;
  padding: 60px 0;
  color: var(--text-secondary);
}

.error-text {
  color: #d32f2f;
  font-size: 14px;
  margin-bottom: 10px;
}

@media (max-width: 768px) {
  .world-map-view { padding: 8px; }
  .region-grid { grid-template-columns: 1fr; }
}
</style>
