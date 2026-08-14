<script setup lang="ts">
import { watch, ref } from 'vue'
import { useQuestStore } from '../../../stores/quest'

const questStore = useQuestStore()
const typing = ref(false)
const displayText = ref('')
let typeTimer: ReturnType<typeof setInterval> | null = null

const dialogue = questStore.currentDialogue

/** 逐字打字效果。 */
function startTyping(text: string) {
  stopTyping()
  displayText.value = ''
  typing.value = true
  let i = 0
  typeTimer = setInterval(() => {
    if (i < text.length) {
      displayText.value += text[i]
      i++
    } else {
      stopTyping()
    }
  }, 30)
}

function stopTyping() {
  if (typeTimer) {
    clearInterval(typeTimer)
    typeTimer = null
  }
  typing.value = false
  if (dialogue) {
    displayText.value = dialogue.text
  }
}

watch(() => dialogue?.text, (newText) => {
  if (newText) startTyping(newText)
}, { immediate: true })

function handleClick() {
  if (typing.value) {
    stopTyping()
    return
  }
  if (dialogue?.hasMore) {
    questStore.continueDialogue()
  } else {
    questStore.closeDialogue()
  }
}

function handleClose() {
  stopTyping()
  questStore.closeDialogue()
}

function getNpcPortraitUrl(npcId: string): string {
  return `/assets/npc/portraits/npc_${npcId}_portrait.png`
}
</script>

<template>
  <div v-if="dialogue" class="dialogue-overlay" @click.self="handleClose">
    <div class="dialogue-box" @click="handleClick">
      <div class="dialogue-header">
        <div class="npc-heading">
          <img class="npc-portrait" :src="getNpcPortraitUrl(dialogue.npcId)" :alt="dialogue.npcName" />
          <span class="npc-name">{{ dialogue.npcName }}</span>
        </div>
        <button class="close-btn" @click.stop="handleClose">×</button>
      </div>
      <div class="dialogue-text">
        {{ displayText }}
        <span v-if="typing" class="cursor-blink">|</span>
      </div>
      <div class="dialogue-footer">
        <span v-if="typing" class="hint">点击跳过</span>
        <span v-else-if="dialogue.hasMore" class="hint">点击继续 ▸</span>
        <span v-else class="hint">点击关闭</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialogue-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 80px;
  z-index: 250;
}

.dialogue-box {
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  width: 520px;
  max-width: 92vw;
  box-shadow: var(--shadow-2);
  cursor: pointer;
  user-select: none;
}

.dialogue-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.npc-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.npc-portrait {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.npc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
}

.close-btn {
  background: transparent;
  border: none;
  font-size: 20px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.dialogue-text {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.8;
  min-height: 48px;
  margin-bottom: 8px;
}

.cursor-blink {
  animation: blink 0.6s infinite;
  color: var(--color-primary);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.dialogue-footer {
  text-align: right;
}

.hint {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
