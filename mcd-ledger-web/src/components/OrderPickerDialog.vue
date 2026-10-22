<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ledgerStore from '@/store/ledger'
import type { McpOrderOption } from '@/types'
import { formatOrderTime } from '@/utils/format'
import { formatYuan } from '@/utils/money'
import { channelLabel, orderStatusMeta } from '@/utils/enumLabel'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'imported'): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const selected = ref<string[]>([])

const loading = computed(() => ledgerStore.mcpLoading.value)
const error = computed(() => ledgerStore.mcpError.value)
const orders = computed<McpOrderOption[]>(() => ledgerStore.mcpOrders.value ?? [])
const importing = computed(() => ledgerStore.importing.value)

function isSelectable(order: McpOrderOption): boolean {
  const status = (order.orderStatus ?? '').toUpperCase()
  return status !== 'CANCELLED' && status !== 'REFUNDED'
}

watch(visible, (v) => {
  if (v) {
    selected.value = []
    if (ledgerStore.mcpOrders.value === null && !ledgerStore.mcpLoading.value) {
      ledgerStore.loadMcpOrders()
    }
  }
})

async function onConfirm() {
  if (selected.value.length === 0) return
  const id = await ledgerStore.importOrders(selected.value)
  if (id) {
    emit('imported')
    visible.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="选择本餐次订单"
    width="720px"
    align-center
    :close-on-click-modal="!importing"
    class="order-picker"
  >
    <template #header>
      <div class="order-picker__header">
        <span class="order-picker__title">选择本餐次订单</span>
        <span class="mcd-muted">数据来源：麦当劳 MCP order-list（只读，不代下单）</span>
      </div>
    </template>

    <div v-loading="loading" class="order-picker__body">
      <!-- 401 / 网络异常 -->
      <div v-if="error" class="state-panel">
        <div class="state-panel__icon">🔒</div>
        <div class="state-panel__title">{{ error }}</div>
        <div class="state-panel__desc">
          <template v-if="error.includes('MCD_MCP_TOKEN')">
            请在服务端环境变量配置 <code>MCD_MCP_TOKEN</code> 后重启 mcd-ledger-server。<br />
            前端永不接触 Token；也可切回「🧪 沙盒经典加班餐」免 Token 体验完整演示。
          </template>
          <template v-else-if="error.includes('后端')">
            请先启动 mcd-ledger-server（默认 http://localhost:8080）。
          </template>
          <template v-else>请稍后重试。</template>
        </div>
        <el-button style="margin-top: 14px" @click="ledgerStore.loadMcpOrders()">重新拉取</el-button>
      </div>

      <!-- 空列表 -->
      <div v-else-if="orders.length === 0" class="state-panel">
        <div class="state-panel__icon">📭</div>
        <div class="state-panel__title">暂无可选订单</div>
        <div class="state-panel__desc">近期没有可导入的麦当劳订单。</div>
      </div>

      <!-- 可选订单列表 -->
      <el-table
        v-else
        :data="orders"
        row-key="mcpOrderId"
        @selection-change="(rows: McpOrderOption[]) => (selected = rows.map((r) => r.mcpOrderId))"
      >
        <el-table-column type="selection" width="46" :selectable="isSelectable" />
        <el-table-column label="订单号" min-width="180">
          <template #default="{ row }">
            <span class="order-picker__id">#{{ row.mcpOrderId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="110">
          <template #default="{ row }">{{ formatOrderTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column label="渠道" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ channelLabel(row.channel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="orderStatusMeta(row.orderStatus).tone" effect="light">
              {{ orderStatusMeta(row.orderStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="100" align="right">
          <template #default="{ row }">
            <b class="money">{{ row.paidTotalCents != null ? formatYuan(row.paidTotalCents) : '—' }}</b>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <div class="order-picker__footer">
        <span class="mcd-muted">已选 {{ selected.length }} 笔</span>
        <div class="mcd-spacer"></div>
        <el-button @click="visible = false" :disabled="importing">取消</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="selected.length === 0 || !!error"
          @click="onConfirm"
        >
          选入餐账
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.order-picker__header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.order-picker__title {
  font-size: 16px;
  font-weight: 600;
}

.order-picker__body {
  min-height: 180px;
}

.order-picker__id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.order-picker__footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

code {
  background: #f2f3f5;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12px;
}
</style>
