<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="工号/姓名" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.result" placeholder="登录结果" clearable style="width: 140px" @change="search">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
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
        <el-table-column prop="jobNo" label="工号" width="120" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="orgName" label="单位" min-width="180" show-overflow-tooltip />
        <el-table-column prop="terminal" label="终端" width="100" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.loginTime) }}</template>
        </el-table-column>
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
import { fmtTime } from '../../utils/format'

const query = reactive({ keyword: '', result: '', pageNum: 1, pageSize: 20 })
const timeRange = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const data = await sysApi.loginLogs({
      keyword: query.keyword || undefined,
      result: query.result || undefined,
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
  query.result = ''
  timeRange.value = []
  search()
}

onMounted(loadData)
</script>
