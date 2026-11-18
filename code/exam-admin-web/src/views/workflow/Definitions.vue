<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="流程定义由工作流引擎自动部署；可查看流程图与 BPMN XML，挂起后新的审批单将无法发起，进行中实例可继续办理。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-input
          v-model="query.name"
          placeholder="流程名称 / 标识"
          clearable
          style="width: 240px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="filteredList" border stripe>
        <el-table-column prop="key" label="流程标识" min-width="180" />
        <el-table-column prop="name" label="流程名称" min-width="200" />
        <el-table-column prop="version" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.suspended ? 'info' : 'success'">{{ row.suspended ? '已挂起' : '已启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDiagram(row)">流程图</el-button>
            <el-button type="primary" link @click="openXml(row)">查看 XML</el-button>
            <el-button :type="row.suspended ? 'success' : 'warning'" link @click="toggleState(row)">
              {{ row.suspended ? '激活' : '挂起' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filteredList.length" description="暂无流程定义" />
    </div>

    <el-dialog v-model="diagramVisible" :title="`流程图 - ${current.name || ''}`" width="900px" top="5vh">
      <ProcessViewer v-if="xml" :xml="xml" height="460px" />
      <el-empty v-else description="暂无流程图" />
    </el-dialog>

    <el-dialog v-model="xmlVisible" :title="`BPMN XML - ${current.name || ''}`" width="900px" top="5vh">
      <pre class="xml-view">{{ xml }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import ProcessViewer from '../../components/workflow/ProcessViewer.vue'

const loading = ref(false)
const list = ref([])
const query = reactive({ name: '' })
const current = ref({})
const xml = ref('')
const diagramVisible = ref(false)
const xmlVisible = ref(false)

const filteredList = computed(() => {
  const keyword = query.name.trim().toLowerCase()
  if (!keyword) return list.value
  return list.value.filter(
    (row) => (row.name || '').toLowerCase().includes(keyword) || (row.key || '').toLowerCase().includes(keyword)
  )
})

async function loadList() {
  loading.value = true
  try {
    list.value = (await workflowApi.definitions()) || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.name = ''
  loadList()
}

async function loadXml(row) {
  current.value = row
  xml.value = ''
  xml.value = (await workflowApi.definitionXml(row.id)) || ''
}

async function openDiagram(row) {
  await loadXml(row)
  diagramVisible.value = true
}

async function openXml(row) {
  await loadXml(row)
  xmlVisible.value = true
}

async function toggleState(row) {
  const suspend = !row.suspended
  const action = suspend ? '挂起' : '激活'
  try {
    await ElMessageBox.confirm(`确认${action}流程「${row.name || row.key}」？`, `${action}流程定义`, {
      confirmButtonText: `确认${action}`,
      cancelButtonText: '取消',
      type: 'warning'
    })
    await workflowApi.changeDefinitionState(row.id, suspend)
    ElMessage.success(`流程已${action}`)
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
.xml-view {
  max-height: 60vh;
  overflow: auto;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
