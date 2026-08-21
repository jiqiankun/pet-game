<script setup lang="ts">
/**
 * 统一浮层渲染层（Overlay 架构 P0/P1）。
 * <p>
 * 依据 `useOverlayStore` 栈渲染全部游戏内功能浮层，业务内容复用各功能 View 组件（懒加载）。
 * 本组件负责「栈 → 浮层外壳 + 业务内容」的映射与渲染，不承载具体业务逻辑。
 * <p>
 * 层级规则：按栈位置递增 z-index（400 起、每层 +100）；仅最上层遮罩不透明，
 * 下层遮罩透明（承载层级顺序但不叠加暗化）。BATTLE / REWARD 由各自专属组件渲染，不在此处展开。
 */
import { computed, defineAsyncComponent } from 'vue'
import { useOverlayStore, type OverlayEntry, type OverlayType } from '../../stores/overlay'
import OverlayShell, { type OverlayShellVariant } from './OverlayShell.vue'
import QuickTeamOverlay from './QuickTeamOverlay.vue'
import DialogueBox from '../../views/Quest/components/DialogueBox.vue'

const overlayStore = useOverlayStore()

/** 功能浮层内容组件（懒加载：打开浮层时才加载，减小主场景首屏包体）。 */
const InventoryView = defineAsyncComponent(() => import('../../views/Inventory/InventoryView.vue'))
const PetView = defineAsyncComponent(() => import('../../views/Pet/PetView.vue'))
const PokedexView = defineAsyncComponent(() => import('../../views/Pokedex/PokedexView.vue'))
const QuestView = defineAsyncComponent(() => import('../../views/Quest/QuestView.vue'))
const SettingsView = defineAsyncComponent(() => import('../../views/Settings/SettingsView.vue'))
const TeamView = defineAsyncComponent(() => import('../../views/Team/TeamView.vue'))
const ShopView = defineAsyncComponent(() => import('../../views/Shop/ShopView.vue'))
const WorldMapView = defineAsyncComponent(() => import('../../views/WorldMap/WorldMapView.vue'))
const BossView = defineAsyncComponent(() => import('../../views/Boss/BossView.vue'))
const AchievementView = defineAsyncComponent(() => import('../../views/Achievement/AchievementView.vue'))
const StatisticsView = defineAsyncComponent(() => import('../../views/Statistics/StatisticsView.vue'))
const SaveBackupView = defineAsyncComponent(() => import('../../views/SaveSave/SaveBackupView.vue'))
const StorageView = defineAsyncComponent(() => import('../../views/Storage/StorageView.vue'))

/** 浮层外壳规格（标题 + 形态 + 是否遮罩）。 */
interface ShellSpec {
  title: string
  variant: OverlayShellVariant
}

const SHELL_SPECS: Partial<Record<OverlayType, ShellSpec>> = {
  INVENTORY: { title: '背包', variant: 'panel' },
  PET: { title: '宠物', variant: 'fullscreen' },
  POKEDEX: { title: '图鉴', variant: 'panel' },
  QUEST: { title: '任务', variant: 'panel' },
  SETTINGS: { title: '设置', variant: 'panel' },
  TEAM: { title: '队伍', variant: 'drawer' },
  SHOP: { title: '商店', variant: 'panel' },
  WORLD_MAP: { title: '大地图', variant: 'panel' },
  REGION_MAP: { title: '区域图', variant: 'panel' },
  BOSS: { title: 'Boss', variant: 'fullscreen' },
  ACHIEVEMENT: { title: '成就', variant: 'panel' },
  STATISTICS: { title: '统计', variant: 'panel' },
  SAVE_BACKUP: { title: '存档备份', variant: 'panel' },
  WAREHOUSE: { title: '道具仓库', variant: 'panel' },
  PET_STORAGE: { title: '宠物仓库', variant: 'panel' },
  QUICK_TEAM: { title: '快捷队伍', variant: 'bottom-sheet' },
  NPC_DIALOG: { title: '', variant: 'bottom-sheet' },
}

/** 需要 OverlayLayer 展开渲染的类型（BATTLE/REWARD 由专项组件渲染）。 */
const RENDERED_TYPES = Object.keys(SHELL_SPECS) as OverlayType[]

/** 由 OverlayLayer 渲染的栈条目（过滤 BATTLE/REWARD）。 */
const rendered = computed(() => overlayStore.stack.filter((e) => RENDERED_TYPES.includes(e.type)))

/** 每层 z-index 依 Context 原栈位置递增，未渲染的专属 Context 也占据层级。 */
function zIndexFor(entry: OverlayEntry): number {
  return 400 + Math.max(0, overlayStore.stack.findIndex((item) => item.id === entry.id)) * 100
}

/** 读取 Overlay data 中的 petId（PET 二级联动聚焦）。 */
function petIdFrom(data: unknown): number | undefined {
  return (data as { petId?: number } | undefined)?.petId
}

/** 读取 Overlay data 中的 bossId（BOSS 聚焦）。 */
function bossIdFrom(data: unknown): string | undefined {
  return (data as { bossId?: string } | undefined)?.bossId
}

/** 读取 Overlay data 中的 highlightRegionId（图鉴栖息地 / 任务地图定位高亮）。 */
function highlightFrom(data: unknown): string | undefined {
  return (data as { highlightRegionId?: string } | undefined)?.highlightRegionId
}

/** 读取区域图对应的兼容 mapId；真实 Region/Map 层级留给阶段 2 的 WorldGraph。 */
function regionMapIdFrom(data: unknown): string | undefined {
  return (data as { regionMapId?: string } | undefined)?.regionMapId
}
</script>

<template>
  <!-- 渲染整个 Overlay 栈：下层透明遮罩承载层级，最上层提供遮罩+业务内容 -->
  <template v-for="entry in rendered" :key="entry.id">
    <!-- NPC 对话：DialogueBox 自带底部对话框与遮罩，直接嵌入（z-index 由外部传入保持一致） -->
    <DialogueBox
      v-if="entry.type === 'NPC_DIALOG'"
      embedded
      :context-id="entry.id"
      :active="overlayStore.top?.id === entry.id"
      :z-index="zIndexFor(entry)"
    />

    <!-- 快捷队伍：BottomSheet -->
    <OverlayShell
      v-else-if="entry.type === 'QUICK_TEAM'"
      :title="SHELL_SPECS.QUICK_TEAM!.title"
      variant="bottom-sheet"
      :show-back="overlayStore.top?.id === entry.id && entry.parentId !== null"
      :masked="overlayStore.top?.id === entry.id"
      :active="overlayStore.top?.id === entry.id"
      :context-id="entry.id"
      :close-on-mask="entry.closePolicy === 'ESCAPE'"
      :z-index="zIndexFor(entry)"
      @back="overlayStore.close(entry.id)"
      @close="overlayStore.close(entry.id)"
    >
      <QuickTeamOverlay />
    </OverlayShell>

    <!-- 通用外壳浮层 -->
    <OverlayShell
      v-else
      :title="SHELL_SPECS[entry.type]!.title"
      :variant="SHELL_SPECS[entry.type]!.variant"
      :show-back="overlayStore.top?.id === entry.id && entry.parentId !== null"
      :masked="overlayStore.top?.id === entry.id"
      :active="overlayStore.top?.id === entry.id"
      :context-id="entry.id"
      :close-on-mask="entry.closePolicy === 'ESCAPE'"
      :z-index="zIndexFor(entry)"
      @back="overlayStore.close(entry.id)"
      @close="overlayStore.close(entry.id)"
    >
      <InventoryView v-if="entry.type === 'INVENTORY'" />
      <PetView v-if="entry.type === 'PET'" :initial-pet-id="petIdFrom(entry.data)" />
      <PokedexView v-if="entry.type === 'POKEDEX'" />
      <QuestView v-if="entry.type === 'QUEST'" />
      <SettingsView v-if="entry.type === 'SETTINGS'" />
      <TeamView v-if="entry.type === 'TEAM'" />
      <ShopView v-if="entry.type === 'SHOP'" />
      <WorldMapView
        v-if="entry.type === 'WORLD_MAP' || entry.type === 'REGION_MAP'"
        :context-id="entry.id"
        :highlight-region-id="highlightFrom(entry.data)"
        :region-map-id="regionMapIdFrom(entry.data)"
      />
      <BossView v-if="entry.type === 'BOSS'" :initial-boss-id="bossIdFrom(entry.data)" />
      <AchievementView v-if="entry.type === 'ACHIEVEMENT'" />
      <StatisticsView v-if="entry.type === 'STATISTICS'" />
      <SaveBackupView v-if="entry.type === 'SAVE_BACKUP'" />
      <StorageView v-if="entry.type === 'WAREHOUSE' || entry.type === 'PET_STORAGE'" />
    </OverlayShell>
  </template>
</template>
