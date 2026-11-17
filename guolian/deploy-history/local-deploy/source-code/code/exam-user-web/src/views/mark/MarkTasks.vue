<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="card-title">我的阅卷任务</span>
          <el-radio-group v-model="status" @change="onStatusChange">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="PENDING">待领取</el-radio-button>
            <el-radio-button value="DOING">阅卷中</el-radio-button>
            <el-radio-button value="DONE">已完成</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="题目" min-width="240">
          <template #default="{ row }">
            <div>
              <el-tag size="small" class="mr8">{{ QUES_TYPE_NAME[row.quesType] || row.quesType }}</el-tag>
              <span>第 {{ row.sheetQuesSeq }} 题</span>
            </div>
            <div class="text-secondary ellipsis" :title="row.stemText">{{ row.stemText }}</div>
          </template>
        </el-table-column>
        <el-table-column label="阅卷进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.totalSheets ? Math.round((row.markedSheets / row.totalSheets) * 100) : 0"
              :stroke-width="10"
              :status="row.status === 'DONE' ? 'success' : undefined"
            />
            <span class="text-secondary">{{ row.markedSheets }}/{{ row.totalSheets }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="{ PENDING: 'info', DOING: 'warning', DONE: 'success' }[row.status]">
              {{ { PENDING: '待领取', DOING: '阅卷中', DONE: '已完成' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="完成期限" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.myRole === 'UNCLAIMED'"
              type="warning"
              size="small"
              :loading="claimingId === row.taskId"
              @click="onClaim(row)"
            >
              领取任务
            </el-button>
            <el-button v-else type="primary" size="small" @click="$router.push(`/mark/${row.taskId}`)">
              {{ row.status === 'DONE' ? '查看' : '开始阅卷' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无阅卷任务" /></template>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMarkTasks, claimMarkTask } from '@/api/mark'
import { QUES_TYPE_NAME } from '@/utils'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const status = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const claimingId = ref(null)

function onStatusChange() {
  pageNum.value = 1
  load()
}

async function onClaim(row) {
  claimingId.value = row.taskId
  try {
    await claimMarkTask(row.taskId)
    ElMessage.success('领取成功')
    router.push(`/mark/${row.taskId}`)
  } finally {
    claimingId.value = null
  }
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (status.value) params.status = status.value
    const res = await getMarkTasks(params)
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
.mr8 {
  margin-right: 8px;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
