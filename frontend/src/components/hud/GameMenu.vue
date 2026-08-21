<script setup lang="ts">
/**
 * 游戏菜单（Overlay 架构 P1）。
 * <p>
 * 探索 HUD 的快捷菜单：网格展示高频功能入口，点击通过统一 Overlay 栈打开对应浮层。
 * 大地图 / 仓库等管理功能在 P2 阶段接入，本阶段聚焦常用功能。
 * 遵循架构边界：菜单只负责「打开对应 Overlay」，不承载业务逻辑。
 */
import { useOverlayStore, type OverlayType } from '../../stores/overlay'
import Tooltip from '../feedback/Tooltip.vue'

const overlayStore = useOverlayStore()

const emit = defineEmits<{ (e: 'close'): void }>()

/** 菜单项：标签 + 对应 Overlay 类型 + 快捷键（P3-19 Tooltip 展示）。 */
const MENU_ITEMS: Array<{ label: string; icon: string; type: OverlayType; key?: string }> = [
  { label: '快捷队伍', icon: '👥', type: 'QUICK_TEAM', key: 'Q' },
  { label: '宠物', icon: '🐾', type: 'PET' },
  { label: '背包', icon: '🎒', type: 'INVENTORY', key: 'B' },
  { label: '图鉴', icon: '📖', type: 'POKEDEX' },
  { label: '任务', icon: '📜', type: 'QUEST', key: 'J' },
  { label: '大地图', icon: '🗺️', type: 'WORLD_MAP', key: 'M' },
  { label: '设置', icon: '⚙️', type: 'SETTINGS' },
]

function openMenu(type: OverlayType) {
  overlayStore.open(type, undefined, { source: 'HUD' })
  emit('close')
}
</script>

<template>
  <div class="game-menu">
    <button
      v-for="item in MENU_ITEMS"
      :key="item.type"
      class="menu-item"
      @click="openMenu(item.type)"
    >
      <Tooltip :tip="item.key ? `${item.label}（快捷键 ${item.key}）` : item.label">
        <span class="menu-icon">{{ item.icon }}</span>
      </Tooltip>
      <span class="menu-label">{{ item.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.game-menu {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px;
  background-color: var(--bg-card, #fff);
  border-radius: var(--radius-md, 10px);
  box-shadow: var(--shadow-2, 0 8px 24px rgba(0, 0, 0, 0.2));
  min-width: 220px;
}

.menu-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  border: none;
  border-radius: var(--radius-sm, 8px);
  background-color: rgba(74, 144, 217, 0.08);
  color: var(--text-primary, #222);
  cursor: pointer;
  transition: background-color 0.2s, transform 0.1s;
}

.menu-item:hover {
  background-color: rgba(74, 144, 217, 0.18);
  transform: translateY(-1px);
}

.menu-icon {
  font-size: 22px;
  line-height: 1;
}

.menu-label {
  font-size: 13px;
  color: var(--color-primary, #4a90d9);
  font-weight: 600;
}
</style>
