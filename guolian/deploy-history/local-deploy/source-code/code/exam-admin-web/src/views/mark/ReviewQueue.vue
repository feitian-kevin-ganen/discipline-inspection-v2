<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.examId" placeholder="考试场次" clearable filterable style="width: 240px" @change="handleSearch">
          <el-option v-for="e in examOptions" :key="e.examId" :label="e.examName" :value="e.examId" />
        </el-select>
        <el-select v-model="query.reviewType" placeholder="复核类型" clearable style="width: 180px" @change="handleSearch">
          <el-option v-for="(label, k) in REVIEW_TYPES" :key="k" :label="label" :value="k" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reviewType === 'FILL_SIMILAR' ? 'warning' : 'danger'">
              {{ REVIEW_TYPES[row.reviewType] || row.reviewType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="examName" label="考试" min-width="160" show-overflow-tooltip />
        <el-table-column prop="stemText" label="题干" min-width="200" show-overflow-tooltip />
        <el-table-column prop="candidateAnswer" label="考生答案" min-width="160" show-overflow-tooltip />
        <el-table-column prop="standardAnswer" label="标准答案" min-width="140" show-overflow-tooltip />
        <el-table-column prop="autoScore" label="自动得分" width="90" align="center">
          <template #default="{ row }">{{ row.autoScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="firstScore" label="一评" width="80" align="center">
          <template #default="{ row }">{{ row.firstScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="secondScore" label="二评" width="80" align="center">
          <template #default="{ row }">{{ row.secondScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="gap" label="分差" width="80" align="center">
          <template #default="{ row }">{{ row.gap ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openReview(row)">复核</el-button>
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

    <el-dialog v-model="dialogVisible" title="人工复核" width="640px">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="复核类型">
            <el-tag :type="current.reviewType === 'FILL_SIMILAR' ? 'warning' : 'danger'">
              {{ REVIEW_TYPES[current.reviewType] || current.reviewType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="考试">{{ current.examName }}</el-descriptions-item>
          <el-descriptions-item label="题干">{{ current.stemText }}</el-descriptions-item>
          <el-descriptions-item label="考生答案">{{ current.candidateAnswer || '（未作答）' }}</el-descriptions-item>
          <el-descriptions-item label="标准答案">{{ current.standardAnswer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="评分情况">
            <span v-if="current.autoScore != null">自动得分：{{ current.autoScore }}</span>
            <span v-if="current.firstScore != null" style="margin-left: 12px">一评：{{ current.firstScore }}</span>
            <span v-if="current.secondScore != null" style="margin-left: 12px">二评：{{ current.secondScore }}</span>
            <span v-if="current.gap != null" style="margin-left: 12px">分差：{{ current.gap }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px" style="margin-top: 16px">
          <el-form-item label="最终得分" required>
            <el-input-number v-model="form.finalScore" :min="0" :step="0.5" step-strictly />
          </el-form-item>
          <el-form-item label="复核意见">
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="复核结论说明（选填）" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examApi, markApi } from '../../api'
import { REVIEW_TYPES } from '../../utils/dict'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const examOptions = ref([])
const dialogVisible = ref(false)
const current = ref(null)

const query = reactive({
  examId: null,
  reviewType: null,
  pageNum: 1,
  pageSize: 10
})
const form = reactive({ finalScore: null, comment: '' })

async function loadExams() {
  const data = await examApi.page({ pageNum: 1, pageSize: 200 })
  examOptions.value = data.list || []
}

async function loadList() {
  loading.value = true
  try {
    const data = await markApi.reviewQueue({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.examId = null
  query.reviewType = null
  query.pageNum = 1
  loadList()
}

function openReview(row) {
  current.value = row
  form.finalScore = row.autoScore ?? row.secondScore ?? row.firstScore ?? null
  form.comment = ''
  dialogVisible.value = true
}

async function submitReview() {
  if (form.finalScore == null || form.finalScore < 0) {
    ElMessage.warning('请填写有效的最终得分')
    return
  }
  submitting.value = true
  try {
    await markApi.submitReview({
      reviewId: current.value.reviewId,
      finalScore: form.finalScore,
      comment: form.comment
    })
    ElMessage.success('复核已提交')
    dialogVisible.value = false
    list.value = list.value.filter((i) => i.reviewId !== current.value.reviewId)
    total.value = Math.max(0, total.value - 1)
    if (!list.value.length && query.pageNum > 1) query.pageNum -= 1
    if (!list.value.length) loadList()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadExams()
  loadList()
})
</script>
