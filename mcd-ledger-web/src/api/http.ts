import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 业务错误码 → 用户可见文案（不暴露原始报文） */
const ERROR_CODE_TEXT: Record<string, string> = {
  PARAM_ERROR: '参数有误',
  MCP_AUTH_FAILED: 'Token 失效，请重新配置',
  MCP_WRITE_TOOL_DENIED: '本产品不代下单',
  PLAN_NOT_FOUND: '方案不存在',
  PLAN_NOT_LINKED: '该餐账未关联选餐方案',
  PLAN_BASELINE_UNAVAILABLE: '该方案尚未生成预算基线，请先试算',
  REASON_REQUIRED: '推荐缺少依据，无法展示',
  LEDGER_DIFF_NOT_ZERO: '三方尾差不为零，无法导出',
  MCP_RATE_LIMITED: '请求过于频繁，请稍后',
  MCP_UPSTREAM_ERROR: '服务暂时不可用',
  PRICE_UNAVAILABLE: '价格服务暂时不可用，请稍后重试',
  ENGINE_IDENTITY_BROKEN: '账目恒等被破坏，请联系管理员',
}

/** 前端自定义的内部错误码（后端未返回业务码时使用） */
export const INTERNAL_CODE = {
  NETWORK_UNREACHABLE: 'NETWORK_UNREACHABLE',
  REQUEST_TIMEOUT: 'REQUEST_TIMEOUT',
  HTTP_ERROR: 'HTTP_ERROR',
} as const

/** 带业务码的前端错误，业务层可据此分支处理 */
export class ApiError extends Error {
  readonly code: string
  readonly status?: number
  /** 服务端 message 原文，仅供内部分支判断，不直接展示给用户 */
  readonly serverMessage?: string
  readonly raw?: unknown

  constructor(code: string, message: string, status?: number, raw?: unknown, serverMessage?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.raw = raw
    this.serverMessage = serverMessage
  }
}

/** 请求级配置扩展：silent = true 时不弹全局 toast，由调用方自行提示 */
export interface HttpConfig extends AxiosRequestConfig {
  silent?: boolean
}

function statusText(status: number): string {
  if (status === 401) return '登录状态已失效，请重新配置'
  if (status === 403) return '没有权限执行该操作'
  if (status === 404) return '请求的资源不存在'
  if (status === 409) return '当前状态冲突，操作被拒绝'
  if (status === 429) return '请求过于频繁，请稍后'
  if (status >= 500) return '服务暂时不可用'
  return '请求失败，请稍后重试'
}

function pickMessage(body: unknown, status: number): string {
  if (body && typeof body === 'object') {
    const code = (body as { code?: unknown }).code
    if (typeof code === 'string' && ERROR_CODE_TEXT[code]) return ERROR_CODE_TEXT[code]
  }
  return statusText(status)
}

/** 取服务端 message 原文（仅用于内部分支，不展示） */
function pickServerMessage(body: unknown): string | undefined {
  if (body && typeof body === 'object') {
    const message = (body as { message?: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return undefined
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.response.use(
  // 返回 any：拦截器已把 ApiResponse 解包为 data，各 api 方法用 as 声明真实返回类型
  (response: AxiosResponse): any => {
    // blob 响应（CSV 导出）不做 ApiResponse 解包，原样返回 AxiosResponse
    if (response.config.responseType === 'blob') return response

    const body = response.data as unknown
    if (body && typeof body === 'object' && 'code' in (body as object)) {
      const { code, message } = body as { code?: unknown; message?: unknown }
      if (code === 'OK') return (body as { data: unknown }).data
      const text =
        (typeof code === 'string' && ERROR_CODE_TEXT[code]) ||
        (typeof message === 'string' && message) ||
        '操作失败，请稍后重试'
      if (!(response.config as HttpConfig).silent) ElMessage.error(text)
      return Promise.reject(
        new ApiError(String(code), text, response.status, body, pickServerMessage(body)),
      )
    }
    return body
  },
  (error: AxiosError) => {
    const config = (error.config ?? {}) as HttpConfig
    const status = error.response?.status
    const body = error.response?.data as unknown

    let code: string
    let text: string
    if (!error.response) {
      code = error.code === 'ECONNABORTED' ? INTERNAL_CODE.REQUEST_TIMEOUT : INTERNAL_CODE.NETWORK_UNREACHABLE
      text =
        code === INTERNAL_CODE.REQUEST_TIMEOUT
          ? '请求超时，请稍后重试'
          : '无法连接后端服务（localhost:8080），请先启动 mcd-ledger-server'
    } else {
      // 业务码以响应体为准，便于调用方按 code 分支（如 401 走 MCP_AUTH_FAILED）
      const bodyCode = body && typeof body === 'object' ? (body as { code?: unknown }).code : undefined
      code = typeof bodyCode === 'string' ? bodyCode : INTERNAL_CODE.HTTP_ERROR
      text = pickMessage(body, status as number)
    }

    if (!config.silent) ElMessage.error(text)
    return Promise.reject(new ApiError(code, text, status, body, pickServerMessage(body)))
  },
)

/**
 * 类型说明：响应拦截器已把 `ApiResponse` 解包为 `data`，
 * 因此各 api 方法用 `as unknown as Promise<T>` 声明真实返回类型。
 */
export default http
