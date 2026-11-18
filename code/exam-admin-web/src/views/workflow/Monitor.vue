<template>
  <div class="page-container">
    <div class="page-card">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="所有流程" name="all">
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
        </el-tab-pane>

        <el-tab-pane label="超时任务" name="timeout">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="超时判定依据「超时配置」中该流程节点设置的小时数；催办会写入站内通知记录（flow_notify_log），可在站内通知中查看。"
            style="margin-bottom: 12px"
          />
          <div class="toolbar">
            <el-button type="primary" plain @click="loadTimeout">刷新</el-button>
            <el-button plain @click="openExtDialog">超时配置</el-button>
          </div>

          <el-table v-loading="timeoutLoading" :data="timeoutList" border stripe>
            <el-table-column prop="taskName" label="节点" width="150" show-overflow-tooltip />
            <el-table-column prop="title" label="业务标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="办理人" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.assigneeName || row.assignee || '-' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="timeoutHours" label="超时阈值(h)" width="110" align="center" />
            <el-table-column label="已超时(h)" width="110" align="center">
              <template #default="{ row }">
                <el-tag type="danger">{{ row.overHours }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="warning" link @click="doUrge(row)">催办</el-button>
                <el-button type="primary" link @click="openProgress({ procInstId: row.procInstId })">
                  流程进度
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!timeoutLoading && !timeoutList.length" description="暂无超时任务" />

          <div class="pager">
            <el-pagination
              v-model:current-page="timeoutQuery.pageNum"
              v-model:page-size="timeoutQuery.pageSize"
              :total="timeoutTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadTimeout"
              @current-change="loadTimeout"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="progressVisible" title="流程进度" size="640px">
      <FlowDetailPanel v-if="current.procInstId" :proc-inst-id="current.procInstId" diagram-height="320px" />
    </el-drawer>

    <el-dialog v-model="extVisible" title="节点超时配置" width="720px" destroy-on-close>
      <el-form :model="extForm" inline>
        <el-form-item label="流程定义">
          <el-select v-model="extForm.procDefId" filterable placeholder="选择流程定义" style="width: 240px">
            <el-option
              v-for="d in definitionOptions"
              :key="d.id"
              :label="`${d.name}（${d.key} v${d.version}）`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点 Key">
          <el-input v-model="extForm.taskDefKey" placeholder="如 auditTask" style="width: 150px" />
        </el-form-item>
        <el-form-item label="节点名">
          <el-input v-model="extForm.taskName" style="width: 130px" />
        </el-form-item>
        <el-form-item label="超时(h)">
          <el-input-number v-model="extForm.timeoutHours" :min="1" style="width: 110px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="extSaving" @click="saveExt">保存</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="extLoading" :data="extList" border size="small">
        <el-table-column label="流程定义" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ defLabel(row.procDefId) }}</template>
        </el-table-column>
        <el-table-column prop="taskDefKey" label="节点 Key" width="130" />
        <el-table-column prop="taskName" label="节点名" width="120" />
        <el-table-column prop="timeoutHours" label="超时(h)" width="90" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="danger" link @click="removeExt(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { FLOW_STATUS, STATUS_OPTIONS, bizLabel } from '../../utils/workflow'
import FlowDetailPanel from '../../components/workflow/FlowDetailPanel.vue'

const tab = ref('all')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ title: '', status: '', pageNum: 1, pageSize: 10 })
const progressVisible = ref(false)
const current = ref({})

const timeoutLoading = ref(false)
const timeoutList = ref([])
const timeoutTotal = ref(0)
const timeoutQuery = reactive({ pageNum: 1, pageSize: 10 })

const extVisible = ref(false)
const extLoading = ref(false)
const extSaving = ref(false)
const extList = ref([])
const definitionOptions = ref([])
const extForm = reactive(emptyExt())

function emptyExt() {
  return { procDefId: '', taskDefKey: '', taskName: '', timeoutHours: 24 }
}

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

async function loadTimeout() {
  timeoutLoading.value = true
  try {
    const data = await workflowApi.timeoutTasks({ ...timeoutQuery })
    timeoutList.value = data?.list || data?.records || []
    timeoutTotal.value = Number(data?.total || 0)
  } finally {
    timeoutLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'timeout') loadTimeout()
}

async function doUrge(row) {
  await ElMessageBox.confirm(
    `向「${row.assigneeName || row.assignee || '办理人'}」发送催办提醒？`,
    '催办',
    { type: 'warning' }
  )
  const msg = await workflowApi.urge(row.taskId)
  ElMessage.success(msg || '已发送催办')
  loadTimeout()
}

function defLabel(procDefId) {
  const d = definitionOptions.value.find((x) => String(x.id) === String(procDefId))
  return d ? `${d.name}（${d.key} v${d.version}）` : procDefId
}

async function openExtDialog() {
  extVisible.value = true
  Object.assign(extForm, emptyExt())
  if (!definitionOptions.value.length) {
    try {
      const data = await workflowApi.designerList()
      definitionOptions.value = Array.isArray(data) ? data : data.records || data.list || []
    } catch (e) {
      definitionOptions.value = []
    }
  }
  loadExtList()
}

async function loadExtList() {
  extLoading.value = true
  try {
    const data = await workflowApi.taskExtList()
    extList.value = Array.isArray(data) ? data : data.records || data.list || []
  } finally {
    extLoading.value = false
  }
}

async function saveExt() {
  if (!extForm.procDefId || !extForm.taskDefKey.trim()) {
    ElMessage.warning('流程定义与节点 Key 必填')
    return
  }
  extSaving.value = true
  try {
    await workflowApi.saveTaskExt({ ...extForm })
    ElMessage.success('保存成功')
    Object.assign(extForm, emptyExt())
    loadExtList()
  } finally {
    extSaving.value = false
  }
}

async function removeExt(row) {
  await ElMessageBox.confirm(`确认删除「${row.taskName || row.taskDefKey}」的超时配置？`, '提示', {
    type: 'warning'
  })
  await workflowApi.deleteTaskExt(row.id)
  ElMessage.success('已删除')
  loadExtList()
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
