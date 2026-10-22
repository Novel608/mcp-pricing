<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import planStore from '@/store/plan'
import type { PlanParticipant } from '@/types/plan'

const participants = computed(() => planStore.participants.value)
const currentId = computed(() => planStore.currentParticipantId.value)
const selections = computed(() => planStore.selections.value)
const projects = computed(() => planStore.plan.value?.projects ?? [])
const adding = computed(() => planStore.addingParticipant.value)

const countOf = (pid: number) => selections.value.filter((s) => s.participantId === pid).length

function projectLabel(p: PlanParticipant): string {
  if (p.projectId == null || p.projectId <= 0) return '未绑定项目'
  const hit = projects.value.find((x) => x.id === p.projectId)
  return hit ? (hit.code ? `${hit.name}（${hit.code}）` : hit.name) : `项目 #${p.projectId}`
}

function onSelect(pid: number) {
  planStore.selectParticipant(pid)
}

/* ---------- 添加成员 ---------- */
const addVisible = ref(false)
const newName = ref('')
const newProjectId = ref<number | undefined>(undefined)

function openAdd() {
  newName.value = ''
  newProjectId.value = undefined
  addVisible.value = true
}

async function confirmAdd() {
  const name = newName.value.trim()
  if (!name) {
    ElMessage.warning('请填写成员姓名')
    return
  }
  const ok = await planStore.addParticipant({ displayName: name, projectId: newProjectId.value ?? null })
  if (ok) {
    ElMessage.success(`已添加成员「${name}」`)
    addVisible.value = false
    // 自动切到新成员，便于立即选餐
    const added = planStore.participants.value.find((p) => p.displayName === name)
    if (added) planStore.selectParticipant(added.id)
  }
}
</script>

<template>
  <section class="member-bar" aria-label="选餐成员">
    <button
      v-for="p in participants"
      :key="p.id"
      type="button"
      class="member-chip"
      :class="{ 'member-chip--active': p.id === currentId }"
      @click="onSelect(p.id)"
    >
      <span class="member-chip__avatar">{{ p.displayName?.slice(0, 1) || '?' }}</span>
      <span class="member-chip__body">
        <span class="member-chip__name">{{ p.displayName }}</span>
        <span class="member-chip__sub">{{ projectLabel(p) }}</span>
      </span>
      <span class="member-chip__badge">{{ countOf(p.id) }} 项</span>
    </button>

    <div class="member-bar__add">
      <el-button size="default" :icon="'Plus'" @click="openAdd">添加成员</el-button>
    </div>

    <div class="mcd-spacer"></div>
    <span class="mcd-muted">当前为「{{ participants.find((p) => p.id === currentId)?.displayName ?? '—' }}」选餐</span>

    <el-dialog v-model="addVisible" title="添加选餐成员" width="420px" align-center append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="成员姓名">
          <el-input v-model="newName" placeholder="如：林工" maxlength="20" />
        </el-form-item>
        <el-form-item label="所属项目（可选）">
          <el-select
            v-model="newProjectId"
            placeholder="暂不绑定"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="pr in projects"
              :key="pr.id"
              :label="pr.code ? `${pr.name}（${pr.code}）` : pr.name"
              :value="pr.id"
            />
          </el-select>
          <div class="mcd-muted" v-if="projects.length === 0">
            方案未提供项目目录，留空即可；转出餐账时再绑定项目。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="confirmAdd">添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>
