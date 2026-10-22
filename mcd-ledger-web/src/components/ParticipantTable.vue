<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore from '@/store/ledger'
import { formatYuan, ratioPercent } from '@/utils/money'
import type { ProjectRef } from '@/types'

const emit = defineEmits<{
  (e: 'project-change', participantId: number, projectId: number): void
}>()

interface PersonRow {
  participantId: number
  displayName: string
  personRef: string
  projectId: number
  totalCents: number
  subsidyBaseCents: number
  withinStandardCents: number
  exceedStandardCents: number
  formulaLines: string[]
  projectName: string
}

const ledger = computed(() => ledgerStore.ledger.value)
const projects = computed<ProjectRef[]>(() => ledger.value?.projects ?? [])
const persons = computed(() => ledger.value?.reconciliation?.persons ?? [])

const projectMap = computed(() => {
  const map = new Map<number, ProjectRef>()
  for (const p of projects.value) map.set(p.id, p)
  return map
})

const rows = computed<PersonRow[]>(() =>
  persons.value.map((p) => ({
    participantId: p.participantId,
    displayName: p.displayName,
    personRef: ledger.value?.participants.find((x) => x.id === p.participantId)?.personRef ?? '',
    projectId: p.projectId,
    totalCents: p.totalCents,
    subsidyBaseCents: p.subsidyBaseCents,
    withinStandardCents: p.withinStandardCents,
    exceedStandardCents: p.exceedStandardCents,
    formulaLines: p.formulaLines ?? [],
    projectName: projectMap.value.get(p.projectId)?.name ?? '未绑定',
  })),
)

const totals = computed(() => {
  const t = rows.value.reduce(
    (acc, r) => {
      acc.total += r.totalCents
      acc.base += r.subsidyBaseCents
      acc.within += r.withinStandardCents
      acc.exceed += r.exceedStandardCents
      return acc
    },
    { total: 0, base: 0, within: 0, exceed: 0 },
  )
  return t
})

function projectLabel(p: ProjectRef): string {
  return p.code ? `${p.name}（${p.code}）` : p.name
}

function onProjectChange(participantId: number, value: number | string) {
  const pid = Number(value)
  if (!Number.isFinite(pid)) return
  emit('project-change', participantId, pid)
}
</script>

<template>
  <div class="mcd-card">
    <el-table :data="rows" row-key="participantId" size="default" border>
      <el-table-column type="expand" width="46">
        <template #default="{ row }">
          <div class="mcd-muted" style="margin: 4px 12px 0">
            {{ row.displayName }} 的分摊计算依据（按订单内闭环后跨单汇总）：
          </div>
          <pre class="formula-box">{{ (row.formulaLines ?? []).join('\n') || '暂无计算依据' }}</pre>
        </template>
      </el-table-column>

      <el-table-column label="参与人" min-width="130">
        <template #default="{ row }">
          <div class="person-cell">
            <span class="person-cell__name">{{ row.displayName }}</span>
            <span class="person-cell__ref" v-if="row.personRef">{{ row.personRef }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="所属项目" min-width="170">
        <template #default="{ row }">
          <el-select
            :model-value="row.projectId"
            placeholder="请选择项目"
            size="small"
            :loading="ledgerStore.savingParticipant.value"
            @change="(v: number | string) => onProjectChange(row.participantId, v)"
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="projectLabel(p)"
              :value="p.id"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="合计" width="104" align="right">
        <template #default="{ row }"><b class="money">{{ formatYuan(row.totalCents) }}</b></template>
      </el-table-column>
      <el-table-column label="餐补基数" width="104" align="right">
        <template #default="{ row }"><span class="money">{{ formatYuan(row.subsidyBaseCents) }}</span></template>
      </el-table-column>
      <el-table-column label="标准内" width="104" align="right">
        <template #default="{ row }">
          <span class="money" style="color: var(--mcd-green)">{{ formatYuan(row.withinStandardCents) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="超出标准" width="108" align="right">
        <template #default="{ row }">
          <span class="money" :style="{ color: row.exceedStandardCents > 0 ? 'var(--mcd-red)' : undefined }">
            {{ formatYuan(row.exceedStandardCents) }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <div class="person-total">
      <span>共 {{ rows.length }} 人</span>
      <span>合计 <b class="money">{{ formatYuan(totals.total) }}</b></span>
      <span>标准内 <b class="money" style="color: var(--mcd-green)">{{ formatYuan(totals.within) }}</b></span>
      <span>超出标准 <b class="money" style="color: var(--mcd-red)">{{ formatYuan(totals.exceed) }}</b></span>
      <span class="mcd-muted">超标占比 {{ ratioPercent(totals.exceed, totals.total) }}%</span>
    </div>
  </div>
</template>

<style scoped>
.person-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.person-cell__name {
  font-weight: 600;
}

.person-cell__ref {
  font-size: 11px;
  color: var(--mcd-text-3);
}

.person-total {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  padding: 10px 4px 2px;
  font-size: 13px;
  color: var(--mcd-text-2);
  border-top: 1px dashed var(--mcd-border);
  margin-top: 4px;
}

.formula-box {
  margin: 4px 12px 10px;
}
</style>
