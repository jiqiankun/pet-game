<script setup lang="ts">
import MainLayout from './layouts/MainLayout.vue'
</script>

<template>
  <MainLayout>
    <RouterView v-slot="{ Component }">
      <!-- 仅缓存桌面世界根；旧功能路由仍可直接访问，但不会销毁已进入的探索现场。 -->
      <KeepAlive include="WorldRoot">
        <component :is="Component" />
      </KeepAlive>
    </RouterView>
  </MainLayout>
</template>
