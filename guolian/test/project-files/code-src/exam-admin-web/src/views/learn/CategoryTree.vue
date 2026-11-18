<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
        <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="tree" row-key="catId" :tree-props="{ children: 'children' }" border default-expand-all>
        <el-table-column prop="catName" label="分类名称" min-width="220" />
        <el-table-column prop="sortNo" label="排序号" width="100" align="center" />
        <el-table-column label="可见范围" width="180">
          <template #default="{ row }">{{ SCOPE_TYPES[row.scopeType] || row.scopeType || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增分类 -->
    <el-dialog v-model="createVisible" title="新增分类" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="父分类" prop="parentId">
          <el-tree-select
            v-model="createForm.parentId"
            :data="parentOptions"
            :props="{ label: 'catName', value: 'catId', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="catName">
          <el-input v-model="createForm.catName" maxlength="50" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortNo">
          <el-input-number v-model="createForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑分类 -->
    <el-dialog v-model="editVisible" title="编辑分类" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="分类名称" prop="catName">
          <el-input v-model="editForm.catName" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortNo">
          <el-input-number v-model="editForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="可见范围" prop="scopeType">
          <el-radio-group v-model="editForm.scopeType" @change="onScopeTypeChange">
            <el-radio v-for="(label, value) in SCOPE_TYPES" :key="value" :value="value">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.scopeType !== 'ALL'" label="可见单位" prop="scopeOrgIds">
          <el-tree-select
            v-model="editForm.scopeOrgIds"
            :data="editScopeOrgTree"
            :props="{ label: 'orgName', value: 'orgId', children: 'children' }"
            multiple
            check-strictly
            :render-after-expand="false"
            collapse-tags
            style="width: 100%"
            placeholder="请选择可见单位"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { libApi, orgApi } from '../../api'
import { SCOPE_TYPES } from '../../utils/dict'
import { excludeRootOrg, stripRootOrgIds } from '../../utils/org'

const loading = ref(false)
const submitting = ref(false)
const tree = ref([])
const orgTree = ref([])

// 新增分类时父分类可选 0=根级
const parentOptions = computed(() => [{ catId: 0, catName: '根级分类（顶层）', children: tree.value }])

async function loadTree() {
  loading.value = true
  try {
    tree.value = await libApi.categoryTree() || []
  } finally {
    loading.value = false
  }
}

async function loadOrgTree() {
  orgTree.value = await orgApi.tree() || []
}

// 新增
const createVisible = ref(false)
const createFormRef = ref()
const createForm = ref({ parentId: 0, catName: '', sortNo: 1 })
const createRules = {
  catName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sortNo: [{ required: true, message: '请填写排序号', trigger: 'blur' }]
}

function openCreate() {
  createForm.value = { parentId: 0, catName: '', sortNo: 1 }
  createVisible.value = true
}

async function submitCreate() {
  await createFormRef.value.validate()
  submitting.value = true
  try {
    await libApi.createCategory({ ...createForm.value })
    ElMessage.success('新增分类成功')
    createVisible.value = false
    loadTree()
  } finally {
    submitting.value = false
  }
}

// 编辑
const editVisible = ref(false)
const editFormRef = ref()
const editForm = ref({ catId: null, catName: '', sortNo: 1, scopeType: 'ALL', scopeOrgIds: [] })

// L2_SUB（指定二级集团及下属）模式下排除集团本部根节点
const editScopeOrgTree = computed(() =>
  editForm.value.scopeType === 'L2_SUB' ? excludeRootOrg(orgTree.value) : orgTree.value
)

function onScopeTypeChange() {
  if (editForm.value.scopeType === 'L2_SUB') {
    editForm.value.scopeOrgIds = stripRootOrgIds(orgTree.value, editForm.value.scopeOrgIds)
  }
}
const editRules = {
  catName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  scopeOrgIds: [{
    validator: (r, v, cb) => {
      if (editForm.value.scopeType !== 'ALL' && (!v || !v.length)) cb(new Error('请选择可见单位'))
      else cb()
    },
    trigger: 'change'
  }]
}

function openEdit(row) {
  editForm.value = {
    catId: row.catId,
    catName: row.catName,
    sortNo: row.sortNo,
    scopeType: row.scopeType || 'ALL',
    // 分类树接口暂未返回 scopeOrgIds（后端缺口），返回后即可正常回显
    scopeOrgIds: row.scopeOrgIds || []
  }
  editVisible.value = true
}

async function submitEdit() {
  await editFormRef.value.validate()
  submitting.value = true
  try {
    await libApi.updateCategory(editForm.value.catId, {
      catName: editForm.value.catName,
      sortNo: editForm.value.sortNo,
      scopeType: editForm.value.scopeType,
      scopeOrgIds: editForm.value.scopeType === 'ALL' ? [] : editForm.value.scopeOrgIds
    })
    ElMessage.success('编辑分类成功')
    editVisible.value = false
    loadTree()
  } finally {
    submitting.value = false
  }
}

// 删除
async function handleRemove(row) {
  await ElMessageBox.confirm(`确认删除分类「${row.catName}」？仅允许删除无子分类且无资料的分类。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
  await libApi.removeCategory(row.catId)
  ElMessage.success('删除成功')
  loadTree()
}

onMounted(() => {
  loadTree()
  loadOrgTree()
})
</script>
