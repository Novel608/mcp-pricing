<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import IdentityBoard from '@/components/IdentityBoard.vue'
import StepGuide from '@/components/StepGuide.vue'
import OrderCard from '@/components/OrderCard.vue'
import ParticipantTable from '@/components/ParticipantTable.vue'
import ProjectSummaryCard from '@/components/ProjectSummaryCard.vue'
import PendingItemsCard from '@/components/PendingItemsCard.vue'
import SubsidyRulePanel from '@/components/SubsidyRulePanel.vue'
import OrderPickerDialog from '@/components/OrderPickerDialog.vue'
import ledgerStore from '@/store/ledger'
import { formatYuan } from '@/utils/money'

const props = defineProps<{ ledgerId: number }>()

const route = useRoute()
const router = useRouter()
const pickerVisible = ref(false)

const ledger = computed(() => ledgerStore.ledger.value)
const loading = computed(() => ledgerStore.loading.value)
const loadError = computed(() => ledgerStore.loadError.value)
const isDemo = computed(() => ledgerStore.mode.value === 'demo')
const orders = computed(() => ledger.value?.orders ?? [])
const balanced = computed(() => ledgerStore.balanced.value)

const rec = computed(() => ledger.value?.reconciliation)

onMounted(async () => {
  await ledgerStore.loadLedger(props.ledgerId)
})

// 路由变化（如真实模式导入后跳到新账）时重新加载
watch(
  () => route.params.id,
  async (next) => {
    const id = Number(next) || 1
    if (id !== ledgerStore.ledgerId.value || !ledger.value) {
      await ledgerStore.loadLedger(id)
    }
  },
)

function onProjectChange(participantId: number, projectId: number) {
  ledgerStore.updateParticipantProject(participantId, projectId)
}

function openPicker() {
  if (ledgerStore.mcpOrders.value === null && !ledgerStore.mcpLoading.value) {
    ledgerStore.loadMcpOrders()
  }
  pickerVisible.value = true
}
</script>

<template>
  <div class="workbench" v-loading="loading">
    <!-- 加载失败 -->
    <div v-if="loadError && !ledger" class="mcd-card state-panel">
      <div class="state-panel__icon">🔌</div>
      <div class="state-panel__title">未能加载餐账数据</div>
      <div class="state-panel__desc">{{ loadError }}</div>
      <el-button type="primary" style="margin-top: 14px" @click="ledgerStore.loadLedger(props.ledgerId)">
        重试
      </el-button>
    </div>

    <template v-else-if="ledger">
      <!-- 恒等看板 -->
      <IdentityBoard />

      <!-- 步骤区 -->
      <StepGuide @pick-orders="openPicker" />

      <div class="wb-grid">
        <!-- 左列：① 选订单 / ② 分餐品 -->
        <div class="wb-col">
          <section id="section-orders" class="mcd-section">
            <div class="mcd-section__head">
              <div class="mcd-section__title">
                <span class="mcd-step-badge">①</span>
                <span>选订单 · 分餐品</span>
              </div>
              <div class="mcd-spacer"></div>
              <span class="mcd-muted">共 {{ orders.length }} 笔订单</span>
            </div>

            <!-- 沙盒模式：直接展示已选订单 -->
            <el-alert v-if="isDemo" type="success" :closable="false" show-icon class="wb-banner">
              <template #title>
                <span>
                  🧪 沙盒模式已内置基准数据集（两单 · 六人 · 两项目），直接展示已选订单，无需 MCP Token。
                  切到「🟢 真实 MCP 账单」即可拉取你自己的订单。
                </span>
              </template>
            </el-alert>

            <!-- 真实模式：依赖 MCP 代理 -->
            <template v-else>
              <el-alert v-if="ledgerStore.mcpLoading.value" type="info" :closable="false" show-icon class="wb-banner">
                <template #title><span>正在通过 MCP order-list 拉取近期订单…</span></template>
              </el-alert>
              <el-alert
                v-else-if="ledgerStore.mcpError.value"
                type="warning"
                :closable="false"
                show-icon
                class="wb-banner"
              >
                <template #title>
                  <span>
                    {{ ledgerStore.mcpError.value }}
                    <template v-if="ledgerStore.mcpError.value.includes('MCD_MCP_TOKEN')">
                      —— 配置 <b>MCD_MCP_TOKEN</b> 后可用。
                    </template>
                  </span>
                </template>
                <div style="margin-top: 8px">
                  <el-button size="small" @click="ledgerStore.loadMcpOrders()">重新拉取</el-button>
                </div>
              </el-alert>
              <div v-else class="wb-pickbar">
                <span class="mcd-muted">
                  可选 {{ (ledgerStore.mcpOrders.value ?? []).length }} 笔近期订单，勾选后由服务端调 query-order 生成快照。
                </span>
                <div class="mcd-spacer"></div>
                <el-button type="primary" :icon="'Plus'" @click="openPicker">选择订单</el-button>
              </div>
            </template>

            <div class="wb-orders">
              <OrderCard v-for="order in orders" :key="order.id" :order="order" />
              <div v-if="orders.length === 0" class="mcd-card state-panel">
                <div class="state-panel__icon">🧾</div>
                <div class="state-panel__title">还没有选入订单</div>
                <div class="state-panel__desc">
                  <template v-if="isDemo">沙盒数据加载中或后端未返回订单。</template>
                  <template v-else>点击上方「选择订单」把本餐次的麦当劳订单选入餐账。</template>
                </div>
              </div>
            </div>

            <PendingItemsCard />
          </section>
        </div>

        <!-- 右列：④ 餐补规则 + 项目归集 -->
        <div class="wb-col">
          <section id="section-rule" class="mcd-section">
            <SubsidyRulePanel />
          </section>
          <section class="mcd-section">
            <ProjectSummaryCard />
          </section>
        </div>
      </div>

      <!-- ③ 人员与项目 -->
      <section id="section-participants" class="mcd-section">
        <div class="mcd-section__head">
          <div class="mcd-section__title">
            <span class="mcd-step-badge">③</span>
            <span>人员与项目</span>
          </div>
          <div class="mcd-spacer"></div>
          <span class="mcd-muted">修改项目下拉后自动重新对账 · 点击行首箭头展开计算依据</span>
        </div>
        <ParticipantTable @project-change="onProjectChange" />
      </section>

      <!-- ⑤ 导出 -->
      <section id="section-export" class="mcd-section">
        <div class="mcd-card export-card">
          <div class="mcd-section__head" style="margin-bottom: 10px">
            <div class="mcd-section__title">
              <span class="mcd-step-badge">⑤</span>
              <span>导出与打印</span>
            </div>
          </div>

          <div class="export-card__check">
            <div class="export-card__check-row">
              <span>订单实付合计</span><b class="money">{{ formatYuan(rec?.ordersTotalCents ?? 0) }}</b>
            </div>
            <div class="export-card__check-row">
              <span>人员分摊合计</span><b class="money">{{ formatYuan(rec?.personsTotalCents ?? 0) }}</b>
            </div>
            <div class="export-card__check-row">
              <span>项目归集合计</span><b class="money">{{ formatYuan(rec?.projectsTotalCents ?? 0) }}</b>
            </div>
            <div class="export-card__check-row export-card__check-row--diff">
              <span>三方尾差</span>
              <b class="money" :style="{ color: balanced ? 'var(--mcd-green)' : 'var(--mcd-red)' }">
                {{ formatYuan(rec?.diffCents ?? 0) }}
              </b>
            </div>
          </div>

          <div class="export-card__actions">
            <el-button
              type="primary"
              size="large"
              :icon="'Download'"
              :loading="ledgerStore.exporting.value"
              :disabled="!balanced"
              @click="ledgerStore.exportCsv()"
            >
              导出 CSV
            </el-button>
            <el-button
              size="large"
              :icon="'Printer'"
              :disabled="!balanced"
              @click="router.push(`/ledger/${ledger.id}/print`)"
            >
              打印报销单
            </el-button>
            <el-alert
              v-if="!balanced"
              type="error"
              :closable="false"
              show-icon
              class="export-card__warn"
            >
              <template #title>
                <span>三方尾差不为零，导出已被阻断（LEDGER_DIFF_NOT_ZERO）。请先处理待确认餐品。</span>
              </template>
            </el-alert>
          </div>
        </div>
      </section>
    </template>

    <OrderPickerDialog v-model="pickerVisible" />
  </div>
</template>

<style scoped>
.wb-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.wb-banner {
  margin-bottom: 14px;
}

.wb-pickbar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--mcd-card);
  border: 1px dashed var(--mcd-border);
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 14px;
}

.wb-orders {
  display: flex;
  flex-direction: column;
}

.mcd-step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--mcd-red);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex: none;
}

.export-card__check {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.export-card__check-row {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--mcd-text-2);
}

.export-card__check-row b {
  font-size: 17px;
  color: var(--mcd-text);
}

.export-card__check-row--diff {
  background: var(--mcd-green-soft);
}

.export-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.export-card__warn {
  flex: 1;
  min-width: 260px;
}

@media (max-width: 760px) {
  .export-card__check {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
