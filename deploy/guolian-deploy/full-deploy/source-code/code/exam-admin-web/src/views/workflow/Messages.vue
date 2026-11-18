<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="消息注册用于登记 BPMN 中定义的消息（messageRef），可对运行中的流程实例投递消息以推进流程；不填流程实例 ID 表示广播。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button type="primary" plain @click="openCreate">新增消息</el-button>
        <el-button @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="messageRef" label="消息标识" min-width="180" show-overflow-tooltip />
        <el-table-column prop="channel" label="通道" width="120" align="center" />
        <el-table-column prop="topic" label="主题/队列" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openTrigger(row)">触发</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无消息注册" />
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑消息' : '新增消息'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="消息标识" required>
          <el-input v-model="form.messageRef" placeholder="与 BPMN 中 message 的 id 一致" />
        </el-form-item>
        <el-form-item label="通道">
          <el-select v-model="form.channel" style="width: 100%">
            <el-option label="内部（INNER）" value="INNER" />
            <el-option label="MQ（MQ）" value="MQ" />
            <el-option label="HTTP 回调（HTTP）" value="HTTP" />
          </el-select>
        </el-form-item>
        <el-form-item label="主题/队列">
          <el-input v-model="form.topic" placeholder="外部通道的 topic 或回调地址" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="triggerVisible" title="触发消息" width="560px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="消息标识">
          <el-input v-model="triggerForm.messageRef" disabled />
        </el-form-item>
        <el-form-item label="流程实例 ID">
          <el-input v-model="triggerForm.procInstId" placeholder="留空表示向所有等待该消息的实例广播" />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="若被触发的流程 BPMN 中未定义 message 捕获事件，则不会有实例被推进。"
      />
      <template #footer>
        <el-button @click="triggerVisible = false">取消</el-button>
        <el-button type="primary" :loading="triggering" @click="doTrigger">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'

const loading = ref(false)
const saving = ref(false)
const triggering = ref(false)
const list = ref([])
const editVisible = ref(false)
const triggerVisible = ref(false)
const form = reactive(emptyForm())
const triggerForm = reactive({ messageRef: '', procInstId: '' })

function emptyForm() {
  return { id: null, messageRef: '', channel: 'INNER', topic: '', description: '' }
}

async function loadList() {
  loading.value = true
  try {
    const data = await workflowApi.messageList()
    list.value = Array.isArray(data) ? data : data.records || data.list || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    messageRef: row.messageRef,
    channel: row.channel || 'INNER',
    topic: row.topic || '',
    description: row.description || ''
  })
  editVisible.value = true
}

async function save() {
  if (!form.messageRef.trim()) {
    ElMessage.warning('消息标识必填')
    return
  }
  saving.value = true
  try {
    await workflowApi.saveMessage({ ...form })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

function openTrigger(row) {
  triggerForm.messageRef = row.messageRef
  triggerForm.procInstId = ''
  triggerVisible.value = true
}

async function doTrigger() {
  triggering.value = true
  try {
    const count = await workflowApi.triggerMessage({
      messageRef: triggerForm.messageRef,
      procInstId: triggerForm.procInstId.trim() || null
    })
    const n = Number(count || 0)
    if (n > 0) {
      ElMessage.success(`已推进 ${n} 个流程实例`)
    } else {
      ElMessage.warning('当前无等待该消息的实例')
    }
    triggerVisible.value = false
  } finally {
    triggering.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除消息「${row.messageRef}」？`, '提示', { type: 'warning' })
  await workflowApi.deleteMessage(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(loadList)
</script>
