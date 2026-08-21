<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useQuestStore } from '../../../stores/quest'
import { useOverlayStore } from '../../../stores/overlay'
import { focusFirstIn, trapFocus } from '../../../utils/focus'

const props = withDefaults(
  defineProps<{
    /** embedded：作为 NPC_DIALOG Overlay 内容嵌入（Overlay 栈统一管理遮罩/层级），关闭时同步关闭 NPC_DIALOG 浮层。 */
    embedded?: boolean
    /** 层级 z-index（由 OverlayLayer 按栈位置传入；独立模式使用默认值）。 */
    zIndex?: number
    /** 当前 NPC_DIALOG Context 实例，用于精确关闭。 */
    contextId?: number
    /** 当前是否为 Context 栈顶。 */
    active?: boolean
  }>(),
  { embedded: false, zIndex: 250, contextId: undefined, active: true },
)

const questStore = useQuestStore()
const overlayStore = useOverlayStore()
const typing = ref(false)
const displayText = ref('')
let typeTimer: ReturnType<typeof setInterval> | null = null
const dialogueBox = ref<HTMLElement | null>(null)

// 用 computed 保持响应式：直接取 questStore.currentDialogue 会得到一次性快照（Pinia 自动解包），导致对话框永不更新
const dialogue = computed(() => questStore.currentDialogue)

/** 旅行商人 NPC：对话结束后提供「打开商店」入口（前端按 NPC 类型映射，最小改动，不改后端）。 */
const SHOP_NPC_IDS = ['NPC_FOREST_MERCHANT']
const canOpenShop = computed(() => {
  const d = dialogue.value
  return !!d && SHOP_NPC_IDS.includes(d.npcId) && !d.hasMore
})

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
  if (dialogue.value) {
    displayText.value = dialogue.value.text
  }
}

watch(() => dialogue.value?.text, (newText) => {
  if (newText) startTyping(newText)
}, { immediate: true })

watch([dialogue, () => props.active], ([current, active]) => {
  if (current && active) nextTick(() => focusFirstIn(dialogueBox.value))
}, { immediate: true })

function handleClick() {
  if (typing.value) {
    stopTyping()
    return
  }
  if (dialogue.value?.hasMore) {
    questStore.continueDialogue()
  } else {
    closeDialogue()
  }
}

function closeDialogue() {
  stopTyping()
  questStore.closeDialogue()
  // embedded（Overlay 栈）模式下同步关闭 NPC_DIALOG 浮层
  if (props.embedded) {
    overlayStore.close(props.contextId ?? 'NPC_DIALOG')
  }
}

function handleClose() {
  closeDialogue()
}

/** 打开商店（NPC 对话 → SHOP 二级联动）。 */
function openShop() {
  if (!dialogue.value) return
  overlayStore.open('SHOP', { npcId: dialogue.value.npcId })
}

function getNpcPortraitUrl(npcId: string): string {
  return `/assets/npc/portraits/npc_${npcId}_portrait.png`
}

function handleKeydown(event: KeyboardEvent) {
  if (props.active) trapFocus(event, dialogueBox.value)
}

onBeforeUnmount(() => {
  stopTyping()
  if (props.embedded && dialogue.value) questStore.closeDialogue()
})
</script>

<template>
  <div v-if="dialogue" class="dialogue-overlay" :style="{ zIndex: props.zIndex }" @click.self="handleClose">
    <div
      ref="dialogueBox"
      class="dialogue-box"
      role="dialog"
      :aria-modal="props.active ? 'true' : undefined"
      :aria-label="`${dialogue.npcName}的对话`"
      tabindex="-1"
      @click="handleClick"
      @keydown="handleKeydown"
    >
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
        <button v-if="canOpenShop" class="shop-btn" @click.stop="openShop">🛒 打开商店</button>
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

.shop-btn {
  margin-left: 8px;
  border: none;
  border-radius: 999px;
  padding: 5px 14px;
  background-color: var(--color-primary, #4a90d9);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.shop-btn:hover {
  opacity: 0.85;
}
</style>
