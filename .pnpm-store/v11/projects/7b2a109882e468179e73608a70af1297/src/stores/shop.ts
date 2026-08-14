import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiGet, apiPost, BusinessError } from '../api/client'
import type { ApiResponse } from '../types/api'
import type { ShopView, BuyResult } from '../types/shop'

/**
 * 商店 Store（阶段 10）。
 */
export const useShopStore = defineStore('shop', () => {
  const shopView = ref<ShopView | null>(null)
  const loading = ref(false)
  const error = ref('')
  const lastBuyResult = ref<BuyResult | null>(null)

  /** 加载商店列表。 */
  async function loadShop() {
    loading.value = true
    error.value = ''
    try {
      const res = await apiGet<ShopView>('/api/shop')
      shopView.value = (res as ApiResponse<ShopView>).data
    } catch (e: any) {
      error.value = e instanceof BusinessError ? e.message : '加载商店失败'
    } finally {
      loading.value = false
    }
  }

  /** 购买商品。 */
  async function buyItem(itemId: string, quantity: number) {
    loading.value = true
    error.value = ''
    lastBuyResult.value = null
    try {
      const res = await apiPost<BuyResult>('/api/shop/buy', { itemId, quantity })
      lastBuyResult.value = (res as ApiResponse<BuyResult>).data
      // 刷新金币
      if (shopView.value) {
        shopView.value.gold = lastBuyResult.value!.remainingGold
      }
    } catch (e: any) {
      error.value = e instanceof BusinessError ? e.message : '购买失败'
    } finally {
      loading.value = false
    }
  }

  return { shopView, loading, error, lastBuyResult, loadShop, buyItem }
})
