<template>
  <div class="page-container">
    <div style="display: flex; gap: 16px; align-items: flex-start">
      <!-- 左侧分类树 -->
      <div class="page-card" style="width: 240px; flex-shrink: 0">
        <div class="cat-header">
          <span class="cat-title">资料分类</span>
          <el-button link type="primary" size="small" @click="filterCat(null)">全部</el-button>
        </div>
        <el-tree
          ref="catTreeRef"
          :data="catTree"
          :props="{ label: 'catName', children: 'children' }"
          node-key="catId"
          highlight-current
          default-expand-all
          @node-click="(node) => filterCat(node)"
        />
      </div>

      <!-- 右侧资料列表 -->
      <div class="page-card" style="flex: 1; min-width: 0">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="标题 / 摘要 / 标签" clearable style="width: 220px" @keyup.enter="reload" @clear="reload" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="reload">
            <el-option v-for="(v, k) in MATERIAL_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="query.materialType" placeholder="类型" clearable style="width: 120px" @change="reload">
            <el-option v-for="(v, k) in MATERIAL_TYPES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-tag v-if="currentCat" closable @close="filterCat(null)">分类：{{ currentCat.catName }}</el-tag>
          <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
          <div class="spacer"></div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增资料</el-button>
        </div>

        <el-table v-loading="loading" :data="list" border>
          <el-table-column label="序号" width="70" align="center">
            <template #default="{ row }">{{ row.sortNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.summary || '-' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="MATERIAL_TYPES[row.materialType]?.type">{{ MATERIAL_TYPES[row.materialType]?.label || row.materialType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="catName" label="分类" width="120" show-overflow-tooltip />
          <el-table-column label="标签" min-width="140">
            <template #default="{ row }">
              <el-tag v-for="t in row.tags || []" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
              <span v-if="!row.tags || !row.tags.length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="MATERIAL_STATUS[row.status]?.type">{{ MATERIAL_STATUS[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100" align="right">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="时长" width="110" align="right">
            <template #default="{ row }">{{ formatSec(row.durationSec) }}</template>
          </el-table-column>
          <el-table-column prop="uploaderOrgName" label="上传单位" width="140" show-overflow-tooltip />
          <el-table-column prop="studyCount" label="学习次数" width="90" align="right" />
          <el-table-column prop="createdTime" label="创建时间" width="165" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'PENDING_AUDIT'" link type="warning" @click="openAudit(row)">审核</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="success" @click="handlePublish(row, 'PUBLISH')">发布</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="handlePublish(row, 'OFFLINE')">下架</el-button>
              <el-button v-if="row.status === 'OFFLINE'" link type="success" @click="handlePublish(row, 'PUBLISH')">上架</el-button>
              <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
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

    <!-- 新增 / 编辑资料 -->
    <el-dialog v-model="formVisible" :title="form.materialId ? '编辑资料' : '新增资料'" width="760px" :close-on-click-modal="false" destroy-on-close top="4vh">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="资料标题" prop="title">
          <el-input v-model="form.title" maxlength="100" placeholder="请输入资料标题" />
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" :precision="0" style="width: 160px" />
          <span style="margin-left: 8px; color: #909399">小值排在前面，0 表示不参与排序</span>
        </el-form-item>
        <el-form-item label="所属分类" prop="catId">
          <el-tree-select
            v-model="form.catId"
            :data="catTree"
            :props="{ label: 'catName', value: 'catId', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
            placeholder="请选择分类"
          />
        </el-form-item>
        <el-form-item label="资料类型" prop="materialType">
          <el-radio-group v-model="form.materialType" @change="file = null; uploadRef?.clearFiles()">
            <el-radio v-for="(v, k) in MATERIAL_TYPES" :key="k" :value="k">{{ v.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.materialType !== 'RICHTEXT'" label="资料文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :accept="fileAccept"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
          >
            <el-button :icon="Upload">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">
                {{ form.materialType === 'VIDEO' ? '仅支持 MP4 视频' : '支持 PDF / Word / PPT 文档' }}
                <template v-if="form.materialId">；不重新选择则保留原文件</template>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <template v-if="form.materialType === 'RICHTEXT'">
          <el-form-item label="外链解析">
            <div style="display: flex; gap: 8px; width: 100%">
              <el-input v-model="linkUrl" clearable placeholder="粘贴外网文章链接，点击解析自动抓取正文转为图文内容入库" style="flex: 1" />
              <el-button :loading="parsingUrl" @click="onParseUrl">解析链接</el-button>
            </div>
            <div style="color: #909399; font-size: 12px; line-height: 1.5">
              图文内容与外链二选一即可；解析成功后内容即固化到数据库，以后链接失效不影响已保存的图文
            </div>
          </el-form-item>
          <el-form-item label="图文内容" prop="contentHtml">
            <div class="editor-wrap">
              <QuillEditor
                v-model:content="form.contentHtml"
                contentType="html"
                theme="snow"
                :toolbar="quillToolbar"
                :options="quillOptions"
                class="editor-body"
              />
            </div>
          </el-form-item>
        </template>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔，如：党纪,条例" />
        </el-form-item>
        <el-form-item label="标称时长" prop="durationSec">
          <el-input-number v-model="form.durationSec" :min="0" :step="60" :precision="0" @blur="form.durationSec = form.durationSec == null ? form.durationSec : Math.round(Number(form.durationSec))" />
          <span style="margin-left: 8px; color: #909399">秒{{ form.materialType === 'VIDEO' ? '（视频必填）' : '（建议阅读时长，选填）' }}</span>
        </el-form-item>
        <el-form-item label="可见范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType" @change="onScopeTypeChange">
            <el-radio v-for="(label, value) in SCOPE_TYPES" :key="value" :value="value">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType !== 'ALL'" label="可见单位" prop="scopeOrgIds">
          <el-tree-select
            v-model="form.scopeOrgIds"
            :data="formScopeOrgTree"
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
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审核 -->
    <el-dialog v-model="auditVisible" title="资料审核" width="480px">
      <div style="margin-bottom: 12px">资料：<b>{{ auditRow?.title }}</b></div>
      <el-form label-width="90px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.action">
            <el-radio value="APPROVE">通过</el-radio>
            <el-radio value="REJECT">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="auditForm.action === 'REJECT' ? '驳回意见' : '审核意见'">
          <el-input v-model="auditForm.comment" type="textarea" :rows="3" :placeholder="auditForm.action === 'REJECT' ? '驳回时必填' : '选填'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 可见范围已并入「编辑资料」弹窗，操作列不再单独入口 -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Upload, RefreshLeft } from '@element-plus/icons-vue'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { libApi, orgApi } from '../../api'
import { MATERIAL_TYPES, MATERIAL_STATUS, SCOPE_TYPES } from '../../utils/dict'
import { excludeRootOrg, stripRootOrgIds } from '../../utils/org'
import { formatSec, formatSize } from '../../utils/download'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const catTree = ref([])
const orgTree = ref([])
const catTreeRef = ref()
const currentCat = ref(null)

const query = ref({ pageNum: 1, pageSize: 10, keyword: '', status: '', materialType: '', catId: undefined })

async function loadList() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (!params.status) delete params.status
    if (!params.materialType) delete params.materialType
    if (!params.keyword) delete params.keyword
    if (params.catId == null) delete params.catId
    const data = await libApi.page(params)
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
  query.value.status = ''
  query.value.materialType = ''
  currentCat.value = null
  query.value.catId = undefined
  catTreeRef.value?.setCurrentKey(null)
  reload()
}

function filterCat(node) {
  currentCat.value = node
  query.value.catId = node ? node.catId : undefined
  if (node) catTreeRef.value?.setCurrentKey(node.catId)
  else catTreeRef.value?.setCurrentKey(null)
  reload()
}

async function loadCatTree() {
  catTree.value = await libApi.categoryTree() || []
}

async function loadOrgTree() {
  orgTree.value = await orgApi.tree() || []
}

// ---------------- 新增 / 编辑 ----------------
const formVisible = ref(false)
const formRef = ref()
const uploadRef = ref()
const file = ref(null)
const detailSnapshot = ref(null)

const emptyForm = () => ({
  materialId: null,
  title: '',
  sortNo: 0,
  catId: undefined,
  materialType: 'DOC',
  summary: '',
  tags: '',
  contentHtml: '',
  durationSec: undefined,
  scopeType: 'ALL',
  scopeOrgIds: []
})
const form = ref(emptyForm())

// ---------------- 富文本编辑器 / 外链解析 ----------------
// wangeditor（Slate 内核）在 Chrome 129+ 存在 beforeinput 变更导致的按键吞字缺陷
//（中文 IME 可走 composition 通道，英文/数字被吞），已替换为 Quill 2。
const linkUrl = ref('')
const parsingUrl = ref(false)
const quillToolbar = [
  ['bold', 'italic', 'underline', 'strike'],
  [{ header: [1, 2, 3, false] }],
  [{ align: [] }],
  [{ list: 'ordered' }, { list: 'bullet' }],
  ['blockquote', 'link', 'image'],
  ['clean']
]
const quillOptions = {
  placeholder: '请输入图文内容，或在上方粘贴外链后点击「解析链接」自动填充'
}

function richtextText(html) {
  return (html || '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
}

async function onParseUrl() {
  if (!linkUrl.value.trim()) {
    ElMessage.warning('请先粘贴外网文章链接')
    return
  }
  parsingUrl.value = true
  try {
    const data = await libApi.fetchUrl(linkUrl.value.trim())
    if (!data.contentHtml || !richtextText(data.contentHtml)) {
      ElMessage.warning('链接解析成功但未提取到有效正文，请换其他链接或手动填写图文内容')
      return
    }
    form.value.contentHtml = data.contentHtml
    if (!form.value.title && data.title) form.value.title = data.title.slice(0, 100)
    ElMessage.success(`解析成功，已提取正文约 ${data.textLength} 字，可在下方编辑器中调整`)
  } finally {
    parsingUrl.value = false
  }
}

const formRules = {
  title: [{ required: true, message: '请输入资料标题', trigger: 'blur' }],
  catId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  materialType: [{ required: true, message: '请选择资料类型', trigger: 'change' }],
  contentHtml: [{
    validator: (r, v, cb) => {
      if (form.value.materialType === 'RICHTEXT' && !richtextText(v)) cb(new Error('请填写图文内容，或粘贴外链后点击「解析链接」'))
      else cb()
    },
    trigger: 'blur'
  }],
  durationSec: [{
    validator: (r, v, cb) => {
      if (v != null && !Number.isInteger(Number(v))) cb(new Error('标称时长必须为整数秒'))
      else if (form.value.materialType === 'VIDEO' && (v == null || v <= 0)) cb(new Error('视频资料必须填写标称时长'))
      else cb()
    },
    trigger: 'blur'
  }],
  scopeOrgIds: [{
    validator: (r, v, cb) => {
      if (form.value.scopeType !== 'ALL' && (!v || !v.length)) cb(new Error('请选择可见单位'))
      else cb()
    },
    trigger: 'change'
  }]
}

const fileAccept = computed(() => (form.value.materialType === 'VIDEO' ? '.mp4' : '.pdf,.doc,.docx,.ppt,.pptx'))

function onFileChange(uploadFile) {
  file.value = uploadFile.raw
}

function onFileExceed(files) {
  uploadRef.value.clearFiles()
  uploadRef.value.handleStart(files[0])
  file.value = files[0]
}

function openCreate() {
  form.value = emptyForm()
  file.value = null
  linkUrl.value = ''
  detailSnapshot.value = null
  formVisible.value = true
}

async function openEdit(row) {
  const d = await libApi.detail(row.materialId)
  detailSnapshot.value = d
  form.value = {
    materialId: d.materialId,
    title: d.title,
    sortNo: d.sortNo ?? 0,
    catId: d.catId,
    materialType: d.materialType,
    summary: d.summary || '',
    tags: (d.tags || []).join(','),
    contentHtml: d.contentHtml || '',
    durationSec: d.durationSec,
    scopeType: d.scopeType || 'ALL',
    scopeOrgIds: d.scopeOrgIds || []
  }
  file.value = null
  linkUrl.value = ''
  formVisible.value = true
}

async function submitForm() {
  // 图文：内容为空白但填了外链时，先自动解析外链再校验
  if (form.value.materialType === 'RICHTEXT' && !richtextText(form.value.contentHtml) && linkUrl.value.trim()) {
    await onParseUrl()
  }
  await formRef.value.validate()
  if (!form.value.materialId && form.value.materialType !== 'RICHTEXT' && !file.value) {
    ElMessage.warning('请选择资料文件')
    return
  }
  const f = form.value
  const scopeOrgStr = f.scopeType === 'ALL' ? '' : (f.scopeOrgIds || []).join(',')
  const fd = new FormData()

  if (!f.materialId) {
    // 新增：全字段提交
    if (file.value) fd.append('file', file.value)
    fd.append('title', f.title)
    fd.append('sortNo', f.sortNo ?? 0)
    fd.append('catId', f.catId)
    fd.append('materialType', f.materialType)
    if (f.summary) fd.append('summary', f.summary)
    if (f.tags) fd.append('tags', f.tags)
    if (f.materialType === 'RICHTEXT') fd.append('contentHtml', f.contentHtml || '')
    if (f.durationSec != null) fd.append('durationSec', f.durationSec)
    fd.append('scopeType', f.scopeType)
    if (scopeOrgStr) fd.append('scopeOrgIds', scopeOrgStr)
  } else {
    // 编辑：仅携带修改字段，file 仅在重新选择时携带
    const d = detailSnapshot.value
    const snap = {
      title: d.title,
      sortNo: d.sortNo ?? 0,
      catId: d.catId,
      materialType: d.materialType,
      summary: d.summary || '',
      tags: (d.tags || []).join(','),
      contentHtml: d.contentHtml || '',
      durationSec: d.durationSec,
      scopeType: d.scopeType || 'ALL',
      scopeOrgIds: (d.scopeOrgIds || []).join(',')
    }
    if (f.title !== snap.title) fd.append('title', f.title)
    if ((f.sortNo ?? 0) !== snap.sortNo) fd.append('sortNo', f.sortNo ?? 0)
    if (f.catId !== snap.catId) fd.append('catId', f.catId)
    if (f.materialType !== snap.materialType) fd.append('materialType', f.materialType)
    if (f.summary !== snap.summary) fd.append('summary', f.summary)
    if (f.tags !== snap.tags) fd.append('tags', f.tags)
    if (f.materialType === 'RICHTEXT' && f.contentHtml !== snap.contentHtml) fd.append('contentHtml', f.contentHtml)
    if (f.durationSec !== snap.durationSec && f.durationSec != null) fd.append('durationSec', f.durationSec)
    if (f.scopeType !== snap.scopeType) fd.append('scopeType', f.scopeType)
    if (scopeOrgStr !== snap.scopeOrgIds) fd.append('scopeOrgIds', scopeOrgStr)
    if (file.value) fd.append('file', file.value)
  }

  submitting.value = true
  try {
    if (!f.materialId) {
      await libApi.create(fd)
      ElMessage.success('新增资料成功')
    } else {
      await libApi.update(f.materialId, fd)
      ElMessage.success('编辑资料成功')
    }
    formVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

// ---------------- 审核 ----------------
const auditVisible = ref(false)
const auditRow = ref(null)
const auditForm = ref({ action: 'APPROVE', comment: '' })

function openAudit(row) {
  auditRow.value = row
  auditForm.value = { action: 'APPROVE', comment: '' }
  auditVisible.value = true
}

async function submitAudit() {
  if (auditForm.value.action === 'REJECT' && !auditForm.value.comment.trim()) {
    ElMessage.warning('驳回时必须填写审核意见')
    return
  }
  submitting.value = true
  try {
    await libApi.audit(auditRow.value.materialId, {
      action: auditForm.value.action,
      comment: auditForm.value.comment
    })
    ElMessage.success('审核已提交')
    auditVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

// ---------------- 发布 / 下架 ----------------
async function handlePublish(row, action) {
  const text = action === 'PUBLISH' ? '发布' : '下架'
  await ElMessageBox.confirm(`确认${text}资料「${row.title}」？`, `${text}确认`, { type: 'warning' })
  await libApi.publish(row.materialId, action)
  ElMessage.success(`${text}成功`)
  loadList()
}

// ---------------- 可见范围（已并入编辑弹窗，操作列不再单独入口） ----------------
// L2_SUB（指定二级集团及下属）模式下排除集团本部根节点
const formScopeOrgTree = computed(() =>
  form.value.scopeType === 'L2_SUB' ? excludeRootOrg(orgTree.value) : orgTree.value
)

function onScopeTypeChange() {
  if (form.value.scopeType === 'L2_SUB') {
    form.value.scopeOrgIds = stripRootOrgIds(orgTree.value, form.value.scopeOrgIds)
  }
}

// ---------------- 删除 ----------------
async function handleRemove(row) {
  await ElMessageBox.confirm(`确认删除资料「${row.title}」？删除后学员端不可见，学习记录保留。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
  await libApi.remove(row.materialId)
  ElMessage.success('删除成功')
  loadList()
}

onMounted(() => {
  loadCatTree()
  loadOrgTree()
  loadList()
})
</script>

<style scoped>
.cat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.cat-title {
  font-weight: 600;
  font-size: 14px;
}
/* 图文富文本编辑器（Quill） */
.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.editor-body :deep(.ql-toolbar) {
  border: none;
  border-bottom: 1px solid #e4e7ed;
}
.editor-body :deep(.ql-container) {
  border: none;
  height: 320px;
  font-size: 14px;
}
</style>
