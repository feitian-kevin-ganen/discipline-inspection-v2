<template>
  <div v-loading="loading" class="flow-detail-panel">
    <template v-if="detail">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="业务标题" :span="2">{{ instance.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ bizLabel(instance.bizType) }}</el-descriptions-item>
        <el-descriptions-item label="流程">{{ instance.procDefName || instance.procDefKey || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="FLOW_STATUS[instance.status]?.type">
            {{ FLOW_STATUS[instance.status]?.label || instance.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ instance.startUserName || instance.startUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ fmtTime(instance.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ fmtTime(instance.endTime) }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="流程图" name="diagram">
          <ProcessViewer v-if="xml" :xml="xml" :highlight="highlight" :height="diagramHeight" />
          <el-empty v-else description="暂无流程图" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane label="流转历史" name="history">
          <ApprovalTimeline :list="detail.history || []" />
        </el-tab-pane>
        <el-tab-pane label="流程变量" name="variables">
          <el-table v-if="variableRows.length" :data="variableRows" border size="small">
            <el-table-column prop="name" label="变量" width="180" />
            <el-table-column prop="value" label="值" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="暂无流程变量" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { FLOW_STATUS, bizLabel } from '../../utils/workflow'
import ProcessViewer from './ProcessViewer.vue'
import ApprovalTimeline from './ApprovalTimeline.vue'

const props = defineProps({
  procInstId: { type: String, default: '' },
  diagramHeight: { type: String, default: '340px' }
})

const loading = ref(false)
const detail = ref(null)
const xml = ref('')
const highlight = ref({ finished: [], active: [], rejected: [] })
const tab = ref('diagram')

const instance = computed(() => detail.value?.instance || {})

const variableRows = computed(() => {
  const vars = detail.value?.variables || {}
  return Object.entries(vars).map(([name, value]) => ({
    name,
    value: value === null || value === undefined ? '-' : typeof value === 'object' ? JSON.stringify(value) : String(value)
  }))
})

async function load() {
  if (!props.procInstId) return
  loading.value = true
  detail.value = null
  xml.value = ''
  highlight.value = { finished: [], active: [], rejected: [] }
  try {
    detail.value = await workflowApi.detail(props.procInstId)
    const definitionId = detail.value?.instance?.definitionId
    if (definitionId) {
      const [xmlText, diagram] = await Promise.all([
        workflowApi.diagramXml(definitionId),
        workflowApi.diagramData(props.procInstId)
      ])
      xml.value = xmlText || ''
      highlight.value = diagram || { finished: [], active: [], rejected: [] }
    }
  } finally {
    loading.value = false
  }
}

watch(() => props.procInstId, load, { immediate: true })
</script>

<style scoped>
.detail-tabs {
  margin-top: 16px;
}
</style>
