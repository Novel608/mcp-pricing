<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import ModeSwitch from '@/components/ModeSwitch.vue'
import ledgerStore from '@/store/ledger'
import { formatMealTime } from '@/utils/format'
import { formatYuan } from '@/utils/money'
import { ledgerStatusMeta } from '@/utils/enumLabel'

const route = useRoute()
const router = useRouter()

const ledger = computed(() => ledgerStore.ledger.value)
const balanced = computed(() => ledgerStore.balanced.value)
const exporting = computed(() => ledgerStore.exporting.value)

const ledgerId = computed(() => ledgerStore.ledgerId.value)
const statusMeta = computed(() => ledgerStatusMeta(ledger.value?.status))
const mealTime = computed(() => formatMealTime(ledger.value?.mealTime))

/** 当前所在工作台 */
type Workbench = 'ledger' | 'plan'
const activeWorkbench = computed<Workbench>(() => (route.path.startsWith('/plan') ? 'plan' : 'ledger'))
const isLedgerRoute = computed(() => activeWorkbench.value === 'ledger')

function gotoWorkbench(target: Workbench) {
  router.push(target === 'plan' ? '/plan/1' : '/ledger/1')
}

async function onExport() {
  if (!balanced.value) {
    ElMessage.warning('三方尾差不为零，无法导出')
    return
  }
  await ledgerStore.exportCsv()
}

function onPrint() {
  router.push(`/ledger/${ledgerId.value}/print`)
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <div class="app-header__brand">
        <span class="app-header__logo">🍔</span>
        <div class="app-header__brand-text">
          <strong>麦麦餐帐</strong>
          <small>餐点完了，账也分清了</small>
        </div>
      </div>

      <div class="app-header__meta" v-if="isLedgerRoute && ledger">
        <div class="app-header__meta-title">
          <span class="app-header__ledger-title">{{ ledger.title }}</span>
          <el-tag :type="statusMeta.tone" size="small" effect="light">{{ statusMeta.label }}</el-tag>
        </div>
        <div class="app-header__meta-sub">
          <span>餐次 {{ mealTime }}</span>
          <span class="app-header__dot">·</span>
          <span>操作人 {{ ledger.operatorName || '—' }}</span>
          <span class="app-header__dot">·</span>
          <span>餐补上限 {{ formatYuan(ledger.subsidyLimitCents) }} / 人</span>
        </div>
      </div>
      <div class="app-header__meta" v-else>
        <div class="app-header__meta-title">
          <span class="app-header__ledger-title">团队餐费对账工作台</span>
        </div>
      </div>

      <div class="app-header__actions">
        <el-radio-group
          :model-value="activeWorkbench"
          size="default"
          class="workbench-switch"
          @change="(v: string | number | boolean | undefined) => gotoWorkbench(v === 'plan' ? 'plan' : 'ledger')"
        >
          <el-radio-button value="ledger">对账工作台</el-radio-button>
          <el-radio-button value="plan">选餐工作台</el-radio-button>
        </el-radio-group>
        <ModeSwitch size="default" />
        <el-button-group v-if="isLedgerRoute">
          <el-tooltip
            :content="balanced ? '导出 CSV 报销明细' : '三方尾差不为零，无法导出'"
            placement="bottom"
          >
            <el-button
              type="primary"
              :icon="'Download'"
              :loading="exporting"
              :disabled="!balanced || !ledger"
              @click="onExport"
            >
              导出 CSV
            </el-button>
          </el-tooltip>
          <el-tooltip content="打印报销单" placement="bottom">
            <el-button :icon="'Printer'" :disabled="!ledger" @click="onPrint">打印</el-button>
          </el-tooltip>
          <el-tooltip content="重新拉取对账数据" placement="bottom">
            <el-button
              :icon="'Refresh'"
              :loading="ledgerStore.loading.value"
              @click="ledgerStore.loadLedger()"
            >
              刷新
            </el-button>
          </el-tooltip>
        </el-button-group>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--mcd-border);
}

.app-header__inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.app-header__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.app-header__logo {
  font-size: 26px;
  line-height: 1;
}

.app-header__brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.app-header__brand-text strong {
  font-size: 17px;
  letter-spacing: 0.02em;
}

.app-header__brand-text small {
  font-size: 11px;
  color: var(--mcd-text-3);
}

.app-header__meta {
  flex: 1;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.app-header__meta-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-header__ledger-title {
  font-size: 15px;
  font-weight: 600;
}

.app-header__meta-sub {
  font-size: 12px;
  color: var(--mcd-text-3);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.app-header__dot {
  color: #d3d6db;
}

.app-header__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  margin-left: auto;
}

.workbench-switch :deep(.el-radio-button__inner) {
  font-size: 12px;
}

@media (max-width: 900px) {
  .app-header__actions {
    margin-left: 0;
  }
}
</style>
