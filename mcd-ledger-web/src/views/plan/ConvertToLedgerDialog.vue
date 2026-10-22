<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { ledgerApi } from '@/api/ledger'
import { formatYuan } from '@/utils/money'

/**
 * 转出餐账（T05 #11 唯一正式入口）：人员 1:1 + 项目预填 + 携带官方核价基线，
 * 转出后进入对账工作台，事后即可做「预算 vs 实际」差异对照。
 */
const props = defineProps<{ planId: number; expectedTotalCents?: number | null; personCount: number }>()
const visible = defineModel<boolean>('visible', { default: false })

const router = useRouter()
const converting = ref(false)

const hasBaseline = computed(() => props.expectedTotalCents != null)

async function convert() {
  converting.value = true
  try {
    const ledgerId = await ledgerApi.createFromPlan(props.planId)
    ElMessage.success('已转出餐账，可继续选入订单做差异对照')
    visible.value = false
    router.push({ name: 'ledger-workbench', params: { id: String(ledgerId) } })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '转出失败')
  } finally {
    converting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="转出为餐账" width="440px">
    <div class="convert">
      <p>将本方案的 <b>{{ props.personCount }}</b> 位成员与项目绑定 1:1 带入新餐账。</p>
      <p v-if="hasBaseline" class="convert__ok">
        ✅ 已携带官方核价基线 <b>{{ formatYuan(props.expectedTotalCents) }}</b>，事后可做「预算 vs 实际」差异对照。
      </p>
      <p v-else class="convert__warn">
        ⚠️ 尚未核价，转出后<b>没有预算基线</b>，差异对照会提示「无官方核价基线」。建议先在上方点「官方核价」。
      </p>
      <p class="mcd-muted">转出不会下单、不会调用任何写接口（零写操作）。</p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="converting" @click="convert">确认转出</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.convert p { margin: 6px 0; font-size: 13px; line-height: 1.7; }
.convert__ok { color: #67c23a; }
.convert__warn { color: #e6a23c; }
</style>
