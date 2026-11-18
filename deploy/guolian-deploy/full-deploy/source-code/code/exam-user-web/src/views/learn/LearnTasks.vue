<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="card-title">我的学习任务</span>
          <el-radio-group v-model="status" @change="load">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="TODO">待完成</el-radio-button>
            <el-radio-button value="DONE">已完成</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" row-key="taskId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="materials">
              <div v-for="m in row.materials || []" :key="m.materialId" class="material-row">
                <el-tag size="small" :type="m.materialType === 'VIDEO' ? 'danger' : m.materialType === 'DOC' ? 'primary' : 'success'">
                  {{ MATERIAL_TYPE_NAME[m.materialType] || m.materialType }}
                </el-tag>
                <span class="m-name">{{ m.title }}</span>
                <span class="text-secondary" v-if="m.durationSec">标称 {{ fmtDuration(m.durationSec) }}</span>
                <el-progress :percentage="Math.round(m.myProgressPct ?? 0)" :stroke-width="8" style="width: 220px" />
                <el-button size="small" type="primary" plain @click="$router.push(`/learn/material/${m.materialId}`)">
                  去学习
                </el-button>
              </div>
              <el-empty v-if="!(row.materials || []).length" description="任务资料为空" :image-size="50" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="资料数" width="90" align="center">
          <template #default="{ row }">{{ row.materials?.length ?? row.materialIds?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="要求有效时长" width="120" align="center">
          <template #default="{ row }">{{ fmtDuration(row.requiredSec) }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="完成期限" width="170" />
        <el-table-column label="完成度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="taskPct(row)" :status="row.myStatus === 'DONE' ? 'success' : undefined" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="{ DONE: 'success', TODO: 'warning', EXPIRED: 'danger' }[row.myStatus] || 'info'">
              {{ { DONE: '已完成', TODO: '待完成', EXPIRED: '已逾期' }[row.myStatus] || row.myStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="completedTime" label="完成时间" width="170">
          <template #default="{ row }">{{ row.completedTime || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getLearnTasks } from '@/api/learn'
import { fmtDuration, MATERIAL_TYPE_NAME } from '@/utils'

const loading = ref(false)
const list = ref([])
const status = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

function taskPct(row) {
  const ms = row.materials || []
  if (!ms.length) return row.myStatus === 'DONE' ? 100 : 0
  const sum = ms.reduce((acc, m) => acc + (m.myProgressPct ?? 0), 0)
  return Math.round(sum / ms.length)
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (status.value) params.status = status.value
    const res = await getLearnTasks(params)
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
}
.materials {
  padding: 8px 24px;
}
.material-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.m-name {
  min-width: 240px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
