<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="表单定义用于给流程节点绑定业务表单字段；formKey 全局唯一，修改后版本号自动 +1。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="表单标识 / 名称"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button type="primary" plain @click="openCreate">新增表单</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="formKey" label="表单标识" min-width="160" show-overflow-tooltip />
        <el-table-column prop="name" label="表单名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="绑定流程" width="150">
          <template #default="{ row }">{{ procLabel(row.procDefKey) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="字段数" width="80" align="center">
          <template #default="{ row }">{{ fieldCount(row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openPreview(row)">预览</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无表单定义" />

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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑表单' : '新增表单'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="表单标识" required>
          <el-input v-model="form.formKey" :disabled="!!form.id" placeholder="如 material_audit_form" />
        </el-form-item>
        <el-form-item label="表单名称" required>
          <el-input v-model="form.name" placeholder="如 学习资料审核表单" />
        </el-form-item>
        <el-form-item label="绑定流程">
          <el-select v-model="form.procDefKey" clearable placeholder="可选" style="width: 100%">
            <el-option label="题目审核（ques_item_audit）" value="ques_item_audit" />
            <el-option label="学习资料审核（material_audit）" value="material_audit" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="表单字段">
          <div style="width: 100%">
            <el-table :data="fields" border size="small">
              <el-table-column label="标签" width="150">
                <template #default="{ row }"><el-input v-model="row.label" size="small" /></template>
              </el-table-column>
              <el-table-column label="字段名" width="150">
                <template #default="{ row }"><el-input v-model="row.key" size="small" placeholder="英文标识" /></template>
              </el-table-column>
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small">
                    <el-option v-for="t in FIELD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="必填" width="70" align="center">
                <template #default="{ row }"><el-checkbox v-model="row.required" /></template>
              </el-table-column>
              <el-table-column label="选项（逗号分隔）">
                <template #default="{ row }">
                  <el-input v-model="row.optionsText" size="small" :disabled="row.type !== 'select'" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link @click="fields.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button style="margin-top: 8px" plain @click="addField">新增字段</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="表单预览" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item v-for="f in previewFields" :key="f.key" :label="f.label" :required="f.required">
          <el-input v-if="f.type === 'input'" disabled :placeholder="f.key" />
          <el-input v-else-if="f.type === 'textarea'" type="textarea" disabled :placeholder="f.key" />
          <el-input-number v-else-if="f.type === 'number'" disabled style="width: 100%" />
          <el-date-picker v-else-if="f.type === 'date'" disabled style="width: 100%" />
          <el-select v-else-if="f.type === 'select'" disabled style="width: 100%" placeholder="请选择">
            <el-option v-for="o in f.options || []" :key="o" :label="o" :value="o" />
          </el-select>
          <el-input v-else disabled :placeholder="f.key" />
        </el-form-item>
        <el-empty v-if="!previewFields.length" description="该表单未定义字段" />
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'

const FIELD_TYPES = [
  { label: '单行文本', value: 'input' },
  { label: '多行文本', value: 'textarea' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '下拉选择', value: 'select' }
]
const PROC_NAMES = { ques_item_audit: '题目审核', material_audit: '学习资料审核' }

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })
const editVisible = ref(false)
const previewVisible = ref(false)
const previewFields = ref([])
const fields = ref([])
const form = reactive(emptyForm())

function emptyForm() {
  return { id: null, formKey: '', name: '', procDefKey: '', remark: '', status: '0' }
}

function procLabel(key) {
  if (!key) return '-'
  return PROC_NAMES[key] || key
}

function parseSchema(row) {
  try {
    const s = row.schemaJson ? JSON.parse(row.schemaJson) : []
    return Array.isArray(s) ? s : []
  } catch (e) {
    return []
  }
}

function fieldCount(row) {
  return parseSchema(row).length
}

async function loadList() {
  loading.value = true
  try {
    const data = await workflowApi.formList({ ...query })
    list.value = data.records || data.list || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function addField() {
  fields.value.push({ label: '', key: '', type: 'input', required: false, optionsText: '' })
}

function openCreate() {
  Object.assign(form, emptyForm())
  fields.value = []
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    formKey: row.formKey,
    name: row.name,
    procDefKey: row.procDefKey || '',
    remark: row.remark || '',
    status: row.status || '0'
  })
  fields.value = parseSchema(row).map((f) => ({
    label: f.label || '',
    key: f.key || '',
    type: f.type || 'input',
    required: !!f.required,
    optionsText: (f.options || []).join(',')
  }))
  editVisible.value = true
}

function openPreview(row) {
  previewFields.value = parseSchema(row)
  previewVisible.value = true
}

async function save() {
  if (!form.formKey.trim() || !form.name.trim()) {
    ElMessage.warning('表单标识与名称必填')
    return
  }
  const schema = fields.value
    .filter((f) => f.key.trim())
    .map((f) => ({
      label: f.label.trim() || f.key.trim(),
      key: f.key.trim(),
      type: f.type,
      required: !!f.required,
      options: f.optionsText ? f.optionsText.split(',').map((s) => s.trim()).filter(Boolean) : []
    }))
  saving.value = true
  try {
    await workflowApi.saveForm({ ...form, schemaJson: JSON.stringify(schema) })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row, enabled) {
  await workflowApi.setFormStatus(row.id, enabled ? '0' : '1')
  row.status = enabled ? '0' : '1'
  ElMessage.success(enabled ? '已启用' : '已停用')
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除表单「${row.name}」？`, '提示', { type: 'warning' })
  await workflowApi.deleteForm(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(loadList)
</script>
