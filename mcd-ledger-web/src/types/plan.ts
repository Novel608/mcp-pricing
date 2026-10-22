/**
 * 麦麦餐帐 — 选餐域（plan）契约的 TypeScript 镜像。
 *
 * 依据《增量设计-组队选餐》V1.4 §3.1 类图 / §3.4 REST 增量。
 * 注意：选餐域私有枚举 PlanConfidence 与对账域 DataConfidence **不合并不互转**，分开建模。
 */

/** 方案状态 */
export type PlanStatus = 'DRAFT' | 'PRICED' | 'CONVERTED'

/** 选餐域私有置信度（与对账域 DataConfidence 不互通） */
export type PlanConfidence = 'ESTIMATED_CURRENT' | 'PENDING_PRICING'

/** 价格来源（与 PlanConfidence 同构，必填） */
export type PriceSource = 'MCP_CALCULATED' | 'MCP_REFERENCE'

/** 推荐类型（T04，本次前端不渲染，仅占位类型） */
export type RecommendType = 'COMBO' | 'THRESHOLD' | 'COUPON'

export interface MealPlan {
  id: number
  title: string
  storeId: string
  storeName?: string
  channel?: string
  mealTime?: string
  status: PlanStatus
  createdAt?: string
  /** 转出后的餐账 id（T05） */
  ledgerId?: number | null
}

export interface PlanParticipant {
  id: number
  planId?: number
  displayName: string
  personRef?: string
  projectId?: number | null
  sortOrder?: number
  /** 域模型派生属性，可能由后端填充 */
  selectionCount?: number
}

/** 单条选餐项；金额单位为分 */
export interface PlanItemSelection {
  id?: number
  planId?: number
  participantId: number
  externalItemId: string
  name: string
  quantity: number
  /** 单位：分 */
  unitPriceCents: number
  /** 规格选项（后端存字符串，可能是 JSON） */
  specOptions?: string | null
  /** 单位：分，派生 = unitPriceCents × quantity */
  subtotalCents?: number
}

/** 按餐品聚合（全员口径） */
export interface ItemAggregate {
  externalItemId: string
  name: string
  totalQuantity: number
  participantIds?: number[]
  /** 单位：分 */
  subtotalCents: number
}

/** 每人预期（T04 试算产物，本次前端不渲染） */
export interface PerPersonExpected {
  participantId: number
  displayName?: string
  expectedCents: number
}

/** 预算基线快照（T04，本次前端不渲染） */
export interface PlanSnapshot {
  id?: number
  planId?: number
  expectedSubtotalCents?: number
  expectedDiscountCents?: number
  expectedDeliveryFeeCents?: number
  expectedTotalCents?: number
  perPersonExpectedJson?: string
  pricedAt?: string
  menuCacheVersion?: string
  itemsHash?: string
  confidence?: PlanConfidence
  partial?: boolean
  stale?: boolean
}

/** 推荐留痕（T04，本次前端不渲染） */
export interface PlanRecommendation {
  id: number
  planId?: number
  type: RecommendType
  title?: string
  reasonJson?: string
  priceSource?: PriceSource
  discountCents?: number | null
  addedCostCents?: number | null
  netSavingCents?: number | null
  notWorthIt?: boolean
  adopted?: boolean
}

/**
 * GET /plans/{id} 与 GET /demo/plan 的方案全量状态。
 * recommendations / snapshot 本次不使用（T04/T05 前端就绪后再渲染）。
 */
export interface PlanDetail extends MealPlan {
  participants: PlanParticipant[]
  selections: PlanItemSelection[]
  aggregates?: ItemAggregate[]
  /** 项目目录。域模型未定义，部分后端实现可能附带，有则用于项目下拉 */
  projects?: Array<{ id: number; name: string; code?: string }>
  recommendations?: PlanRecommendation[]
  snapshot?: PlanSnapshot | null
}

/* ============ 菜单（GET /mcp/meals） ============ */

/** 菜单卡片。后端适配层应把 MCP 的元字符串转成整数分 */
export interface MenuMeal {
  /** MCP 餐品编码，即 externalItemId */
  code: string
  name: string
  image?: string
  /** 单位：分 */
  currentPriceCents: number
  /** 单位：分，划线原价 */
  originalPriceCents?: number | null
  discountType?: string | null
  tags?: string[]
  canWithOrder?: boolean
}

export interface MenuCategory {
  name: string
  /** 分类下的餐品编码列表 */
  meals?: Array<{ code: string; tags?: string[] } | string>
  mealCodes?: string[]
}

export interface MenuResponse {
  storeId?: string
  storeName?: string
  categories?: MenuCategory[]
  /**
   * 餐品字典。MCP 原始结构是「code → meal」的 map，
   * 后端可能原样透传或转成数组，两种都要兼容。
   */
  meals?: Record<string, MenuMeal> | MenuMeal[]
  /** 菜单缓存版本（T04 试算用） */
  menuCacheVersion?: string
}

/* ============ 餐品详情（GET /mcp/meals/{itemId}） ============ */

/** 规格替换项的可选项值 */
export interface ModificationValue {
  code: string
  name: string
  /** MCP 原始字段为数字，单位存疑，仅展示不参与计算 */
  price?: number
  maxQuantity?: number
  minQuantity?: number
  selectedQuantity?: number
}

export interface ModificationItem {
  maxValues?: number
  minValues?: number
  values?: ModificationValue[]
}

export interface MealChoice {
  code: string
  name: string
  quantity?: number
  maxQuantity?: number
  /** 0/1 */
  isDefault?: number
  /** MCP 原始为字符串，如 "+ ¥2.5" / "- ¥0.5" */
  diffPrice?: string
  supportModify?: boolean
  modification?: { items?: ModificationItem[] }
}

export interface MealRound {
  id?: number
  name: string
  quantity?: number
  maxQuantity?: number
  minQuantity?: number
  choices?: MealChoice[]
}

export interface MealDetail {
  code: string
  name: string
  image?: string
  supportModify?: boolean
  rounds?: MealRound[]
}

/* ============ 请求体 ============ */

/** POST /plans */
export interface CreatePlanPayload {
  title: string
  storeCode?: string
  storeId?: string
  beCode?: string
  channel?: string
  mealTime?: string
}

/** POST /plans/{id}/participants */
export interface AddPlanParticipantPayload {
  displayName: string
  personRef?: string
  projectId?: number | null
}

/**
 * PUT /plans/{id}/selections 请求体。
 * 设计时序图显示 participantId 走 query param，这里同时支持 body 携带（双路兼容）。
 */
export interface SubmitSelectionsPayload {
  participantId: number
  items: PlanItemSelection[]
}
