<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore from '@/store/ledger'
import { formatYuan } from '@/utils/money'

const items = computed(() => ledgerStore.ledger.value?.reconciliation?.pendingItems ?? [])
const totalCents = computed(() => items.value.reduce((sum, i) => sum + (i.itemTotalCents || 0), 0))
</script>

<template>
  <div class="mcd-card pending-card" v-if="items.length > 0">
    <div class="mcd-section__head">
      <div class="mcd-section__title">
        <el-icon><WarningFilled /></el-icon>
        <span>待确认区</span>
      </div>
      <div class="mcd-spacer"></div>
      <span class="mcd-muted">合计 {{ formatYuan(totalCents) }}</span>
    </div>

    <el-alert type="warning" :closable="false" show-icon class="pending-card__alert">
      <template #title>
        <span>
          以下餐品尚未分配或口径待核实，<b>不计入任何合计</b>；分配或人工补录后自动进入三方恒等。
        </span>
      </template>
    </el-alert>

    <el-table :data="items" size="small" border>
      <el-table-column label="餐品" min-width="160">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <span class="mcd-muted"> · 订单 #{{ row.orderId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="96" align="right">
        <template #default="{ row }"><b class="money">{{ formatYuan(row.itemTotalCents) }}</b></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.pending-card {
  border-color: #f3d9a4;
  background: linear-gradient(180deg, #fffaf0 0%, #ffffff 55%);
}

.pending-card__alert {
  margin-bottom: 12px;
}
</style>
