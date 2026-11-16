<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.examId" placeholder="考试场次" clearable filterable style="width: 240px" @change="handleSearch">
          <el-option v-for="e in examOptions" :key="e.examId" :label="e.examName" :value="e.examId" />
        </el-select>
        <el-select v-model="query.status" placeholder="任务状态" clearable style="width: 140px" @change="handleSearch">
          <el-option v-for="(v, k) in MARK_TASK_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sheetQuesSeq" label="题目序号" width="90" align="center" />
        <el-table-column label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="QUES_TYPES[row.quesType]?.type">{{ QUES_TYPES[row.quesType]?.label || row.quesType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stemText" label="题干" min-width="220" show-overflow-tooltip />
        <el-table-column prop="totalSheets" label="答卷总数" width="90" align="center" />
        <el-table-column prop="markedSheets" label="已阅" width="80" align="center" />
        <el-table-column label="进度" width="160" align="center">
          <template #default="{ row }">
            <el-progress :percentage="progressOf(row)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="我的角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.myRole === 'OWNER' ? 'primary' : 'info'">
              {{ row.myRole === 'OWNER' ? '负责人' : '未领取' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="MARK_TASK_STATUS[row.status]?.type">{{ MARK_TASK_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="primary" link @click="handleClaim(row)">领取任务</el-button>
            <el-button v-else-if="row.status === 'DOING'" type="primary" link @click="goMark(row)">进入阅卷</el-button>
            <el-button v-else type="primary" link @click="goMark(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { markApi } from '../../api'
import { MARK_TASK_STATUS, QUES_TYPES } from '../../utils/dict'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const examOptions = ref([])

const query = reactive({
  examId: null,
  status: null,
  pageNum: 1,
  pageSize: 10
})

const progressOf = (row) => (row.totalSheets ? Math.round((row.markedSheets / row.totalSheets) * 100) : 0)

async function loadExams() {
  try {
    // 阅卷教师无考试管理权限时下拉置空即可，静默降级不弹错误提示
    const data = await request.get('/admin/exams', { params: { pageNum: 1, pageSize: 200 }, __silent: true })
    examOptions.value = data.list || []
  } catch (e) {
    examOptions.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await markApi.taskPage({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.examId = null
  query.status = null
  query.pageNum = 1
  loadList()
}

async function handleClaim(row) {
  await markApi.claim(row.taskId)
  ElMessage.success('领取成功')
  loadList()
}

function goMark(row) {
  router.push(`/mark/tasks/${row.taskId}`)
}

onMounted(() => {
  loadExams()
  loadList()
})
</script>
