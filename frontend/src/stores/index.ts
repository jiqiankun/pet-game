import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用全局状态 Store。
 * 阶段 0 仅包含基础连接状态，后续阶段扩展。
 */
export const useAppStore = defineStore('app', () => {
  const serverConnected = ref(false)
  const serverVersion = ref('')

  function setServerStatus(connected: boolean, version: string) {
    serverConnected.value = connected
    serverVersion.value = version
  }

  return { serverConnected, serverVersion, setServerStatus }
})
