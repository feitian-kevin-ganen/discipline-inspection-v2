<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input
          v-model="query.taskName"
          placeholder="任务名称"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="我的待办" name="todo" />
        <el-tab-pane label="我的已办" name="done" />
      </el-tabs>

      <el-table v-loading="loading" :data="filteredList" border stripe>
        <el-table-column prop="bizTitle" label="业务标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="procDefName" label="流程" width="160" show-overflow-tooltip />
        <el-table-column prop="taskName" label="当前节点" width="140" />
        <el-table-column label="业务类型" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到达/完成时间" width="180">
          <template #default="{ row }">{{ fmtTime(activeTab === 'todo' ? row.createTime : row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="activeTab === 'todo'" type="primary" link @click="openHandle(row)">办理</el-button>
            <el-button type="info" link @click="openProgress(row)">查看进度</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !filteredList.length" description="暂无数据" />
    </div>

    <el-dialog v-model="handleVisible" title="办理审批" width="900px" top="5vh">
      <FlowDetailPanel v-if="current.procInstId" :proc-inst-id="current.procInstId" diagram-height="280px" />
      <el-form label-width="90px" style="margin-top: 16px">
        <el-form-item label="审批结果" required>
          <el-radio-group v-model="form.approve">
            <el-radio :value="true">同意</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见" :required="!form.approve">
          <el-input
            v-model="form.comment"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="驳回时必须填写原因"
          />
        </el-form-item>
        <el-form-item label="抄送人">
          <el-select
            v-model="form.ccUserIds"
            multiple
            filterable
            clearable
            placeholder="可选择多人，办理完成后推送抄送"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="`${u.name || u.userName || u.phone || u.userId}（${u.jobNo || u.userId}）`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="progressVisible" title="流程进度" size="640px">
      <FlowDetailPanel v-if="current.procInstId" :proc-inst-id="current.procInstId" diagram-height="320px" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { bizLabel } from '../../utils/workflow'
import FlowDetailPanel from '../../components/workflow/FlowDetailPanel.vue'

const activeTab = ref('todo')
const loading = ref(false)
const list = ref([])
const query = reactive({ taskName: '' })

const handleVisible = ref(false)
const submitting = ref(false)
const progressVisible = ref(false)
const current = ref({})
const form = reactive({ approve: true, comment: '', ccUserIds: [] })
const userOptions = ref([])

const filteredList = computed(() => {
  const keyword = query.taskName.trim()
  if (!keyword) return list.value
  return list.value.filter((row) => (row.taskName || '').includes(keyword))
})

async function loadList() {
  loading.value = true
  try {
    const data = activeTab.value === 'todo' ? await workflowApi.todo() : await workflowApi.done()
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.taskName = ''
  loadList()
}

function openHandle(row) {
  current.value = row
  form.approve = true
  form.comment = ''
  form.ccUserIds = []
  handleVisible.value = true
  loadUsers()
}

function openProgress(row) {
  current.value = row
  progressVisible.value = true
}

async function loadUsers() {
  if (userOptions.value.length) return
  try {
    const data = await userApi.page({ pageNum: 1, pageSize: 500 })
    userOptions.value = data?.list || data?.records || []
  } catch (e) {
    userOptions.value = []
  }
}

async function submit() {
  if (!form.approve && !form.comment.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  submitting.value = true
  try {
    await workflowApi.complete(current.value.taskId, {
      approve: form.approve,
      comment: form.comment.trim(),
      ccUserIds: form.ccUserIds
    })
    ElMessage.success('审批已提交')
    handleVisible.value = false
    loadList()
  } finally {
    submitting.value = false
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
</style>
