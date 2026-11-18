<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="group" placeholder="参数分组" clearable style="width: 180px" @change="loadData">
          <el-option v-for="g in GROUPS" :key="g" :label="g" :value="g" />
        </el-select>
        <el-button @click="loadData">查询</el-button>
        <div class="spacer" />
        <el-button type="primary" :loading="saving" :disabled="changedList.length === 0" @click="saveAll">
          保存修改<template v-if="changedList.length">（{{ changedList.length }}）</template>
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="configName" label="参数名称" min-width="160" />
        <el-table-column prop="configKey" label="参数键" min-width="200" show-overflow-tooltip />
        <el-table-column label="参数值" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.configValue" @input="markChanged(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="valueType" label="值类型" width="100" />
        <el-table-column prop="defaultValue" label="默认值" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updatedBy" label="最近修改人" width="110" />
        <el-table-column label="修改时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.updatedTime) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '../../api'
import { fmtTime } from '../../utils/format'

const GROUPS = ['EXAM', 'LEARN', 'QUES', 'LIB', 'STAT', 'SECURITY']

const group = ref('')
const list = ref([])
const loading = ref(false)
const saving = ref(false)

const changedList = computed(() => list.value.filter((r) => r.configValue !== r._originValue))

async function loadData() {
  loading.value = true
  try {
    const data = await sysApi.configs({ group: group.value || undefined })
    list.value = (data || []).map((r) => ({ ...r, _originValue: r.configValue }))
  } finally {
    loading.value = false
  }
}

function markChanged() {
  // 触发 computed 重新计算即可（configValue 为响应式字段）
}

async function saveAll() {
  const configs = changedList.value.map((r) => ({ configKey: r.configKey, configValue: r.configValue }))
  if (configs.length === 0) return
  saving.value = true
  try {
    await sysApi.saveConfigs(configs)
    ElMessage.success(`已保存 ${configs.length} 项参数`)
    loadData()
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
