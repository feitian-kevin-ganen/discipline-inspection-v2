<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate(null)">新增根级单位</el-button>
        <el-button :icon="Sort" @click="toggleExpand">{{ expandAll ? '收起' : '展开' }}</el-button>
        <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
        <span class="spacer"></span>
        <el-checkbox v-model="showDisabled" @change="loadTree">显示停用</el-checkbox>
      </div>

      <el-tree
        ref="treeRef"
        v-loading="loading"
        :data="treeData"
        node-key="orgId"
        :props="{ label: 'orgName', children: 'children' }"
        :expand-on-click-node="false"
        default-expand-all
      >
        <template #default="{ data }">
          <div class="custom-tree-node">
            <span class="node-info">
              <span class="node-name">{{ data.orgName }}</span>
              <el-tag size="small" effect="plain">{{ orgTypeName(data.orgType) }}</el-tag>
              <span class="user-count">{{ data.userCount ?? 0 }}人</span>
              <el-tag v-if="data.status === 0" size="small" type="info">停用</el-tag>
            </span>
            <span class="node-ops" @click.stop>
              <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, data)">
                <el-icon class="ops-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="add">新增下级</el-dropdown-item>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="toggleStatus">{{ data.status === 1 ? '停用' : '启用' }}</el-dropdown-item>
                    <el-dropdown-item command="moveUp">上移</el-dropdown-item>
                    <el-dropdown-item command="moveDown">下移</el-dropdown-item>
                    <el-dropdown-item command="remove" divided>
                      <span style="color: var(--el-color-danger)">删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </div>
        </template>
      </el-tree>
      <el-empty v-if="!loading && treeData.length === 0" description="暂无组织数据" />
    </div>

    <!-- 新增/编辑单位 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑单位' : '新增单位'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="!isEdit" label="上级单位">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'orgName', value: 'orgId', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            default-expand-all
            style="width: 100%"
            placeholder="不选则为根级单位"
          />
        </el-form-item>
        <el-form-item label="单位名称" prop="orgName">
          <el-input v-model="form.orgName" maxlength="50" placeholder="请输入单位名称" />
        </el-form-item>
        <el-form-item label="单位编码" prop="orgCode">
          <el-input v-model="form.orgCode" maxlength="30" placeholder="字母数字，全局唯一" />
        </el-form-item>
        <el-form-item label="单位类型" prop="orgType">
          <el-select v-model="form.orgType" placeholder="请选择单位类型" style="width: 100%">
            <el-option v-for="t in allowedOrgTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leaderName" maxlength="30" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" maxlength="20" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, Refresh, Sort } from '@element-plus/icons-vue'
import { orgApi } from '../../api'
import { ORG_TYPES, orgTypeName } from '../../utils/dict'

const treeRef = ref()
const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const treeData = ref([])
const showDisabled = ref(false)
const expandAll = ref(true)

// 新增单位时的上级单位可选项：完整组织树（不排除任何正常节点），根级用 0 表示
const parentOptions = computed(() => [{ orgId: 0, orgName: '根级单位（无上级）', children: treeData.value }])

// 在树中按 orgId 查找节点
function findNode(list, orgId) {
  for (const n of list) {
    if (n.orgId === orgId) return n
    const r = findNode(n.children || [], orgId)
    if (r) return r
  }
  return null
}

// 单位类型随上级单位联动：根级仅可为集团总部，子级类型不得高于上级层级
const TYPE_ORDER = { HQ: 1, L2: 2, SUB: 3, DEPT: 4 }
const allowedOrgTypes = computed(() => {
  const pid = form.parentId
  if (!pid) return ORG_TYPES.filter((t) => t.value === 'HQ')
  const parent = findNode(treeData.value, pid)
  if (!parent) return ORG_TYPES
  const parentOrder = TYPE_ORDER[parent.orgType] || 4
  return ORG_TYPES.filter((t) => TYPE_ORDER[t.value] > parentOrder)
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({
  orgId: null,
  parentId: 0,
  orgName: '',
  orgCode: '',
  orgType: '',
  leaderName: '',
  phone: '',
  sortNo: undefined
})

// 上级变化后，若当前类型不再合法则清空重选
watch(() => form.parentId, () => {
  if (!isEdit.value && form.orgType && !allowedOrgTypes.value.some((t) => t.value === form.orgType)) {
    form.orgType = ''
  }
})

const rules = {
  orgName: [{ required: true, message: '请输入单位名称', trigger: 'blur' }],
  orgCode: [
    { required: true, message: '请输入单位编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]+$/, message: '编码仅支持字母和数字', trigger: 'blur' }
  ],
  orgType: [{ required: true, message: '请选择单位类型', trigger: 'change' }]
}

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await orgApi.tree({ includeDisabled: showDisabled.value }) || []
  } finally {
    loading.value = false
  }
}

function toggleExpand() {
  expandAll.value = !expandAll.value
  const nodesMap = treeRef.value?.store?.nodesMap || {}
  Object.values(nodesMap).forEach((n) => {
    n.expanded = expandAll.value
  })
}

function resetForm() {
  Object.assign(form, {
    orgId: null,
    parentId: 0,
    orgName: '',
    orgCode: '',
    orgType: '',
    leaderName: '',
    phone: '',
    sortNo: undefined
  })
}

// parent 为 null 表示新增根级单位
function openCreate(parent) {
  isEdit.value = false
  resetForm()
  form.parentId = parent ? parent.orgId : 0
  dialogVisible.value = true
}

function openEdit(data) {
  isEdit.value = true
  resetForm()
  Object.assign(form, {
    orgId: data.orgId,
    orgName: data.orgName,
    orgCode: data.orgCode,
    orgType: data.orgType,
    leaderName: data.leaderName || '',
    phone: data.phone || '',
    sortNo: data.sortNo
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await orgApi.update(form.orgId, {
        orgName: form.orgName,
        orgCode: form.orgCode,
        orgType: form.orgType,
        leaderName: form.leaderName,
        phone: form.phone,
        sortNo: form.sortNo
      })
      ElMessage.success('修改成功')
    } else {
      await orgApi.create({
        parentId: form.parentId,
        orgName: form.orgName,
        orgCode: form.orgCode,
        orgType: form.orgType,
        leaderName: form.leaderName,
        phone: form.phone,
        sortNo: form.sortNo
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadTree()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(data) {
  const target = data.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    target === 0
      ? `停用后「${data.orgName}」下人员不可登录考生端，历史数据保留，确认停用？`
      : `确认启用「${data.orgName}」？`,
    '提示',
    { type: 'warning' }
  )
  await orgApi.setStatus(data.orgId, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  loadTree()
}

async function removeNode(data) {
  await ElMessageBox.confirm(
    `确认删除「${data.orgName}」？仅允许删除无子级且无在册人员的单位，删除后不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
  )
  await orgApi.remove(data.orgId)
  ElMessage.success('删除成功')
  loadTree()
}

// 在树中查找节点所在的同级数组及其 parentId
function findSiblings(list, orgId, parentId = 0) {
  for (const n of list) {
    if (n.orgId === orgId) return { siblings: list, parentId }
    const r = findSiblings(n.children || [], orgId, n.orgId)
    if (r) return r
  }
  return null
}

async function moveNode(data, dir) {
  const found = findSiblings(treeData.value, data.orgId)
  if (!found) return
  const { siblings, parentId } = found
  const idx = siblings.findIndex((n) => n.orgId === data.orgId)
  const target = idx + dir
  if (target < 0 || target >= siblings.length) {
    ElMessage.warning(dir < 0 ? '已在同级最前' : '已在同级最后')
    return
  }
  ;[siblings[idx], siblings[target]] = [siblings[target], siblings[idx]]
  await orgApi.sort({ parentId, orderedOrgIds: siblings.map((n) => n.orgId) })
  ElMessage.success('排序成功')
  loadTree()
}

function handleCommand(cmd, data) {
  if (cmd === 'add') openCreate(data)
  else if (cmd === 'edit') openEdit(data)
  else if (cmd === 'toggleStatus') toggleStatus(data)
  else if (cmd === 'remove') removeNode(data)
  else if (cmd === 'moveUp') moveNode(data, -1)
  else if (cmd === 'moveDown') moveNode(data, 1)
}

onMounted(loadTree)
</script>

<style scoped>
.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}
.node-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.user-count {
  font-size: 12px;
  color: #909399;
}
.node-ops {
  display: none;
  align-items: center;
}
.custom-tree-node:hover .node-ops {
  display: inline-flex;
}
.ops-icon {
  cursor: pointer;
  color: #606266;
  transform: rotate(90deg);
}
</style>
