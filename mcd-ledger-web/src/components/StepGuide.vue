<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore, { type LedgerMode } from '@/store/ledger'

type StepStatus = 'done' | 'active' | 'warning' | 'blocked' | 'skipped'

interface StepDef {
  key: string
  title: string
  status: StepStatus
  hint: string
  anchor: string
  /** 是否可点击跳转 */
  clickable: boolean
}

const emit = defineEmits<{
  (e: 'pick-orders'): void
}>()

const mode = computed<LedgerMode>(() => ledgerStore.mode.value)
const ledger = computed(() => ledgerStore.ledger.value)

const isDemo = computed(() => mode.value === 'demo')
const orders = computed(() => ledger.value?.orders ?? [])
const participants = computed(() => ledger.value?.participants ?? [])
const pendingItems = computed(() => ledger.value?.reconciliation?.pendingItems ?? [])

function scrollTo(anchor: string) {
  document.getElementById(anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const steps = computed<StepDef[]>(() => {
  // ① 选订单
  let pickStatus: StepStatus = 'active'
  let pickHint = '点击勾选本餐次订单'
  if (isDemo.value) {
    pickStatus = 'skipped'
    pickHint = `沙盒已内置 ${orders.value.length} 笔订单，无需选单`
  } else if (ledgerStore.mcpLoading.value) {
    pickStatus = 'active'
    pickHint = '正在拉取近期订单…'
  } else if (ledgerStore.mcpError.value) {
    pickStatus = 'blocked'
    pickHint =
      ledgerStore.mcpError.value.includes('MCD_MCP_TOKEN') || ledgerStore.mcpError.value.includes('Token')
        ? '配置 MCD_MCP_TOKEN 后可用'
        : '后端未启动，无法选单'
  } else if (ledgerStore.mcpOrders.value) {
    pickStatus = 'active'
    pickHint = `可选 ${ledgerStore.mcpOrders.value.length} 笔，点击勾选`
  }

  // ② 分餐品
  const itemStatus: StepStatus =
    orders.value.length === 0 ? 'active' : pendingItems.value.length > 0 ? 'warning' : 'done'
  const itemHint =
    orders.value.length === 0
      ? '等待选入订单'
      : pendingItems.value.length > 0
        ? `${pendingItems.value.length} 个餐品待确认`
        : '全部分配完成，已计入合计'

  // ③ 人员与项目
  const unbound = participants.value.filter((p) => !p.projectId || p.projectId <= 0)
  const personStatus: StepStatus = participants.value.length === 0 ? 'active' : unbound.length > 0 ? 'warning' : 'done'
  const personHint =
    participants.value.length === 0
      ? '等待参与人数据'
      : unbound.length > 0
        ? `${unbound.length} 人未绑定项目`
        : '全部绑定项目'

  // ④ 餐补规则
  const limitCents = ledger.value?.subsidyLimitCents ?? 0
  const ruleStatus: StepStatus = limitCents > 0 ? 'done' : 'active'
  const ruleHint =
    limitCents > 0
      ? `上限已设，${ledger.value?.includeDeliveryInSubsidy ? '含' : '不含'}配送费`
      : '请设置餐补上限'

  // ⑤ 导出
  const exportStatus: StepStatus = ledgerStore.balanced.value ? 'active' : 'blocked'
  const exportHint = ledgerStore.balanced.value ? '可导出 CSV / 打印报销单' : '尾差不为零，导出被阻断'

  return [
    { key: 'orders', title: '选订单', status: pickStatus, hint: pickHint, anchor: 'section-orders', clickable: true },
    { key: 'items', title: '分餐品', status: itemStatus, hint: itemHint, anchor: 'section-orders', clickable: orders.value.length > 0 },
    { key: 'persons', title: '人员与项目', status: personStatus, hint: personHint, anchor: 'section-participants', clickable: participants.value.length > 0 },
    { key: 'rule', title: '餐补规则', status: ruleStatus, hint: ruleHint, anchor: 'section-rule', clickable: true },
    { key: 'export', title: '导出', status: exportStatus, hint: exportHint, anchor: 'section-export', clickable: true },
  ]
})

function onStepClick(step: StepDef) {
  if (step.key === 'orders' && !isDemo.value) {
    emit('pick-orders')
    return
  }
  if (!step.clickable) return
  scrollTo(step.anchor)
}
</script>

<template>
  <nav class="step-guide" aria-label="对账步骤">
    <button
      v-for="(step, index) in steps"
      :key="step.key"
      type="button"
      class="step-item"
      :class="`step-item--${step.status}`"
      :disabled="!step.clickable"
      @click="onStepClick(step)"
    >
      <span class="step-item__index">
        <template v-if="step.status === 'done'">✓</template>
        <template v-else-if="step.status === 'blocked'">!</template>
        <template v-else>{{ index + 1 }}</template>
      </span>
      <span class="step-item__body">
        <span class="step-item__title">{{ step.title }}</span>
        <span class="step-item__hint" :title="step.hint">{{ step.hint }}</span>
      </span>
    </button>
  </nav>
</template>
