<script setup lang="ts">
import { ref, watch } from 'vue'
import ledgerStore from '@/store/ledger'
import { centsToYuan, formatYuan, yuanToCents } from '@/utils/money'

const emit = defineEmits<{
  (e: 'change'): void
}>()

/** 输入框以「元」为单位，内部一律「分」 */
const limitYuan = ref<number>(centsToYuan(ledgerStore.ledger.value?.subsidyLimitCents ?? 0))
const includeDelivery = ref<boolean>(ledgerStore.ledger.value?.includeDeliveryInSubsidy ?? false)

// 重新拉取 reconciliation 后，把服务端权威值同步回输入控件
watch(
  () => ledgerStore.ledger.value,
  (next) => {
    if (!next) return
    limitYuan.value = centsToYuan(next.subsidyLimitCents)
    includeDelivery.value = next.includeDeliveryInSubsidy
  },
)

async function onLimitChange(value: number | undefined) {
  const cents = yuanToCents(value)
  limitYuan.value = centsToYuan(cents)
  if (cents === (ledgerStore.ledger.value?.subsidyLimitCents ?? 0)) return
  await ledgerStore.updateSubsidyRule(cents, includeDelivery.value)
  emit('change')
}

async function onIncludeDeliveryChange(value: boolean | string | number) {
  const next = value === true
  if (next === (ledgerStore.ledger.value?.includeDeliveryInSubsidy ?? false)) return
  await ledgerStore.updateSubsidyRule(yuanToCents(limitYuan.value), next)
  emit('change')
}
</script>

<template>
  <div class="mcd-card rule-panel">
    <div class="mcd-section__head" style="margin-bottom: 12px">
      <div class="mcd-section__title">
        <el-icon><Money /></el-icon>
        <span>餐补规则</span>
      </div>
      <div class="mcd-spacer"></div>
      <el-tag size="small" type="info" effect="plain">毫秒级联动</el-tag>
    </div>

    <div class="rule-panel__row">
      <div class="rule-panel__label">
        <div class="rule-panel__label-title">餐补上限（元 / 人）</div>
        <div class="mcd-muted">同一人同一餐次共用一个上限</div>
      </div>
      <el-input-number
        v-model="limitYuan"
        :min="0"
        :max="100000"
        :precision="2"
        :step="1"
        :controls="false"
        size="default"
        style="width: 110px"
        @change="onLimitChange"
      >
        <template #prefix>¥</template>
      </el-input-number>
    </div>

    <div class="rule-panel__row">
      <div class="rule-panel__label">
        <div class="rule-panel__label-title">餐补含配送费</div>
        <div class="mcd-muted">开启后餐补基数计入外送打包费</div>
      </div>
      <el-switch
        v-model="includeDelivery"
        inline-prompt
        active-text="含"
        inactive-text="不含"
        @change="onIncludeDeliveryChange"
      />
    </div>

    <el-divider style="margin: 12px 0" />

    <div class="rule-panel__summary">
      <div class="rule-panel__summary-row">
        <span class="mcd-muted">当前上限</span>
        <b class="money">{{ formatYuan(ledgerStore.ledger.value?.subsidyLimitCents ?? 0) }} / 人</b>
      </div>
      <div class="rule-panel__summary-row">
        <span class="mcd-muted">配送费口径</span>
        <b>{{ ledgerStore.ledger.value?.includeDeliveryInSubsidy ? '计入餐补基数' : '不计入餐补基数' }}</b>
      </div>
      <div class="rule-panel__summary-row">
        <span class="mcd-muted">计算规则</span>
        <span class="mcd-muted" style="text-align: right">
          标准内 = min(餐补基数, 上限)<br />超出标准 = 个人合计 − 标准内
        </span>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="rule-panel__note">
      <template #title>
        <span class="mcd-muted">
          先在订单内按各人餐品小计分摊优惠与配送费，再跨单汇总套一次上限；金额全程整数分，最大余额法消尾差。
        </span>
      </template>
    </el-alert>
  </div>
</template>

<style scoped>
.rule-panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
}

.rule-panel__label-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}

.rule-panel__summary-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 0;
  font-size: 13px;
}

.rule-panel__note {
  margin-top: 10px;
}
</style>
