<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input
          v-model="query.title"
          placeholder="业务标题"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-select v-model="query.status" placeholder="流程状态" clearable style="width: 140px" @change="loadList">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="title" label="业务标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">{{ bizLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="procDefName" label="流程" width="150" show-overflow-tooltip />
        <el-table-column label="发起人" width="120">
          <template #default="{ row }">{{ row.startUserName || row.startUserId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="FLOW_STATUS[row.status]?.type">{{ FLOW_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openProgress(row)">流程进度</el-button>
            <el-button v-if="row.status === 'RUNNING'" type="danger" link @click="stopRow(row)">终止</el-button>
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

    <el-drawer v-model="progressVisible" title="流程进度" size="640px">
      <FlowDetailPanel v-if="current.procInstId" :proc-inst-id="current.procInstId" diagram-height="320px" />
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { FLOW_STATUS, STATUS_OPTIONS, bizLabel } from '../../utils/workflow'
import FlowDetailPanel from '../../components/workflow/FlowDetailPanel.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ title: '', status: '', pageNum: 1, pageSize: 10 })
const progressVisible = ref(false)
const current = ref({})

async function loadList() {
  loading.value = true
  try {
    const data = await workflowApi.allInstances({
      title: query.title.trim() || undefined,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.title = ''
  query.status = ''
  query.pageNum = 1
  loadList()
}

function openProgress(row) {
  current.value = row
  progressVisible.value = true
}

async function stopRow(row) {
  try {
    const { value } = await ElMessageBox.prompt('终止后流程不可恢复，请填写终止原因', '终止流程', {
      confirmButtonText: '确认终止',
      cancelButtonText: '取消',
      inputPlaceholder: '终止原因（可空）'
    })
    await workflowApi.stopInstance(row.procInstId, { comment: (value || '').trim() })
    ElMessage.success('流程已终止')
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') throw e
  }
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
