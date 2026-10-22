<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

import planStore from '@/store/plan'
import { formatYuan } from '@/utils/money'
import type { PlanItemSelection } from '@/types/plan'

const currentName = computed(() => planStore.currentParticipant.value?.displayName ?? '')
const currentSelections = computed<PlanItemSelection[]>(() => planStore.currentSelections.value)
const perPerson = computed(() => planStore.perPersonSubtotal.value)
const totalCents = computed(() => planStore.totalCents.value)
const saving = computed(() => planStore.saving.value)
const aggregates = computed(() => planStore.aggregates.value)

const currentSubtotal = computed(() =>
  currentSelections.value.reduce((sum, s) => sum + (s.subtotalCents ?? s.unitPriceCents * s.quantity), 0),
)

async function onQtyChange(item: PlanItemSelection, qty: number) {
  const ok = await planStore.setQuantity(item.externalItemId, qty)
  if (ok && qty <= 0) ElMessage.success(`已移除 ${item.name}`)
}

async function onRemove(item: PlanItemSelection) {
  const ok = await planStore.removeSelection(item.externalItemId)
  if (ok) ElMessage.success(`已移除 ${item.name}`)
}
</script>

<template>
  <div>
    <!-- 当前成员已选 -->
    <section class="mcd-card sum-card">
      <div class="mcd-section__head">
        <div class="mcd-section__title">
          <el-icon><ShoppingCart /></el-icon>
          <span>当前成员已选</span>
        </div>
        <div class="mcd-spacer"></div>
        <span class="mcd-muted">{{ currentName || '未选择成员' }}</span>
      </div>

      <div v-if="currentSelections.length === 0" class="state-panel" style="padding: 28px 12px">
        <div class="state-panel__icon">🛒</div>
        <div class="state-panel__title">还没有选餐</div>
        <div class="state-panel__desc">在左侧菜单点击餐品，加入当前成员。</div>
      </div>

      <div v-else class="sum-list">
        <div v-for="item in currentSelections" :key="item.externalItemId" class="sum-item">
          <div class="sum-item__body">
            <span class="sum-item__name" :title="item.name">{{ item.name }}</span>
            <span class="sum-item__meta">
              单价 {{ formatYuan(item.unitPriceCents) }}
              <template v-if="item.specOptions"> · {{ item.specOptions }}</template>
            </span>
          </div>
          <el-input-number
            :model-value="item.quantity"
            :min="0"
            :max="99"
            size="small"
            :controls="false"
            style="width: 52px"
            :loading="saving"
            @change="(v: number | undefined) => onQtyChange(item, v ?? 0)"
          />
          <span class="sum-item__amount">
            {{ formatYuan(item.subtotalCents ?? item.unitPriceCents * item.quantity) }}
          </span>
          <el-button text type="danger" :icon="'Delete'" :loading="saving" @click="onRemove(item)" />
        </div>

        <div class="sum-total">
          <span>{{ currentName }} 小计</span>
          <span class="sum-total__value">{{ formatYuan(currentSubtotal) }}</span>
        </div>
      </div>
    </section>

    <!-- 全员选餐汇总 -->
    <section class="mcd-card sum-card">
      <div class="mcd-section__head">
        <div class="mcd-section__title">
          <el-icon><User /></el-icon>
          <span>全员选餐汇总</span>
        </div>
        <div class="mcd-spacer"></div>
        <span class="mcd-muted">点击行可切换当前成员</span>
      </div>

      <div v-if="perPerson.length === 0" class="state-panel" style="padding: 24px 12px">
        <div class="state-panel__title">暂无成员</div>
      </div>

      <div v-else>
        <div
          v-for="p in perPerson"
          :key="p.participantId"
          class="person-row"
          :style="p.participantId === planStore.currentParticipantId.value ? { color: 'var(--mcd-red)' } : undefined"
          @click="planStore.selectParticipant(p.participantId)"
        >
          <span class="person-row__name">{{ p.displayName }}</span>
          <span class="person-row__count">{{ p.count }} 件</span>
          <span class="person-row__amount">{{ formatYuan(p.subtotalCents) }}</span>
        </div>

        <div class="sum-total">
          <span>全员合计（{{ perPerson.length }} 人）</span>
          <span class="sum-total__value">{{ formatYuan(totalCents) }}</span>
        </div>
      </div>
    </section>

    <!-- 按餐品聚合 -->
    <section v-if="aggregates.length > 0" class="mcd-card sum-card">
      <div class="mcd-section__head">
        <div class="mcd-section__title">
          <el-icon><Files /></el-icon>
          <span>按餐品聚合</span>
        </div>
      </div>
      <div v-for="a in aggregates" :key="a.externalItemId" class="person-row">
        <span class="person-row__name">{{ a.name }}</span>
        <span class="person-row__count">× {{ a.totalQuantity }}</span>
        <span class="person-row__amount">{{ formatYuan(a.subtotalCents) }}</span>
      </div>
    </section>
  </div>
</template>
