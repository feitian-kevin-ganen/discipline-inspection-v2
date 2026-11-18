<template>
  <div class="page-container">
    <div class="page-card">
      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="我的待办" name="todo" />
        <el-tab-pane label="我的已办" name="done" />
      </el-tabs>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="bizTitle" label="业务标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="procDefName" label="流程" width="160" show-overflow-tooltip />
        <el-table-column prop="taskName" label="当前节点" width="140" />
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">
            <el-tag type="info">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到达/完成时间" width="180">
          <template #default="{ row }">{{ fmtTime(activeTab === 'todo' ? row.createTime : row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="activeTab === 'todo'" type="primary" link @click="openHandle(row)">办理</el-button>
            <el-button v-else type="info" link @click="openDetail(row)">查看流程</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="暂无数据" />
    </div>

    <el-dialog v-model="handleVisible" title="办理审批" width="520px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="业务标题">{{ current.bizTitle }}</el-descriptions-item>
        <el-descriptions-item label="流程节点">{{ current.taskName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="审批结果" required>
          <el-radio-group v-model="form.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见" :required="!form.approve">
          <el-input v-model="form.comment" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="驳回时必须填写原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="流程详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务类型">{{ bizLabel(detail.instance?.bizType) }}</el-descriptions-item>
          <el-descriptions-item label="业务标题">{{ detail.instance?.title }}</el-descriptions-item>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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

const activeTab = ref('todo')
const loading = ref(false)
const list = ref([])

const handleVisible = ref(false)
const submitting = ref(false)
const current = ref({})
const form = reactive({ approve: true, comment: '' })

const detailVisible = ref(false)
const detail = ref(null)

async function loadList() {
  loading.value = true
  try {
    const data = activeTab.value === 'todo' ? await workflowApi.todo() : await workflowApi.done()
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function openHandle(row) {
  current.value = row
  form.approve = true
  form.comment = ''
  handleVisible.value = true
}

async function submit() {
  if (!form.approve && !form.comment.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  submitting.value = true
  try {
    await workflowApi.complete(current.value.taskId, { approve: form.approve, comment: form.comment.trim() })
    ElMessage.success('审批已提交')
    handleVisible.value = false
    loadList()
  } finally {
    submitting.value = false
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
