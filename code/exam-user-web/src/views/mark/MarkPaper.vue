<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div>
            <span class="card-title">阅卷</span>
            <span class="text-secondary ml12" v-if="first">第 {{ first.sheetQuesSeq }} 题 · 满分 {{ first.fullScore }} 分</span>
          </div>
          <div class="header-right">
            <el-radio-group v-model="markedFilter" @change="onFilterChange" size="small">
              <el-radio-button value="false">待阅</el-radio-button>
              <el-radio-button value="true">已阅</el-radio-button>
              <el-radio-button value="">全部</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="$router.back()">返回任务列表</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!items.length" description="暂无待阅答卷" />

      <div v-for="item in items" :key="item.detailId" class="mark-item">
        <div class="m-head">
          <el-tag size="small" effect="dark">{{ item.anonymousNo }}</el-tag>
          <el-tag :type="item.marked ? 'success' : 'warning'" size="small">{{ item.marked ? '已评分' : '待评分' }}</el-tag>
          <span v-if="item.marked" class="text-secondary">上次评分：{{ item.myScore }} 分</span>
        </div>
        <div class="m-body">
          <div class="block">
            <div class="label">题目</div>
            <div class="rich-content" v-html="item.stem"></div>
          </div>
          <div class="block" v-if="item.reference">
            <div class="label">参考答案</div>
            <div class="rich-content ref" v-html="item.reference"></div>
          </div>
          <div class="block" v-if="item.keyPoints && item.keyPoints.length">
            <div class="label">评分要点</div>
            <ul class="keypoints">
              <li v-for="(kp, i) in item.keyPoints" :key="i">{{ kp }}</li>
            </ul>
          </div>
          <div class="block">
            <div class="label">考生答案</div>
            <div class="candidate-answer">{{ item.candidateAnswer || '（未作答）' }}</div>
          </div>
          <div class="score-row">
            <span class="label">评分</span>
            <el-input-number
              v-model="edits[item.detailId].score"
              :min="0"
              :max="item.fullScore"
              :step="1"
              :precision="1"
              style="width: 140px"
            />
            <span class="text-secondary">/ {{ item.fullScore }} 分</span>
            <el-input
              v-model="edits[item.detailId].comment"
              placeholder="评语（可选）"
              style="flex: 1; max-width: 420px"
            />
            <el-button
              type="primary"
              :loading="savingId === item.detailId"
              @click="onSave(item)"
            >
              提交评分
            </el-button>
          </div>
        </div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMarkItems, submitMarkScores } from '@/api/mark'

const route = useRoute()
const taskId = route.params.taskId

const loading = ref(true)
const items = ref([])
const markedFilter = ref('false')
const pageNum = ref(1)
const pageSize = 5
const total = ref(0)
const savingId = ref(null)
const edits = reactive({})

const first = computed(() => items.value[0] || null)

function onFilterChange() {
  pageNum.value = 1
  load()
}

async function onSave(item) {
  const e = edits[item.detailId]
  if (e.score == null) {
    ElMessage.warning('请先填写分值')
    return
  }
  if (e.score < 0 || e.score > item.fullScore) {
    ElMessage.warning(`分值须在 0 ~ ${item.fullScore} 之间`)
    return
  }
  savingId.value = item.detailId
  try {
    const res = await submitMarkScores({
      taskId: Number(taskId),
      scores: [{ detailId: item.detailId, score: e.score, comment: e.comment || '' }]
    })
    ElMessage.success(res?.taskStatus === 'DONE' ? '评分已提交，本任务已全部阅完' : '评分已提交')
    await load()
  } finally {
    savingId.value = null
  }
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (markedFilter.value !== '') params.marked = markedFilter.value
    const res = await getMarkItems(taskId, params)
    items.value = res.list || []
    total.value = res.total || 0
    items.value.forEach((it) => {
      if (!edits[it.detailId]) {
        edits[it.detailId] = { score: it.myScore ?? null, comment: it.myComment || '' }
      } else {
        if (edits[it.detailId].score == null && it.myScore != null) edits[it.detailId].score = it.myScore
      }
    })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-right {
  display: flex;
  gap: 12px;
  align-items: center;
}
.card-title {
  font-weight: 600;
}
.ml12 {
  margin-left: 12px;
}
.mark-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 16px;
}
.m-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}
.m-body {
  padding: 16px;
}
.block {
  margin-bottom: 14px;
}
.label {
  font-weight: 600;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.ref {
  background: #f0f9eb;
  padding: 10px;
  border-radius: 4px;
}
.keypoints {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
}
.candidate-answer {
  background: #fdf6ec;
  padding: 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  line-height: 1.8;
}
.score-row {
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px dashed #ebeef5;
  padding-top: 14px;
}
.score-row .label {
  margin-bottom: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
