<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import MemberBar from '@/views/plan/MemberBar.vue'
import MenuBrowser from '@/views/plan/MenuBrowser.vue'
import SelectionSummary from '@/views/plan/SelectionSummary.vue'
import planStore from '@/store/plan'
import { formatMealTime } from '@/utils/format'
import { formatYuan } from '@/utils/money'
import { channelLabel, ledgerStatusMeta } from '@/utils/enumLabel'

const props = defineProps<{ planId: number }>()

const route = useRoute()
const plan = computed(() => planStore.plan.value)
const loading = computed(() => planStore.loading.value)
const loadError = computed(() => planStore.loadError.value)
const saving = computed(() => planStore.saving.value)
const totalCents = computed(() => planStore.totalCents.value)
const personCount = computed(() => planStore.participants.value.length)
const selectionCount = computed(() => planStore.selections.value.length)

/** 方案状态徽章（复用对账域的展示映射，缺失时原样显示） */
const statusMeta = computed(() => {
  const s = plan.value?.status
  if (s === 'DRAFT') return { label: '草稿', tone: 'info' as const }
  if (s === 'PRICED') return { label: '已试算', tone: 'warning' as const }
  if (s === 'CONVERTED') return { label: '已转出', tone: 'success' as const }
  return ledgerStatusMeta(s)
})

let menuLoadedFor: string | null = null

async function bootstrap(id: number) {
  const detail = await planStore.loadPlan(id)
  // 拉到门店编码后加载菜单（只加载一次，避免重复请求）
  const storeId = detail?.storeId
  if (storeId && menuLoadedFor !== storeId) {
    menuLoadedFor = storeId
    await planStore.loadMenu(storeId)
  }
}

onMounted(() => bootstrap(props.planId))

watch(
  () => route.params.id,
  (next) => {
    const id = Number(next) || 1
    if (id !== planStore.planId.value || !plan.value) bootstrap(id)
  },
)

async function onSave() {
  const ok = await planStore.savePlan()
  if (ok) ElMessage.success('方案已保存')
  else ElMessage.error('保存失败，请稍后重试')
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <!-- 加载失败 -->
    <div v-if="loadError && !plan" class="mcd-card state-panel">
      <div class="state-panel__icon">🔌</div>
      <div class="state-panel__title">未能加载选餐方案</div>
      <div class="state-panel__desc">{{ loadError }}</div>
      <el-button type="primary" style="margin-top: 14px" @click="bootstrap(props.planId)">重试</el-button>
    </div>

    <template v-else-if="plan">
      <!-- 方案信息 -->
      <header class="plan-head">
        <div class="plan-head__title">
          <span>🧑‍🤝‍🧑</span>
          <span>{{ plan.title }}</span>
          <el-tag :type="statusMeta.tone" size="small" effect="light">{{ statusMeta.label }}</el-tag>
        </div>
        <div class="mcd-spacer"></div>
        <div class="plan-head__meta">
          <span class="plan-head__chip">🏪 {{ plan.storeName || plan.storeId || '未指定门店' }}</span>
          <span class="plan-head__chip">{{ channelLabel(plan.channel) }}</span>
          <span class="plan-head__chip">🕐 {{ formatMealTime(plan.mealTime) }}</span>
          <span class="plan-head__chip">👥 {{ personCount }} 人</span>
          <span class="plan-head__chip">🛒 {{ selectionCount }} 项选餐</span>
        </div>
      </header>

      <!-- 成员条 -->
      <MemberBar />

      <!-- 主体 -->
      <div class="plan-layout">
        <MenuBrowser />
        <SelectionSummary />
      </div>

      <!-- 底部动作条 -->
      <div class="plan-actionbar">
        <div class="plan-actionbar__total">
          <span>当前方案合计</span>
          <b>{{ formatYuan(totalCents) }}</b>
        </div>
        <div class="mcd-spacer"></div>
        <span class="mcd-muted">保存后可在对账工作台继续处理；转出餐账待后续版本</span>
        <el-button type="primary" size="large" :icon="'Select'" :loading="saving" @click="onSave">
          保存方案
        </el-button>
      </div>
    </template>
  </div>
</template>
