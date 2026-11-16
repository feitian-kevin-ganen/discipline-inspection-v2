<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="任务名称" clearable style="width: 220px" @keyup.enter="reload" @clear="reload" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
        <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
        <div class="spacer"></div>
        <el-button type="primary" :icon="Plus" @click="openCreate">下达任务</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="title" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="materialCount" label="资料数" width="80" align="right" />
        <el-table-column label="要求时长" width="110" align="right">
          <template #default="{ row }">{{ formatSec(row.requiredSec) }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="165" />
        <el-table-column label="目标单位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.targetOrgNames || []).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="assigneeCount" label="人数" width="80" align="right" />
        <el-table-column prop="doneCount" label="完成数" width="80" align="right" />
        <el-table-column label="完成率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="doneRate(row)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="110" />
        <el-table-column prop="createdTime" label="创建时间" width="165" />
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
    </div>

    <!-- 下达任务 -->
    <el-dialog v-model="createVisible" title="下达学习任务" width="640px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="title">
          <el-input v-model="form.title" maxlength="100" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="学习资料" prop="materialIds">
          <el-select
            v-model="form.materialIds"
            multiple
            filterable
            collapse-tags
            :max-collapse-tags="3"
            style="width: 100%"
            placeholder="选择已发布的资料"
            :loading="materialLoading"
          >
            <el-option v-for="m in materialOptions" :key="m.materialId" :label="m.title" :value="m.materialId" />
          </el-select>
        </el-form-item>
        <el-form-item label="要求时长" prop="requiredSec">
          <el-input-number v-model="form.requiredSec" :min="60" :step="60" @blur="onRequiredSecBlur" />
          <span style="margin-left: 8px; color: #909399">秒（须为 60 的倍数，约 {{ requiredMinutes }} 分钟）</span>
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择完成期限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="目标单位" prop="targetOrgIds">
          <el-tree-select
            v-model="form.targetOrgIds"
            :data="orgTree"
            :props="{ label: 'orgName', value: 'orgId', children: 'children' }"
            multiple
            check-strictly
            :render-after-expand="false"
            collapse-tags
            style="width: 100%"
            placeholder="目标单位含下级人员"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">下达</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft } from '@element-plus/icons-vue'
import { learnApi, libApi, orgApi } from '../../api'
import { snapToStep } from '../../utils/number'
import { formatSec } from '../../utils/download'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = ref({ pageNum: 1, pageSize: 10, keyword: '' })

function doneRate(row) {
  if (!row.assigneeCount) return 0
  return Math.min(100, Math.round((row.doneCount / row.assigneeCount) * 100))
}

async function loadList() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (!params.keyword) delete params.keyword
    const data = await learnApi.taskPage(params)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.value.pageNum = 1
  loadList()
}

function resetQuery() {
  query.value.keyword = ''
  reload()
}

// ---------------- 下达任务 ----------------
const createVisible = ref(false)
const formRef = ref()
const orgTree = ref([])
const materialOptions = ref([])
const materialLoading = ref(false)
const form = ref({ title: '', materialIds: [], requiredSec: 1800, deadline: '', targetOrgIds: [] })

const rules = {
  title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  materialIds: [{ required: true, type: 'array', min: 1, message: '请选择学习资料', trigger: 'change' }],
  requiredSec: [{ required: true, message: '请填写要求时长', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
  targetOrgIds: [{ required: true, type: 'array', min: 1, message: '请选择目标单位', trigger: 'change' }]
}

const requiredMinutes = computed(() => (form.value.requiredSec ? (form.value.requiredSec / 60).toFixed(0) : 0))

// 要求时长须为 60 的倍数：失焦自动纠正到最近的 60 倍数
function onRequiredSecBlur() {
  form.value.requiredSec = snapToStep(form.value.requiredSec, 60, 60)
}

async function openCreate() {
  form.value = { title: '', materialIds: [], requiredSec: 1800, deadline: '', targetOrgIds: [] }
  createVisible.value = true
  if (!orgTree.value.length) orgTree.value = await orgApi.tree() || []
  materialLoading.value = true
  try {
    const data = await libApi.page({ pageNum: 1, pageSize: 200, status: 'PUBLISHED' })
    materialOptions.value = data.list || []
  } finally {
    materialLoading.value = false
  }
}

async function submitCreate() {
  // 提交前兜底纠正，防止绕过失焦校验
  form.value.requiredSec = snapToStep(form.value.requiredSec, 60, 60)
  await formRef.value.validate()
  submitting.value = true
  try {
    const data = await learnApi.createTask({ ...form.value })
    createVisible.value = false
    await ElMessageBox.alert(`任务下达成功，覆盖人员 ${data.assigneeCount} 人。`, '下达成功', { type: 'success' })
    reload()
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>
