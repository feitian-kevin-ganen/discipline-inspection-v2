<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="操作人" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.module" placeholder="模块" clearable style="width: 140px" @change="search">
          <el-option v-for="m in MODULES" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="orgName" label="单位" min-width="160" show-overflow-tooltip />
        <el-table-column label="模块" width="100">
          <template #default="{ row }">
            <el-tag>{{ moduleName(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="targetDesc" label="对象" min-width="180" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="operateTime" label="操作时间" width="170" />
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
import { sysApi } from '../../api'

const MODULES = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '人员' },
  { value: 'LIB', label: '资料' },
  { value: 'QUES', label: '题库' },
  { value: 'PAPER', label: '试卷' },
  { value: 'EXAM', label: '考试' },
  { value: 'MARK', label: '阅卷' },
  { value: 'SYS', label: '系统' }
]
const moduleName = (v) => (MODULES.find((m) => m.value === v) || {}).label || v

const query = reactive({ keyword: '', module: '', pageNum: 1, pageSize: 20 })
const timeRange = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const data = await sysApi.operationLogs({
      keyword: query.keyword || undefined,
      module: query.module || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
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
  query.keyword = ''
  query.module = ''
  timeRange.value = []
  search()
}

onMounted(loadData)
</script>
