/**
 * 金额工具 —— 全链路唯一允许把「分」转成「元」的地方。
 *
 * 约定（NF-1）：
 *   - 前端内部一律使用「分」（整数）；
 *   - 业务组件禁止自行 `price * 100`，禁止拼接货币字符串；
 *   - 元 → 分只允许经由 `yuanToCents`（用户输入边界）进入。
 */

const YUAN_SYMBOL = '¥'
/** U+2212 MINUS SIGN，排版上比 ASCII '-' 更美观 */
const MINUS_SIGN = '−'

export type MoneyTone = 'positive' | 'negative' | 'zero'

/** 把任意输入规整为整数分；非法值归零。 */
export function normalizeCents(cents: unknown): number {
  const n = typeof cents === 'number' ? cents : Number(cents)
  if (!Number.isFinite(n)) return 0
  return Math.round(n)
}

/** 14800 -> "¥148.00"；-850 -> "−¥8.50" */
export function formatYuan(cents: unknown): string {
  const n = normalizeCents(cents)
  const sign = n < 0 ? MINUS_SIGN : ''
  const abs = Math.abs(n)
  const yuan = Math.floor(abs / 100)
  const rest = abs - yuan * 100
  return `${sign}${YUAN_SYMBOL}${yuan}.${String(rest).padStart(2, '0')}`
}

/**
 * 带符号金额：1200 -> "+¥12.00"，-800 -> "−¥8.00"，0 -> "¥0.00"。
 * 约定：正数视为「超预算 / 不平」（红），负数视为「结余 / 平」（绿）。
 */
export function formatSignedYuan(cents: unknown): string {
  const n = normalizeCents(cents)
  if (n === 0) return formatYuan(0)
  if (n > 0) return `+${formatYuan(n)}`
  return `${MINUS_SIGN}${formatYuan(-n)}`
}

/**
 * 带符号金额的色调约定：正 = 超预算红（positive），负 = 绿（negative），零 = info。
 */
export function moneyTone(cents: unknown): MoneyTone {
  const n = normalizeCents(cents)
  return n > 0 ? 'positive' : n < 0 ? 'negative' : 'zero'
}

/** 元（可为小数/字符串）→ 整数分。仅用于用户输入边界。 */
export function yuanToCents(yuan: unknown): number {
  const n = typeof yuan === 'number' ? yuan : Number(yuan)
  if (!Number.isFinite(n)) return 0
  return Math.round(n * 100)
}

/** 整数分 → 元（数字）。仅用于回填输入框。 */
export function centsToYuan(cents: unknown): number {
  return normalizeCents(cents) / 100
}

/**
 * 防御性金额归一：把后端可能返回的「元字符串」或「元小数」统一转成整数分。
 *
 * 背景：MCP 订单侧/菜单侧金额是**元字符串**（如 "25.5"），仅 calculate-price 是分整数；
 * 后端适配层负责转换，但 A1 联调已发现过「按分假设」的单位 bug。故前端入口做一次归一，
 * 避免后端漏转时把 ¥25.50 显示成 ¥25.50×100 的荒谬数字。
 *
 * 判定规则（无歧义信号优先）：
 *   - 字符串            → 视为元
 *   - 数字且含小数      → 视为元
 *   - 数字且为整数      → 视为分（契约口径）
 */
export function normalizeMoneyToCents(value: unknown): number {
  if (value === null || value === undefined || value === '') return 0
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return 0
    return yuanToCents(trimmed)
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return 0
    return Number.isInteger(value) ? Math.round(value) : yuanToCents(value)
  }
  return 0
}

/** 百分比（0-100），保留一位小数；分母为 0 时返回 0。 */
export function ratioPercent(part: unknown, total: unknown): number {
  const p = normalizeCents(part)
  const t = normalizeCents(total)
  if (t === 0) return 0
  return Math.round((p / t) * 1000) / 10
}
