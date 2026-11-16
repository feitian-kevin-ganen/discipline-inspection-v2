<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="card-title">考试中心</span>
          <el-radio-group v-model="type" @change="onTypeChange">
            <el-radio-button value="TODO">待考</el-radio-button>
            <el-radio-button value="HISTORY">历史考试</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="examName" label="考试名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column label="时长" width="80" align="center">
          <template #default="{ row }">{{ row.durationMin }}分钟</template>
        </el-table-column>
        <el-table-column label="答卷状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="sheetStatusTag(row.mySheetStatus)" size="small">
              {{ sheetStatusText(row.mySheetStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成绩" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="scoreVisible(row)" type="success" size="small">已发布</el-tag>
            <span v-else-if="row.scorePublished" class="text-secondary">未参考</span>
            <span v-else class="text-secondary">未发布</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.canEnter"
              type="primary"
              size="small"
              @click="$router.push(`/exam/notice/${row.examId}`)"
            >
              {{ row.mySheetStatus === 'IN_EXAM' ? '继续答题' : '进入考试' }}
            </el-button>
            <el-tooltip v-else-if="row.cannotEnterReason" :content="row.cannotEnterReason">
              <el-button size="small" disabled>不可进入</el-button>
            </el-tooltip>
            <el-button
              v-if="scoreVisible(row)"
              size="small"
              type="success"
              plain
              @click="$router.push(`/score/${row.examId}`)"
            >
              查看成绩
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="type === 'TODO' ? '暂无待考考试' : '暂无历史考试'" />
        </template>
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
import { getMyExams } from '@/api/take'

const loading = ref(false)
const type = ref('TODO')
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

const SHEET_STATUS = {
  NOT_ENTERED: '未进入',
  IN_EXAM: '答题中',
  SUBMITTED: '已交卷',
  FORCED: '强制交卷',
  ABSENT: '缺考'
}
const sheetStatusText = (s) => SHEET_STATUS[s] || s || '—'
const sheetStatusTag = (s) =>
  ({ NOT_ENTERED: 'info', IN_EXAM: 'warning', SUBMITTED: 'success', FORCED: 'danger', ABSENT: 'danger' }[s] || 'info')

// 成绩是否可查看：场次已发布且本人已交卷（缺考/未进入场次发布后也无本人成绩，与详情接口口径一致）
const scoreVisible = (row) => row.scorePublished && ['SUBMITTED', 'FORCED'].includes(row.mySheetStatus)

function onTypeChange() {
  pageNum.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await getMyExams({ type: type.value, pageNum: pageNum.value, pageSize })
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
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
