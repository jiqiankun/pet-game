<script setup lang="ts">
import type { QuestDetail, QuestCompleteResult } from '../../../types/quest'

const props = defineProps<{
  detail: QuestDetail
  completeResult: QuestCompleteResult | null
}>()

const emit = defineEmits<{
  (e: 'accept', questId: string): void
  (e: 'complete', questId: string): void
  (e: 'choose-reward', choiceId: string, optionIndex: number): void
  (e: 'close'): void
}>()

const rewardTypeLabel: Record<string, string> = {
  GOLD: '金币',
  EXP: '经验',
  ITEM: '道具',
  SKILL_BOOK: '技能书',
}

function rewardDesc(entry: { type: string; itemId: string | null; quantity: number }): string {
  const label = rewardTypeLabel[entry.type] ?? entry.type
  return entry.itemId ? `${label}(${entry.itemId}) x${entry.quantity}` : `${label} x${entry.quantity}`
}

function objectiveProgress(obj: { progress: number; targetCount: number; completed: boolean }): string {
  if (obj.completed) return '已完成'
  return `${obj.progress}/${obj.targetCount}`
}
</script>

<template>
  <div class="quest-detail">
    <div class="detail-header">
      <h3>{{ detail.name }}</h3>
      <span class="detail-type">{{ detail.type === 'MAIN' ? '主线' : detail.type === 'SIDE' ? '支线' : '隐藏' }}</span>
      <span v-if="detail.hidden && detail.status !== 'COMPLETED'" class="hidden-badge">隐藏任务</span>
    </div>

    <p class="detail-desc">{{ detail.description }}</p>

    <!-- 目标列表 -->
    <div class="detail-section">
      <h4>任务目标</h4>
      <div v-if="detail.objectives.length === 0" class="empty-text">暂无目标</div>
      <ul class="objective-list">
        <li
          v-for="obj in detail.objectives"
          :key="obj.objectiveId"
          class="objective-item"
          :class="{ completed: obj.completed }"
        >
          <span class="obj-icon">{{ obj.completed ? '✓' : '○' }}</span>
          <span class="obj-desc">{{ obj.description }}</span>
          <span class="obj-progress">{{ objectiveProgress(obj) }}</span>
        </li>
      </ul>
    </div>

    <!-- 奖励预览 -->
    <div v-if="detail.rewards" class="detail-section">
      <h4>任务奖励</h4>
      <div v-if="detail.rewards.fixed.length > 0" class="reward-fixed">
        <div v-for="(entry, i) in detail.rewards.fixed" :key="i" class="reward-tag">
          {{ rewardDesc(entry) }}
        </div>
      </div>

      <!-- 三选一 -->
      <div v-if="detail.rewards.choices.length > 0" class="reward-choices">
        <div v-for="group in detail.rewards.choices" :key="group.choiceId" class="choice-group">
          <p class="choice-label">选择一项奖励：</p>
          <div class="choice-options">
            <button
              v-for="(opt, idx) in group.options"
              :key="idx"
              class="choice-btn"
              :disabled="detail.rewardChosen || detail.status === 'COMPLETED'"
              @click="emit('choose-reward', group.choiceId, idx)"
            >
              {{ rewardDesc(opt) }}
            </button>
          </div>
          <p v-if="detail.rewardChosen" class="choice-done">已选择奖励</p>
        </div>
      </div>
    </div>

    <!-- 赠送宠物 -->
    <div v-if="detail.giftPet" class="detail-section gift-pet-section">
      <h4>赠送宠物</h4>
      <div class="gift-pet-card">
        <span class="gift-species">{{ detail.giftPet.speciesName }}</span>
        <span class="gift-level">Lv.{{ detail.giftPet.level }}</span>
        <span class="gift-source">{{ detail.giftPet.source }}</span>
      </div>
    </div>

    <!-- 地图变更 -->
    <div v-if="detail.mapChanges.length > 0" class="detail-section">
      <h4>地图变更</h4>
      <ul class="map-change-list">
        <li v-for="mc in detail.mapChanges" :key="mc.changeId">
          {{ mc.description }}
        </li>
      </ul>
    </div>

    <!-- 操作按钮 -->
    <div class="detail-actions">
      <button
        v-if="detail.status === 'AVAILABLE'"
        class="btn-primary"
        @click="emit('accept', detail.questId)"
      >
        接受任务
      </button>
      <button
        v-if="detail.status === 'ACTIVE'"
        class="btn-primary"
        :disabled="detail.objectives.some(o => !o.completed)"
        @click="emit('complete', detail.questId)"
      >
        {{ detail.objectives.some(o => !o.completed) ? '目标未完成' : '完成任务' }}
      </button>
      <button class="btn-secondary" @click="emit('close')">关闭</button>
    </div>

    <!-- 完成结果 -->
    <div v-if="completeResult" class="complete-result modal-mask" @click.self="completeResult = null">
      <div class="result-card">
        <h3>任务完成！</h3>
        <p class="result-name">{{ completeResult.name }}</p>
        <ul class="result-list">
          <li v-if="completeResult.goldGained > 0">金币 +{{ completeResult.goldGained }}</li>
          <li v-if="completeResult.expGained > 0">经验 +{{ completeResult.expGained }}</li>
          <li v-for="item in completeResult.itemsGained" :key="item.itemId">
            {{ item.itemId }} ×{{ item.quantity }}
          </li>
          <li v-if="completeResult.unlockedRegions?.length">解锁区域：{{ completeResult.unlockedRegions.join('、') }}</li>
          <li v-if="completeResult.activatedMapChanges?.length">地图变更：{{ completeResult.activatedMapChanges.join('、') }}</li>
          <li v-if="completeResult.giftPet">获得宠物：{{ completeResult.giftPet.speciesName }} Lv.{{ completeResult.giftPet.level }}</li>
          <li v-if="completeResult.storyCompleted" class="story-complete">🎉 恭喜通关！</li>
        </ul>
        <div class="modal-actions">
          <button class="btn-primary" @click="completeResult = null">收下</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quest-detail {
  padding: 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.detail-header h3 {
  font-size: 18px;
  color: var(--color-primary);
  margin: 0;
}

.detail-type {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-secondary, #f5f5f5);
  padding: 2px 8px;
  border-radius: 4px;
}

.hidden-badge {
  font-size: 11px;
  color: #9B59B6;
  background: rgba(155, 89, 182, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.detail-desc {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 16px;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-section h4 {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 8px;
  font-weight: 600;
}

.empty-text {
  color: var(--text-secondary);
  font-size: 13px;
}

.objective-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.objective-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 14px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.objective-item.completed {
  color: var(--text-secondary);
  text-decoration: line-through;
}

.obj-icon {
  width: 16px;
  text-align: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.objective-item.completed .obj-icon {
  color: #7ED321;
}

.obj-desc {
  flex: 1;
}

.obj-progress {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.reward-fixed {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.reward-tag {
  font-size: 13px;
  padding: 3px 10px;
  background: rgba(245, 166, 35, 0.1);
  color: #F5A623;
  border-radius: 4px;
  font-weight: 500;
}

.choice-group {
  margin-top: 8px;
}

.choice-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.choice-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.choice-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-primary);
  background: transparent;
  color: var(--color-primary);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.choice-btn:hover:not(:disabled) {
  background: var(--color-primary);
  color: #fff;
}

.choice-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.choice-done {
  font-size: 12px;
  color: #7ED321;
  margin-top: 4px;
}

.gift-pet-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: rgba(126, 211, 33, 0.08);
  border-radius: 6px;
}

.gift-species {
  font-weight: 600;
  color: var(--text-primary);
}

.gift-level {
  color: var(--color-primary);
  font-weight: 600;
}

.gift-source {
  font-size: 12px;
  color: var(--text-secondary);
}

.map-change-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.map-change-list li {
  padding: 4px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.map-change-list li::before {
  content: '•';
  color: var(--color-primary);
  margin-right: 6px;
}

.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #eee);
}

.btn-primary {
  padding: 8px 20px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 14px;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 8px 20px;
  background-color: var(--bg-secondary, #f0f0f0);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 14px;
  cursor: pointer;
}

.complete-result {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}

.result-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 24px;
  width: 400px;
  max-width: 90vw;
  box-shadow: var(--shadow-2);
}

.result-card h3 {
  font-size: 18px;
  color: var(--color-primary);
  margin-bottom: 4px;
}

.result-name {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 12px;
}

.result-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 2;
  color: var(--text-primary);
}

.story-complete {
  font-size: 16px;
  font-weight: 700;
  color: #F5A623;
  margin-top: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}
</style>
