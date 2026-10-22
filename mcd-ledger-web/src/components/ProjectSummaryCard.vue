<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore from '@/store/ledger'
import { formatYuan, ratioPercent } from '@/utils/money'

interface ProjectRow {
  projectId: number
  name: string
  code: string
  totalCents: number
  withinStandardCents: number
  exceedStandardCents: number
}

const ledger = computed(() => ledgerStore.ledger.value)

const projectMap = computed(() => {
  const map = new Map<number, { name: string; code: string }>()
  for (const p of ledger.value?.projects ?? []) map.set(p.id, { name: p.name, code: p.code })
  return map
})

const rows = computed<ProjectRow[]>(() =>
  (ledger.value?.reconciliation?.projects ?? []).map((p) => ({
    projectId: p.projectId,
    name: p.name || projectMap.value.get(p.projectId)?.name || `项目 #${p.projectId}`,
    code: projectMap.value.get(p.projectId)?.code ?? '',
    totalCents: p.totalCents,
    withinStandardCents: p.withinStandardCents,
    exceedStandardCents: p.exceedStandardCents,
  })),
)

const totals = computed(() =>
  rows.value.reduce(
    (acc, r) => {
      acc.total += r.totalCents
      acc.within += r.withinStandardCents
      acc.exceed += r.exceedStandardCents
      return acc
    },
    { total: 0, within: 0, exceed: 0 },
  ),
)

function withinRatio(cents: number): number {
  return ratioPercent(cents, totals.value.total)
}
</script>

<template>
  <div class="mcd-card">
    <div class="mcd-section__head">
      <div class="mcd-section__title">
        <el-icon><FolderOpened /></el-icon>
        <span>项目归集</span>
      </div>
      <div class="mcd-spacer"></div>
      <span class="mcd-muted">{{ rows.length }} 个项目</span>
    </div>

    <el-table :data="rows" size="small" border>
      <el-table-column label="项目" min-width="150">
        <template #default="{ row }">
          <div class="proj-cell">
            <span class="proj-cell__name">{{ row.name }}</span>
            <span class="proj-cell__code" v-if="row.code">{{ row.code }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="合计" width="92" align="right">
        <template #default="{ row }"><b class="money">{{ formatYuan(row.totalCents) }}</b></template>
      </el-table-column>
      <el-table-column label="标准内" width="88" align="right">
        <template #default="{ row }">
          <span class="money" style="color: var(--mcd-green)">{{ formatYuan(row.withinStandardCents) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="超标" width="88" align="right">
        <template #default="{ row }">
          <span class="money" :style="{ color: row.exceedStandardCents > 0 ? 'var(--mcd-red)' : undefined }">
            {{ formatYuan(row.exceedStandardCents) }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <div class="proj-total">
      <div class="proj-total__row">
        <span>项目归集合计</span>
        <b class="money">{{ formatYuan(totals.total) }}</b>
      </div>
      <div class="proj-bar" aria-hidden="true">
        <div
          class="proj-bar__within"
          :style="{ width: `${withinRatio(totals.within)}%` }"
        ></div>
        <div
          class="proj-bar__exceed"
          :style="{ width: `${withinRatio(totals.exceed)}%` }"
        ></div>
      </div>
      <div class="proj-total__legend">
        <span><i class="dot dot--within"></i>标准内 {{ formatYuan(totals.within) }}</span>
        <span><i class="dot dot--exceed"></i>超出标准 {{ formatYuan(totals.exceed) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.proj-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.proj-cell__name {
  font-weight: 600;
}

.proj-cell__code {
  font-size: 11px;
  color: var(--mcd-text-3);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.proj-total {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--mcd-border);
}

.proj-total__row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--mcd-text-2);
  margin-bottom: 8px;
}

.proj-total__legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--mcd-text-2);
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}

.dot--within {
  background: var(--mcd-green);
}

.dot--exceed {
  background: var(--mcd-red);
}
</style>
