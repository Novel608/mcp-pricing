<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore from '@/store/ledger'
import { formatSignedYuan, formatYuan, moneyTone } from '@/utils/money'

const rec = computed(() => ledgerStore.ledger.value?.reconciliation)
const balanced = computed(() => ledgerStore.balanced.value)

const diffCents = computed(() => rec.value?.diffCents ?? 0)
const diffTone = computed(() => (balanced.value ? 'zero' : moneyTone(diffCents.value)))
const diffText = computed(() => formatSignedYuan(diffCents.value))

const cells = computed(() => [
  {
    key: 'orders',
    label: '订单实付合计',
    icon: 'Goods',
    cents: rec.value?.ordersTotalCents ?? 0,
  },
  {
    key: 'persons',
    label: '人员分摊合计',
    icon: 'User',
    cents: rec.value?.personsTotalCents ?? 0,
  },
  {
    key: 'projects',
    label: '项目归集合计',
    icon: 'FolderOpened',
    cents: rec.value?.projectsTotalCents ?? 0,
  },
])
</script>

<template>
  <section class="identity-board" aria-label="三方恒等看板">
    <div
      v-for="cell in cells"
      :key="cell.key"
      class="identity-cell"
      :class="balanced ? 'identity-cell--balanced' : 'identity-cell--broken'"
    >
      <div class="identity-cell__label">
        <el-icon><component :is="cell.icon" /></el-icon>
        <span>{{ cell.label }}</span>
      </div>
      <div class="money money--lg" :class="balanced ? '' : 'money--positive'">
        {{ formatYuan(cell.cents) }}
      </div>
      <div class="mcd-muted">
        {{ balanced ? '与其它两方一致' : '与其它两方不一致，请先核对' }}
      </div>
    </div>

    <div
      class="identity-cell identity-diff"
      :class="balanced ? 'identity-cell--balanced' : 'identity-cell--broken'"
    >
      <div class="identity-cell__label">
        <el-icon><Switch /></el-icon>
        <span>三方尾差</span>
      </div>
      <div class="money money--lg" :class="`money--${diffTone}`">
        {{ diffText }}
      </div>
      <span
        class="identity-diff__badge"
        :class="balanced ? 'identity-diff__badge--ok' : 'identity-diff__badge--bad'"
      >
        <el-icon><component :is="balanced ? 'CircleCheck' : 'WarningFilled'" /></el-icon>
        {{ balanced ? '恒等成立，可导出' : '尾差不为零，导出被阻断' }}
      </span>
    </div>
  </section>
</template>

<style scoped>
.identity-diff__badge--ok {
  background: var(--mcd-green-soft);
  color: var(--mcd-green);
}

.identity-diff__badge--bad {
  background: var(--mcd-red-soft);
  color: var(--mcd-red);
}

.mcd-muted {
  min-height: 17px;
}
</style>
