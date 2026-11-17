<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-button @click="router.back()">返回</el-button>
        <el-radio-group v-model="markedFilter" @change="handleFilterChange">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="false">未阅</el-radio-button>
          <el-radio-button :value="true">已阅</el-radio-button>
        </el-radio-group>
        <div class="spacer"></div>
        <span class="progress-text">已阅 {{ markedCount }} / {{ totalCount }}</span>
        <el-progress :percentage="totalCount ? Math.round((markedCount / totalCount) * 100) : 0" :stroke-width="12" style="width: 220px" />
        <el-button type="warning" :disabled="!pendingScores.length" :loading="submitting" @click="submitBatch">
          批量提交（{{ pendingScores.length }}）
        </el-button>
      </div>

      <div class="mark-layout">
        <div class="sheet-list">
          <el-table v-loading="loading" :data="list" border highlight-current-row :current-row-key="current?.detailId" row-key="detailId" @current-change="handleSelect">
            <el-table-column prop="anonymousNo" label="答卷编号" width="100" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.marked ? 'success' : 'info'" size="small">{{ row.marked ? '已阅' : '未阅' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="得分" width="80" align="center">
              <template #default="{ row }">{{ displayScore(row) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadItems"
              @current-change="loadItems"
            />
          </div>
        </div>

        <div v-if="current" class="mark-panel">
          <el-card shadow="never" class="block-card">
            <template #header>
              <div class="card-header">
                <span>题目</span>
                <el-tag type="danger">满分 {{ current.fullScore }} 分</el-tag>
              </div>
            </template>
            <div class="stem-html" v-html="current.stem"></div>
          </el-card>

          <el-card shadow="never" class="block-card">
            <template #header><span>参考答案与评分要点</span></template>
            <div class="reference">{{ current.reference || '无' }}</div>
            <div v-if="current.keyPoints?.length" class="key-points">
              <el-tag v-for="(kp, i) in current.keyPoints" :key="i" type="warning" effect="plain">{{ i + 1 }}. {{ kp }}</el-tag>
            </div>
          </el-card>

          <el-card shadow="never" class="block-card">
            <template #header><span>考生答案（{{ current.anonymousNo }}）</span></template>
            <pre class="candidate-answer">{{ current.candidateAnswer || '（未作答）' }}</pre>
          </el-card>

          <el-card shadow="never" class="block-card">
            <template #header><span>打分</span></template>
            <div class="score-area">
              <span class="score-label">得分：</span>
              <el-input-number v-model="form.score" :min="0" :max="current.fullScore" :step="0.5" step-strictly />
              <span class="score-hint">（0 ~ {{ current.fullScore }} 分）</span>
            </div>
            <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="评语（选填）" style="margin-top: 12px" />
            <div class="score-actions">
              <el-button :disabled="form.score == null" @click="stashScore">暂存</el-button>
              <el-button type="primary" :loading="submitting" :disabled="form.score == null" @click="submitAndNext">提交并下一份</el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="请从左侧选择答卷" class="mark-panel" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { markApi } from '../../api'

const route = useRoute()
const router = useRouter()
const taskId = route.params.taskId

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const current = ref(null)
const markedFilter = ref(null)
const totalCount = ref(0)
const markedCount = ref(0)
const pendingScores = ref([])

const query = reactive({ pageNum: 1, pageSize: 10 })
const form = reactive({ score: null, comment: '' })

const pendingMap = computed(() => new Map(pendingScores.value.map((s) => [s.detailId, s])))

function displayScore(row) {
  const p = pendingMap.value.get(row.detailId)
  if (p) return p.score + '*'
  return row.myScore ?? '-'
}

async function loadProgress() {
  const [all, unmarked] = await Promise.all([
    markApi.items(taskId, { pageNum: 1, pageSize: 1 }),
    markApi.items(taskId, { marked: false, pageNum: 1, pageSize: 1 })
  ])
  totalCount.value = all.total || 0
  markedCount.value = (all.total || 0) - (unmarked.total || 0)
}

async function loadItems() {
  loading.value = true
  try {
    const params = { ...query }
    if (markedFilter.value !== null) params.marked = markedFilter.value
    const data = await markApi.items(taskId, params)
    list.value = data.list || []
    total.value = data.total || 0
    if (current.value && !list.value.some((i) => i.detailId === current.value.detailId)) {
      current.value = null
    }
    if (!current.value && list.value.length) selectRow(list.value[0])
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  query.pageNum = 1
  current.value = null
  loadItems()
}

function selectRow(row) {
  current.value = row
  const p = pendingMap.value.get(row.detailId)
  form.score = p ? p.score : row.myScore ?? null
  form.comment = p ? p.comment : row.myComment || ''
}

function handleSelect(row) {
  if (row) selectRow(row)
}

function validScore(score) {
  return score != null && score >= 0 && score <= current.value.fullScore
}

function stashScore() {
  if (!current.value) return
  if (!validScore(form.score)) {
    ElMessage.warning(`分值须在 0 ~ ${current.value.fullScore} 之间`)
    return
  }
  const idx = pendingScores.value.findIndex((s) => s.detailId === current.value.detailId)
  const item = { detailId: current.value.detailId, score: form.score, comment: form.comment }
  if (idx >= 0) pendingScores.value.splice(idx, 1, item)
  else pendingScores.value.push(item)
  ElMessage.success(`已暂存，待批量提交 ${pendingScores.value.length} 份`)
}

async function doSubmit(scores) {
  submitting.value = true
  try {
    const data = await markApi.submitScores({ taskId, scores })
    ElMessage.success(`已提交 ${data?.savedCount ?? scores.length} 份评分`)
    await Promise.all([loadItems(), loadProgress()])
    return true
  } finally {
    submitting.value = false
  }
}

async function submitBatch() {
  if (!pendingScores.value.length) return
  const scores = [...pendingScores.value]
  const ok = await doSubmit(scores)
  if (!ok) return
  pendingScores.value = []
  ElMessage.success('批量提交完成，已返回阅卷任务列表')
  router.replace('/mark/tasks')
}

async function submitAndNext() {
  if (!current.value) return
  if (!validScore(form.score)) {
    ElMessage.warning(`分值须在 0 ~ ${current.value.fullScore} 之间`)
    return
  }
  const currentDetailId = current.value.detailId
  const scores = [{ detailId: currentDetailId, score: form.score, comment: form.comment }]
  const ok = await doSubmit(scores)
  if (!ok) return
  pendingScores.value = pendingScores.value.filter((s) => s.detailId !== currentDetailId)
  // 切到下一份未阅答卷
  const next = list.value.find((i) => !i.marked && i.detailId !== currentDetailId)
  if (next) selectRow(next)
  else current.value = null
}

onMounted(() => {
  loadItems()
  loadProgress()
})
</script>

<style scoped>
.progress-text {
  font-size: 13px;
  color: #606266;
}
.mark-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.sheet-list {
  width: 320px;
  flex-shrink: 0;
}
.mark-panel {
  flex: 1;
  min-width: 0;
}
.block-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.reference {
  white-space: pre-wrap;
  color: #303133;
  line-height: 1.7;
}
.key-points {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.candidate-answer {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  line-height: 1.7;
  color: #303133;
}
.score-area {
  display: flex;
  align-items: center;
  gap: 10px;
}
.score-label {
  font-weight: 600;
}
.score-hint {
  font-size: 12px;
  color: #909399;
}
.score-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
