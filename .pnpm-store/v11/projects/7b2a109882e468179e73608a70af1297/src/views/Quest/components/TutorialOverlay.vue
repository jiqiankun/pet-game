<script setup lang="ts">
import { onMounted } from 'vue'
import { useQuestStore } from '../../../stores/quest'

const questStore = useQuestStore()

onMounted(async () => {
  if (!questStore.tutorialState) {
    await questStore.loadTutorial()
  }
})

const currentStep = () => {
  const state = questStore.tutorialState
  if (!state || state.allCompleted) return null
  return state.steps.find(s => !s.completed && !s.skipped) ?? null
}

async function completeCurrent() {
  const step = currentStep()
  if (!step) return
  await questStore.completeTutorialStep(step.stepId)
}

async function skipAll() {
  await questStore.skipTutorial()
}

async function resetAll() {
  await questStore.resetTutorial()
}
</script>

<template>
  <div
    v-if="questStore.tutorialState && !questStore.tutorialState.allCompleted"
    class="tutorial-overlay"
  >
    <div class="tutorial-card">
      <div class="tutorial-header">
        <span class="tutorial-label">新手教学</span>
        <span class="tutorial-progress">
          {{ questStore.tutorialState.completedCount }}/{{ questStore.tutorialState.totalCount }}
        </span>
      </div>
      <template v-if="currentStep()">
        <h4 class="step-name">{{ currentStep()!.name }}</h4>
        <p class="step-desc">{{ currentStep()!.description }}</p>
        <div class="tutorial-actions">
          <button
            v-if="currentStep()?.skippable"
            class="btn-skip"
            @click="skipAll"
          >
            跳过全部
          </button>
          <button class="btn-next" @click="completeCurrent">
            完成此步
          </button>
        </div>
      </template>
      <div class="tutorial-reset">
        <button class="btn-reset" :title="'重新开始全部教学引导'" @click="resetAll">
          重置教学提示
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tutorial-overlay {
  position: fixed;
  top: 68px;
  right: 16px;
  z-index: 180;
  width: 280px;
}

.tutorial-card {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  box-shadow: var(--shadow-2);
  border-left: 3px solid var(--color-primary);
}

.tutorial-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tutorial-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.tutorial-progress {
  font-size: 12px;
  color: var(--text-secondary);
}

.step-name {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.step-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
}

.tutorial-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn-next {
  padding: 5px 14px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-skip {
  padding: 5px 14px;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color, #ddd);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-skip:hover {
  color: var(--text-primary);
}

.tutorial-reset {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-color, #ddd);
  text-align: right;
}

.btn-reset {
  padding: 3px 10px;
  background: transparent;
  color: var(--text-secondary);
  border: none;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}

.btn-reset:hover {
  color: var(--color-primary);
}
</style>
