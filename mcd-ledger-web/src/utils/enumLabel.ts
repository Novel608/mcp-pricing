import type { AllocationType, DataConfidence } from '@/types'

export type TagTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface EnumMeta<T extends string> {
  label: string
  tone: TagTone
  tip: string
}

/** 数据置信度三态（V1.0 §3.3） */
export const DATA_CONFIDENCE_META: Record<DataConfidence, EnumMeta<DataConfidence>> = {
  VERIFIED_HISTORICAL: {
    label: '已核实',
    tone: 'success',
    tip: '历史商品金额、优惠、配送费、实付总额齐全且自洽，来源：MCP query-order 历史实付',
  },
  AGREED_ALLOCATION: {
    label: '约定分摊',
    tone: 'warning',
    tip: '仅实付总额可靠，子项单价缺失或套餐无拆解，按约定均分/权重分摊',
  },
  PENDING_CONFIRMATION: {
    label: '待确认',
    tone: 'danger',
    tip: '金额缺失、口径不清或取消/退款待核实，进入待确认区，不计入合计',
  },
}

/** 餐品归属类型 */
export const ALLOCATION_TYPE_META: Record<AllocationType, EnumMeta<AllocationType>> = {
  PERSONAL: { label: '整份归人', tone: 'primary', tip: '整份餐品归属某一位参与人' },
  SHARED: { label: '多人共享', tone: 'info', tip: '共享餐品，按权重均分给指定参与人' },
  UNASSIGNED: { label: '未分配', tone: 'danger', tip: '尚未分配，进入待确认区，不计入合计' },
}

/** 订单渠道展示 */
export function channelLabel(channel?: string | null): string {
  if (!channel) return '—'
  const map: Record<string, string> = {
    DELIVERY: '麦乐送',
    mc_delivery: '麦乐送',
    PICKUP: '到店自提',
    DINE_IN: '堂食',
  }
  return map[channel] ?? channel
}

/** 订单状态展示 */
export function orderStatusMeta(status?: string | null): { label: string; tone: TagTone } {
  if (!status) return { label: '—', tone: 'info' }
  const map: Record<string, { label: string; tone: TagTone }> = {
    COMPLETED: { label: '已完成', tone: 'success' },
    FINISHED: { label: '已完成', tone: 'success' },
    PAID: { label: '已支付', tone: 'success' },
    CANCELLED: { label: '已取消', tone: 'danger' },
    REFUNDED: { label: '已退款', tone: 'danger' },
    PENDING: { label: '待确认', tone: 'warning' },
  }
  return map[status] ?? { label: status, tone: 'info' }
}

/** 餐账状态展示 */
export function ledgerStatusMeta(status?: string | null): { label: string; tone: TagTone } {
  if (!status) return { label: '—', tone: 'info' }
  const map: Record<string, { label: string; tone: TagTone }> = {
    DEMO: { label: '沙盒', tone: 'warning' },
    DRAFT: { label: '草稿', tone: 'info' },
    CONFIRMED: { label: '已确认', tone: 'success' },
    EXPORTED: { label: '已导出', tone: 'success' },
  }
  return map[status] ?? { label: status, tone: 'info' }
}

export function enumMeta<T extends string>(
  table: Record<T, EnumMeta<T>>,
  key: T | string | null | undefined,
  fallback: EnumMeta<T>,
): EnumMeta<T> {
  if (key && key in table) return table[key as T]
  return fallback
}
