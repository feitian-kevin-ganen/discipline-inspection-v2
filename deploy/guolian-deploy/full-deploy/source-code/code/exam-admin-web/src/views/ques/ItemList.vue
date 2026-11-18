<template>
  <div class="page-container">
    <div class="ques-layout">
      <!-- 左侧：题库分类树 -->
      <div class="page-card bank-panel">
        <div class="bank-panel-header">
          <span class="bank-panel-title">题库分类</span>
          <el-button type="primary" size="small" plain @click="openBankDialog(null)">新增根分类</el-button>
        </div>
        <el-tree
          ref="treeRef"
          :key="treeKey"
          :data="bankTree"
          :props="{ label: 'bankName', children: 'children' }"
          node-key="bankId"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="onBankClick"
        >
          <template #default="{ data }">
            <span class="custom-tree-node" @contextmenu.prevent.stop="openCtxMenu(data, $event)">
              <span class="node-label" :title="data.bankName + '（' + data.itemCount + '）'">{{ data.bankName }}（{{ data.itemCount }}）</span>
            </span>
          </template>
        </el-tree>
        <el-empty v-if="!bankTree.length" description="暂无分类" :image-size="60" />
        <!-- 分类节点右键菜单（fixed 定位，跟随鼠标） -->
        <div v-show="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
          <div class="ctx-item" @click="onCtxAddChild">新增子分类</div>
          <div class="ctx-item" @click="onCtxEdit">编辑</div>
          <div class="ctx-item danger" @click="onCtxRemove">删除</div>
        </div>
      </div>

      <!-- 右侧：题目列表 -->
      <div class="page-card item-panel">
        <!-- 筛选区 -->
        <div class="toolbar">
          <el-select v-model="query.quesType" placeholder="题型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(v, k) in QUES_TYPES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="query.difficulty" placeholder="难度" clearable style="width: 110px" @change="onSearch">
            <el-option v-for="(v, k) in DIFFICULTIES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(v, k) in QUES_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="query.genType" placeholder="来源" clearable style="width: 120px" @change="onSearch">
            <el-option label="AI生成" value="AI" />
            <el-option label="人工录入" value="MANUAL" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width: 200px" @keyup.enter="onSearch" />
          <el-input v-model="query.tag" placeholder="标签" clearable style="width: 130px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <span class="spacer" />
          <el-button type="primary" @click="router.push('/ques/items/edit')">新增题目</el-button>
          <el-button type="warning" plain @click="openImportDialog">批量导入</el-button>
        </div>

        <!-- 题目表格 -->
        <el-table :data="tableData" v-loading="loading" border stripe>
          <el-table-column prop="stemText" label="题干" min-width="260" show-overflow-tooltip />
          <el-table-column label="题型" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="QUES_TYPES[row.quesType]?.type">{{ QUES_TYPES[row.quesType]?.label || row.quesType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="DIFFICULTIES[row.difficulty]?.type">{{ DIFFICULTIES[row.difficulty]?.label || row.difficulty }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="scoreSuggest" label="建议分值" width="90" align="center" />
          <el-table-column label="标签" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-for="t in row.tags || []" :key="t" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
              <span v-if="!row.tags || !row.tags.length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="QUES_STATUS[row.status]?.type">{{ QUES_STATUS[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.genType === 'AI' ? 'warning' : 'info'" size="small">{{ row.genType === 'AI' ? 'AI生成' : '人工录入' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="useCount" label="使用次数" width="90" align="center" />
          <el-table-column label="正确率" width="90" align="center">
            <template #default="{ row }">{{ row.correctRate == null ? '-' : Number(row.correctRate).toFixed(1) + '%' }}</template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="160" align="center" />
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="router.push('/ques/items/edit/' + row.itemId)">编辑</el-button>
              <el-button v-if="row.status === 'PENDING_AUDIT'" link type="warning" size="small" @click="openAuditDialog(row)">审核</el-button>
              <el-button v-if="row.status === 'ENABLED'" link type="warning" size="small" @click="onDisable(row)">停用</el-button>
              <el-button v-if="row.status === 'DISABLED'" link type="success" size="small" @click="onEnable(row)">启用</el-button>
              <el-button link type="danger" size="small" @click="onRemoveClick(row)">删除</el-button>
            </template>
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
    </div>

    <!-- 分类维护 dialog -->
    <el-dialog v-model="bankDialog.visible" :title="bankDialog.isEdit ? '编辑分类' : '新增分类'" width="420px">
      <el-form :model="bankDialog.form" label-width="90px">
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="bankDialog.form.parentId"
            :data="bankTree"
            :props="{ label: 'bankName', value: 'bankId', children: 'children' }"
            check-strictly
            clearable
            placeholder="不选则为根分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="bankDialog.form.bankName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="bankDialog.form.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bankDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="bankDialog.loading" @click="onSaveBank">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审核 dialog -->
    <el-dialog v-model="auditDialog.visible" title="题目审核" width="420px">
      <el-form label-width="90px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditDialog.action">
            <el-radio value="ENABLE">通过（启用）</el-radio>
            <el-radio value="REJECT">驳回至草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditDialog.comment" type="textarea" :rows="3" placeholder="可填写审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="auditDialog.loading" @click="onAudit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 dialog -->
    <el-dialog v-model="importDialog.visible" title="批量导入题目" width="640px">
      <el-form label-width="90px">
        <el-form-item label="导入文件">
          <el-upload
            ref="uploadRef"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.docx"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
          >
            <el-icon size="40"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .xlsx / .docx 格式，单个文件不超过 20MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="归属分类">
          <el-tree-select
            v-model="importDialog.bankId"
            :data="bankTree"
            :props="{ label: 'bankName', value: 'bankId', children: 'children' }"
            check-strictly
            clearable
            placeholder="默认归属分类（模板内可逐行覆盖）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="模板">
          <el-button link type="primary" @click="downloadFile('/admin/ques/items/import-template', '题目导入模板.xlsx')">
            下载模板
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 导入结果 -->
      <template v-if="importDialog.result">
        <el-alert
          :type="importDialog.result.failCount > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${importDialog.result.totalCount} 条，成功 ${importDialog.result.successCount} 条，失败 ${importDialog.result.failCount} 条`"
          style="margin-bottom: 12px"
        />
        <el-table v-if="importDialog.result.failures?.length" :data="importDialog.result.failures" border size="small" max-height="240">
          <el-table-column prop="rowNo" label="行号" width="70" align="center" />
          <el-table-column prop="reason" label="失败原因" min-width="220" />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importDialog.visible = false">关闭</el-button>
        <el-button type="primary" :loading="importDialog.loading" @click="onImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quesApi } from '../../api'
import { QUES_TYPES, DIFFICULTIES, QUES_STATUS } from '../../utils/dict'
import { downloadFile } from '../../utils/download'

const router = useRouter()

// ---------- 题库分类树 ----------
const treeRef = ref()
const bankTree = ref([])
const currentBankId = ref(null)
// 增删改后重挂树组件：保持 default-expand-all 展开态（数据替换会导致新节点折叠）
const treeKey = ref(0)

const loadBankTree = async () => {
  bankTree.value = (await quesApi.bankTree()) || []
}

// 分类树局部刷新：重新拉取 + 重挂组件保持展开 + 恢复当前选中
const refreshBankTree = async () => {
  await loadBankTree()
  treeKey.value++
  await nextTick()
  if (currentBankId.value) {
    treeRef.value?.setCurrentKey(currentBankId.value)
  }
}

const onBankClick = (data) => {
  currentBankId.value = data.bankId
  query.pageNum = 1
  loadList()
}

// 分类维护 dialog
const bankDialog = reactive({
  visible: false,
  isEdit: false,
  loading: false,
  form: { bankId: null, parentId: null, bankName: '', sortNo: 1 }
})

const openBankDialog = (editData, parentData) => {
  bankDialog.isEdit = !!editData
  bankDialog.form = editData
    ? { bankId: editData.bankId, parentId: editData.parentId || null, bankName: editData.bankName, sortNo: editData.sortNo ?? 1 }
    : { bankId: null, parentId: parentData ? parentData.bankId : null, bankName: '', sortNo: 1 }
  bankDialog.visible = true
}

const onSaveBank = async () => {
  if (!bankDialog.form.bankName?.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  bankDialog.loading = true
  try {
    if (bankDialog.isEdit) {
      // 后端校验 parentId 必填（编辑时实际忽略，仅过校验；根节点传 0）
      await quesApi.updateBank(bankDialog.form.bankId, {
        parentId: bankDialog.form.parentId ?? 0,
        bankName: bankDialog.form.bankName,
        sortNo: bankDialog.form.sortNo
      })
    } else {
      await quesApi.createBank({
        parentId: bankDialog.form.parentId || 0,
        bankName: bankDialog.form.bankName,
        sortNo: bankDialog.form.sortNo
      })
    }
    ElMessage.success('保存成功')
    bankDialog.visible = false
    await refreshBankTree()
  } finally {
    bankDialog.loading = false
  }
}

const onRemoveBank = async (data) => {
  if ((data.children && data.children.length) || data.itemCount > 0) {
    ElMessage.warning('该分类下存在子分类或题目，不可删除')
    return
  }
  await ElMessageBox.confirm(`确定删除分类「${data.bankName}」吗？存在子分类或题目时不可删除。`, '删除确认', { type: 'warning' })
  await quesApi.removeBank(data.bankId)
  ElMessage.success('删除成功')
  if (currentBankId.value === data.bankId) currentBankId.value = null
  await refreshBankTree()
  loadList()
}

// 分类节点右键菜单（替代原 hover 操作条）：右键弹出，点击任意处收起；
// 鼠标离开源节点行、移到其他行上时自动收起（悬停在菜单本身不收起）
const ctxMenu = reactive({ visible: false, x: 0, y: 0, data: null, sourceEl: null })

const openCtxMenu = (data, e) => {
  const w = 140
  const h = 110
  ctxMenu.data = data
  ctxMenu.x = Math.min(e.clientX, window.innerWidth - w - 8)
  ctxMenu.y = Math.min(e.clientY, window.innerHeight - h - 8)
  ctxMenu.sourceEl = e.currentTarget ? e.currentTarget.closest('.el-tree-node') : null
  ctxMenu.visible = true
}

const closeCtxMenu = () => {
  ctxMenu.visible = false
}

// 菜单打开期间：鼠标移到其他树节点行 → 自动收起
const onDocMouseMove = (e) => {
  if (!ctxMenu.visible) return
  const t = e.target
  if (!(t instanceof Element)) return
  if (t.closest('.ctx-menu')) return
  const row = t.closest('.el-tree-node')
  if (row && ctxMenu.sourceEl && row !== ctxMenu.sourceEl) closeCtxMenu()
}

const onCtxAddChild = () => {
  openBankDialog(null, ctxMenu.data)
  closeCtxMenu()
}

const onCtxEdit = () => {
  openBankDialog(ctxMenu.data)
  closeCtxMenu()
}

const onCtxRemove = () => {
  onRemoveBank(ctxMenu.data)
  closeCtxMenu()
}

// ---------- 题目列表 ----------
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  quesType: '',
  difficulty: '',
  status: '',
  genType: '',
  keyword: '',
  tag: ''
})

const loadList = async () => {
  loading.value = true
  try {
    const data = await quesApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      bankId: currentBankId.value || undefined,
      quesType: query.quesType || undefined,
      difficulty: query.difficulty || undefined,
      status: query.status || undefined,
      genType: query.genType || undefined,
      keyword: query.keyword || undefined,
      tag: query.tag || undefined
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  query.pageNum = 1
  loadList()
}

const onReset = () => {
  query.quesType = ''
  query.difficulty = ''
  query.status = ''
  query.genType = ''
  query.keyword = ''
  query.tag = ''
  currentBankId.value = null
  treeRef.value?.setCurrentKey(null)
  onSearch()
}

// ---------- 审核 ----------
const auditDialog = reactive({ visible: false, loading: false, itemId: null, action: 'ENABLE', comment: '' })

const openAuditDialog = (row) => {
  auditDialog.itemId = row.itemId
  auditDialog.action = 'ENABLE'
  auditDialog.comment = ''
  auditDialog.visible = true
}

const onAudit = async () => {
  auditDialog.loading = true
  try {
    await quesApi.audit(auditDialog.itemId, { action: auditDialog.action, comment: auditDialog.comment })
    ElMessage.success('审核已提交')
    auditDialog.visible = false
    loadList()
  } finally {
    auditDialog.loading = false
  }
}

const onDisable = async (row) => {
  await ElMessageBox.confirm(`确定停用题目「${row.stemText}」吗？`, '停用确认', { type: 'warning' })
  await quesApi.audit(row.itemId, { action: 'DISABLE', comment: '' })
  ElMessage.success('已停用')
  loadList()
}

const onEnable = async (row) => {
  await ElMessageBox.confirm(`确定重新启用题目「${row.stemText}」吗？`, '启用确认', { type: 'warning' })
  await quesApi.audit(row.itemId, { action: 'ENABLE', comment: '' })
  ElMessage.success('已启用')
  loadList()
}

// ---------- 删除 ----------
// 删除优先物理删除（不可恢复）；已被试卷引用或已使用的题目（40004）不可删除，
// 询问后降级为停用（软删除，保留历史数据可追溯）
const onRemoveClick = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除题目「${row.stemText}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await quesApi.remove(row.itemId, true, { __silent: true })
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    if (e && e.code === 40004) {
      try {
        await ElMessageBox.confirm('该题目已被试卷引用或已使用，不可删除。是否改为停用？', '无法删除', {
          type: 'warning',
          confirmButtonText: '改为停用',
          cancelButtonText: '取消'
        })
      } catch (ignored) {
        return
      }
      await quesApi.remove(row.itemId, false, { __silent: true })
      ElMessage.success('已停用')
      loadList()
    }
    // 其他错误（权限/网络等）已由拦截器提示
  }
}

// ---------- 批量导入 ----------
const uploadRef = ref()
const importDialog = reactive({ visible: false, loading: false, bankId: null, file: null, result: null })

const openImportDialog = () => {
  importDialog.bankId = currentBankId.value
  importDialog.file = null
  importDialog.result = null
  importDialog.visible = true
}

const onFileChange = (file) => {
  importDialog.file = file.raw
}

const onFileExceed = (files) => {
  uploadRef.value.clearFiles()
  uploadRef.value.handleStart(files[0])
  importDialog.file = files[0]
}

const onImport = async () => {
  if (!importDialog.bankId) {
    ElMessage.warning('请先选择归属题库分类')
    return
  }
  if (!importDialog.file) {
    ElMessage.warning('请先选择导入文件')
    return
  }
  const fd = new FormData()
  fd.append('file', importDialog.file)
  if (importDialog.bankId) fd.append('bankId', importDialog.bankId)
  importDialog.loading = true
  try {
    importDialog.result = await quesApi.importItems(fd)
    ElMessage.success('导入完成')
    loadBankTree()
    loadList()
  } catch (e) {
    // 导入存在失败行时，展示行级失败明细（拦截器已提示总体信息）
    if (e && e.data && (e.data.failures || e.data.failCount != null)) {
      importDialog.result = e.data
    }
  } finally {
    importDialog.loading = false
  }
}

onMounted(() => {
  loadBankTree()
  loadList()
  document.addEventListener('click', closeCtxMenu)
  document.addEventListener('contextmenu', closeCtxMenu)
  document.addEventListener('mousemove', onDocMouseMove)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeCtxMenu)
  document.removeEventListener('contextmenu', closeCtxMenu)
  document.removeEventListener('mousemove', onDocMouseMove)
})
</script>

<style scoped>
.ques-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.bank-panel {
  width: 280px;
  flex-shrink: 0;
}
.bank-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.bank-panel-title {
  font-weight: 600;
}
.item-panel {
  flex: 1;
  min-width: 0;
}
.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  position: relative;
  flex-wrap: wrap;
}
.node-label {
  flex: 1 1 auto;
  min-width: 0;
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
  padding-right: 8px;
}
.custom-tree-node:hover {
  background: transparent;
}
/* 分类节点右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 128px;
  background: #fff;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
}
.ctx-item {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  line-height: 1.4;
}
.ctx-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-color-primary, #409eff);
}
.ctx-item.danger:hover {
  color: var(--el-color-danger, #f56c6c);
}
/* 分类名换行后行高自适应：el-tree 节点默认 26px 固定高，多行文本会与上下节点重叠 */
.bank-panel :deep(.el-tree-node__content) {
  height: auto;
  min-height: 26px;
  align-items: flex-start;
  padding-top: 3px;
  padding-bottom: 3px;
}
/* 展开箭头与第一行文字对齐（默认垂直居中对多行文本不适用） */
.bank-panel :deep(.el-tree-node__expand-icon) {
  margin-top: 3px;
  flex-shrink: 0;
}
.bank-panel :deep(.el-tree-node) {
  white-space: normal;
}
</style>
