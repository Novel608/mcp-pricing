<script setup lang="ts">
import { ref, watch } from 'vue'

import { ledgerApi } from '@/api/ledger'
import type { PlanVsActual } from '@/types/plan'
import { formatSignedYuan, formatYuan } from '@/utils/money'

/**
 * 预算 vs 实际差异（T05 #12）：预期来自官方 calculate-price，实际来自官方 query-order。
 * 只读派生、不落库；未关联方案 / 无基线时按后端错误码给出对应引导。
 */
const props = defineProps<{ ledgerId: number }>()

const data = ref<PlanVsActual | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const DIRECTION_META: Record<string, { label: string; tone: string }> = {
  OVER: { label: '超支', tone: '#d81e06' },
  UNDER: { label: '结余', tone: '#67c23a' },
  EQUAL: { label: '持平', tone: '#606266' },
}

async function load() {
  loading.value = true
  error.value = null
  data.value = null
  try {
    data.value = await ledgerApi.getVsPlan(props.ledgerId)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '差异对照不可用'
    error.value = msg.includes('PLAN_NOT_LINKED')
      ? '该餐账并非由选餐方案转出，没有预算基线可对照。'
      : msg.includes('PLAN_BASELINE_UNAVAILABLE')
        ? '该方案尚未完成官方核价，暂无预算基线。'
        : msg
  } finally {
    loading.value = false
  }
}

watch(() => props.ledgerId, load, { immediate: true })
</script>

<template>
  <section class="mcd-card vs-plan" v-loading="loading">
    <header class="vs-plan__head">
      <div class="vs-plan__title"><span>📊</span><span>预算 vs 实际</span></div>
      <div class="mcd-spacer"></div>
      <span v-if="data" class="mcd-muted">
        口径：预期=官方核价，实际=官方订单
      </span>
    </header>

    <div v-if="error" class="vs-plan__empty">{{ error }}</div>

    <template v-else-if="data">
      <div class="vs-plan__grid">
        <div class="vs-plan__cell">
          <span>预期（官方核价）</span><b>{{ formatYuan(data.expectedTotalCents) }}</b>
        </div>
        <div class="vs-plan__cell">
          <span>实际（官方订单）</span><b>{{ formatYuan(data.actualTotalCents) }}</b>
        </div>
        <div class="vs-plan__cell vs-plan__cell--diff">
          <span>差异（实际 − 预期）</span>
          <b :style="{ color: DIRECTION_META[data.direction]?.tone }">
            {{ formatSignedYuan(data.diffCents) }}
            <small>{{ DIRECTION_META[data.direction]?.label }}</small>
          </b>
        </div>
      </div>

      <table class="vs-plan__table">
        <thead>
          <tr>
            <th>成员</th>
            <th style="text-align: right">预期</th>
            <th style="text-align: right">实际</th>
            <th style="text-align: right">差异</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in data.persons" :key="p.participantId">
            <td>{{ p.displayName }}</td>
            <td style="text-align: right">{{ formatYuan(p.expectedCents) }}</td>
            <td style="text-align: right">{{ formatYuan(p.actualCents) }}</td>
            <td style="text-align: right" :style="{ color: p.diffCents > 0 ? '#d81e06' : p.diffCents < 0 ? '#67c23a' : '#606266' }">
              {{ formatSignedYuan(p.diffCents) }}
            </td>
          </tr>
        </tbody>
      </table>

      <div class="vs-plan__foot" :class="{ 'vs-plan__foot--ok': data.balanced }">
        {{ data.balanced ? '✅ 人员级差异之和 = 总额差异（同源恒等）' : '⚠️ 人员级与总额差异不一致，请检查订单归属' }}
      </div>
    </template>
  </section>
</template>

<style scoped>
.vs-plan__head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.vs-plan__title { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.vs-plan__empty { color: #909399; font-size: 13px; padding: 12px 0; }
.vs-plan__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 12px; }
.vs-plan__cell {
  border: 1px solid var(--mcd-border, #ece6dd);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}
.vs-plan__cell b { font-size: 16px; color: #303133; }
.vs-plan__table { width: 100%; border-collapse: collapse; font-size: 13px; }
.vs-plan__table th, .vs-plan__table td { border-bottom: 1px solid #f0f0f0; padding: 6px 4px; text-align: left; }
.vs-plan__foot { margin-top: 8px; font-size: 12px; color: #e6a23c; }
.vs-plan__foot--ok { color: #67c23a; }
</style>
