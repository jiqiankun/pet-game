import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home/HomeView.vue'),
  },
  {
    path: '/new-game',
    name: 'NewGame',
    component: () => import('../views/NewGame/NewGameView.vue'),
  },
  {
    path: '/explore',
    name: 'Explore',
    component: () => import('../views/Explore/WorldRoot.vue'),
  },
  {
    path: '/world-map',
    name: 'WorldMap',
    component: () => import('../views/WorldMap/WorldMapView.vue'),
  },
  {
    path: '/battle',
    name: 'Battle',
    component: () => import('../views/Battle/BattleView.vue'),
  },
  {
    path: '/pets',
    name: 'Pets',
    component: () => import('../views/Pet/PetView.vue'),
  },
  {
    path: '/team',
    name: 'Team',
    component: () => import('../views/Team/TeamView.vue'),
  },
  {
    path: '/storage',
    name: 'Storage',
    component: () => import('../views/Storage/StorageView.vue'),
  },
  {
    path: '/pokedex',
    name: 'Pokedex',
    component: () => import('../views/Pokedex/PokedexView.vue'),
  },
  {
    path: '/boss',
    name: 'Boss',
    component: () => import('../views/Boss/BossView.vue'),
  },
  {
    path: '/inventory',
    name: 'Inventory',
    component: () => import('../views/Inventory/InventoryView.vue'),
  },
  {
    path: '/shop',
    name: 'Shop',
    component: () => import('../views/Shop/ShopView.vue'),
  },
  {
    path: '/quest',
    name: 'Quest',
    component: () => import('../views/Quest/QuestView.vue'),
  },
  {
    path: '/achievement',
    name: 'Achievement',
    component: () => import('../views/Achievement/AchievementView.vue'),
  },
  {
    path: '/statistics',
    name: 'Statistics',
    component: () => import('../views/Statistics/StatisticsView.vue'),
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings/SettingsView.vue'),
  },
  {
    path: '/save-backup',
    name: 'SaveBackup',
    component: () => import('../views/SaveSave/SaveBackupView.vue'),
  },
  {
    path: '/dev-tools',
    name: 'DevTools',
    component: () => import('../views/DevTools/DevToolsView.vue'),
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
