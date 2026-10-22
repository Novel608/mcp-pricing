<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { planApi } from '@/api/plan'
import type { PlanRecommendation } from '@/types/plan'
import { formatYuan } from '@/utils/money'

/** R1 套餐组合 / R2 满减凑单 / R3 券匹配：净节省按类型分级展示（R6） */
const props = defineProps<{ planId: number }>()

const recs = ref<PlanRecommendation[]>([])
const loading = ref(false)
const calculating = ref(false)
const error = ref<string | null>(null)

const TYPE_META: Record<string, { label: string; tone: 'success' | 'warning' | 'info' }> = {
  COMBO: { label: '套餐组合', tone: 'success' },
  THRESHOLD: { label: '满减凑单', tone: 'warning' },
  COUPON: { label: '券匹配', tone: 'info' },
}

const worth = computed(() => recs.value.filter((r) => !r.notWorthIt))
const notWorth = computed(() => recs.value.filter((r) => r.notWorthIt))

function reasonText(r: PlanRecommendation): string {
  if (!r.reasonJson) return ''
  try {
    const obj = JSON.parse(r.reasonJson) as { text?: string }
    return obj.text ?? ''
  } catch {
    return ''
  }
}

/** 券匹配不给金额（R3）：展示定性结论，绝不臆造优惠 */
function savingText(r: PlanRecommendation): string {
  if (r.netSavingCents == null) return '以官方实际结算为准'
  return formatYuan(r.netSavingCents)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    recs.value = await planApi.recommend(props.planId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '推荐加载失败'
  } finally {
    loading.value = false
  }
}

async function recalc() {
  calculating.value = true
  try {
    recs.value = await planApi.recommend(props.planId)
    ElMessage.success('已按官方菜单价重算推荐')
  } catch {
    ElMessage.error('推荐计算失败')
  } finally {
    calculating.value = false
  }
}

async function toggleAccept(r: PlanRecommendation, accepted: boolean) {
  if (r.id == null) return
  try {
    await planApi.acceptRecommendation(props.planId, r.id, accepted)
    r.accepted = accepted
    ElMessage.success(accepted ? '已采纳，转出时可按建议下单' : '已取消采纳')
  } catch {
    ElMessage.error('操作失败')
  }
}

defineExpose({ load })
load()
</script>

<template>
  <section class="mcd-card rec-panel" v-loading="loading">
    <header class="rec-panel__head">
      <div class="rec-panel__title">
        <span>💡</span>
        <span>省钱建议（依据官方菜单价计算）</span>
      </div>
      <div class="mcd-spacer"></div>
      <el-button size="small" :loading="calculating" @click="recalc">重新计算</el-button>
    </header>

    <div v-if="error" class="mcd-muted">{{ error }}</div>
    <div v-else-if="!recs.length" class="mcd-muted">暂无建议：当前选餐未命中套餐组合 / 满减档位 / 适用券。</div>

    <template v-else>
      <div
        v-for="r in worth"
        :key="r.id"
        class="rec-item"
        :class="{ 'rec-item--accepted': r.accepted }"
      >
        <div class="rec-item__top">
          <el-tag :type="TYPE_META[r.type]?.tone ?? 'info'" size="small" effect="light">
            {{ TYPE_META[r.type]?.label ?? r.type }}
          </el-tag>
          <b class="rec-item__title">{{ r.title }}</b>
          <div class="mcd-spacer"></div>
          <span class="rec-item__saving">{{ savingText(r) }}</span>
          <el-switch
            size="small"
            :model-value="!!r.accepted"
            active-text="已采纳"
            @change="(v: boolean) => toggleAccept(r, v)"
          />
        </div>
        <p class="rec-item__text">{{ reasonText(r) }}</p>
        <p class="rec-item__src">口径：{{ r.priceSource === 'MCP_CALCULATED' ? '官方核价' : '官方菜单参考价' }}（活动规则以门店公示为准）</p>
      </div>

      <!-- 不划算的建议照常展示但灰显（R6：不隐藏、不诱导凑单） -->
      <div v-for="r in notWorth" :key="r.id" class="rec-item rec-item--dim">
        <div class="rec-item__top">
          <el-tag type="danger" size="small" effect="plain">不划算</el-tag>
          <span class="rec-item__title">{{ r.title }}</span>
          <div class="mcd-spacer"></div>
          <span class="rec-item__saving rec-item__saving--neg">{{ savingText(r) }}</span>
        </div>
        <p class="rec-item__text">{{ reasonText(r) }}</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.rec-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.rec-panel__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.rec-item {
  border: 1px solid var(--mcd-border, #ece6dd);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
}
.rec-item--accepted {
  border-color: #ffc107;
  background: #fffaf0;
}
.rec-item--dim {
  opacity: 0.62;
  background: #fafafa;
}
.rec-item__top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rec-item__title {
  font-size: 14px;
}
.rec-item__saving {
  font-weight: 700;
  color: #d81e06;
}
.rec-item__saving--neg {
  color: #909399;
}
.rec-item__text {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
}
.rec-item__src {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}
</style>
