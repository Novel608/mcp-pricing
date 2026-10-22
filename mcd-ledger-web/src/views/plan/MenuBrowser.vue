<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import planStore from '@/store/plan'
import { formatSignedYuan, formatYuan } from '@/utils/money'
import { isDiscounted } from '@/utils/menu'
import type { MealChoice, MealRound, MenuMeal } from '@/types/plan'

const categories = computed(() => planStore.menu.value.categories)
const menuLoading = computed(() => planStore.menuLoading.value)
const menuError = computed(() => planStore.menuError.value)
const saving = computed(() => planStore.saving.value)
const currentName = computed(() => planStore.currentParticipant.value?.displayName ?? '')

const activeCat = ref(0)
const meals = computed<MenuMeal[]>(() => categories.value[activeCat.value]?.meals ?? [])

function onCatChange(index: number) {
  activeCat.value = index
}

/* ---------- 详情抽屉 ---------- */
const detailVisible = computed({
  get: () => planStore.detailVisible.value,
  set: (v: boolean) => !v && planStore.closeMealDetail(),
})
const activeMeal = computed(() => planStore.activeMeal.value)
const detail = computed(() => planStore.mealDetail.value)
const detailLoading = computed(() => planStore.detailLoading.value)
const detailError = computed(() => planStore.detailError.value)

async function openDetail(meal: MenuMeal) {
  await planStore.openMealDetail(meal)
}

/** diffPrice 形如 "+ ¥2.5" / "- ¥0.5" / "+ ¥0"，只做展示着色 */
function diffClass(diff?: string): string {
  if (!diff) return ''
  if (diff.trim().startsWith('-')) return 'choice-row__diff--down'
  if (diff.trim().startsWith('+')) return 'choice-row__diff--up'
  return ''
}

function choiceMeta(c: MealChoice): string {
  const parts: string[] = []
  if (c.isDefault === 1) parts.push('默认')
  if (c.supportModify) parts.push('可调整')
  return parts.join(' · ')
}

async function addToCurrent(meal: MenuMeal) {
  if (!currentName.value) {
    ElMessage.warning('请先添加或选择一位成员')
    return
  }
  const ok = await planStore.addSelection(meal, 1)
  if (ok) {
    ElMessage.success(`已为「${currentName}」加入 ${meal.name}`)
    planStore.closeMealDetail()
  }
}
</script>

<template>
  <section class="mcd-card">
    <div class="mcd-section__head">
      <div class="mcd-section__title">
        <el-icon><Goods /></el-icon>
        <span>菜单浏览</span>
      </div>
      <div class="mcd-spacer"></div>
      <span class="mcd-muted">点击餐品查看套餐组成，可加入当前成员</span>
    </div>

    <div v-loading="menuLoading">
      <!-- 菜单加载失败 -->
      <div v-if="menuError" class="state-panel">
        <div class="state-panel__icon">🍽️</div>
        <div class="state-panel__title">{{ menuError }}</div>
        <div class="state-panel__desc">
          <template v-if="menuError.includes('MCD_MCP_TOKEN') || menuError.includes('Token')">
            请在服务端配置 <b>MCD_MCP_TOKEN</b> 后重启；也可先用沙盒方案体验。
          </template>
          <template v-else-if="menuError.includes('后端')">请先启动 mcd-ledger-server（localhost:8080）。</template>
          <template v-else>请稍后重试。</template>
        </div>
        <el-button style="margin-top: 12px" @click="planStore.loadMenu(planStore.plan.value?.storeId ?? '')">
          重新拉取
        </el-button>
      </div>

      <template v-else-if="categories.length > 0">
        <div class="menu-cats">
          <button
            v-for="(cat, i) in categories"
            :key="cat.name"
            type="button"
            class="menu-cat"
            :class="{ 'menu-cat--active': i === activeCat }"
            @click="onCatChange(i)"
          >
            {{ cat.name }}
            <span style="opacity: 0.7">({{ cat.meals.length }})</span>
          </button>
        </div>

        <div class="menu-grid">
          <button
            v-for="meal in meals"
            :key="meal.code"
            type="button"
            class="meal-card"
            @click="openDetail(meal)"
          >
            <div class="meal-card__img" :style="meal.image ? { backgroundImage: `url(${meal.image})` } : undefined">
              <span v-if="!meal.image">🍔</span>
            </div>
            <div class="meal-card__body">
              <div class="meal-card__name" :title="meal.name">{{ meal.name }}</div>
              <div class="meal-card__tags">
                <span v-if="meal.discountType" class="meal-card__tag" style="color: var(--mcd-red)">
                  {{ meal.discountType }}
                </span>
                <span v-for="t in (meal.tags ?? []).slice(0, 2)" :key="t" class="meal-card__tag">{{ t }}</span>
              </div>
              <div class="meal-card__price">
                <span class="meal-card__now">{{ formatYuan(meal.currentPriceCents) }}</span>
                <span v-if="isDiscounted(meal)" class="meal-card__origin">
                  {{ formatYuan(meal.originalPriceCents) }}
                </span>
              </div>
            </div>
          </button>
        </div>
      </template>

      <div v-else class="state-panel">
        <div class="state-panel__icon">📭</div>
        <div class="state-panel__title">暂无菜单数据</div>
        <div class="state-panel__desc">方案未提供门店编码，或后端菜单接口暂不可用。</div>
      </div>
    </div>

    <!-- 餐品详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="activeMeal?.name ?? '餐品详情'"
      size="420px"
      append-to-body
      class="meal-detail"
    >
      <div v-loading="detailLoading">
        <div v-if="detailError" class="state-panel">
          <div class="state-panel__icon">⚠️</div>
          <div class="state-panel__title">{{ detailError }}</div>
        </div>

        <template v-else-if="detail">
          <div class="meal-detail__head">
            <div
              class="meal-detail__thumb"
              :style="detail.image || activeMeal?.image ? { backgroundImage: `url(${detail.image ?? activeMeal?.image})` } : undefined"
            >
              <span v-if="!(detail.image ?? activeMeal?.image)">🍔</span>
            </div>
            <div>
              <div class="meal-detail__title">{{ detail.name }}</div>
              <div class="meal-detail__price">
                现价 <b class="money">{{ formatYuan(activeMeal?.currentPriceCents ?? 0) }}</b>
                <span v-if="activeMeal && isDiscounted(activeMeal)">
                  ｜原价 <span class="money">{{ formatYuan(activeMeal.originalPriceCents) }}</span>
                </span>
              </div>
              <div class="mcd-muted">餐品编码 {{ detail.code }}</div>
            </div>
          </div>

          <template v-if="(detail.rounds ?? []).length > 0">
            <div v-for="(round, ri) in detail.rounds" :key="round.id ?? ri" class="round-block">
              <div class="round-block__title">
                <span>{{ round.name }}</span>
                <span class="round-block__hint">
                  <template v-if="round.maxQuantity && round.maxQuantity > 0">
                    可选 {{ round.minQuantity ?? 0 }}–{{ round.maxQuantity }}
                  </template>
                  <template v-else>多选</template>
                </span>
              </div>
              <div v-for="(c, ci) in round.choices ?? []" :key="c.code ?? ci" class="choice-row">
                <span v-if="c.isDefault === 1" style="color: var(--mcd-red)">●</span>
                <span v-else style="color: #d3d6db">○</span>
                <span class="choice-row__name">{{ c.name }}</span>
                <span v-if="choiceMeta(c)" class="mcd-muted">{{ choiceMeta(c) }}</span>
                <span class="choice-row__diff" :class="diffClass(c.diffPrice)">{{ c.diffPrice ?? '' }}</span>
              </div>
              <div
                v-for="(c, ci) in (round.choices ?? []).filter((x) => x.modification?.items?.length)"
                :key="'m' + ci"
                class="mod-values"
              >
                <span
                  v-for="(item, ii) in c.modification!.items!"
                  :key="ii"
                  class="mod-value"
                  :title="`${c.name} · 可选 ${item.minValues ?? 0}–${item.maxValues ?? 0} 项`"
                >
                  {{ c.name }}：
                  <span v-for="v in item.values ?? []" :key="v.code">{{ v.name }}</span>
                </span>
              </div>
            </div>
          </template>
          <div v-else class="mcd-muted" style="padding: 8px 0">该餐品为单品，无套餐组成信息。</div>

          <div class="plan-actionbar" style="position: static; margin-top: 16px">
            <span class="mcd-muted">加入「{{ currentName || '未选择成员' }}」</span>
            <div class="mcd-spacer"></div>
            <el-button
              type="primary"
              :icon="'Plus'"
              :loading="saving"
              :disabled="!currentName"
              @click="addToCurrent(activeMeal!)"
            >
              加入当前成员
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </section>
</template>
