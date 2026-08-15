<script setup lang="ts">
/**
 * 探索 HUD（Overlay 架构 P1）。
 * <p>
 * 地图常驻主界面高价值信息：当前区域、金币、首发宠物状态、队伍整体状态、游戏菜单入口。
 * 数据全部来自现有 stores（game/map），后端仍为唯一业务裁定来源。
 * 遵循架构边界：HUD 只做展示与「打开对应 Overlay」的入口，不承载业务计算。
 */
import { computed, onMounted, ref } from 'vue'
import { useGameStore } from '../../stores/game'
import { useMapStore } from '../../stores/map'
import { useOverlayStore } from '../../stores/overlay'
import { usePokedexStore } from '../../stores/pokedex'
import { petIconUrl } from '../../game-assets'
import GameMenu from './GameMenu.vue'
import Tooltip from '../feedback/Tooltip.vue'

const gameStore = useGameStore()
const mapStore = useMapStore()
const overlayStore = useOverlayStore()
const pokedexStore = usePokedexStore()

const menuOpen = ref(false)

// 图鉴解锁提醒：进入探索时若尚未加载图鉴，则拉取一次（HUD 只读展示，不承载业务）。
onMounted(() => {
  if (!pokedexStore.entries.length) pokedexStore.loadPokedex()
})

/** HUD 队伍成员（合并 teamMembers/pets/petSummaries，含 HP 比例）。 */
interface HudPet {
  petId: number
  position: number
  nickname: string
  speciesId: string
  level: number
  currentHp: number
  maxHp: number
}

const team = computed<HudPet[]>(() => {
  const members = (gameStore.teamMembers as any[]) || []
  return members
    .map((m) => {
      const pet = (gameStore.pets as any[]).find((p) => p.id === m.petId)
      const summary = gameStore.petSummaries.find((s) => s.pet.id === m.petId)
      const maxHp = summary?.panelStats?.maxHp ?? pet?.maxHp ?? pet?.currentHp ?? 0
      return {
        petId: m.petId,
        position: m.position ?? 0,
        nickname: pet?.nickname || summary?.speciesName || '',
        speciesId: pet?.speciesId || summary?.pet.speciesId || '',
        level: pet?.level ?? summary?.pet.level ?? 0,
        currentHp: pet?.currentHp ?? 0,
        maxHp,
      }
    })
    .sort((a, b) => a.position - b.position)
})

/** 首发（队伍第一位）。 */
const starter = computed<HudPet | null>(() => team.value[0] ?? null)

const starterHpPct = computed(() => {
  if (!starter.value || starter.value.maxHp <= 0) return 0
  return Math.min(100, Math.round((starter.value.currentHp / starter.value.maxHp) * 100))
})

/** 队伍是否濒危（任一成员 HP < 25%）。 */
const hasEndangered = computed(() =>
  team.value.some((p) => p.maxHp > 0 && p.currentHp / p.maxHp < 0.25),
)

const regionName = computed(() => mapStore.currentMap?.name ?? '')

/** 动态 HUD 提醒（P3）：由客户端派生状态生成，濒危/任务追踪/图鉴解锁。 */
interface HudAlert {
  id: string
  level: 'warn' | 'info'
  text: string
}

const dynamicAlerts = computed<HudAlert[]>(() => {
  const list: HudAlert[] = []
  if (hasEndangered.value) {
    list.push({ id: 'endangered', level: 'warn', text: '队伍成员 HP 不足，请及时恢复' })
  }
  const quest = gameStore.activeMainQuest
  if (quest?.name) {
    list.push({ id: 'quest', level: 'info', text: `追踪任务：${quest.name}` })
  }
  if (pokedexStore.discoveredCount > 0 && pokedexStore.totalCount > 0) {
    list.push({
      id: 'pokedex',
      level: 'info',
      text: `图鉴解锁 ${pokedexStore.discoveredCount}/${pokedexStore.totalCount}`,
    })
  }
  return list
})

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

/** 打开快捷队伍（BottomSheet，查看 HP/异常 + 快速恢复）。 */
function openQuickTeam() {
  menuOpen.value = false
  overlayStore.open('QUICK_TEAM')
}
</script>

<template>
  <div class="exploration-hud">
    <!-- 动态提醒条（P3：濒危/任务追踪/图鉴解锁） -->
    <div v-if="dynamicAlerts.length" class="hud-alerts">
      <span
        v-for="a in dynamicAlerts"
        :key="a.id"
        class="hud-alert"
        :class="`level-${a.level}`"
      >{{ a.text }}</span>
    </div>

    <!-- 顶部信息条 -->
    <div class="hud-top">
      <div class="hud-region">
        <span class="region-badge">{{ regionName || '区域' }}</span>
      </div>
      <div class="hud-actions">
        <span class="hud-gold">💰 {{ gameStore.player?.gold ?? 0 }}</span>
        <button
          class="hud-btn"
          :class="{ endangered: hasEndangered }"
          title="队伍状态"
          @click="openQuickTeam"
        >
          {{ hasEndangered ? '⚠ 队伍濒危' : `队伍 ${team.length}/6` }}
        </button>
        <button class="hud-btn" title="游戏菜单" @click="toggleMenu">☰ 菜单</button>
      </div>
    </div>

    <!-- 游戏菜单浮层 -->
    <div v-if="menuOpen" class="hud-menu-wrap">
      <GameMenu @close="menuOpen = false" />
    </div>

    <!-- 底部信息栏：首发 + 追踪任务 -->
    <div class="hud-bottom">
      <div class="hud-starter">
        <Tooltip
          v-if="starter"
          :tip="`${starter.nickname} Lv.${starter.level} · HP ${starter.currentHp}/${starter.maxHp}`"
        >
          <img
            class="starter-icon"
            :src="petIconUrl(starter.speciesId, 64)"
            alt=""
          />
        </Tooltip>
        <div v-if="starter" class="starter-info">
          <span class="starter-name">{{ starter.nickname }} Lv.{{ starter.level }}</span>
          <div class="hp-bar">
            <div class="hp-fill" :style="{ width: starterHpPct + '%' }"></div>
          </div>
          <span class="hp-text">{{ starter.currentHp }}/{{ starter.maxHp }}</span>
        </div>
        <span v-else class="starter-empty">队伍为空</span>
      </div>

      <div v-if="gameStore.activeMainQuest" class="hud-quest">
        <span class="quest-label">追踪</span>
        <span class="quest-name">{{ gameStore.activeMainQuest.name }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exploration-hud {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  z-index: 30;
  pointer-events: none;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hud-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: auto;
}

.region-badge {
  font-size: 13px;
  font-weight: 600;
  background-color: rgba(16, 24, 32, 0.72);
  color: #fff;
  padding: 5px 12px;
  border-radius: var(--radius-sm, 8px);
}

/* P3：动态提醒条 */
.hud-alerts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  pointer-events: auto;
}

.hud-alert {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 99px;
  color: #fff;
}

.hud-alert.level-info {
  background-color: rgba(74, 144, 217, 0.85);
}

.hud-alert.level-warn {
  background-color: rgba(211, 47, 47, 0.9);
  animation: hud-pulse 1.2s ease-in-out infinite;
}

/* 警告脉冲：高优先级提醒持续闪烁吸引注意 */
@keyframes hud-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.hud-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hud-gold {
  font-size: 13px;
  font-weight: 600;
  background-color: rgba(16, 24, 32, 0.72);
  color: #ffd35c;
  padding: 5px 10px;
  border-radius: var(--radius-sm, 8px);
}

.hud-btn {
  font-size: 13px;
  border: none;
  border-radius: var(--radius-sm, 8px);
  padding: 6px 12px;
  background-color: rgba(16, 24, 32, 0.72);
  color: #fff;
  cursor: pointer;
  transition: background-color 0.2s;
}

.hud-btn:hover {
  background-color: rgba(74, 144, 217, 0.85);
}

.hud-btn.endangered {
  background-color: rgba(211, 47, 47, 0.85);
}

.hud-menu-wrap {
  position: absolute;
  right: 12px;
  top: 46px;
  z-index: 5;
  pointer-events: auto;
}

.hud-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  pointer-events: auto;
}

.hud-starter {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: rgba(16, 24, 32, 0.72);
  border-radius: var(--radius-md, 10px);
  padding: 6px 10px;
}

.starter-icon {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border-radius: var(--radius-sm, 6px);
  background-color: rgba(255, 255, 255, 0.08);
}

.starter-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.starter-name {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.hp-bar {
  width: 90px;
  height: 6px;
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  background-color: #4caf50;
  border-radius: 3px;
  transition: width 0.3s;
}

.hp-text {
  font-size: 10px;
  color: #cfe3ff;
}

.starter-empty {
  font-size: 12px;
  color: #cfe3ff;
}

.hud-quest {
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: rgba(16, 24, 32, 0.72);
  border-radius: var(--radius-md, 10px);
  padding: 6px 10px;
  max-width: 46%;
}

.quest-label {
  font-size: 11px;
  color: #ffd35c;
  font-weight: 600;
  flex-shrink: 0;
}

.quest-name {
  font-size: 12px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .exploration-hud { padding: 6px; }
  .hud-quest { display: none; }
}
</style>