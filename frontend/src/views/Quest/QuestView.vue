<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useQuestStore } from '../../stores/quest'
import type { QuestSummary, QuestDetail } from '../../types/quest'
import QuestDetailPanel from './components/QuestDetail.vue'

const questStore = useQuestStore()

type Tab = 'main' | 'side' | 'completed'
const activeTab = ref<Tab>('main')
const selectedQuestId = ref<string | null>(null)
const selectedDetail = ref<QuestDetail | null>(null)
const detailLoading = ref(false)
const completeResult = ref<any>(null)

const tabs: { key: Tab; label: string }[] = [
  { key: 'main', label: '主线' },
  { key: 'side', label: '支线' },
  { key: 'completed', label: '已完成' },
]

const mainQuests = computed(() => questStore.questList?.mainQuests ?? [])
const sideQuests = computed(() => questStore.questList?.sideQuests ?? [])

const hiddenQuests = computed(() => {
  const list = questStore.questList?.hiddenQuests ?? []
  return list.filter(q => q.status !== 'LOCKED')
})

const completedQuests = computed(() => {
  const all = [...mainQuests.value, ...sideQuests.value, ...hiddenQuests.value]
  return all.filter(q => q.status === 'COMPLETED')
})

const currentList = computed<QuestSummary[]>(() => {
  if (activeTab.value === 'main') return [...mainQuests.value, ...hiddenQuests.value]
  if (activeTab.value === 'side') return sideQuests.value
  return completedQuests.value
})

const statusLabel: Record<string, string> = {
  AVAILABLE: '可接受',
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  LOCKED: '未解锁',
}

const statusClass: Record<string, string> = {
  AVAILABLE: 'status-available',
  ACTIVE: 'status-active',
  COMPLETED: 'status-completed',
  LOCKED: 'status-locked',
}

const typeLabel: Record<string, string> = {
  MAIN: '主线',
  SIDE: '支线',
  HIDDEN: '隐藏',
}

onMounted(async () => {
  await questStore.loadQuests()
})

async function selectQuest(quest: QuestSummary) {
  selectedQuestId.value = quest.questId
  completeResult.value = null
  detailLoading.value = true
  try {
    selectedDetail.value = await questStore.loadQuestDetail(quest.questId)
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  selectedQuestId.value = null
  selectedDetail.value = null
  completeResult.value = null
}

async function acceptQuest(questId: string) {
  const ok = await questStore.acceptQuest(questId)
  if (ok) {
    await selectQuest({ questId } as QuestSummary)
  }
}

async function completeQuest(questId: string) {
  const result = await questStore.completeQuest(questId)
  if (result) {
    completeResult.value = result
    await selectQuest({ questId } as QuestSummary)
  }
}

async function chooseReward(choiceId: string, optionIndex: number) {
  if (!selectedDetail.value) return
  const ok = await questStore.chooseReward(selectedDetail.value.questId, choiceId, optionIndex)
  if (ok) {
    await selectQuest({ questId: selectedDetail.value.questId } as QuestSummary)
  }
}
</script>

<template>
  <div class="quest-view">
    <h2 class="page-title">任务</h2>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span v-if="tab.key === 'completed'" class="tab-count">({{ completedQuests.length }})</span>
      </button>
    </div>

    <!-- 列表 + 详情双栏 -->
    <div class="quest-layout">
      <!-- 任务列表 -->
      <div class="quest-list">
        <p v-if="questStore.loading && !questStore.questList" class="list-loading">加载中...</p>
        <p v-else-if="currentList.length === 0" class="list-empty">暂无任务</p>
        <div
          v-for="quest in currentList"
          :key="quest.questId"
          class="quest-item"
          :class="[statusClass[quest.status], { selected: selectedQuestId === quest.questId }]"
          @click="selectQuest(quest)"
        >
          <div class="quest-item-header">
            <span class="quest-type-badge" :class="'type-' + quest.type.toLowerCase()">
              {{ typeLabel[quest.type] ?? quest.type }}
            </span>
            <span class="quest-name">{{ quest.hidden && quest.status !== 'COMPLETED' ? '???' : quest.name }}</span>
          </div>
          <div class="quest-item-footer">
            <span class="quest-status" :class="statusClass[quest.status]">
              {{ statusLabel[quest.status] ?? quest.status }}
            </span>
          </div>
        </div>
      </div>

      <!-- 任务详情 -->
      <div class="quest-detail-pane">
        <p v-if="detailLoading" class="detail-loading">加载中...</p>
        <p v-else-if="!selectedDetail" class="detail-placeholder">选择一个任务查看详情</p>
        <QuestDetailPanel
          v-else
          :detail="selectedDetail"
          :complete-result="completeResult"
          @accept="acceptQuest"
          @complete="completeQuest"
          @choose-reward="chooseReward"
          @close="closeDetail"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.quest-view {
  max-width: 960px;
  margin: 0 auto;
}

.page-title {
  font-size: 22px;
  color: var(--color-primary);
  margin-bottom: 16px;
}

.tab-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 2px solid var(--border-color, #e0e0e0);
  padding-bottom: 0;
}

.tab-btn {
  padding: 8px 20px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.2s, border-color 0.2s;
}

.tab-btn:hover {
  color: var(--color-primary);
}

.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}

.tab-count {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.quest-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: start;
}

.quest-list {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
  max-height: 560px;
  overflow-y: auto;
}

.list-loading, .list-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.quest-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #eee);
  cursor: pointer;
  transition: background-color 0.15s;
}

.quest-item:last-child {
  border-bottom: none;
}

.quest-item:hover {
  background-color: rgba(74, 144, 217, 0.06);
}

.quest-item.selected {
  background-color: rgba(74, 144, 217, 0.12);
  border-left: 3px solid var(--color-primary);
}

.quest-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.quest-type-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
  color: #fff;
}

.type-main { background-color: #4A90D9; }
.type-side { background-color: #7ED321; }
.type-hidden { background-color: #9B59B6; }

.quest-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quest-item-footer {
  display: flex;
  align-items: center;
}

.quest-status {
  font-size: 12px;
}

.status-available { color: #4A90D9; }
.status-active { color: #F5A623; font-weight: 600; }
.status-completed { color: #7ED321; }
.status-locked { color: #bbb; }

.quest-detail-pane {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
  min-height: 300px;
}

.detail-loading, .detail-placeholder {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}

@media (max-width: 768px) {
  .quest-layout {
    grid-template-columns: 1fr;
  }
  .quest-list {
    max-height: 320px;
  }
}
</style>
