<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.noticeType" placeholder="通知类型" clearable style="width: 160px" @change="search">
          <el-option v-for="(label, value) in NOTICE_TYPES" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openDialog">发布通知</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="noticeTagType(row.noticeType)">
              {{ NOTICE_TYPES[row.noticeType] || row.noticeType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="targetDesc" label="目标" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sentCount" label="发送数" width="90" align="right" />
        <el-table-column prop="readCount" label="已读数" width="90" align="right" />
        <el-table-column label="阅读率" width="180">
          <template #default="{ row }">
            <el-progress :percentage="readRate(row)" :stroke-width="14" />
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="110" />
        <el-table-column prop="createdTime" label="创建时间" width="170" />
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="发布通知" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="通知类型" prop="noticeType">
          <el-select v-model="form.noticeType" style="width: 100%">
            <el-option v-for="(label, value) in NOTICE_TYPES" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入通知标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入通知内容（支持纯文本，富文本格式可后续扩展）" />
        </el-form-item>
        <el-form-item label="目标单位">
          <el-tree-select
            v-model="form.targetOrgIds"
            :data="orgTree"
            :props="treeProps"
            multiple
            check-strictly
            collapse-tags
            collapse-tags-tooltip
            placeholder="不选择则发送给全体人员"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi, orgApi } from '../../api'
import { NOTICE_TYPES } from '../../utils/dict'

const NOTICE_TAG_TYPES = { EXAM: 'primary', SCORE: 'success', LEARN_TASK: 'warning', GENERAL: 'info' }
const noticeTagType = (v) => NOTICE_TAG_TYPES[v] || 'info'

const query = reactive({ noticeType: '', pageNum: 1, pageSize: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const readRate = (row) => (row.sentCount > 0 ? Math.round((row.readCount / row.sentCount) * 100) : 0)

async function loadData() {
  loading.value = true
  try {
    const data = await sysApi.noticePage({
      noticeType: query.noticeType || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadData()
}

function reset() {
  query.noticeType = ''
  search()
}

// 发布通知
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ noticeType: 'GENERAL', title: '', content: '', targetOrgIds: [] })
const rules = {
  noticeType: [{ required: true, message: '请选择通知类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }]
}
const orgTree = ref([])
const treeProps = { label: 'orgName', value: 'orgId', children: 'children' }

async function openDialog() {
  dialogVisible.value = true
  if (orgTree.value.length === 0) {
    const data = await orgApi.tree()
    orgTree.value = data || []
  }
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const data = await sysApi.createNotice({
      noticeType: form.noticeType,
      title: form.title,
      content: form.content,
      targetOrgIds: form.targetOrgIds,
      channels: ['INBOX']
    })
    ElMessage.success(`通知已发布，成功发送 ${data.sentCount} 人`)
    dialogVisible.value = false
    form.noticeType = 'GENERAL'
    form.title = ''
    form.content = ''
    form.targetOrgIds = []
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
