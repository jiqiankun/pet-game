<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBossStore } from '../../stores/boss'
import type { BossInfo, DifficultyInfo, DropTierInfo } from '../../types/boss'

const route = useRoute()
const router = useRouter()
const bossStore = useBossStore()

const selectedBoss = ref<BossInfo | null>(null)
const selectedDifficulty = ref<string>('NORMAL')
const autoMode = ref<string>('ONCE')
const showAutoResult = ref(false)

const difficultyLabels: Record<string, string> = {
  NORMAL: '普通',
  HARD: '困难',
  NIGHTMARE: '噩梦',
}

const rarityLabels: Record<string, string> = {
  COMMON: '普通',
  RARE: '稀有',
  EPIC: '珍稀',
  LEGENDARY: '传说',
}

const rarityColors: Record<string, string> = {
  COMMON: '#8b8b8b',
  RARE: '#4a90d9',
  EPIC: '#c455e8',
  LEGENDARY: '#f5a623',
}

const elementLabels: Record<string, string> = {
  NONE: '无', FIRE: '火', WATER: '水', WOOD: '木', METAL: '金',
  EARTH: '土', WIND: '风', THUNDER: '雷', LIGHT: '光', DARK: '暗',
}

onMounted(async () => {
  await bossStore.loadBosses()
  // 从地图入口携带的 bossId 参数预选 Boss
  const queryBossId = route.query.bossId as string | undefined
  if (queryBossId && bossStore.bosses.some(b => b.bossId === queryBossId)) {
    selectBoss(queryBossId)
  } else if (bossStore.bosses.length > 0) {
    selectBoss(bossStore.bosses[0].bossId)
  }
})

async function selectBoss(bossId: string) {
  bossStore.currentBossId = bossId
  selectedBoss.value = await bossStore.loadBoss(bossId) ?? null
  selectedDifficulty.value = 'NORMAL'
  showAutoResult.value = false
}

const currentDifficulty = computed<DifficultyInfo | undefined>(() => {
  return selectedBoss.value?.difficulties.find(d => d.difficulty === selectedDifficulty.value)
})

async function startBattle() {
  if (!selectedBoss.value) return
  const battleId = await bossStore.startBossBattle(
    selectedBoss.value.bossId, selectedDifficulty.value
  )
  if (battleId) {
    router.push('/battle')
  }
}

async function doAutoChallenge() {
  if (!selectedBoss.value) return
  await bossStore.autoChallenge(selectedBoss.value.bossId, selectedDifficulty.value, autoMode.value)
  showAutoResult.value = true
  // 刷新详情
  selectedBoss.value = await bossStore.loadBoss(selectedBoss.value.bossId) ?? null
}

async function doExchange(itemId: string) {
  if (!selectedBoss.value) return
  const ok = await bossStore.exchangeLuck(selectedBoss.value.bossId, itemId)
  if (ok) {
    selectedBoss.value = await bossStore.loadBoss(selectedBoss.value.bossId) ?? null
  }
}

function getTotalDefeatCount(boss: BossInfo): number {
  return boss.difficulties.reduce((sum, d) => sum + d.defeatCount, 0)
}
</script>

<template>
  <div class="boss-page">
    <h2 class="page-title">Boss 挑战</h2>

    <div v-if="bossStore.loading" class="loading">加载中...</div>
    <div v-if="bossStore.error" class="error">{{ bossStore.error }}</div>

    <div class="boss-layout" v-if="selectedBoss">
      <!-- 左侧 Boss 列表 -->
      <div class="boss-list">
        <div
          v-for="boss in bossStore.bosses"
          :key="boss.bossId"
          :class="['boss-card', { active: boss.bossId === selectedBoss.bossId }]"
          @click="selectBoss(boss.bossId)"
        >
          <div class="boss-name">{{ boss.name }}</div>
          <div class="boss-meta">
            <span class="element-tag">{{ elementLabels[boss.element] ?? boss.element }}</span>
            <span class="level-tag">Lv.{{ boss.recommendedLevel }}</span>
          </div>
          <div class="boss-stats">
            <span>击败: {{ getTotalDefeatCount(boss) }}</span>
            <span>幸运: {{ boss.luckValue }}</span>
          </div>
        </div>
      </div>

      <!-- 中部详情 -->
      <div class="boss-detail">
        <h3>{{ selectedBoss.name }}</h3>
        <div class="info-row">
          <span>属性: {{ elementLabels[selectedBoss.element] ?? selectedBoss.element }}</span>
          <span>推荐等级: {{ selectedBoss.recommendedLevel }}</span>
          <span>幸运值: {{ selectedBoss.luckValue }}</span>
        </div>

        <!-- 难度选择 -->
        <div class="difficulty-tabs">
          <button
            v-for="diff in selectedBoss.difficulties"
            :key="diff.difficulty"
            :class="['diff-btn', { active: diff.difficulty === selectedDifficulty, locked: !diff.unlocked }]"
            :disabled="!diff.unlocked"
            @click="selectedDifficulty = diff.difficulty"
          >
            <span v-if="!diff.unlocked">🔒</span>
            {{ difficultyLabels[diff.difficulty] ?? diff.difficulty }}
            <span v-if="diff.defeatCount > 0" class="defeat-badge">{{ diff.defeatCount }}</span>
          </button>
        </div>

        <!-- 掉落情报 -->
        <div class="drop-info" v-if="currentDifficulty">
          <h4>掉落情报</h4>
          <div v-for="tier in currentDifficulty.dropInfo" :key="tier.rarity" class="drop-tier">
            <div class="tier-header" :style="{ color: rarityColors[tier.rarity] }">
              {{ rarityLabels[tier.rarity] ?? tier.rarity }}
              <span v-if="!tier.unlocked" class="locked-tag">未解锁</span>
            </div>
            <div v-if="tier.unlocked" class="tier-items">
              <div v-for="item in tier.items" :key="item.itemId" class="drop-item">
                <span class="item-id">{{ item.itemId }}</span>
                <span class="item-qty">×{{ item.qty }}</span>
                <span class="item-chance">{{ (item.chance * 100).toFixed(0) }}%</span>
              </div>
            </div>
            <div v-else class="tier-locked">??? （击败更多 Boss 解锁）</div>
          </div>
        </div>

        <!-- 操作栏 -->
        <div class="action-bar">
          <button class="btn-primary" @click="startBattle"
                  :disabled="!currentDifficulty?.unlocked || bossStore.loading">
            挑战
          </button>

          <div class="auto-section">
            <select v-model="autoMode" class="auto-select">
              <option value="ONCE">1 次</option>
              <option value="FIVE">5 次</option>
              <option value="TEN">10 次</option>
              <option value="UNTIL_FAIL">直到失败</option>
              <option value="UNTIL_LUCKY">直到可兑换</option>
            </select>
            <button class="btn-secondary" @click="doAutoChallenge"
                    :disabled="!currentDifficulty?.unlocked || bossStore.loading">
              自动挑战
            </button>
          </div>
        </div>

        <!-- 幸运兑换 -->
        <div class="exchange-section" v-if="currentDifficulty && selectedBoss.luckValue >= 100">
          <h4>幸运兑换（消耗 100 幸运值）</h4>
          <div v-for="tier in currentDifficulty.dropInfo.filter(t => t.unlocked)" :key="tier.rarity" class="exchange-tier">
            <div v-for="item in tier.items" :key="item.itemId" class="exchange-item">
              <span>{{ item.itemId }} ×{{ item.exchangeQty }}</span>
              <button class="btn-small" @click="doExchange(item.itemId)">兑换</button>
            </div>
          </div>
        </div>

        <!-- 自动挑战结果 -->
        <div class="auto-result" v-if="showAutoResult && bossStore.autoResult">
          <h4>自动挑战结果</h4>
          <div class="result-grid">
            <div>总场次: {{ bossStore.autoResult.totalBattles }}</div>
            <div>胜利: {{ bossStore.autoResult.wins }}</div>
            <div>失败: {{ bossStore.autoResult.losses }}</div>
            <div>经验: +{{ bossStore.autoResult.totalExp }}</div>
            <div>金币: +{{ bossStore.autoResult.totalGold }}</div>
            <div>幸运值: {{ bossStore.autoResult.finalLuck }}</div>
          </div>
          <div v-if="bossStore.autoResult.totalDrops.length > 0" class="result-drops">
            <div v-for="(drop, i) in bossStore.autoResult.totalDrops" :key="i">
              {{ drop.itemId }} ×{{ drop.qty }}
              <span :style="{ color: rarityColors[drop.rarity] }">({{ rarityLabels[drop.rarity] ?? drop.rarity }})</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.boss-page { padding: 16px; }
.page-title { font-size: 22px; color: var(--color-primary); margin-bottom: 16px; }
.loading { color: var(--text-secondary); padding: 24px; }
.error { color: #e74c3c; padding: 12px; background: #fdf0f0; border-radius: 6px; margin-bottom: 12px; }

.boss-layout { display: flex; gap: 20px; }
.boss-list { width: 240px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.boss-card {
  padding: 12px; background: var(--bg-card); border-radius: 8px; cursor: pointer;
  border: 2px solid transparent; transition: border-color 0.2s;
}
.boss-card.active { border-color: var(--color-primary); }
.boss-card:hover { border-color: var(--color-primary-hover, var(--color-primary)); }
.boss-name { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
.boss-meta { display: flex; gap: 8px; font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; }
.element-tag { background: #e8f4f8; padding: 1px 6px; border-radius: 4px; }
.level-tag { color: var(--text-secondary); }
.boss-stats { display: flex; gap: 12px; font-size: 12px; color: var(--text-secondary); }

.boss-detail { flex: 1; background: var(--bg-card); border-radius: 8px; padding: 20px; }
.boss-detail h3 { font-size: 18px; margin-bottom: 8px; }
.info-row { display: flex; gap: 16px; font-size: 13px; color: var(--text-secondary); margin-bottom: 16px; }

.difficulty-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.diff-btn {
  padding: 6px 16px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer;
  font-size: 13px; transition: all 0.2s;
}
.diff-btn.active { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.diff-btn.locked { opacity: 0.5; cursor: not-allowed; }
.defeat-badge {
  display: inline-block; background: rgba(0,0,0,0.1); padding: 0 5px; border-radius: 8px;
  font-size: 11px; margin-left: 4px;
}

.drop-info { margin-bottom: 16px; }
.drop-info h4 { font-size: 14px; margin-bottom: 8px; }
.drop-tier { margin-bottom: 8px; padding: 8px; background: #f8f9fa; border-radius: 6px; }
.tier-header { font-weight: 600; font-size: 13px; margin-bottom: 4px; }
.locked-tag { font-size: 11px; color: #999; margin-left: 6px; font-weight: normal; }
.tier-items { display: flex; flex-wrap: wrap; gap: 8px; }
.drop-item { font-size: 12px; display: flex; gap: 4px; align-items: center; }
.item-id { font-weight: 500; }
.item-qty { color: #666; }
.item-chance { color: #999; font-size: 11px; }
.tier-locked { font-size: 12px; color: #999; font-style: italic; }

.action-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.btn-primary {
  padding: 8px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-size: 14px; font-weight: 600;
}
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary {
  padding: 8px 16px; background: #6c757d; color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-size: 13px;
}
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.auto-section { display: flex; gap: 8px; align-items: center; }
.auto-select { padding: 6px 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 13px; }

.exchange-section { margin-bottom: 16px; padding: 12px; background: #fff8e6; border-radius: 8px; }
.exchange-section h4 { font-size: 14px; margin-bottom: 8px; color: #b8860b; }
.exchange-item {
  display: flex; justify-content: space-between; align-items: center; padding: 4px 0; font-size: 13px;
}
.btn-small {
  padding: 3px 10px; background: #f5a623; color: #fff; border: none; border-radius: 4px;
  cursor: pointer; font-size: 12px;
}

.auto-result { padding: 12px; background: #f0f7ff; border-radius: 8px; }
.auto-result h4 { font-size: 14px; margin-bottom: 8px; }
.result-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; font-size: 13px; margin-bottom: 8px; }
.result-drops { font-size: 12px; }
.result-drops div { padding: 2px 0; }
</style>
