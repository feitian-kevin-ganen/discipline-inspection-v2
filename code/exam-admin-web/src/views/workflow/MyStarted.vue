<template>
  <div class="page-container">
    <div class="page-card">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="title" label="业务标题" min-width="260" show-overflow-tooltip />
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">{{ bizLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="procDefKey" label="流程标识" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="FLOW_STATUS[row.status]?.type">{{ FLOW_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看进度</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="暂无我发起的流程" />
    </div>

    <el-drawer v-model="detailVisible" title="流程进度" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务标题">{{ detail.instance?.title }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ bizLabel(detail.instance?.bizType) }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">
            <el-tag :type="FLOW_STATUS[detail.instance?.status]?.type">{{ FLOW_STATUS[detail.instance?.status]?.label || detail.instance?.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmtTime(detail.instance?.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmtTime(detail.instance?.endTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-timeline style="margin-top: 20px">
          <el-timeline-item
            v-for="(h, i) in detail.history || []"
            :key="i"
            :timestamp="fmtTime(h.endTime || h.startTime)"
            :type="h.endTime ? 'success' : 'primary'"
          >
            <div class="node-title">{{ h.taskName }}</div>
            <div class="node-meta">办理人：{{ h.assignee || '-' }}</div>
            <div v-if="h.comment" class="node-comment">意见：{{ h.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'

const FLOW_STATUS = {
  RUNNING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  STOPPED: { label: '已终止', type: 'info' }
}

const BIZ_LABELS = {
  ques_item_audit: '题目审核',
  material_audit: '资料审核'
}
const bizLabel = (b) => BIZ_LABELS[b] || b || '-'

const loading = ref(false)
const list = ref([])
const detailVisible = ref(false)
const detail = ref(null)

async function loadList() {
  loading.value = true
  try {
    list.value = (await workflowApi.myStarted()) || []
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detail.value = await workflowApi.detail(row.procInstId)
  detailVisible.value = true
}

onMounted(loadList)
</script>

<style scoped>
.node-title { font-weight: 600; }
.node-meta { color: #909399; font-size: 13px; margin-top: 4px; }
.node-comment { color: #606266; font-size: 13px; margin-top: 4px; }
</style>
