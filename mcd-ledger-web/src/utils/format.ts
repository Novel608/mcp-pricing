import dayjs from 'dayjs'

/** 餐次时间展示；非法值回退原串。 */
export function formatMealTime(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : String(value)
}

/** 订单时间展示（MCP 可选订单）。 */
export function formatOrderTime(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('MM-DD HH:mm') : String(value)
}
