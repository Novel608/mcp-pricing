/**
 * 订单内核对工具：把「实付」与「已分摊 + 优惠 + 配送费」对齐（V1.0 §4.4 Step 1）。
 */
import type { LedgerOrder } from '@/types'
import { normalizeCents } from '@/utils/money'

export interface OrderCheck {
  /** 餐品原价合计（分） */
  itemsTotalCents: number
  /** 已分摊到人的餐品金额（分） */
  allocatedCents: number
  /** 未分配餐品金额（分） */
  unassignedCents: number
  /** 实付 - (已分摊 + 优惠 + 配送费)，0 表示本单已平 */
  diffCents: number
  /** 是否完全对平且无待确认餐品 */
  balanced: boolean
}

export function checkOrder(order: LedgerOrder): OrderCheck {
  const items = order.items ?? []
  let itemsTotalCents = 0
  let allocatedCents = 0
  for (const item of items) {
    const itemTotal = normalizeCents(item.itemTotalCents)
    itemsTotalCents += itemTotal
    for (const a of item.allocations ?? []) {
      allocatedCents += normalizeCents(a.allocatedCents)
    }
  }
  const unassignedCents = itemsTotalCents - allocatedCents
  const expected = allocatedCents + normalizeCents(order.discountCents) + normalizeCents(order.deliveryFeeCents)
  const diffCents = normalizeCents(order.paidTotalCents) - expected
  return {
    itemsTotalCents,
    allocatedCents,
    unassignedCents,
    diffCents,
    balanced: diffCents === 0 && unassignedCents === 0,
  }
}

/** 汇总某订单内每个参与人分到的餐品金额（分），按 participantId 归并。 */
export function allocationsByParticipant(order: LedgerOrder): Map<number, number> {
  const result = new Map<number, number>()
  for (const item of order.items ?? []) {
    for (const a of item.allocations ?? []) {
      result.set(a.participantId, (result.get(a.participantId) ?? 0) + normalizeCents(a.allocatedCents))
    }
  }
  return result
}
