<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="试卷名称关键词"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.paperType" placeholder="试卷类型" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="(v, k) in PAPER_TYPES" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="(v, k) in PAPER_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="router.push('/papers/edit')">创建试卷</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="paperName" label="试卷名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="PAPER_TYPES[row.paperType]?.type">{{ PAPER_TYPES[row.paperType]?.label || row.paperType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" align="center" />
        <el-table-column prop="passScore" label="及格分" width="80" align="center" />
        <el-table-column prop="quesCount" label="题数" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="PAPER_STATUS[row.status]?.type">{{ PAPER_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="useCount" label="使用次数" width="90" align="center" />
        <el-table-column prop="createdBy" label="创建人" width="110" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'SEALED'" link type="primary" @click="router.push(`/papers/edit/${row.paperId}`)">编辑</el-button>
            <el-button link type="primary" @click="router.push(`/papers/${row.paperId}/preview`)">预览</el-button>
            <el-button link type="primary" @click="handleCopy(row)">复制</el-button>
            <el-button v-if="row.status !== 'SEALED'" link type="warning" @click="handleSeal(row)">封存</el-button>
            <el-button v-if="row.useCount === 0" link type="danger" @click="handleRemove(row)">删除</el-button>
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
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paperApi } from '../../api'
import { PAPER_TYPES, PAPER_STATUS } from '../../utils/dict'
import { fmtTime } from '../../utils/format'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', paperType: '', status: '', pageNum: 1, pageSize: 10 })

const loadData = async () => {
  loading.value = true
  try {
    const data = await paperApi.page({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.paperType = ''
  query.status = ''
  handleSearch()
}

const handleCopy = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入新试卷名称', '复制试卷', {
    inputValue: `${row.paperName} 副本`,
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  })
  await paperApi.copy(row.paperId, value.trim())
  ElMessage.success('复制成功')
  loadData()
}

const handleSeal = async (row) => {
  await ElMessageBox.confirm(`确定封存试卷「${row.paperName}」吗？封存后不可再修改，仅可复制新版本。`, '封存确认', {
    type: 'warning'
  })
  await paperApi.seal(row.paperId)
  ElMessage.success('封存成功')
  loadData()
}

const handleRemove = async (row) => {
  await ElMessageBox.confirm(`确定删除试卷「${row.paperName}」吗？`, '删除确认', { type: 'warning' })
  await paperApi.remove(row.paperId)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
