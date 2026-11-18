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
        >
          <template #default="{ data }">
            <span class="cat-node" @contextmenu.prevent.stop="openCtxMenu(data, $event)">{{ data.catName }}</span>
          </template>
        </el-tree>
        <!-- 分类节点右键菜单：新增分类 / 编辑 / 删除（fixed 定位，跟随鼠标） -->
        <div v-show="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
          <div class="ctx-item" @click="ctxCreate">新增分类</div>
          <div class="ctx-item" @click="ctxEdit">编辑</div>
          <div class="ctx-divider"></div>
          <div class="ctx-item danger" @click="ctxRemove">删除</div>
        </div>
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
          <el-table-column prop="createdTime" label="创建时间" width="165">
            <template #default="{ row }">{{ row.createdTime ? row.createdTime.replace('T', ' ').slice(0, 19) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.materialType === 'DOC' || row.materialType === 'VIDEO'" link type="primary" @click="openPreview(row)">查看</el-button>
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
    <el-dialog v-model="formVisible" :title="form.materialId ? '编辑资料' : '新增资料'" width="80%" :close-on-click-modal="false" destroy-on-close top="4vh" @opened="dialogOpened = true" @closed="onFormDialogClosed">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="资料标题" prop="title">
          <el-input v-model="form.title" maxlength="100" placeholder="请输入资料标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" :precision="0" style="width: 140px" />
              <div style="color: #909399; font-size: 12px; line-height: 1.5">小值排在前面，0 表示不参与排序</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
          </el-col>
        </el-row>
        <el-form-item label="资料类型" prop="materialType">
          <el-radio-group v-model="form.materialType" @change="file = null; uploadRef?.clearFiles()">
            <el-radio v-for="(v, k) in MATERIAL_TYPES" :key="k" :value="k">{{ v.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.materialType === 'RICHTEXT'" label="外链解析">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="linkUrl" clearable placeholder="粘贴外网文章链接，点击解析自动抓取正文转为图文内容入库" style="flex: 1" />
            <el-button :loading="parsingUrl" @click="onParseUrl">解析链接</el-button>
          </div>
          <div style="color: #909399; font-size: 12px; line-height: 1.5">
            图文内容与外链二选一即可；解析成功后内容即固化到数据库，以后链接失效不影响已保存的图文
          </div>
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
              <div v-if="form.materialId && currentFileName" class="el-upload__tip current-file">
                当前文件：{{ currentFileName }}（重新选择文件可替换）
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="form.materialType === 'RICHTEXT'" label="图文内容" prop="contentHtml">
          <!-- 弹窗完全打开（@opened）后再创建编辑器：wangEditor 在隐藏/动画中的容器里初始化会导致选区失效、无法输入 -->
          <div class="editor-wrap" v-if="dialogOpened" @mousedown="onEditorWrapMouseDown" @click="onEditorWrapClick">
            <Toolbar :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" class="editor-toolbar" />
            <Editor
              v-model="form.contentHtml"
              :defaultConfig="editorConfig"
              mode="default"
              class="editor-body"
              @onCreated="onEditorCreated"
            />
          </div>
        </el-form-item>
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

    <!-- 资料预览（文档/视频） -->
    <el-dialog v-model="previewVisible" :title="'预览 - ' + preview.title" width="70%" top="4vh" destroy-on-close @closed="closePreview">
      <div v-loading="previewLoading" class="preview-body">
        <video v-if="preview.mode === 'video'" controls autoplay style="width: 100%; max-height: 64vh; background: #000">
          <source :src="preview.fileUrl" type="video/mp4" />
          <source v-if="preview.altFileUrl" :src="preview.altFileUrl" type="video/webm" />
        </video>
        <template v-else-if="preview.mode === 'pdf'">
          <div class="pv-bar">
            <el-button size="small" :disabled="preview.pageNo <= 1" @click="previewPage(preview.pageNo - 1)">上一页</el-button>
            <span>第 {{ preview.pageNo }} / {{ preview.pageCount || '?' }} 页</span>
            <el-button size="small" :disabled="preview.pageNo >= preview.pageCount" @click="previewPage(preview.pageNo + 1)">下一页</el-button>
          </div>
          <div class="pv-canvas">
            <img v-if="preview.pageUrl" :src="preview.pageUrl" style="max-width: 100%" alt="" />
          </div>
        </template>
        <div v-else-if="preview.mode === 'docx'" class="pv-docx" v-html="preview.html"></div>
        <el-empty v-else description="该资料暂不支持在线预览" />
      </div>
    </el-dialog>

    <!-- 分类维护（右键菜单入口：新增 / 编辑） -->
    <el-dialog v-model="catDialogVisible" :title="catForm.catId ? '编辑分类' : '新增分类'" width="520px">
      <el-form ref="catFormRef" :model="catForm" :rules="catRules" label-width="90px">
        <el-form-item v-if="!catForm.catId" label="上级分类">
          <el-input :model-value="catForm.parentName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="catName">
          <el-input v-model="catForm.catName" maxlength="50" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortNo">
          <el-input-number v-model="catForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <template v-if="catForm.catId">
          <el-form-item label="可见范围" prop="scopeType">
            <el-radio-group v-model="catForm.scopeType" @change="onCatScopeTypeChange">
              <el-radio v-for="(label, value) in SCOPE_TYPES" :key="value" :value="value">{{ label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="catForm.scopeType !== 'ALL'" label="可见单位" prop="scopeOrgIds">
            <el-tree-select
              v-model="catForm.scopeOrgIds"
              :data="catScopeOrgTree"
              :props="{ label: 'orgName', value: 'orgId', children: 'children' }"
              multiple
              check-strictly
              :render-after-expand="false"
              collapse-tags
              style="width: 100%"
              placeholder="请选择可见单位"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="catDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="catSubmitting" @click="submitCatForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 可见范围已并入「编辑资料」弹窗，操作列不再单独入口 -->
  </div>
</template>

<script setup>
import { ref, shallowRef, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Upload, RefreshLeft } from '@element-plus/icons-vue'
import '@wangeditor-next/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor-next/editor-for-vue'
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

// ---------------- 分类树右键菜单（新增分类 / 编辑 / 删除） ----------------
// 右键弹出，点击任意处收起；鼠标移开源节点行即自动收起（悬停菜单本身不收起）
const ctxMenu = reactive({ visible: false, x: 0, y: 0, data: null, sourceEl: null })

function openCtxMenu(data, e) {
  const w = 140
  const h = 150
  ctxMenu.data = data
  ctxMenu.x = Math.min(e.clientX, window.innerWidth - w - 8)
  ctxMenu.y = Math.min(e.clientY, window.innerHeight - h - 8)
  ctxMenu.sourceEl = e.currentTarget ? e.currentTarget.closest('.el-tree-node') : null
  ctxMenu.visible = true
}

function closeCtxMenu() {
  ctxMenu.visible = false
}

function onDocMouseMove(e) {
  if (!ctxMenu.visible) return
  const t = e.target
  if (!(t instanceof Element)) return
  if (t.closest('.ctx-menu')) return
  const row = t.closest('.el-tree-node')
  if (row && ctxMenu.sourceEl && row === ctxMenu.sourceEl) return
  closeCtxMenu()
}

// 分类维护弹窗（新增 / 编辑共用）
const catDialogVisible = ref(false)
const catFormRef = ref()
const catSubmitting = ref(false)
const catForm = ref({ catId: null, parentId: 0, parentName: '', catName: '', sortNo: 1, scopeType: 'ALL', scopeOrgIds: [] })
const catRules = {
  catName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sortNo: [{ required: true, message: '请填写排序号', trigger: 'blur' }],
  scopeOrgIds: [{
    validator: (r, v, cb) => {
      if (catForm.value.scopeType !== 'ALL' && (!v || !v.length)) cb(new Error('请选择可见单位'))
      else cb()
    },
    trigger: 'change'
  }]
}
const catScopeOrgTree = computed(() =>
  catForm.value.scopeType === 'L2_SUB' ? excludeRootOrg(orgTree.value) : orgTree.value
)

function onCatScopeTypeChange() {
  if (catForm.value.scopeType === 'L2_SUB') {
    catForm.value.scopeOrgIds = stripRootOrgIds(orgTree.value, catForm.value.scopeOrgIds)
  }
}

function ctxCreate() {
  const node = ctxMenu.data
  closeCtxMenu()
  if (!node) return
  catForm.value = { catId: null, parentId: node.catId, parentName: node.catName, catName: '', sortNo: 1, scopeType: 'ALL', scopeOrgIds: [] }
  catDialogVisible.value = true
}

function ctxEdit() {
  const node = ctxMenu.data
  closeCtxMenu()
  if (!node) return
  catForm.value = {
    catId: node.catId,
    parentId: node.parentId ?? 0,
    parentName: '',
    catName: node.catName,
    sortNo: node.sortNo ?? 1,
    scopeType: node.scopeType || 'ALL',
    scopeOrgIds: node.scopeOrgIds || []
  }
  catDialogVisible.value = true
}

async function ctxRemove() {
  const node = ctxMenu.data
  closeCtxMenu()
  if (!node) return
  await ElMessageBox.confirm(`确认删除分类「${node.catName}」？仅允许删除无子分类且无资料的分类。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
  await libApi.removeCategory(node.catId)
  ElMessage.success('删除成功')
  // 删除的正是当前筛选分类时，重置为全部
  if (currentCat.value && currentCat.value.catId === node.catId) filterCat(null)
  loadCatTree()
  loadList()
}

async function submitCatForm() {
  await catFormRef.value.validate()
  catSubmitting.value = true
  try {
    if (catForm.value.catId) {
      await libApi.updateCategory(catForm.value.catId, {
        catName: catForm.value.catName,
        sortNo: catForm.value.sortNo,
        scopeType: catForm.value.scopeType,
        scopeOrgIds: catForm.value.scopeType === 'ALL' ? [] : catForm.value.scopeOrgIds
      })
      ElMessage.success('编辑分类成功')
    } else {
      await libApi.createCategory({
        parentId: catForm.value.parentId,
        catName: catForm.value.catName,
        sortNo: catForm.value.sortNo
      })
      ElMessage.success('新增分类成功')
    }
    catDialogVisible.value = false
    loadCatTree()
    loadList()
  } finally {
    catSubmitting.value = false
  }
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

// 当前文件展示（编辑弹窗）：优先用详情返回的原始上传文件名 fileName；
// 历史数据无该字段时回退为「标题 + 存储文件扩展名」，避免展示 UUID 对象名
const currentFileName = computed(() => {
  const d = detailSnapshot.value
  if (!d) return ''
  if (d.fileName) return d.fileName
  const u = d.fileUrl
  if (!u) return ''
  try {
    const stored = decodeURIComponent(u.split('?')[0].split('/').pop() || '')
    const ext = stored.includes('.') ? stored.substring(stored.lastIndexOf('.')) : ''
    const base = (form.value.title || d.title || '文件').trim()
    return base + ext
  } catch (e) {
    return u.split('?')[0].split('/').pop() || ''
  }
})

// ---------------- 资料预览（文档/视频） ----------------
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref({
  materialId: null, title: '', mode: '',
  fileUrl: '', altFileUrl: '', pageNo: 1, pageCount: 0, pageUrl: '', html: ''
})

function adminHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('mgmt_accessToken') || ''}` }
}

async function openPreview(row) {
  preview.value = {
    materialId: row.materialId, title: row.title, mode: '',
    fileUrl: '', altFileUrl: '', pageNo: 1, pageCount: 0, pageUrl: '', html: ''
  }
  previewVisible.value = true
  previewLoading.value = true
  try {
    const info = await libApi.previewFile(row.materialId)
    const ext = (info.ext || '').toLowerCase()
    if (row.materialType === 'VIDEO') {
      preview.value.mode = 'video'
      preview.value.fileUrl = info.fileUrl
      preview.value.altFileUrl = info.altFileUrl
    } else if (ext === 'pdf') {
      preview.value.mode = 'pdf'
      await previewPage(1)
    } else if (ext === 'docx') {
      preview.value.mode = 'docx'
      const resp = await fetch(`/api/v1/admin/lib/materials/${row.materialId}/doc-html`, { headers: adminHeaders() })
      if (!resp.ok) throw new Error('文档解析失败')
      preview.value.html = await resp.text()
    } else {
      preview.value.mode = 'unsupported'
    }
  } catch (e) {
    ElMessage.error(e.message || '预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function previewPage(n) {
  if (!preview.value.materialId) return
  const resp = await fetch(`/api/v1/admin/lib/materials/${preview.value.materialId}/pages/${n}`, { headers: adminHeaders() })
  if (!resp.ok) throw new Error('页图加载失败')
  const tp = parseInt(resp.headers.get('X-Total-Pages') || '0', 10)
  if (tp > 0) preview.value.pageCount = tp
  const blob = await resp.blob()
  if (preview.value.pageUrl) URL.revokeObjectURL(preview.value.pageUrl)
  preview.value.pageUrl = URL.createObjectURL(blob)
  preview.value.pageNo = n
}

function closePreview() {
  if (preview.value.pageUrl) URL.revokeObjectURL(preview.value.pageUrl)
}

// ---------------- 富文本编辑器 / 外链解析 ----------------
const editorRef = shallowRef()
// 弹窗是否已完全打开：控制编辑器延迟创建（见模板注释）
const dialogOpened = ref(false)

function destroyEditor() {
  // editor-for-vue@5.1.12 卸载组件时不会自动销毁编辑器实例，需手动 destroy 防止泄漏
  if (editorRef.value) {
    editorRef.value.destroy()
    editorRef.value = undefined
  }
}

function onFormDialogClosed() {
  dialogOpened.value = false
  destroyEditor()
}
const linkUrl = ref('')
const parsingUrl = ref(false)
const toolbarConfig = {
  excludeKeys: ['group-video', 'insertVideo', 'uploadVideo', 'fullScreen', 'emotion']
}
const editorConfig = {
  placeholder: '请输入图文内容，或在上方粘贴外链后点击「解析链接」自动填充',
  MENU_CONF: {
    // 字体下拉：覆盖常用公文字体（宋体/仿宋/楷体/黑体/微软雅黑等）与西文字体
    fontFamily: {
      fontFamilyList: [
        '宋体', '仿宋', '仿宋_GB2312', '楷体', '楷体_GB2312', '黑体', '微软雅黑',
        '华文宋体', '华文仿宋', '华文楷体',
        'Arial', 'Times New Roman', 'Georgia', 'Verdana', 'Tahoma', 'Courier New'
      ]
    },
    // 行距下拉：外链解析回填的行距已换算为无单位倍数，需在此列表内才能被编辑器保留
    lineHeight: {
      lineHeightList: ['1', '1.15', '1.5', '1.6', '1.75', '1.8', '1.9', '2', '2.5', '3']
    },
    uploadImage: {
      // 无独立图床接口，5MB 内图片直接以 base64 内联入库
      base64LimitSize: 5 * 1024 * 1024
    }
  }
}

function onEditorCreated(editor) {
  editorRef.value = editor
}

// Firefox 点击编辑区空白处或图片时，浏览器会把 DOM 选区落在 contenteditable 根节点或图片包裹层上
// （而非文本节点），slate 无法把该选区映射为文档位置，导致英文/数字输入被静默丢弃
// （中文 IME 走 composition 链路所以不受影响）。
// mousedown 阶段拦截落在根节点上的点击：阻止浏览器产生坏选区，手动聚焦并把光标定位到文末；
// click 阶段再兜底校验：选区锚点不是文本节点时（根节点/图片包裹层等），直接重置 DOM 选区到
// 编辑器内最后一个文本节点末尾——走 slate 正常的 selectionchange 映射路径，不依赖其内部同步时序。
function onEditorWrapMouseDown(e) {
  const target = e.target
  if (target && target.nodeType === 1 && target.hasAttribute('data-slate-editor')) {
    e.preventDefault()
    target.focus()
    moveCaretToEditorTextEnd(target)
  }
}

function onEditorWrapClick(e) {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0 || !sel.anchorNode || sel.anchorNode.nodeType === 3) return
  const root = e.currentTarget.querySelector('[data-slate-editor]')
  if (!root || !root.contains(sel.anchorNode)) return
  moveCaretToEditorTextEnd(root)
}

function moveCaretToEditorTextEnd(root) {
  const sel = window.getSelection()
  if (!sel) return
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let last = null
  let n
  while ((n = walker.nextNode())) {
    if (n.textContent.length) last = n
  }
  if (last) {
    sel.setBaseAndExtent(last, last.textContent.length, last, last.textContent.length)
  }
}

onBeforeUnmount(() => {
  destroyEditor()
  document.removeEventListener('click', closeCtxMenu)
  document.removeEventListener('contextmenu', closeCtxMenu)
  document.removeEventListener('mousemove', onDocMouseMove)
})

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
  document.addEventListener('click', closeCtxMenu)
  document.addEventListener('contextmenu', closeCtxMenu)
  document.addEventListener('mousemove', onDocMouseMove)
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
.cat-node {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 分类树右键菜单 */
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
.ctx-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter, #ebeef5);
}
/* 图文富文本编辑器 */
.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.editor-toolbar {
  border-bottom: 1px solid #e4e7ed;
}
.editor-body {
  height: 320px !important;
  overflow-y: hidden;
}

.current-file {
  color: #606266;
}
.preview-body {
  min-height: 200px;
}
.pv-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
  margin-bottom: 10px;
}
.pv-canvas {
  text-align: center;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
}
.pv-docx {
  max-height: 64vh;
  overflow-y: auto;
  padding: 8px 16px;
  line-height: 1.9;
  word-break: break-word;
}
</style>
