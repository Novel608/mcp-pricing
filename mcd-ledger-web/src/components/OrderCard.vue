<script setup lang="ts">
import { computed } from 'vue'
import type { ItemAllocation, LedgerOrder } from '@/types'
import ledgerStore from '@/store/ledger'
import { checkOrder } from '@/utils/orderCheck'
import { formatYuan } from '@/utils/money'
import { channelLabel, DATA_CONFIDENCE_META, ALLOCATION_TYPE_META, enumMeta, orderStatusMeta } from '@/utils/enumLabel'

const props = defineProps<{ order: LedgerOrder }>()

const nameMap = computed(() => ledgerStore.participantNameMap.value)
const check = computed(() => checkOrder(props.order))
const statusMeta = computed(() => orderStatusMeta(props.order.orderStatus))
const confidence = computed(() =>
  enumMeta(DATA_CONFIDENCE_META, props.order.dataConfidence, {
    label: props.order.dataConfidence || '未知',
    tone: 'info',
    tip: '未知置信度',
  }),
)

const UNKNOWN_ALLOC = { label: '未知', tone: 'info' as const, tip: '未知归属类型' }

function allocMeta(type: unknown) {
  return enumMeta(ALLOCATION_TYPE_META, type as string, UNKNOWN_ALLOC)
}

/** 把一笔餐品的分摊结果压缩成可读文本，凸显最大余额法的分币尾差 */
function describeAllocations(allocations: ItemAllocation[]): string {
  const list = allocations ?? []
  if (list.length === 0) return '未分配'
  // 少量分摊（如「巨无霸 ×2」各归一人）直接列姓名，读起来更直观
  if (list.length <= 2) {
    return list
      .map((a) => `${nameMap.value.get(a.participantId) ?? `#${a.participantId}`} ${formatYuan(a.allocatedCents)}`)
      .join(' + ')
  }
  // 多人共享：按金额聚合，展示「¥4.67 × 4 人 + ¥4.66 × 2 人」式的尾差派发结果
  const buckets = new Map<number, number>()
  for (const a of list) buckets.set(a.allocatedCents, (buckets.get(a.allocatedCents) ?? 0) + 1)
  const parts = [...buckets.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([cents, count]) => `${formatYuan(cents)} × ${count} 人`)
  return parts.join(' + ')
}
</script>

<template>
  <div class="order-card mcd-card">
    <div class="order-card__head">
      <span class="order-card__id">#{{ order.mcpOrderId }}</span>
      <el-tag size="small" effect="plain">{{ channelLabel(order.channel) }}</el-tag>
      <el-tag size="small" :type="statusMeta.tone" effect="light">{{ statusMeta.label }}</el-tag>
      <el-tooltip :content="confidence.tip" placement="top">
        <el-tag size="small" :type="confidence.tone" effect="dark">{{ confidence.label }}</el-tag>
      </el-tooltip>
      <div class="mcd-spacer"></div>
      <span class="mcd-muted">来源：MCP query-order</span>
    </div>

    <el-table :data="order.items ?? []" size="small" border class="order-items">
      <el-table-column type="index" label="#" width="44" align="center" />
      <el-table-column prop="name" label="餐品" min-width="180" show-overflow-tooltip />
      <el-table-column label="数量" width="64" align="center">
        <template #default="{ row }">×{{ row.quantity }}</template>
      </el-table-column>
      <el-table-column label="金额" width="96" align="right">
        <template #default="{ row }"><span class="money">{{ formatYuan(row.itemTotalCents) }}</span></template>
      </el-table-column>
      <el-table-column label="归属" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="allocMeta(row.allocationType).tone" effect="light">
            {{ allocMeta(row.allocationType).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分摊明细" min-width="220">
        <template #default="{ row }">
          <span class="order-card__alloc">{{ describeAllocations(row.allocations) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="order-card__foot">
      <span><span class="order-card__foot-label">餐品原价</span> <b class="money">{{ formatYuan(check.itemsTotalCents) }}</b></span>
      <span><span class="order-card__foot-label">优惠</span> <b class="money">{{ formatYuan(order.discountCents) }}</b></span>
      <span><span class="order-card__foot-label">配送费</span> <b class="money">{{ formatYuan(order.deliveryFeeCents) }}</b></span>
      <span><span class="order-card__foot-label">订单实付</span> <b class="money" style="color: var(--mcd-red)">{{ formatYuan(order.paidTotalCents) }}</b></span>
      <div class="mcd-spacer"></div>
      <span v-if="check.unassignedCents > 0">
        <el-tag type="warning" size="small" effect="light">待确认 {{ formatYuan(check.unassignedCents) }}</el-tag>
      </span>
      <el-tag v-if="check.balanced" type="success" size="small" effect="dark">✓ 本单已平</el-tag>
      <el-tooltip v-else content="实付与已分摊+优惠+配送费存在差额，请先处理待确认餐品" placement="top">
        <el-tag type="danger" size="small" effect="dark">
          差额 {{ formatYuan(check.diffCents) }}
        </el-tag>
      </el-tooltip>
    </div>
  </div>
</template>
