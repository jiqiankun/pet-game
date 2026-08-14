/**
 * 商店类型定义（阶段 10）。
 */

/** 商店商品视图。 */
export interface ShopItemView {
  itemId: string
  name: string
  description: string
  category: string
  price: number
  unlocked: boolean
  unlockQuestId: string | null
}

/** 商店视图（后端 GET /api/shop）。 */
export interface ShopView {
  gold: number
  items: ShopItemView[]
}

/** 购买结果（后端 POST /api/shop/buy）。 */
export interface BuyResult {
  itemId: string
  itemName: string
  quantity: number
  totalCost: number
  remainingGold: number
}
