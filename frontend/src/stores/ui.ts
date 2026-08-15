import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 全局 UI 反馈类型。 */
export type ToastType = 'info' | 'success' | 'error'

export interface ToastItem {
  id: number
  msg: string
  type: ToastType
}

/**
 * 全局 UI 反馈 Store（阶段 14 收尾 / Overlay 架构 P0）。
 * 统一 Toast / 错误反馈，避免各页面各自实现一套提示样式。
 * 仅承载 UI 反馈，不承载业务计算。
 */
export const useUiStore = defineStore('ui', () => {
  const toasts = ref<ToastItem[]>([])
  let seq = 0

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  /** 弹出轻提示（自动消失）。 */
  function toast(msg: string, type: ToastType = 'info', duration = 3200) {
    const id = ++seq
    toasts.value.push({ id, msg, type })
    setTimeout(() => dismiss(id), duration)
  }

  /** 操作成功提示。 */
  function success(msg: string) {
    toast(msg, 'success')
  }

  /** 错误反馈（统一 ErrorFeedback 语义，作为 toast 错误变体展示）。 */
  function error(msg: string) {
    toast(msg, 'error', 5000)
  }

  return { toasts, toast, success, error, dismiss }
})