<template>
  <div class="page-container user-page">
    <!-- 左侧组织树 -->
    <div class="page-card org-panel">
      <div class="org-panel-header">
        <el-link type="primary" underline="never" @click="clearOrgFilter">全部单位</el-link>
        <el-checkbox v-model="query.includeSub" size="small" @change="onFilterChange">含下级</el-checkbox>
      </div>
      <el-tree
        ref="orgTreeRef"
        v-loading="orgLoading"
        :data="orgTree"
        node-key="orgId"
        :props="{ label: 'orgName', children: 'children' }"
        highlight-current
        default-expand-all
        @node-click="onOrgClick"
      />
    </div>

    <!-- 右侧人员列表 -->
    <div class="page-card user-panel">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="姓名/工号"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.roleCode" placeholder="角色" clearable style="width: 150px" @change="onSearch">
          <el-option v-for="r in ROLES" :key="r.code" :label="r.name" :value="r.code" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
          <el-option label="锁定" :value="2" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <span class="spacer"></span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增人员</el-button>
        <el-button :icon="Upload" @click="openImport">批量导入</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="姓名" min-width="90" />
        <el-table-column prop="jobNo" label="工号" min-width="100" />
        <el-table-column prop="phone" label="手机号" min-width="120" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属单位" min-width="150" show-overflow-tooltip />
        <el-table-column prop="position" label="职务" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.position || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="c in row.roles || []" :key="c" size="small" style="margin-right: 4px">{{ roleName(c) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="USER_STATUS[row.status]?.type" size="small">{{ USER_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" min-width="150">
          <template #default="{ row }">{{ row.lastLoginTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openRoleDialog(row)">角色分配</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, row)">
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="resetPwd">重置密码</el-dropdown-item>
                  <el-dropdown-item command="toggleStatus">
                    {{ row.status === 1 ? '锁定' : row.status === 2 ? '解锁' : '启用' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="remove" divided>
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </div>

    <!-- 新增/编辑人员 -->
    <el-dialog v-model="userDialogVisible" :title="isEdit ? '编辑人员' : '新增人员'" width="560px" destroy-on-close>
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="userForm.name" maxlength="30" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="工号" prop="jobNo">
          <el-input v-model="userForm.jobNo" maxlength="30" placeholder="全局唯一" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" maxlength="11" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="userForm.gender">
            <el-radio value="M">男</el-radio>
            <el-radio value="F">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属单位" prop="orgId">
          <el-tree-select
            v-model="userForm.orgId"
            :data="orgTree"
            :props="{ label: 'orgName', value: 'orgId', children: 'children' }"
            node-key="orgId"
            check-strictly
            :render-after-expand="false"
            default-expand-all
            placeholder="请选择所属单位"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="职务">
          <el-input v-model="userForm.position" maxlength="30" placeholder="请输入职务" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCodes">
          <el-select v-model="userForm.roleCodes" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in ROLES" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配 -->
    <el-dialog v-model="roleDialogVisible" title="角色分配" width="440px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <span>{{ currentUser?.name }}（{{ currentUser?.jobNo }}）</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="roleForm.roleCodes" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in ROLES" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importDialogVisible" title="批量导入人员" width="640px" destroy-on-close @closed="resetImport">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        accept=".xlsx"
        :limit="1"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        :on-exceed="onFileExceed"
      >
        <el-icon size="40" color="#909399"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 .xlsx 文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 文件，不超过 10MB</div>
        </template>
      </el-upload>
      <div class="import-options">
        <span class="import-label">导入模式：</span>
        <el-radio-group v-model="importMode">
          <el-radio value="INSERT_ONLY">仅新增（重复工号即失败行）</el-radio>
          <el-radio value="UPSERT">按工号更新（存在则更新）</el-radio>
        </el-radio-group>
        <el-button link type="primary" :icon="Download" @click="downloadTemplate">下载模板</el-button>
      </div>

      <template v-if="importResult">
        <el-alert
          :type="importResult.failCount > 0 ? 'warning' : 'success'"
          :closable="false"
          style="margin-top: 12px"
        >
          共 {{ importResult.totalCount }} 条，成功 {{ importResult.successCount }} 条，失败 {{ importResult.failCount }} 条
        </el-alert>
        <el-table
          v-if="importResult.failures && importResult.failures.length"
          :data="importResult.failures"
          border
          size="small"
          max-height="240"
          style="margin-top: 12px"
        >
          <el-table-column prop="rowNo" label="行号" width="70" />
          <el-table-column prop="jobNo" label="工号" width="120" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="reason" label="失败原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Download, Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import { orgApi, userApi } from '../../api'
import { ROLES, USER_STATUS, roleName } from '../../utils/dict'
import { downloadFile } from '../../utils/download'

// 组织树
const orgTreeRef = ref()
const orgTree = ref([])
const orgLoading = ref(false)

// 列表查询
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  orgId: null,
  includeSub: true,
  keyword: '',
  roleCode: '',
  status: null
})

async function loadOrgTree() {
  orgLoading.value = true
  try {
    orgTree.value = await orgApi.tree() || []
  } finally {
    orgLoading.value = false
  }
}

function buildParams() {
  const params = {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    includeSub: query.includeSub
  }
  if (query.orgId != null) params.orgId = query.orgId
  if (query.keyword) params.keyword = query.keyword
  if (query.roleCode) params.roleCode = query.roleCode
  if (query.status !== null && query.status !== '') params.status = query.status
  return params
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await userApi.page(buildParams())
    rows.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadUsers()
}

function onFilterChange() {
  if (query.orgId != null) onSearch()
}

function onReset() {
  query.keyword = ''
  query.roleCode = ''
  query.status = null
  query.pageNum = 1
  loadUsers()
}

function onOrgClick(data) {
  query.orgId = data.orgId
  onSearch()
}

function clearOrgFilter() {
  query.orgId = null
  orgTreeRef.value?.setCurrentKey(null)
  onSearch()
}

// 新增/编辑人员
const userFormRef = ref()
const userDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const userForm = reactive({
  userId: null,
  name: '',
  jobNo: '',
  phone: '',
  gender: 'M',
  orgId: null,
  position: '',
  roleCodes: []
})

const userRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  jobNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  orgId: [{ required: true, message: '请选择所属单位', trigger: 'change' }],
  roleCodes: [{ required: true, type: 'array', min: 1, message: '请至少选择一个角色', trigger: 'change' }]
}

function resetUserForm() {
  Object.assign(userForm, {
    userId: null,
    name: '',
    jobNo: '',
    phone: '',
    gender: 'M',
    orgId: null,
    position: '',
    roleCodes: []
  })
}

function openCreate() {
  isEdit.value = false
  resetUserForm()
  userForm.orgId = query.orgId
  userDialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  resetUserForm()
  Object.assign(userForm, {
    userId: row.userId,
    name: row.name,
    jobNo: row.jobNo,
    phone: row.phone,
    gender: row.gender || 'M',
    orgId: row.orgId,
    position: row.position || '',
    roleCodes: [...(row.roles || [])]
  })
  userDialogVisible.value = true
}

function showInitialPassword(pwd) {
  ElMessageBox.alert(
    `初始密码：${pwd}`,
    '请妥善保存',
    { confirmButtonText: '我已保存', type: 'warning' }
  )
}

async function submitUser() {
  await userFormRef.value.validate()
  saving.value = true
  try {
    const payload = {
      name: userForm.name,
      phone: userForm.phone,
      gender: userForm.gender,
      orgId: userForm.orgId,
      position: userForm.position,
      roleCodes: userForm.roleCodes
    }
    if (isEdit.value) {
      await userApi.update(userForm.userId, payload)
      ElMessage.success('修改成功')
    } else {
      const data = await userApi.create({ ...payload, jobNo: userForm.jobNo })
      ElMessage.success('新增成功')
      if (data?.initialPassword) showInitialPassword(data.initialPassword)
    }
    userDialogVisible.value = false
    loadUsers()
  } finally {
    saving.value = false
  }
}

// 重置密码
async function resetPwd(row) {
  await ElMessageBox.confirm(`确认重置「${row.name}」的登录密码？重置后该用户需重新登录。`, '提示', { type: 'warning' })
  const data = await userApi.resetPassword(row.userId)
  if (data?.initialPassword) showInitialPassword(data.initialPassword)
}

// 角色分配
const roleDialogVisible = ref(false)
const currentUser = ref(null)
const roleForm = reactive({ roleCodes: [] })

function openRoleDialog(row) {
  currentUser.value = row
  roleForm.roleCodes = [...(row.roles || [])]
  roleDialogVisible.value = true
}

async function submitRoles() {
  if (!roleForm.roleCodes.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  saving.value = true
  try {
    await userApi.setRoles(currentUser.value.userId, roleForm.roleCodes)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadUsers()
  } finally {
    saving.value = false
  }
}

// 状态切换：status=1 锁定(2)，status=2 解锁(1)，status=0 启用(1)
async function toggleStatus(row) {
  const target = row.status === 1 ? 2 : 1
  const action = row.status === 1 ? '锁定' : row.status === 2 ? '解锁' : '启用'
  await ElMessageBox.confirm(`确认${action}「${row.name}」的账号？`, '提示', { type: 'warning' })
  await userApi.setStatus(row.userId, target)
  ElMessage.success(`${action}成功`)
  loadUsers()
}

// 删除
async function removeUser(row) {
  await ElMessageBox.confirm(
    `确认删除「${row.name}」？账号将被停用且不可登录，历史学习与考试数据保留。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
  )
  await userApi.remove(row.userId)
  ElMessage.success('删除成功')
  loadUsers()
}

function handleCommand(cmd, row) {
  if (cmd === 'resetPwd') resetPwd(row)
  else if (cmd === 'toggleStatus') toggleStatus(row)
  else if (cmd === 'remove') removeUser(row)
}

// 批量导入
const uploadRef = ref()
const importDialogVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
const importMode = ref('INSERT_ONLY')
const importResult = ref(null)

function openImport() {
  importDialogVisible.value = true
}

function resetImport() {
  importFile.value = null
  importMode.value = 'INSERT_ONLY'
  importResult.value = null
  uploadRef.value?.clearFiles()
}

function onFileChange(file) {
  importFile.value = file.raw
  importResult.value = null
}

function onFileRemove() {
  importFile.value = null
}

function onFileExceed(files) {
  uploadRef.value.clearFiles()
  uploadRef.value.handleStart(files[0])
  importFile.value = files[0]
  importResult.value = null
}

function downloadTemplate() {
  downloadFile('/admin/users/import-template', '人员导入模板.xlsx')
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择导入文件')
    return
  }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    formData.append('mode', importMode.value)
    importResult.value = await userApi.importUsers(formData)
    if (importResult.value?.successCount > 0) loadUsers()
  } catch (e) {
    // 存在失败行时展示行级失败明细（拦截器已提示总体信息）
    if (e && e.data && (e.data.failures || e.data.failCount != null)) {
      importResult.value = e.data
    }
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadOrgTree()
  loadUsers()
})
</script>

<style scoped>
.user-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.org-panel {
  width: 280px;
  flex-shrink: 0;
  max-height: calc(100vh - 120px);
  overflow: auto;
}
.org-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.user-panel {
  flex: 1;
  min-width: 0;
}
.import-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.import-label {
  font-size: 14px;
  color: #606266;
}
</style>
