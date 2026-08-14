import axios from 'axios'
import type { ApiResponse } from '../types/api'

/**
 * Axios 实例，统一处理后端响应结构。
 */
const client = axios.create({
  baseURL: '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 响应拦截器：统一解包 ApiResponse。
 */
client.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<unknown>
    if (!data.success) {
      // 业务错误：reject 并携带 code 和 message
      return Promise.reject(new BusinessError(data.code ?? 'UNKNOWN', data.message ?? '未知错误'))
    }
    return response
  },
  (error) => {
    // 网络错误或 HTTP 状态码错误
    return Promise.reject(error)
  },
)

/**
 * 业务错误类。
 */
export class BusinessError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message)
    this.name = 'BusinessError'
  }
}

/**
 * 通用 GET 请求。
 */
export async function apiGet<T>(url: string): Promise<ApiResponse<T>> {
  const response = await client.get<ApiResponse<T>>(url)
  return response.data
}

/**
 * 通用 POST 请求。
 */
export async function apiPost<T>(url: string, data?: unknown): Promise<ApiResponse<T>> {
  const response = await client.post<ApiResponse<T>>(url, data)
  return response.data
}

/**
 * 通用 PUT 请求。
 */
export async function apiPut<T>(url: string, data?: unknown): Promise<ApiResponse<T>> {
  const response = await client.put<ApiResponse<T>>(url, data)
  return response.data
}

export default client
