/**
 * 麦麦餐帐 — 前后端共享契约的 TypeScript 镜像。
 *
 * 字段名与后端 REST 契约（V1.0 §6 / 增量设计 §7）严格一致，
 * 任何字段增改必须先改后端再改本文件。
 */

/** 数据置信度三态（仅对账域） */
export type DataConfidence =
  | 'VERIFIED_HISTORICAL'
  | 'AGREED_ALLOCATION'
  | 'PENDING_CONFIRMATION'

/** 餐品归属类型 */
export type AllocationType = 'PERSONAL' | 'SHARED' | 'UNASSIGNED'

/** 统一响应包装 */
export interface ApiResponse<T> {
  code: string
  message: string | null
  data: T
  traceId: string
  timestamp: string
}

export interface ItemAllocation {
  participantId: number
  weight: number
  /** 单位：分 */
  allocatedCents: number
}

export interface LedgerOrderItem {
  id: number
  externalItemId: string
  name: string
  quantity: number
  /** 单位：分 */
  itemTotalCents: number
  allocationType: AllocationType
  confidence: DataConfidence
  allocations: ItemAllocation[]
}

export interface LedgerOrder {
  id: number
  mcpOrderId: string
  channel: string
  orderStatus: string
  dataConfidence: DataConfidence
  /** 单位：分 */
  paidTotalCents: number
  /** 单位：分，优惠为负 */
  discountCents: number
  /** 单位：分 */
  deliveryFeeCents: number
  items: LedgerOrderItem[]
}

export interface Participant {
  id: number
  displayName: string
  personRef: string
  projectId: number
  sortOrder: number
}

export interface ProjectRef {
  id: number
  name: string
  code: string
}

export interface ReconciliationPerson {
  participantId: number
  displayName: string
  projectId: number
  /** 单位：分 */
  totalCents: number
  /** 单位：分 */
  subsidyBaseCents: number
  /** 单位：分 */
  withinStandardCents: number
  /** 单位：分 */
  exceedStandardCents: number
  formulaLines: string[]
}

export interface ReconciliationProject {
  projectId: number
  name: string
  /** 单位：分 */
  totalCents: number
  /** 单位：分 */
  withinStandardCents: number
  /** 单位：分 */
  exceedStandardCents: number
}

export interface PendingItem {
  orderId: number
  itemId: number
  name: string
  /** 单位：分 */
  itemTotalCents: number
}

export interface Reconciliation {
  /** 单位：分 */
  ordersTotalCents: number
  /** 单位：分 */
  personsTotalCents: number
  /** 单位：分 */
  projectsTotalCents: number
  /** 单位：分 */
  diffCents: number
  persons: ReconciliationPerson[]
  projects: ReconciliationProject[]
  pendingItems: PendingItem[]
}

/** 餐账全量聚合状态：GET /ledgers/{id} 与 GET /demo/ledger 的 data 结构 */
export interface LedgerDetail {
  id: number
  title: string
  mealTime: string
  operatorName: string
  /** 单位：分 */
  subsidyLimitCents: number
  includeDeliveryInSubsidy: boolean
  status: string
  orders: LedgerOrder[]
  participants: Participant[]
  projects: ProjectRef[]
  reconciliation: Reconciliation
}

/**
 * 可选订单（GET /mcp/orders 代理 order-list 的脱敏结果）。
 * 后端对该结构的字段命名尚未在契约中固定，这里全部设为可选并做防御渲染。
 */
export interface McpOrderOption {
  mcpOrderId: string
  orderTime?: string | null
  channel?: string | null
  orderStatus?: string | null
  /** 单位：分 */
  paidTotalCents?: number | null
  itemCount?: number | null
  title?: string | null
}

/** 新建餐账请求体 */
export interface CreateLedgerPayload {
  title: string
  mealTime: string
  operatorName: string
  subsidyLimitCents: number
  includeDeliveryInSubsidy: boolean
}

/** PUT /ledgers/{id}/participants/{pid} 请求体 */
export interface UpdateParticipantPayload {
  projectId: number
  displayName?: string
}

/**
 * PUT /ledgers/{id}/subsidy-rule 请求体。
 *
 * 注意：读模型字段为 `subsidyLimitCents`，但当前后端写入 DTO 接受的是 `limitCents`
 * （发 `subsidyLimitCents` 会被静默忽略）。这里同时提交两个字段做兼容，
 * 待后端把写入 DTO 对齐为 `subsidyLimitCents` 后可移除 `limitCents`。
 */
export interface UpdateSubsidyRulePayload {
  limitCents: number
  subsidyLimitCents?: number
  includeDeliveryInSubsidy: boolean
}

/** POST /ledgers/{id}/orders 请求体 */
export interface ImportOrdersPayload {
  mcpOrderIds: string[]
}
