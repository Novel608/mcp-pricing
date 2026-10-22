<script setup lang="ts">
import { computed } from 'vue'
import ledgerStore, { type LedgerMode } from '@/store/ledger'

const props = defineProps<{ size?: 'default' | 'small' }>()

const mode = computed<LedgerMode>(() => ledgerStore.mode.value)

const options = computed<Array<{ value: LedgerMode; label: string; icon: string }>>(() => [
  { value: 'live', label: '真实 MCP 账单', icon: '🟢' },
  { value: 'demo', label: '沙盒经典加班餐', icon: '🧪' },
])

async function onChange(value: LedgerMode | undefined) {
  if (!value || value === mode.value) return
  await ledgerStore.switchMode(value)
}
</script>

<template>
  <el-radio-group
    :model-value="mode"
    :size="props.size ?? 'default'"
    class="mode-switch"
    @change="onChange"
  >
    <el-radio-button v-for="opt in options" :key="opt.value" :value="opt.value">
      <span class="mode-switch__label">
        <span class="mode-switch__icon">{{ opt.icon }}</span>
        <span>{{ opt.label }}</span>
      </span>
    </el-radio-button>
  </el-radio-group>
</template>

<style scoped>
.mode-switch__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.mode-switch__icon {
  font-size: 12px;
  line-height: 1;
}
</style>
