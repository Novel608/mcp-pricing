import { computed, ref } from 'vue'
import dayjs from 'dayjs'

import { ApiError } from '@/api/http'
import { ledgerApi } from '@/api/ledger'
import router from '@/router'
import type { LedgerDetail, McpOrderOption } from '@/types'

/** 运行模式：demo = 沙盒经典加班餐；live = 真实 MCP 账单 */
export type LedgerMode = 'demo' | 'live'

export const DEMO_LEDGER_ID = 1

const MODE_STORAGE_KEY = 'mcd-ledger-mode'

const mode = ref<LedgerMode>(readStoredMode())
const ledgerId = ref<number>(DEMO_LEDGER_ID)
const ledger = ref<LedgerDetail | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

/** null = 尚未加载；[] = 已加载但为空 */
const mcpOrders = ref<McpOrderOption[] | null>(null)
const mcpLoading = ref(false)
const mcpError = ref<string | null>(null)

const importing = ref(false)
const exporting = ref(false)
const savingRule = ref(false)
const savingParticipant = ref(false)

function readStoredMode(): LedgerMode {
  try {
    const v = localStorage.getItem(MODE_STORAGE_KEY)
    return v === 'live' ? 'live' : 'demo'
  } catch {
    return 'demo'
  }
}

const diffCents = computed(() => ledger.value?.reconciliation?.diffCents ?? 0)
const balanced = computed(() => diffCents.value === 0)

/** 参与人 id -> 姓名，供订单卡片展示分摊归属 */
const participantNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const p of ledger.value?.participants ?? []) map.set(p.id, p.displayName)
  return map
})

async function loadLedger(id: number = ledgerId.value): Promise<LedgerDetail | null> {
  loading.value = true
  loadError.value = null
  try {
    const detail = id === DEMO_LEDGER_ID ? await ledgerApi.getDemoLedger() : await ledgerApi.getLedger(id)
    ledgerId.value = id
    ledger.value = detail
    return detail
  } catch (err) {
    ledger.value = null
    if (err instanceof ApiError) {
      loadError.value =
        err.code === 'NETWORK_UNREACHABLE'
          ? '无法连接后端服务（localhost:8080）。请先启动 mcd-ledger-server，沙盒演示数据由 GET /api/v1/demo/ledger 提供。'
          : err.code === 'REQUEST_TIMEOUT'
            ? '请求超时，请稍后重试。'
            : err.message
    } else {
      loadError.value = '加载餐账失败，请稍后重试。'
    }
    return null
  } finally {
    loading.value = false
  }
}

async function switchMode(next: LedgerMode): Promise<void> {
  if (mode.value === next) return
  mode.value = next
  try {
    localStorage.setItem(MODE_STORAGE_KEY, next)
  } catch {
    /* 隐私模式下忽略 */
  }
  if (next === 'live') {
    await loadMcpOrders()
  } else {
    mcpOrders.value = null
    mcpError.value = null
  }
}

/** 拉取可选订单；401 / 网络异常给出可操作提示 */
async function loadMcpOrders(): Promise<void> {
  mcpLoading.value = true
  mcpError.value = null
  mcpOrders.value = null
  try {
    mcpOrders.value = await ledgerApi.listMcpOrders()
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.code === 'MCP_AUTH_FAILED' || err.status === 401) {
        mcpError.value = 'Token 失效，请先配置 MCD_MCP_TOKEN'
      } else if (err.code === 'MCP_UPSTREAM_ERROR' && err.serverMessage && /token/i.test(err.serverMessage)) {
        // 后端未配置 Token 时以 MCP_UPSTREAM_ERROR 上报，统一引导到 MCD_MCP_TOKEN
        mcpError.value = '尚未配置 MCD_MCP_TOKEN，真实模式暂不可用'
      } else if (err.code === 'NETWORK_UNREACHABLE') {
        mcpError.value = '无法连接后端服务（localhost:8080），请先启动 mcd-ledger-server'
      } else if (err.code === 'REQUEST_TIMEOUT') {
        mcpError.value = '请求超时，请稍后重试'
      } else {
        mcpError.value = err.message
      }
    } else {
      mcpError.value = '拉取可选订单失败，请稍后重试'
    }
  } finally {
    mcpLoading.value = false
  }
}

/**
 * 选入订单。当前账是沙盒基准账（id=1）时先新建一本账，避免污染演示数据。
 */
async function importOrders(mcpOrderIds: string[]): Promise<number | null> {
  if (mcpOrderIds.length === 0) return null
  importing.value = true
  try {
    let targetId = ledgerId.value
    if (targetId === DEMO_LEDGER_ID) {
      const created = await ledgerApi.createLedger({
        title: `团队餐对账 ${dayjs().format('YYYY-MM-DD HH:mm')}`,
        mealTime: new Date().toISOString(),
        operatorName: ledger.value?.operatorName || '报账负责人',
        subsidyLimitCents: ledger.value?.subsidyLimitCents ?? 3500,
        includeDeliveryInSubsidy: ledger.value?.includeDeliveryInSubsidy ?? false,
      })
      targetId = created.id
      ledgerId.value = targetId
      router.push(`/ledger/${targetId}`)
    }
    await ledgerApi.importOrders(targetId, { mcpOrderIds })
    await loadLedger(targetId)
    return targetId
  } catch {
    return null
  } finally {
    importing.value = false
  }
}

async function updateParticipantProject(participantId: number, projectId: number): Promise<void> {
  savingParticipant.value = true
  try {
    await ledgerApi.updateParticipant(ledgerId.value, participantId, { projectId })
    await loadLedger(ledgerId.value)
  } catch {
    /* 拦截器已 toast */
  } finally {
    savingParticipant.value = false
  }
}

async function updateSubsidyRule(subsidyLimitCents: number, includeDeliveryInSubsidy: boolean): Promise<void> {
  savingRule.value = true
  try {
    // 双字段兼容：读模型为 subsidyLimitCents，当前后端写入 DTO 接受 limitCents
    await ledgerApi.updateSubsidyRule(ledgerId.value, {
      limitCents: subsidyLimitCents,
      subsidyLimitCents,
      includeDeliveryInSubsidy,
    })
    await loadLedger(ledgerId.value)
  } catch {
    /* 拦截器已 toast */
  } finally {
    savingRule.value = false
  }
}

/** 从 Content-Disposition 解析文件名，失败时回退默认名 */
function resolveFilename(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : fallback
}

async function exportCsv(): Promise<void> {
  if (!ledger.value) return
  if (!balanced.value) return
  exporting.value = true
  try {
    const res = await ledgerApi.exportCsv(ledgerId.value)
    // 加 BOM，保证 Excel 打开中文不乱码
    const blob = new Blob(['\uFEFF', res.data], { type: 'text/csv;charset=utf-8' })
    const filename = resolveFilename(res.headers?.['content-disposition'], `${ledger.value.title}-报销明细.csv`)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
  } catch {
    /* 拦截器已 toast */
  } finally {
    exporting.value = false
  }
}

export const ledgerStore = {
  mode,
  ledgerId,
  ledger,
  loading,
  loadError,
  mcpOrders,
  mcpLoading,
  mcpError,
  importing,
  exporting,
  savingRule,
  savingParticipant,
  diffCents,
  balanced,
  participantNameMap,
  loadLedger,
  switchMode,
  loadMcpOrders,
  importOrders,
  updateParticipantProject,
  updateSubsidyRule,
  exportCsv,
}

export default ledgerStore
