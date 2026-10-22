<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'

import ledgerStore from '@/store/ledger'
import { formatMealTime } from '@/utils/format'
import { formatYuan } from '@/utils/money'
import { channelLabel, ledgerStatusMeta } from '@/utils/enumLabel'

const props = defineProps<{ ledgerId: number }>()
const route = useRoute()
const router = useRouter()

const ledger = computed(() => ledgerStore.ledger.value)
const loading = computed(() => ledgerStore.loading.value)
const balanced = computed(() => ledgerStore.balanced.value)

const rec = computed(() => ledger.value?.reconciliation)
const printedAt = computed(() => dayjs().format('YYYY-MM-DD HH:mm'))
const docNo = computed(() => `MCD-${String(ledger.value?.id ?? 0).padStart(4, '0')}-${dayjs().format('YYYYMMDDHHmm')}`)

onMounted(async () => {
  if (!ledger.value || ledgerStore.ledgerId.value !== props.ledgerId) {
    await ledgerStore.loadLedger(props.ledgerId)
  }
})

const RULES = [
  'R1 先在订单内分摊，再汇总到人和项目：每笔订单的优惠与配送费只在该单参与人间按餐品小计权重分配。',
  'R2 每个金额都能解释来源：个人餐品 + 共享份额 − 优惠分摊 + 配送等费用 = 个人分摊额；已含优惠的商品金额不二次扣减。',
  'R3 同一人同一餐次共用一个餐补上限：先合并本餐费用再套一次上限；餐补是否含配送费为显式开关。',
  'R4 所有计算精确到分：整数分 + 最大余额法派发尾差；取消/退款未核实订单保持待核对状态，不计入合计。',
]
function onPrint() {
  window.print()
}
</script>

<template>
  <div class="print-page" v-loading="loading">
    <!-- 工具栏（不打印） -->
    <div class="print-toolbar no-print">
      <el-button :icon="'Back'" @click="router.back()">返回工作台</el-button>
      <el-button type="primary" :icon="'Printer'" @click="onPrint">打印 / 另存 PDF</el-button>
    </div>

    <div v-if="!ledger" class="state-panel">
      <div class="state-panel__icon">🧾</div>
      <div class="state-panel__title">暂无可打印的餐账</div>
      <div class="state-panel__desc">请先返回工作台加载餐账数据。</div>
    </div>

    <article v-else class="print-sheet">
      <header class="print-sheet__head">
        <div>
          <h1>团队餐费报销明细</h1>
          <p class="print-sheet__brand">麦麦餐帐 · 餐点完了，账也分清了</p>
        </div>
        <div class="print-sheet__meta">
          <div>单据编号：{{ docNo }}</div>
          <div>打印时间：{{ printedAt }}</div>
        </div>
      </header>

      <!-- 餐账信息 -->
      <section class="print-block">
        <h2>一、餐账信息</h2>
        <table class="print-table print-table--kv">
          <tbody>
            <tr>
              <th>餐账标题</th>
              <td>{{ ledger.title }}</td>
              <th>餐次时间</th>
              <td>{{ formatMealTime(ledger.mealTime) }}</td>
            </tr>
            <tr>
              <th>操作人</th>
              <td>{{ ledger.operatorName || '—' }}</td>
              <th>餐账状态</th>
              <td>{{ ledgerStatusMeta(ledger.status).label }}</td>
            </tr>
            <tr>
              <th>订单数</th>
              <td>{{ ledger.orders.length }} 笔</td>
              <th>参与人数</th>
              <td>{{ ledger.participants.length }} 人</td>
            </tr>
            <tr>
              <th>餐补上限</th>
              <td>{{ formatYuan(ledger.subsidyLimitCents) }} / 人</td>
              <th>餐补含配送费</th>
              <td>{{ ledger.includeDeliveryInSubsidy ? '是（计入餐补基数）' : '否（不计入餐补基数）' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 订单来源 -->
      <section class="print-block">
        <h2>二、订单来源（MCP query-order 历史实付）</h2>
        <table class="print-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>渠道</th>
              <th>状态</th>
              <th>置信度</th>
              <th class="num">餐品原价</th>
              <th class="num">优惠</th>
              <th class="num">配送费</th>
              <th class="num">实付</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in ledger.orders" :key="o.id">
              <td class="mono">#{{ o.mcpOrderId }}</td>
              <td>{{ channelLabel(o.channel) }}</td>
              <td>{{ o.orderStatus }}</td>
              <td>{{ o.dataConfidence }}</td>
              <td class="num">{{ formatYuan((o.items ?? []).reduce((s, i) => s + (i.itemTotalCents || 0), 0)) }}</td>
              <td class="num">{{ formatYuan(o.discountCents) }}</td>
              <td class="num">{{ formatYuan(o.deliveryFeeCents) }}</td>
              <td class="num strong">{{ formatYuan(o.paidTotalCents) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 人员明细 -->
      <section class="print-block">
        <h2>三、人员分摊明细</h2>
        <table class="print-table">
          <thead>
            <tr>
              <th>参与人</th>
              <th>所属项目</th>
              <th class="num">合计</th>
              <th class="num">餐补基数</th>
              <th class="num">标准内</th>
              <th class="num">超出标准</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in rec?.persons ?? []" :key="p.participantId">
              <td>{{ p.displayName }}</td>
              <td>{{ ledger.projects.find((x) => x.id === p.projectId)?.name ?? '未绑定' }}</td>
              <td class="num strong">{{ formatYuan(p.totalCents) }}</td>
              <td class="num">{{ formatYuan(p.subsidyBaseCents) }}</td>
              <td class="num">{{ formatYuan(p.withinStandardCents) }}</td>
              <td class="num">{{ formatYuan(p.exceedStandardCents) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="2">合计</td>
              <td class="num strong">{{ formatYuan(rec?.personsTotalCents ?? 0) }}</td>
              <td class="num">{{ formatYuan((rec?.persons ?? []).reduce((s, p) => s + p.subsidyBaseCents, 0)) }}</td>
              <td class="num">{{ formatYuan((rec?.persons ?? []).reduce((s, p) => s + p.withinStandardCents, 0)) }}</td>
              <td class="num">{{ formatYuan((rec?.persons ?? []).reduce((s, p) => s + p.exceedStandardCents, 0)) }}</td>
            </tr>
          </tfoot>
        </table>
      </section>

      <!-- 项目归集 -->
      <section class="print-block">
        <h2>四、项目归集</h2>
        <table class="print-table">
          <thead>
            <tr>
              <th>项目</th>
              <th>编码</th>
              <th class="num">合计</th>
              <th class="num">标准内</th>
              <th class="num">超出标准</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in rec?.projects ?? []" :key="p.projectId">
              <td>{{ p.name }}</td>
              <td class="mono">{{ ledger.projects.find((x) => x.id === p.projectId)?.code ?? '—' }}</td>
              <td class="num strong">{{ formatYuan(p.totalCents) }}</td>
              <td class="num">{{ formatYuan(p.withinStandardCents) }}</td>
              <td class="num">{{ formatYuan(p.exceedStandardCents) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 三方合计校验行 -->
      <section class="print-block">
        <h2>五、三方合计校验</h2>
        <table class="print-table print-table--verify">
          <tbody>
            <tr>
              <th>订单实付合计</th>
              <td class="num strong">{{ formatYuan(rec?.ordersTotalCents ?? 0) }}</td>
              <th>人员分摊合计</th>
              <td class="num strong">{{ formatYuan(rec?.personsTotalCents ?? 0) }}</td>
              <th>项目归集合计</th>
              <td class="num strong">{{ formatYuan(rec?.projectsTotalCents ?? 0) }}</td>
              <th>三方尾差</th>
              <td class="num strong" :class="balanced ? 'ok' : 'bad'">{{ formatYuan(rec?.diffCents ?? 0) }}</td>
            </tr>
          </tbody>
        </table>
        <p class="print-note" :class="balanced ? 'ok' : 'bad'">
          {{ balanced ? '✓ 三方恒等成立（订单合计 = 人员分摊合计 = 项目归集合计），可以报销。' : '✗ 三方尾差不为零，本单据不得作为报销凭证。' }}
        </p>
      </section>

      <!-- 规则说明 -->
      <section class="print-block">
        <h2>六、规则说明</h2>
        <ul class="print-rules">
          <li v-for="(r, i) in RULES" :key="i">{{ r }}</li>
        </ul>
        <p class="print-note">
          数据来源标签：MCP query-order 历史实付 / 套餐整体约定均分 / 人工补录；金额全程以「分」为整数单位计算，
          出口层格式化为元，最大余额法派发尾差，保证相同输入永远得到相同分摊结果。
        </p>
      </section>

      <!-- 签收区 -->
      <section class="print-block print-sign">
        <div class="print-sign__cell">报销人（垫付人）：_____________</div>
        <div class="print-sign__cell">审核人：_____________</div>
        <div class="print-sign__cell">日期：_____________</div>
      </section>
    </article>
  </div>
</template>

<style scoped>
.print-page {
  max-width: 900px;
  margin: 0 auto;
}

.print-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.print-sheet {
  background: #fff;
  border: 1px solid var(--mcd-border);
  border-radius: 8px;
  padding: 40px 44px;
  color: #111;
}

.print-sheet__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  border-bottom: 2px solid #111;
  padding-bottom: 14px;
  margin-bottom: 22px;
}

.print-sheet__head h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0.04em;
}

.print-sheet__brand {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--mcd-red);
}

.print-sheet__meta {
  font-size: 11px;
  color: #555;
  line-height: 1.7;
  text-align: right;
}

.print-block {
  margin-bottom: 22px;
  break-inside: avoid;
}

.print-block h2 {
  font-size: 14px;
  margin: 0 0 8px;
  padding-left: 8px;
  border-left: 3px solid var(--mcd-red);
}

.print-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.print-table th,
.print-table td {
  border: 1px solid #c9ccd1;
  padding: 6px 8px;
  text-align: left;
}

.print-table thead th {
  background: #f2f3f5;
  font-weight: 600;
}

.print-table tfoot td {
  background: #f7f8fa;
  font-weight: 600;
}

.print-table--kv th {
  width: 16%;
  background: #f7f8fa;
  font-weight: 600;
}

.print-table--verify th {
  background: #f7f8fa;
  width: 12%;
}

.print-table--verify td {
  width: 13%;
  font-size: 13px;
}

.print-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.print-table .strong {
  font-weight: 700;
}

.print-table .mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.print-note {
  font-size: 11px;
  color: #555;
  line-height: 1.8;
  margin: 8px 0 0;
}

.print-note.ok {
  color: var(--mcd-green);
  font-weight: 600;
}

.print-note.bad {
  color: var(--mcd-red);
  font-weight: 600;
}

.print-rules {
  margin: 0;
  padding-left: 18px;
  font-size: 11px;
  color: #444;
  line-height: 1.9;
}

.print-sign {
  display: flex;
  gap: 24px;
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px dashed #c9ccd1;
}

.print-sign__cell {
  flex: 1;
  font-size: 12px;
  color: #333;
}

@media print {
  .print-sheet {
    border: none;
    border-radius: 0;
    padding: 0;
  }

  .print-toolbar {
    display: none !important;
  }
}
</style>
