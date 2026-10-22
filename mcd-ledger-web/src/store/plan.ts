import { computed, ref } from 'vue'

import { ApiError } from '@/api/http'
import { planApi } from '@/api/plan'
import type { MealDetail, MenuMeal, PlanDetail, PlanItemSelection } from '@/types/plan'
import { normalizeMenu, type NormalizedMenu } from '@/utils/menu'

const DEMO_PLAN_ID = 1

const planId = ref<number>(DEMO_PLAN_ID)
const plan = ref<PlanDetail | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

const menu = ref<NormalizedMenu>({ categories: [], mealMap: new Map() })
const menuLoading = ref(false)
const menuError = ref<string | null>(null)

/** 当前正在选餐的成员 */
const currentParticipantId = ref<number | null>(null)

const saving = ref(false)
const addingParticipant = ref(false)

/** 餐品详情抽屉 */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const activeMeal = ref<MenuMeal | null>(null)
const mealDetail = ref<MealDetail | null>(null)

const participants = computed(() => plan.value?.participants ?? [])
const selections = computed(() => plan.value?.selections ?? [])
const aggregates = computed(() => plan.value?.aggregates ?? [])

/** 当前成员的已选清单 */
const currentSelections = computed<PlanItemSelection[]>(() => {
  const pid = currentParticipantId.value
  if (pid == null) return []
  return selections.value.filter((s) => s.participantId === pid)
})

/** 按人小计 */
const perPersonSubtotal = computed<Array<{ participantId: number; displayName: string; subtotalCents: number; count: number }>>(() => {
  const map = new Map<number, { subtotalCents: number; count: number }>()
  for (const s of selections.value) {
    const cur = map.get(s.participantId) ?? { subtotalCents: 0, count: 0 }
    const subtotal = s.subtotalCents ?? (s.unitPriceCents || 0) * (s.quantity || 0)
    cur.subtotalCents += subtotal
    cur.count += s.quantity || 0
    map.set(s.participantId, cur)
  }
  return participants.value.map((p) => ({
    participantId: p.id,
    displayName: p.displayName,
    subtotalCents: map.get(p.id)?.subtotalCents ?? 0,
    count: map.get(p.id)?.count ?? 0,
  }))
})

const totalCents = computed(() => perPersonSubtotal.value.reduce((sum, p) => sum + p.subtotalCents, 0))

const currentParticipant = computed(() => participants.value.find((p) => p.id === currentParticipantId.value) ?? null)

async function loadPlan(id: number = planId.value): Promise<PlanDetail | null> {
  loading.value = true
  loadError.value = null
  try {
    const detail = id === DEMO_PLAN_ID ? await planApi.getDemoPlan() : await planApi.getPlan(id)
    planId.value = id
    plan.value = detail
    // 默认选中第一个成员
    if (currentParticipantId.value == null || !detail.participants.some((p) => p.id === currentParticipantId.value)) {
      currentParticipantId.value = detail.participants?.[0]?.id ?? null
    }
    return detail
  } catch (err) {
    plan.value = null
    if (err instanceof ApiError) {
      loadError.value =
        err.code === 'NETWORK_UNREACHABLE'
          ? '无法连接后端服务（localhost:8080）。请先启动 mcd-ledger-server，沙盒选餐数据由 GET /api/v1/demo/plan 提供。'
          : err.code === 'REQUEST_TIMEOUT'
            ? '请求超时，请稍后重试。'
            : err.code === 'PLAN_NOT_FOUND'
              ? '方案不存在，请确认方案 id。'
              : err.message
    } else {
      loadError.value = '加载选餐方案失败，请稍后重试。'
    }
    return null
  } finally {
    loading.value = false
  }
}

async function loadMenu(storeId: string): Promise<void> {
  if (!storeId) {
    menuError.value = '未提供门店编码，无法拉取菜单'
    return
  }
  menuLoading.value = true
  menuError.value = null
  try {
    const res = await planApi.listMeals(storeId)
    menu.value = normalizeMenu(res)
    if (menu.value.categories.length === 0) {
      menuError.value = '该门店暂无可售餐品'
    }
  } catch (err) {
    menu.value = { categories: [], mealMap: new Map() }
    if (err instanceof ApiError) {
      menuError.value =
        err.code === 'NETWORK_UNREACHABLE'
          ? '无法连接后端服务（localhost:8080），请先启动 mcd-ledger-server'
          : err.code === 'REQUEST_TIMEOUT'
            ? '请求超时，请稍后重试'
            : err.code === 'MCP_AUTH_FAILED' || err.status === 401
              ? 'Token 失效，请先配置 MCD_MCP_TOKEN'
              : err.message
    } else {
      menuError.value = '拉取菜单失败，请稍后重试'
    }
  } finally {
    menuLoading.value = false
  }
}

function selectParticipant(participantId: number) {
  currentParticipantId.value = participantId
}

/** 打开餐品详情抽屉 */
async function openMealDetail(meal: MenuMeal): Promise<void> {
  activeMeal.value = meal
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = null
  mealDetail.value = null
  try {
    mealDetail.value = await planApi.getMeal(meal.code)
  } catch (err) {
    if (err instanceof ApiError) {
      detailError.value =
        err.code === 'NETWORK_UNREACHABLE'
          ? '无法连接后端服务（localhost:8080）'
          : err.code === 'MCP_AUTH_FAILED' || err.status === 401
            ? 'Token 失效，请先配置 MCD_MCP_TOKEN'
            : err.message
    } else {
      detailError.value = '拉取餐品详情失败，请稍后重试'
    }
  } finally {
    detailLoading.value = false
  }
}

function closeMealDetail() {
  detailVisible.value = false
}

/** 本地增量修改当前成员选餐，随后整体覆盖提交 */
async function mutateAndSubmit(mutate: (list: PlanItemSelection[]) => PlanItemSelection[]): Promise<boolean> {
  const pid = currentParticipantId.value
  if (pid == null || !plan.value) return false
  const next = mutate(currentSelections.value)
  saving.value = true
  try {
    const updated = await planApi.submitSelections(planId.value, { participantId: pid, items: next })
    // 以服务端返回为准，避免本地派生字段（如 subtotalCents）与服务端不一致
    plan.value = updated
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

/** 加入当前成员（已存在则数量 +1） */
async function addSelection(meal: MenuMeal, quantity = 1): Promise<boolean> {
  const pid = currentParticipantId.value
  if (pid == null) return false
  return mutateAndSubmit((list) => {
    const idx = list.findIndex((s) => s.externalItemId === meal.code)
    if (idx >= 0) {
      const copy = [...list]
      copy[idx] = { ...copy[idx], quantity: (copy[idx].quantity || 0) + quantity }
      return copy
    }
    return [
      ...list,
      {
        participantId: pid,
        externalItemId: meal.code,
        name: meal.name,
        quantity,
        unitPriceCents: meal.currentPriceCents,
      },
    ]
  })
}

/** 设置数量（≤0 视为移除） */
async function setQuantity(externalItemId: string, quantity: number): Promise<boolean> {
  if (quantity <= 0) return removeSelection(externalItemId)
  return mutateAndSubmit((list) =>
    list.map((s) => (s.externalItemId === externalItemId ? { ...s, quantity } : s)),
  )
}

async function removeSelection(externalItemId: string): Promise<boolean> {
  return mutateAndSubmit((list) => list.filter((s) => s.externalItemId !== externalItemId))
}

/** 保存方案（占位动作，T05 转出前先落库） */
async function savePlan(): Promise<boolean> {
  if (!plan.value) return false
  saving.value = true
  try {
    // 逐成员提交一次，确保所有选餐都已持久化
    for (const p of participants.value) {
      const items = selections.value.filter((s) => s.participantId === p.id)
      if (items.length === 0) continue
      await planApi.submitSelections(planId.value, { participantId: p.id, items })
    }
    await loadPlan(planId.value)
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

/** 添加成员 */
async function addParticipant(payload: { displayName: string; projectId?: number | null }): Promise<boolean> {
  if (!plan.value) return false
  addingParticipant.value = true
  try {
    const updated = await planApi.addParticipant(planId.value, {
      displayName: payload.displayName,
      projectId: payload.projectId ?? null,
    })
    plan.value = updated
    return true
  } catch {
    return false
  } finally {
    addingParticipant.value = false
  }
}

export const planStore = {
  planId,
  plan,
  loading,
  loadError,
  menu,
  menuLoading,
  menuError,
  currentParticipantId,
  currentParticipant,
  participants,
  selections,
  aggregates,
  currentSelections,
  perPersonSubtotal,
  totalCents,
  saving,
  addingParticipant,
  detailVisible,
  detailLoading,
  detailError,
  activeMeal,
  mealDetail,
  loadPlan,
  loadMenu,
  selectParticipant,
  openMealDetail,
  closeMealDetail,
  addSelection,
  setQuantity,
  removeSelection,
  savePlan,
  addParticipant,
}

export default planStore
