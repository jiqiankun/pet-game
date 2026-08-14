<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../../api/client'
import type { ApiResponse } from '../../types/api'
import { useGameStore } from '../../stores/game'
import { elementIconUrl } from '../../game-assets'

const router = useRouter()
const gameStore = useGameStore()

const step = ref<'input' | 'select'>('input')
const playerName = ref('')
const avatarId = ref('AVATAR_DEFAULT')
const selectedPetIndex = ref<number | null>(null)
const initialPets = ref<any[]>([])
const initialGold = ref(0)
const submitError = ref('')
const submitting = ref(false)

const avatarOptions = [
  { id: 'AVATAR_DEFAULT', name: '默认', color: '#4A90D9' },
  { id: 'AVATAR_ADVENTURER', name: '冒险者', color: '#F5A623' },
  { id: 'AVATAR_SCHOLAR', name: '学者', color: '#7ED321' },
]

onMounted(async () => {
  try {
    const res = await apiGet<any>('/api/game/initial-pets')
    const data = (res as ApiResponse<any>).data
    initialPets.value = data.initialPets || []
    initialGold.value = data.initialGold || 500
  } catch (e: any) {
    submitError.value = '加载初始宠物选项失败'
  }
})

function goToSelect() {
  if (!playerName.value.trim()) {
    submitError.value = '请输入玩家名称'
    return
  }
  if (playerName.value.length > 32) {
    submitError.value = '玩家名称不能超过 32 个字符'
    return
  }
  submitError.value = ''
  step.value = 'select'
}

function selectPet(index: number) {
  selectedPetIndex.value = index
}

async function confirmCreate() {
  if (selectedPetIndex.value === null) {
    submitError.value = '请选择一只初始宠物'
    return
  }
  submitError.value = ''
  submitting.value = true
  try {
    const pet = initialPets.value[selectedPetIndex.value]
    await gameStore.createNewGame(playerName.value.trim(), avatarId.value, pet.speciesId)
    router.push('/')
  } catch (e: any) {
    submitError.value = e.message || '创建失败'
  } finally {
    submitting.value = false
  }
}

function getElementName(elementId: string): string {
  const map: Record<string, string> = {
    METAL: '金', WOOD: '木', WATER: '水', FIRE: '火', EARTH: '土',
    WIND: '风', THUNDER: '雷', LIGHT: '光', DARK: '暗',
  }
  return map[elementId] || elementId
}

</script>

<template>
  <div class="new-game-view">
    <div class="new-game-container">
      <h1 class="title">开始新游戏</h1>

      <!-- 步骤 1：输入名称 -->
      <div v-if="step === 'input'" class="step-input">
        <div class="form-group">
          <label for="player-name">玩家名称</label>
          <input
            id="player-name"
            v-model="playerName"
            type="text"
            maxlength="32"
            placeholder="请输入你的名字"
            class="form-input"
            @keyup.enter="goToSelect"
          />
        </div>

        <div class="form-group">
          <label>选择形象</label>
          <div class="avatar-options">
            <button
              v-for="avatar in avatarOptions"
              :key="avatar.id"
              class="avatar-btn"
              :class="{ selected: avatarId === avatar.id }"
              @click="avatarId = avatar.id"
            >
              <span class="avatar-circle" :style="{ backgroundColor: avatar.color }"></span>
              <span class="avatar-label">{{ avatar.name }}</span>
            </button>
          </div>
        </div>

        <button class="btn-primary" @click="goToSelect">下一步：选择初始宠物</button>
      </div>

      <!-- 步骤 2：选择初始宠物 -->
      <div v-if="step === 'select'" class="step-select">
        <p class="subtitle">选择你的初始伙伴（三选一）</p>

        <div class="pet-options">
          <button
            v-for="(pet, index) in initialPets"
            :key="pet.speciesId"
            class="pet-card"
            :class="{ selected: selectedPetIndex === index }"
            @click="selectPet(index)"
          >
            <div class="pet-header">
              <img class="pet-element" :src="elementIconUrl(pet.element)" :alt="`${getElementName(pet.element)}属性`" />
              <span class="pet-name">{{ pet.name }}</span>
            </div>
            <p class="pet-desc">{{ pet.description }}</p>
            <div class="pet-stats">
              <span>HP {{ pet.baseHp }}</span>
              <span>力量 {{ pet.baseStrength }}</span>
              <span>灵力 {{ pet.baseSpirit }}</span>
              <span>防御 {{ pet.baseDefense }}</span>
              <span>抗性 {{ pet.baseResistance }}</span>
              <span>速度 {{ pet.baseSpeed }}</span>
            </div>
          </button>
        </div>

        <div class="actions">
          <button class="btn-secondary" @click="step = 'input'">返回</button>
          <button
            class="btn-primary"
            :disabled="selectedPetIndex === null || submitting"
            @click="confirmCreate"
          >
            {{ submitting ? '创建中...' : '开始冒险！' }}
          </button>
        </div>
      </div>

      <p v-if="submitError" class="error-msg">{{ submitError }}</p>
    </div>
  </div>
</template>

<style scoped>
.new-game-view {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 80vh;
  padding: 40px 16px;
}

.new-game-container {
  width: 100%;
  max-width: 640px;
}

.title {
  text-align: center;
  font-size: 28px;
  color: var(--color-primary);
  margin-bottom: 32px;
}

.subtitle {
  text-align: center;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 8px;
  font-weight: 500;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color, #ddd);
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  background-color: var(--bg-card, #fff);
  color: var(--text-primary, #333);
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(74, 144, 217, 0.2);
}

.avatar-options {
  display: flex;
  gap: 12px;
}

.avatar-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  border: 2px solid transparent;
  border-radius: var(--radius-md, 8px);
  background-color: var(--bg-card, #fff);
  cursor: pointer;
  transition: border-color 0.2s;
}

.avatar-btn.selected {
  border-color: var(--color-primary);
}

.avatar-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.avatar-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.pet-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.pet-card {
  display: block;
  width: 100%;
  text-align: left;
  padding: 16px;
  border: 2px solid var(--border-color, #eee);
  border-radius: var(--radius-md, 8px);
  background-color: var(--bg-card, #fff);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.pet-card:hover {
  border-color: var(--color-primary);
}

.pet-card.selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(74, 144, 217, 0.2);
}

.pet-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.pet-element {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.pet-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.pet-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.pet-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.pet-stats span {
  background-color: var(--bg-secondary, #f5f5f5);
  padding: 2px 8px;
  border-radius: 4px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-primary {
  padding: 10px 28px;
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 28px;
  background-color: var(--bg-secondary, #f0f0f0);
  color: var(--text-primary);
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 16px;
  cursor: pointer;
}

.error-msg {
  color: #d32f2f;
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
}
</style>
