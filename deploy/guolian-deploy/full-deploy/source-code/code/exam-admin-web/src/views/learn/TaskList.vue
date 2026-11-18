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
        <el-table-column label="截止时间" width="165">
          <template #default="{ row }">{{ row.deadline ? row.deadline.replace('T', ' ').slice(0, 19) : '-' }}</template>
        </el-table-column>
        <el-table-column label="目标单位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.targetOrgNames || []).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="assigneeCount" label="人数" width="80" align="right" />
        <el-table-column prop="doneCount" label="完成数" width="80" align="right" />
        <el-table-column label="完成率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="doneRate(row)" :stroke-width="10" />
            <div class="avg-rate">平均进度 {{ fmtAvgProgress(row.avgProgress) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="110" />
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ row.createdTime ? row.createdTime.replace('T', ' ').slice(0, 19) : '-' }}</template>
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
    </div>

    <!-- 下达任务 -->
    <el-dialog v-model="createVisible" title="下达学习任务" width="760px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="title">
          <el-input v-model="form.title" maxlength="100" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="学习资料" prop="materialIds">
          <!-- 分类可折叠面板：默认收起；点分类行展开/收起；勾选分类=全选该分类文件并自动展开；
               文件行点击切换选中，选中文件右侧显示勾 -->
          <div class="mat-panel">
            <el-input v-model="matFilter" placeholder="搜索资料" clearable size="small" :prefix-icon="Search" style="margin-bottom: 6px" />
            <div v-loading="materialLoading" class="mat-list">
              <div v-for="g in filteredMatGroups" :key="g.catId" class="mat-group">
                <div class="mat-cat" @click="toggleCatExpand(g.catId)">
                  <el-icon class="tri" :class="{ open: isCatOpen(g.catId) }"><CaretRight /></el-icon>
                  <el-checkbox
                    :model-value="isCatAllSelected(g)"
                    :indeterminate="isCatPartial(g)"
                    @click.stop
                    @change="(v) => toggleCat(g, v)"
                  />
                  <span class="cat-name">{{ g.catName }}</span>
                  <span class="cat-count">{{ catSelectedCount(g) }}/{{ g.items.length }}</span>
                </div>
                <div v-show="isCatOpen(g.catId)" class="mat-files">
                  <div
                    v-for="m in g.items"
                    :key="m.materialId"
                    class="mat-file"
                    :class="{ on: isMatSelected(m.materialId) }"
                    @click="toggleMat(m)"
                  >
                    <span class="file-title">{{ m.title }}</span>
                    <el-icon v-if="isMatSelected(m.materialId)" class="check"><Check /></el-icon>
                  </div>
                </div>
              </div>
              <el-empty v-if="!materialLoading && filteredMatGroups.length === 0" description="暂无匹配资料" :image-size="50" />
            </div>
            <div class="mat-picked-tip">已选 {{ form.materialIds.length }} 个资料</div>
          </div>
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
            placement="bottom-start"
            :fallback-placements="['bottom-start', 'top-start']"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="目标人员" prop="userIds">
          <!-- 左：组织架构树（可搜索）；右：该单位人员多选；下：已选人员标签 -->
          <div class="picker">
            <div class="picker-left">
              <el-input v-model="orgFilter" placeholder="搜索单位" clearable size="small" :prefix-icon="Search" style="margin-bottom: 6px" />
              <el-tree
                ref="orgTreeRef"
                :data="orgTree"
                :props="{ label: 'orgName', children: 'children' }"
                node-key="orgId"
                :filter-node-method="filterOrgNode"
                highlight-current
                :expand-on-click-node="false"
                @node-click="onOrgClick"
              />
            </div>
            <div class="picker-right">
              <div class="picker-right-head">
                <el-input v-model="userFilter" placeholder="搜索姓名/工号" clearable size="small" :prefix-icon="Search" style="flex: 1" />
                <el-checkbox :model-value="checkAll" :indeterminate="checkAllIndeterminate" @change="onCheckAll">全选</el-checkbox>
              </div>
              <div v-loading="userLoading" class="picker-user-list">
                <el-checkbox
                  v-for="u in filteredUsers"
                  :key="u.userId"
                  :model-value="isSelected(u.userId)"
                  class="picker-user-item"
                  @change="(v) => toggleUser(u, v)"
                >
                  <span class="u-name">{{ u.name }}</span>
                  <span class="u-job">{{ u.jobNo }}</span>
                </el-checkbox>
                <el-empty
                  v-if="!userLoading && filteredUsers.length === 0"
                  :description="currentOrg ? '该单位暂无人员' : '请先选择左侧单位'"
                  :image-size="50"
                />
              </div>
            </div>
          </div>
          <div class="picked">
            <span class="picked-label">已选 {{ selectedUsers.length }} 人</span>
            <el-tag
              v-for="u in selectedUsers"
              :key="u.userId"
              closable
              size="small"
              style="margin: 2px 4px 2px 0"
              @close="toggleUser(u, false)"
            >{{ u.name }}（{{ u.jobNo }}）</el-tag>
          </div>
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
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, CaretRight, Check } from '@element-plus/icons-vue'
import { learnApi, libApi, orgApi, userApi } from '../../api'
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

// 平均进度：0~100 保留 1 位小数（整数不显示小数）
function fmtAvgProgress(v) {
  const n = v == null ? 0 : Number(v)
  if (!Number.isFinite(n) || n <= 0) return '0%'
  const r = Math.min(100, Math.round(n * 10) / 10)
  return (Number.isInteger(r) ? r : r.toFixed(1)) + '%'
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
const form = ref({ title: '', materialIds: [], requiredSec: 1800, deadline: '', userIds: [] })

// 目标人员选择器：左组织树 + 右人员多选 + 已选标签
const orgTreeRef = ref()
const orgFilter = ref('')
const userFilter = ref('')
const currentOrg = ref(null)
const userLoading = ref(false)
const orgUsers = ref([]) // 当前单位人员（已剔除停用）
const selectedUsers = ref([])

watch(orgFilter, (v) => orgTreeRef.value?.filter(v))

const filteredUsers = computed(() => {
  const kw = userFilter.value.trim()
  if (!kw) return orgUsers.value
  return orgUsers.value.filter((u) => (u.name || '').includes(kw) || (u.jobNo || '').includes(kw))
})

const checkAll = computed(() => filteredUsers.value.length > 0 && filteredUsers.value.every((u) => isSelected(u.userId)))
const checkAllIndeterminate = computed(() => !checkAll.value && filteredUsers.value.some((u) => isSelected(u.userId)))

function filterOrgNode(value, data) {
  if (!value) return true
  return (data.orgName || '').includes(value)
}

function isSelected(userId) {
  return selectedUsers.value.some((u) => u.userId === userId)
}

function toggleUser(u, checked) {
  if (checked) {
    if (!isSelected(u.userId)) selectedUsers.value.push({ userId: u.userId, name: u.name, jobNo: u.jobNo })
  } else {
    selectedUsers.value = selectedUsers.value.filter((x) => x.userId !== u.userId)
  }
}

function onCheckAll(checked) {
  for (const u of filteredUsers.value) {
    toggleUser(u, checked)
  }
}

async function onOrgClick(data) {
  currentOrg.value = data
  userFilter.value = ''
  userLoading.value = true
  try {
    const res = await userApi.page({ pageNum: 1, pageSize: 500, orgId: data.orgId })
    // 与后端快照口径一致：剔除停用(status=0)
    orgUsers.value = (res.list || []).filter((u) => u.status !== 0)
  } finally {
    userLoading.value = false
  }
}

const rules = {
  title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  materialIds: [{ required: true, type: 'array', min: 1, message: '请选择学习资料', trigger: 'change' }],
  requiredSec: [{ required: true, message: '请填写要求时长', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
  userIds: [{
    validator: (r, v, cb) => (selectedUsers.value.length > 0 ? cb() : cb(new Error('请选择目标人员'))),
    trigger: 'change'
  }]
}

const requiredMinutes = computed(() => (form.value.requiredSec ? (form.value.requiredSec / 60).toFixed(0) : 0))

// 学习资料按分类分组展示：先分类、再在分类下列文件（保持接口返回的排序）
const materialGroups = computed(() => {
  const map = new Map()
  for (const m of materialOptions.value) {
    const key = m.catId ?? 0
    if (!map.has(key)) {
      map.set(key, { catId: key, catName: m.catName || '未分类', items: [] })
    }
    map.get(key).items.push(m)
  }
  return Array.from(map.values())
})

// 分类折叠面板状态：默认全部收起；搜索时命中分类自动展开
const matFilter = ref('')
const openCatIds = ref([])

const filteredMatGroups = computed(() => {
  const kw = matFilter.value.trim()
  if (!kw) return materialGroups.value
  const out = []
  for (const g of materialGroups.value) {
    if (g.catName.includes(kw)) {
      out.push(g)
    } else {
      const items = g.items.filter((m) => (m.title || '').includes(kw))
      if (items.length) out.push({ ...g, items })
    }
  }
  return out
})

function isCatOpen(catId) {
  // 搜索时命中分类自动展开
  if (matFilter.value.trim() && filteredMatGroups.value.some((g) => g.catId === catId)) return true
  return openCatIds.value.includes(catId)
}

function toggleCatExpand(catId) {
  if (isCatOpen(catId) && !matFilter.value.trim()) {
    openCatIds.value = openCatIds.value.filter((id) => id !== catId)
  } else if (!isCatOpen(catId)) {
    openCatIds.value = [...openCatIds.value, catId]
  }
}

function isMatSelected(materialId) {
  return form.value.materialIds.includes(materialId)
}

function toggleMat(m) {
  const ids = form.value.materialIds
  form.value.materialIds = isMatSelected(m.materialId)
    ? ids.filter((id) => id !== m.materialId)
    : [...ids, m.materialId]
}

function catSelectedCount(g) {
  return g.items.filter((m) => isMatSelected(m.materialId)).length
}

function isCatAllSelected(g) {
  return g.items.length > 0 && catSelectedCount(g) === g.items.length
}

function isCatPartial(g) {
  const n = catSelectedCount(g)
  return n > 0 && n < g.items.length
}

// 勾选分类：全选/全不选该分类文件；勾选时自动展开
function toggleCat(g, checked) {
  const catIds = g.items.map((m) => m.materialId)
  if (checked) {
    form.value.materialIds = [...new Set([...form.value.materialIds, ...catIds])]
    if (!openCatIds.value.includes(g.catId)) {
      openCatIds.value = [...openCatIds.value, g.catId]
    }
  } else {
    form.value.materialIds = form.value.materialIds.filter((id) => !catIds.includes(id))
  }
}

// 要求时长须为 60 的倍数：失焦自动纠正到最近的 60 倍数
function onRequiredSecBlur() {
  form.value.requiredSec = snapToStep(form.value.requiredSec, 60, 60)
}

async function openCreate() {
  form.value = { title: '', materialIds: [], requiredSec: 1800, deadline: '', userIds: [] }
  selectedUsers.value = []
  orgUsers.value = []
  currentOrg.value = null
  orgFilter.value = ''
  userFilter.value = ''
  matFilter.value = ''
  openCatIds.value = []
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
  form.value.userIds = selectedUsers.value.map((u) => u.userId)
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

<style scoped>
/* 目标人员选择器：左组织树 + 右人员列表 */
.picker {
  display: flex;
  gap: 12px;
  width: 100%;
  height: 300px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  padding: 10px;
  box-sizing: border-box;
}
.picker-left {
  width: 220px;
  flex-shrink: 0;
  overflow: auto;
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  padding-right: 10px;
}
.picker-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.picker-right-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.picker-user-list {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 4px;
  padding: 6px 10px;
}
.picker-user-item {
  display: flex;
  width: 100%;
  margin-right: 0;
  height: 30px;
}
.picker-user-item :deep(.el-checkbox__label) {
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.u-name {
  color: var(--el-text-color-primary, #303133);
}
.u-job {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}
.picked {
  width: 100%;
  margin-top: 8px;
  padding: 6px 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  max-height: 84px;
  overflow: auto;
}
.picked-label {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  margin-right: 8px;
}
</style>

<style scoped>
/* 学习资料分类折叠面板 */
.mat-panel {
  width: 100%;
}
.mat-list {
  max-height: 260px;
  overflow: auto;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  padding: 4px 6px;
}
.mat-cat {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 4px;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
}
.mat-cat:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
.mat-cat .tri {
  transition: transform 0.15s;
  color: var(--el-text-color-secondary, #909399);
}
.mat-cat .tri.open {
  transform: rotate(90deg);
}
.cat-name {
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.cat-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.mat-files {
  padding-left: 34px;
}
.mat-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 8px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular, #606266);
}
.mat-file:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
.mat-file.on {
  color: var(--el-color-primary, #409eff);
}
.mat-file .check {
  color: var(--el-color-primary, #409eff);
}
.file-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mat-picked-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.avg-rate {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
