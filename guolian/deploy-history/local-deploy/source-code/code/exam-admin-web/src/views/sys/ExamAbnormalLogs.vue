<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select
          v-model="query.examId"
          placeholder="考试场次"
          clearable
          filterable
          style="width: 280px"
          @change="search"
        >
          <el-option v-for="e in examOptions" :key="e.examId" :label="e.examName" :value="e.examId" />
        </el-select>
        <el-select v-model="query.abnormalType" placeholder="异常类型" clearable style="width: 160px" @change="search">
          <el-option v-for="(label, value) in ABNORMAL_TYPES" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="examName" label="考试" min-width="200" show-overflow-tooltip />
        <el-table-column prop="jobNo" label="工号" width="120" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="异常类型" width="120">
          <template #default="{ row }">
            <el-tag :type="abnormalTagType(row.abnormalType)">
              {{ ABNORMAL_TYPES[row.abnormalType] || row.abnormalType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
        <el-table-column prop="occurTime" label="发生时间" width="170" />
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { sysApi, examApi } from '../../api'
import { ABNORMAL_TYPES } from '../../utils/dict'

const route = useRoute()

const query = reactive({
  examId: route.query.examId ? Number(route.query.examId) : undefined,
  abnormalType: '',
  pageNum: 1,
  pageSize: 20
})
const examOptions = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(false)

const ABNORMAL_TAG_TYPES = { FORCE_SUBMIT: 'danger', SWITCH_EXCEED: 'warning', KICK_OFFLINE: 'info' }
const abnormalTagType = (v) => ABNORMAL_TAG_TYPES[v] || 'info'

async function loadExams() {
  const data = await examApi.page({ pageNum: 1, pageSize: 200 })
  examOptions.value = data.list || []
}

async function loadData() {
  loading.value = true
  try {
    const data = await sysApi.examAbnormalLogs({
      examId: query.examId || undefined,
      abnormalType: query.abnormalType || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadData()
}

function reset() {
  query.examId = undefined
  query.abnormalType = ''
  search()
}

onMounted(() => {
  loadExams()
  loadData()
})
</script>
