<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="审批人办理待办时可选择抄送人；你收到的抄送会出现在这里，可查看流程进度或标记已读。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-radio-group v-model="query.readFlag" @change="resetPage">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="0">未读</el-radio-button>
          <el-radio-button value="1">已读</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain :disabled="!list.some((r) => r.readFlag === '0')" @click="markAll">
          全部标记已读
        </el-button>
        <span class="unread">未读 {{ unreadCount }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="title" label="业务标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="taskName" label="触发节点" width="140" show-overflow-tooltip />
        <el-table-column label="抄送人" width="120">
          <template #default="{ row }">{{ row.fromUserName || row.fromUserId || '-' }}</template>
        </el-table-column>
        <el-table-column label="抄送时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readFlag === '1' ? 'info' : 'warning'">{{ row.readFlag === '1' ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openProgress(row)">流程进度</el-button>
            <el-button v-if="row.readFlag === '0'" type="primary" link @click="markOne(row)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无抄送" />

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
import { ElMessage } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'
import FlowDetailPanel from '../../components/workflow/FlowDetailPanel.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const unreadCount = ref(0)
const query = reactive({ readFlag: '', pageNum: 1, pageSize: 10 })
const progressVisible = ref(false)
const current = ref({})

async function loadList() {
  loading.value = true
  try {
    const data = await workflowApi.myCopies({
      readFlag: query.readFlag || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = data?.list || []
    total.value = data?.total || 0
    unreadCount.value = await workflowApi.unreadCopyCount()
  } finally {
    loading.value = false
  }
}

function resetPage() {
  query.pageNum = 1
  loadList()
}

async function markOne(row) {
  await workflowApi.markCopyRead(row.id)
  ElMessage.success('已标记已读')
  loadList()
}

async function markAll() {
  const count = await workflowApi.markAllCopyRead()
  ElMessage.success(`已标记 ${count} 条为已读`)
  loadList()
}

function openProgress(row) {
  current.value = row
  progressVisible.value = true
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.unread {
  color: #909399;
  font-size: 13px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
