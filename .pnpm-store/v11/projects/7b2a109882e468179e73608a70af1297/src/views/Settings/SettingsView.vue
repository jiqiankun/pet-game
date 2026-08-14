<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiGet, apiPut } from '../../api/client'

type Difficulty = 'NORMAL' | 'ELITE' | 'NIGHTMARE' | 'HELL'

interface DifficultyView {
  difficulty: Difficulty
  available: Difficulty[]
}

const difficulty = ref<Difficulty>('NORMAL')
const available = ref<Difficulty[]>([])
const loading = ref(false)
const message = ref('')
const error = ref('')

const labels: Record<Difficulty, string> = {
  NORMAL: '普通',
  ELITE: '精英',
  NIGHTMARE: '噩梦',
  HELL: '地狱',
}

async function loadDifficulty() {
  loading.value = true
  error.value = ''
  try {
    const response = await apiGet<DifficultyView>('/api/game/difficulty')
    difficulty.value = response.data.difficulty
    available.value = response.data.available
  } catch (e: any) {
    error.value = e.message ?? '加载难度设置失败'
  } finally {
    loading.value = false
  }
}

async function saveDifficulty() {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    const response = await apiPut<DifficultyView>('/api/game/difficulty', { difficulty: difficulty.value })
    difficulty.value = response.data.difficulty
    message.value = '全局难度已保存。新的野外遭遇会立即按此难度生成。'
  } catch (e: any) {
    error.value = e.message ?? '保存难度设置失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDifficulty)
</script>

<template>
  <div class="settings-page">
    <h2>设置</h2>
    <section class="settings-card">
      <h3>全局难度</h3>
      <p>影响新的野外遭遇与首次生成的 Boss 遭遇。已有 Boss 快照不会自动改变，可在 Boss 页面确认后重置。</p>
      <label class="difficulty-row">
        <span>当前难度</span>
        <select v-model="difficulty" :disabled="loading">
          <option v-for="item in available" :key="item" :value="item">{{ labels[item] }}</option>
        </select>
      </label>
      <button class="save-button" :disabled="loading" @click="saveDifficulty">
        {{ loading ? '保存中…' : '保存难度' }}
      </button>
      <p v-if="message" class="message">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>
    </section>
  </div>
</template>

<style scoped>
.settings-page { padding: 24px; }
.settings-page h2 { margin: 0 0 16px; color: var(--color-primary); font-size: 20px; }
.settings-card { max-width: 560px; padding: 20px; border-radius: var(--radius-md); background: var(--bg-card); box-shadow: var(--shadow-1); }
.settings-card h3 { margin: 0 0 8px; font-size: 16px; }
.settings-card p { color: var(--text-secondary); line-height: 1.6; }
.difficulty-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 18px 0 12px; }
.difficulty-row select { min-width: 150px; padding: 7px 10px; border: 1px solid #d6d6d6; border-radius: 6px; }
.save-button { padding: 8px 16px; border: 0; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; }
.save-button:disabled { cursor: not-allowed; opacity: .6; }
.message { color: #20864b !important; }
.error { color: #c43d3d !important; }
</style>
