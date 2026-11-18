<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="考试名称关键字" clearable style="width: 220px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-select v-model="query.status" placeholder="考试状态" clearable style="width: 160px" @change="onSearch">
          <el-option v-for="(v, k) in EXAM_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开考开始日期"
          end-placeholder="开考结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="$router.push('/exams/edit')">创建考试</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="paperName" label="试卷" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="PAPER_TYPES[row.paperType]" :type="PAPER_TYPES[row.paperType].type">{{ PAPER_TYPES[row.paperType].label }}</el-tag>
            <span v-else>{{ row.paperType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开考时间" width="165" />
        <el-table-column prop="endTime" label="结束时间" width="165" />
        <el-table-column label="时长" width="90" align="center">
          <template #default="{ row }">{{ row.durationMin }} 分钟</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="EXAM_STATUS[row.status]" :type="EXAM_STATUS[row.status].type">{{ EXAM_STATUS[row.status].label }}</el-tag>
            <span v-else>{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="candidateCount" label="名单数" width="80" align="center" />
        <el-table-column prop="attendedCount" label="实考" width="80" align="center" />
        <el-table-column prop="createdOrgName" label="创建单位" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/exams/${row.examId}`)">详情</el-button>
            <el-button v-if="row.status === 'NOT_STARTED'" link type="primary" @click="$router.push(`/exams/edit/${row.examId}`)">编辑</el-button>
            <el-button v-if="row.status === 'NOT_STARTED'" link type="success" @click="onAction(row, 'START')">开始</el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" link type="warning" @click="onAction(row, 'END')">结束</el-button>
            <el-button v-if="row.status === 'NOT_STARTED'" link type="warning" @click="onAction(row, 'CANCEL')">取消</el-button>
            <el-button v-if="row.status === 'NOT_STARTED' && !row.candidateCount" link type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examApi } from '../../api'
import { EXAM_STATUS, PAPER_TYPES } from '../../utils/dict'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      // 后端 @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")，需带时间部分
      params.startDate = `${dateRange.value[0]} 00:00:00`
      params.endDate = `${dateRange.value[1]} 23:59:59`
    }
    const data = await examApi.page(params)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.status = ''
  dateRange.value = []
  onSearch()
}

const ACTION_TIPS = {
  START: { title: '开始考试', msg: '确认提前开考？考生将可进入本场考试。' },
  END: { title: '结束考试', msg: '确认提前结束本场考试？将强制收取未交卷答卷。' },
  CANCEL: { title: '取消考试', msg: '确认取消本场考试？取消后不可恢复。' }
}

async function onAction(row, action) {
  const tip = ACTION_TIPS[action]
  await ElMessageBox.confirm(tip.msg, tip.title, { type: 'warning' })
  await examApi.setStatus(row.examId, action)
  ElMessage.success('操作成功')
  load()
}

async function onRemove(row) {
  await ElMessageBox.confirm(`确认删除考试「${row.examName}」？删除后不可恢复。`, '删除考试', { type: 'warning' })
  await examApi.remove(row.examId)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
