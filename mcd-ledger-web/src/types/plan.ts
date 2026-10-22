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
  /** 规格选项。后端字段名为 `specOptionsJson`，`specOptions` 为兼容别名 */
  specOptions?: string | null
  specOptionsJson?: string | null
  /** 单位：分，派生 = unitPriceCents × quantity */
  subtotalCents?: number
}

/**
 * 后端 `GET /plans/{id}` 的 selections 实际是「按成员分组」结构（ParticipantSelectionVO），
 * 不是扁平列表。前端内部会拍平使用，但类型必须与响应一致。
 */
export interface ParticipantSelectionGroup {
  participantId: number
  displayName?: string
  items: PlanItemSelection[]
  /** 单位：分，服务端已算好该成员小计 */
  subtotalCents?: number
}

/** 每人小计（后端 summary.perPerson） */
export interface PersonSubtotal {
  participantId: number
  displayName?: string
  /** 单位：分 */
  subtotalCents: number
}

/** 方案汇总（后端 summary） */
export interface PlanSummary {
  perPerson: PersonSubtotal[]
  /** 单位：分 */
  totalCents: number
}

/** 按餐品聚合（设计域模型 ItemAggregate，后端当前未提供，保留兼容） */
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
 * GET /plans/{id} 与 GET /demo/plan 的方案全量状态（PlanDetailVO）。
 * 注意：`selections` 是按成员分组的数组，`summary` 由服务端算好小计。
 * recommendations / snapshot 本次不使用（T04/T05 前端就绪后再渲染）。
 */
export interface PlanDetail extends MealPlan {
  participants: PlanParticipant[]
  selections: ParticipantSelectionGroup[]
  summary?: PlanSummary
  /** 项目目录。后端 PlanDetailVO 当前未提供，有则用于项目下拉 */
  projects?: Array<{ id: number; name: string; code?: string }>
  /** 按餐品聚合。后端当前未提供，保留兼容 */
  aggregates?: ItemAggregate[]
  recommendations?: PlanRecommendation[]
  snapshot?: PlanSnapshot | null
}

/* ============ 菜单（GET /mcp/meals） ============ */

/** 菜单卡片（后端 MealVO，金额已由适配层转为分） */
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
  daypart?: string
  /** 分类下的餐品编码列表 */
  meals?: Array<{ code: string; tags?: string[] } | string>
  mealCodes?: string[]
}

export interface MenuResponse {
  storeId?: string
  storeName?: string
  categories?: MenuCategory[]
  /**
   * 餐品列表。后端 MealMenuVO 给出的是数组；
   * 同时兼容 MCP 原始的「code → meal」map 形态。
   */
  meals?: Record<string, MenuMeal> | MenuMeal[]
  /** 菜单缓存版本（T04 试算用） */
  menuCacheVersion?: string
}

/* ============ 餐品详情（GET /mcp/meals/{itemId}，后端 MealDetailVO） ============ */

/** 规格替换项的可选项值（MCP 原始结构，后端当前未透出，保留兼容） */
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
  /** 后端为 Boolean；MCP 原始为 0/1 数字 */
  isDefault?: boolean | number
  /** 单位：分。后端字段名，形如 +¥250 / -¥50 */
  diffPriceCents?: number | null
  /** MCP 原始字符串（如 "+ ¥2.5"），后端未透出时保留兼容 */
  diffPrice?: string
  supportModify?: boolean
  /** 规格修改项（后端当前未透出，保留兼容） */
  modification?: { items?: ModificationItem[] }
}

export interface MealRound {
  /** 后端为 String */
  id?: string | number
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
  /** 单位：分 */
  priceCents?: number
  supportModify?: boolean
  rounds?: MealRound[]
}

/* ============ 请求体 ============ */

/** POST /plans */
export interface CreatePlanPayload {
  title: string
  storeId?: string
  storeName?: string
  beCode?: string
  channel?: string
  mealTime?: string
  participants?: Array<{ displayName: string; personRef?: string; projectId?: number | null; sortOrder?: number }>
}

/** POST /plans/{id}/participants */
export interface AddPlanParticipantPayload {
  displayName: string
  personRef?: string
  projectId?: number | null
  sortOrder?: number
}

/** PUT /plans/{id}/selections 的单项（后端 SelectionItem） */
export interface SubmitSelectionItem {
  externalItemId: string
  name: string
  quantity: number
  unitPriceCents: number
  specOptionsJson?: string | null
}

/**
 * PUT /plans/{id}/selections 请求体（后端 SubmitSelectionRequest）。
 * participantId 走 body；设计时序图写的 query param 一并带上做兼容。
 */
export interface SubmitSelectionsPayload {
  participantId: number
  items: SubmitSelectionItem[]
}
