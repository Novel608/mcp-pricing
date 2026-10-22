<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { planApi } from '@/api/plan'
import type { PlanSnapshot } from '@/types/plan'
import { formatYuan } from '@/utils/money'

/**
 * 官方核价基线（T04 #9/#10）：金额全部来自官方 calculate-price，
 * 每人均摊由 PennyAllocator 按「各人餐品小计」权重分摊，Σ == 官方总额（R5，尾差 0）。
 * 未核价时显示「待核价」，绝不本地估算（L3）。
 */
const props = defineProps<{ planId: number; currentSubtotalCents: number }>()
const emit = defineEmits<{ (e: 'priced'): void }>()

const snap = ref<PlanSnapshot | null>(null)
const loading = ref(false)
const pricing = ref(false)
const missing = ref(false)

const perPersonSum = computed(() =>
  (snap.value?.perPerson ?? []).reduce((s, p) => s + (p.expectedCents ?? 0), 0),
)
/** R5 自校验：前端也验一次，两侧都为 0 才展示「恒等通过」 */
const identityOk = computed(
  () => !!snap.value && perPersonSum.value === (snap.value.expectedTotalCents ?? -1),
)

async function load() {
  loading.value = true
  missing.value = false
  try {
    snap.value = await planApi.getSnapshot(props.planId)
  } catch {
    snap.value = null
    missing.value = true
  } finally {
    loading.value = false
  }
}

async function doPricing() {
  pricing.value = true
  try {
    snap.value = await planApi.pricing(props.planId)
    missing.value = false
    ElMessage.success('已按官方 calculate-price 核价')
    emit('priced')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '核价失败')
  } finally {
    pricing.value = false
  }
}

defineExpose({ load })
load()
</script>

<template>
  <section class="mcd-card budget" v-loading="loading">
    <header class="budget__head">
      <div class="budget__title"><span>🧮</span><span>预算基线（官方核价）</span></div>
      <div class="mcd-spacer"></div>
      <el-tag v-if="snap?.stale" type="warning" size="small" effect="light">已过期</el-tag>
      <el-button type="primary" size="small" :loading="pricing" @click="doPricing">
        {{ snap ? '重新核价' : '官方核价' }}
      </el-button>
    </header>

    <div v-if="!snap" class="mcd-muted">
      {{ missing ? '尚未核价：点击「官方核价」由麦当劳官方接口试算（零写操作）。' : '加载中…' }}
      <span class="budget__hint">（当前按菜单价合计 {{ formatYuan(props.currentSubtotalCents) }}，仅供参考，不等于官方核价结果）</span>
    </div>

    <template v-else>
      <div class="budget__grid">
        <div class="budget__cell">
          <span>餐品小计</span><b>{{ formatYuan(snap.expectedSubtotalCents) }}</b>
        </div>
        <div class="budget__cell">
          <span>优惠</span><b>-{{ formatYuan(snap.expectedDiscountCents) }}</b>
        </div>
        <div class="budget__cell">
          <span>配送费</span><b>{{ formatYuan(snap.expectedDeliveryFeeCents) }}</b>
        </div>
        <div class="budget__cell budget__cell--total">
          <span>官方核价合计</span><b>{{ formatYuan(snap.expectedTotalCents) }}</b>
        </div>
      </div>

      <div v-if="snap.stale" class="budget__stale">⚠️ {{ snap.staleReason }}</div>

      <table class="budget__table">
        <thead>
          <tr><th>成员</th><th style="text-align: right">预期承担</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in snap.perPerson ?? []" :key="p.participantId">
            <td>{{ p.displayName }}</td>
            <td style="text-align: right">{{ formatYuan(p.expectedCents) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th>合计</th>
            <th style="text-align: right">{{ formatYuan(perPersonSum) }}</th>
          </tr>
        </tfoot>
      </table>

      <div class="budget__identity" :class="{ 'budget__identity--ok': identityOk }">
        {{ identityOk ? '✅ 恒等校验通过：Σ 每人预期 = 官方总额（尾差 0）' : '⚠️ 恒等校验未通过，请重新核价' }}
      </div>
    </template>
  </section>
</template>

<style scoped>
.budget__head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.budget__title { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.budget__hint { display: block; margin-top: 4px; font-size: 12px; }
.budget__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}
.budget__cell {
  border: 1px solid var(--mcd-border, #ece6dd);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}
.budget__cell b { font-size: 16px; color: #303133; }
.budget__cell--total b { color: #d81e06; }
.budget__stale { color: #e6a23c; font-size: 13px; margin-bottom: 8px; }
.budget__table { width: 100%; border-collapse: collapse; font-size: 13px; }
.budget__table th, .budget__table td {
  border-bottom: 1px solid #f0f0f0;
  padding: 6px 4px;
  text-align: left;
}
.budget__identity { margin-top: 8px; font-size: 12px; color: #e6a23c; }
.budget__identity--ok { color: #67c23a; }
</style>
