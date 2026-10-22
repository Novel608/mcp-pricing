/**
 * 菜单数据归一化工具。
 *
 * MCP `query-meals` 原始结构是 `data.categories[].meals[].code` + `data.meals{code → meal}` 的
 * 双引用形式，后端可能原样透传、转成数组、或只给其中一半。这里统一归一成
 * 「分类 → 餐品数组」+ 「code → 餐品」索引，并做金额单位防御。
 */
import { normalizeMoneyToCents } from '@/utils/money'
import type { MenuMeal, MenuResponse } from '@/types/plan'

export interface NormalizedCategory {
  name: string
  meals: MenuMeal[]
}

export interface NormalizedMenu {
  storeName?: string
  categories: NormalizedCategory[]
  mealMap: Map<string, MenuMeal>
}

function normalizeMeal(raw: any): MenuMeal | null {
  if (!raw || typeof raw !== 'object') return null
  const code = String(raw.code ?? raw.itemId ?? raw.externalItemId ?? '')
  if (!code) return null
  const name = String(raw.name ?? raw.title ?? '未命名餐品')
  const currentCents = normalizeMoneyToCents(
    raw.currentPriceCents ?? raw.currentPrice ?? raw.price ?? raw.priceCents,
  )
  const originalRaw = raw.originalPriceCents ?? raw.originalPrice ?? raw.marketPrice
  const originalCents =
    originalRaw === undefined || originalRaw === null || originalRaw === ''
      ? null
      : normalizeMoneyToCents(originalRaw)

  return {
    code,
    name,
    image: raw.image ?? raw.imageUrl ?? undefined,
    currentPriceCents: currentCents,
    originalPriceCents: originalCents,
    discountType: raw.discountType ?? raw.discountTag ?? null,
    tags: Array.isArray(raw.tags) ? raw.tags.map((t: unknown) => String(t)) : undefined,
    canWithOrder: raw.canWithOrder,
  }
}

/** 从 meals 字段构建 code → meal 索引（兼容 map 与数组两种形态） */
function buildMealMap(res: MenuResponse): Map<string, MenuMeal> {
  const map = new Map<string, MenuMeal>()
  const meals = res.meals
  if (!meals) return map

  if (Array.isArray(meals)) {
    for (const raw of meals) {
      const m = normalizeMeal(raw)
      if (m) map.set(m.code, m)
    }
    return map
  }

  for (const [code, raw] of Object.entries(meals)) {
    const m = normalizeMeal({ ...(raw as object), code: (raw as any)?.code ?? code })
    if (m) map.set(m.code, m)
  }
  return map
}

export function normalizeMenu(res: MenuResponse | null | undefined): NormalizedMenu {
  const empty: NormalizedMenu = { categories: [], mealMap: new Map() }
  if (!res) return empty

  const mealMap = buildMealMap(res)
  const categories: NormalizedCategory[] = []

  for (const cat of res.categories ?? []) {
    if (!cat || typeof cat !== 'object') continue
    const name = String(cat.name ?? '未分类')
    const entries = cat.meals ?? cat.mealCodes ?? []
    const meals: MenuMeal[] = []
    const seen = new Set<string>()

    for (const entry of entries) {
      // 分类项可能是 {code, tags}、字符串 code，或已展开的餐品对象
      const code =
        typeof entry === 'string'
          ? entry
          : String((entry as any)?.code ?? (entry as any)?.itemId ?? '')
      if (!code || seen.has(code)) continue

      const fromMap = mealMap.get(code)
      if (fromMap) {
        // 合并分类级 tags
        const catTags = typeof entry === 'object' ? (entry as any).tags : undefined
        meals.push(
          Array.isArray(catTags) && catTags.length
            ? { ...fromMap, tags: [...(fromMap.tags ?? []), ...catTags.map(String)] }
            : fromMap,
        )
      } else if (typeof entry === 'object') {
        const m = normalizeMeal({ ...(entry as object), code })
        if (m) meals.push(m)
      }
      seen.add(code)
    }

    // 分类缺失或为空时，回退展示全部餐品（仅第一个空分类触发，避免重复）
    if (meals.length === 0 && categories.length === 0 && mealMap.size > 0 && (res.categories ?? []).length === 0) {
      meals.push(...mealMap.values())
    }

    if (meals.length > 0) categories.push({ name, meals })
  }

  // 完全没有任何分类信息时，用索引里的全部餐品兜底
  if (categories.length === 0 && mealMap.size > 0) {
    categories.push({ name: '全部餐品', meals: [...mealMap.values()] })
  }

  return { storeName: res.storeName, categories, mealMap }
}

/** 是否促销（现价低于原价） */
export function isDiscounted(meal: MenuMeal): boolean {
  return (
    meal.originalPriceCents != null &&
    meal.originalPriceCents > 0 &&
    meal.originalPriceCents > meal.currentPriceCents
  )
}
