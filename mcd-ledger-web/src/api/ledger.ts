import http, { type HttpConfig } from './http'
import type {
  CreateLedgerPayload,
  ImportOrdersPayload,
  LedgerDetail,
  McpOrderOption,
  UpdateParticipantPayload,
  UpdateSubsidyRulePayload,
} from '@/types'

/**
 * 对账域 REST 客户端（V1.0 §6）。
 *
 * 注意：http 拦截器已统一解包 ApiResponse，这里的返回值就是 `data`。
 */
export const ledgerApi = {
  /** 沙盒基准餐账（无需 Token） */
  getDemoLedger(): Promise<LedgerDetail> {
    return http.get('/demo/ledger') as unknown as Promise<LedgerDetail>
  },

  /** 餐账全量聚合状态 */
  getLedger(id: number): Promise<LedgerDetail> {
    return http.get(`/ledgers/${id}`) as unknown as Promise<LedgerDetail>
  },

  /**
   * 代理 order-list，返回脱敏可选订单。
   * silent：错误由 store 转成页面内引导（如「Token 失效，请先配置 MCD_MCP_TOKEN」），不再弹全局 toast。
   */
  listMcpOrders(): Promise<McpOrderOption[]> {
    return http.get('/mcp/orders', { silent: true } as HttpConfig) as unknown as Promise<McpOrderOption[]>
  },

  /** 新建餐账 */
  createLedger(payload: CreateLedgerPayload): Promise<LedgerDetail> {
    return http.post('/ledgers', payload) as unknown as Promise<LedgerDetail>
  },

  /** 选入订单（服务端调 query-order 生成快照，重复 mcpOrderId 幂等跳过） */
  importOrders(id: number, payload: ImportOrdersPayload): Promise<LedgerDetail> {
    return http.post(`/ledgers/${id}/orders`, payload) as unknown as Promise<LedgerDetail>
  },

  /** 修改参与人花名 / 所属项目 */
  updateParticipant(id: number, participantId: number, payload: UpdateParticipantPayload): Promise<unknown> {
    return http.put(`/ledgers/${id}/participants/${participantId}`, payload) as unknown as Promise<unknown>
  },

  /** 修改餐补上限 / 含配送费开关 */
  updateSubsidyRule(id: number, payload: UpdateSubsidyRulePayload): Promise<unknown> {
    return http.put(`/ledgers/${id}/subsidy-rule`, payload) as unknown as Promise<unknown>
  },

  /** 导出 CSV（blob 原样返回，含响应头以便取文件名） */
  exportCsv(id: number) {
    return http.get(`/ledgers/${id}/export.csv`, {
      responseType: 'blob',
      silent: true,
    } as HttpConfig) as unknown as Promise<{ data: Blob; headers: Record<string, string> }>
  },
}
