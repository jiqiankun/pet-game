/**
 * 后端统一响应结构。
 */
export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
  code: string | null
}
