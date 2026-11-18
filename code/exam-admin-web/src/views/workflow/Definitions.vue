<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="流程定义由工作流引擎自动部署，修改 BPMN 文件后重启服务即可生效；如需新增审批流程，请在流程定义中配置后由开发发布。"
        style="margin-bottom: 16px"
      />
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="key" label="流程标识" min-width="180" />
        <el-table-column prop="name" label="流程名称" min-width="200" />
        <el-table-column prop="version" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.suspended ? 'info' : 'success'">{{ row.suspended ? '已挂起' : '已启用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无流程定义" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { workflowApi } from '../../api'

const loading = ref(false)
const list = ref([])

async function loadList() {
  loading.value = true
  try {
    list.value = (await workflowApi.definitions()) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>
