<template>
  <div class="page" v-loading="loading">
    <template v-if="result">
      <el-card shadow="never" class="mb16" v-if="result.scorePublished">
        <template #header>
          <div class="header">
            <span class="card-title">{{ result.examName }} — 成绩详情</span>
            <el-button size="small" @click="$router.back()">返回</el-button>
          </div>
        </template>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="我的得分">
            <span class="score">{{ result.myScore }}</span> / {{ result.totalScore }}
          </el-descriptions-item>
          <el-descriptions-item label="等级">
            <el-tag :type="result.passed ? 'success' : 'danger'">{{ result.grade || '—' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="是否通过">{{ result.passed ? '通过' : '未通过' }}</el-descriptions-item>
          <el-descriptions-item label="客观题 / 主观题">
            {{ result.objectiveScore ?? '—' }} / {{ result.subjectiveScore ?? '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" v-if="result.scorePublished">
        <template #header><span class="card-title">逐题作答情况</span></template>
        <div v-for="d in result.details || []" :key="d.seq" class="detail-item">
          <div class="d-head">
            <span class="seq">第 {{ d.seq }} 题</span>
            <el-tag size="small">{{ QUES_TYPE_NAME[d.quesType] || d.quesType }}</el-tag>
            <el-tag :type="d.isCorrect ? 'success' : 'danger'" size="small">
              {{ d.isCorrect ? '正确' : '错误' }}
            </el-tag>
            <span class="text-secondary">得分 {{ d.gainedScore ?? 0 }} 分</span>
          </div>
          <div class="stem rich-content" v-html="d.stem"></div>

          <!-- 选择题选项 -->
          <div v-if="d.options && d.options.length" class="options">
            <div
              v-for="opt in d.options"
              :key="opt.optKey"
              class="opt"
              :class="{
                correct: ansArr(d.correctAnswer).includes(opt.optKey),
                mine: ansArr(d.myAnswer).includes(opt.optKey) && !ansArr(d.correctAnswer).includes(opt.optKey)
              }"
            >
              <span class="opt-key">{{ opt.optKey }}.</span>
              <span class="rich-content" v-html="opt.content"></span>
              <el-icon v-if="ansArr(d.correctAnswer).includes(opt.optKey)" color="#67c23a"><CircleCheckFilled /></el-icon>
              <el-icon v-else-if="ansArr(d.myAnswer).includes(opt.optKey)" color="#f56c6c"><CircleCloseFilled /></el-icon>
            </div>
          </div>

          <div class="answer-line">
            <span>我的答案：<b :class="{ wrong: !d.isCorrect }">{{ fmtAnswer(d.myAnswer, d.quesType) }}</b></span>
            <span>正确答案：<b class="right">{{ fmtAnswer(d.correctAnswer, d.quesType) }}</b></span>
          </div>
          <el-alert
            v-if="result.showAnalysis && d.analysis"
            type="info"
            :closable="false"
            class="analysis"
          >
            <template #title>
              <div class="rich-content" v-html="d.analysis"></div>
            </template>
          </el-alert>
        </div>
        <el-empty v-if="!(result.details || []).length" description="暂无逐题明细" />
      </el-card>

      <el-card shadow="never" v-else>
        <el-result icon="info" title="成绩尚未发布" sub-title="含主观题的试卷需阅卷完成并发布后方可查看成绩与解析">
          <template #extra>
            <el-button type="primary" plain @click="load">刷新</el-button>
            <el-button @click="$router.back()">返回</el-button>
          </template>
        </el-result>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getExamResult } from '@/api/take'
import { QUES_TYPE_NAME } from '@/utils'

const route = useRoute()
const examId = route.params.examId
const loading = ref(true)
const result = ref(null)

// 作答/正确答案取值：兼容数组与 {keys}/{selected}/{blanks}/{text}/{points} 对象形态
function ansArr(ans) {
  if (Array.isArray(ans)) return ans
  if (ans && typeof ans === 'object') {
    if (Array.isArray(ans.keys)) return ans.keys
    if (Array.isArray(ans.selected)) return ans.selected
  }
  return []
}

function fmtAnswer(ans, type) {
  if (ans == null) return '未作答'
  if (Array.isArray(ans)) {
    if (!ans.length) return '未作答'
    if (type === 'JUDGE') return ans.map((x) => (x === 'T' ? '正确' : '错误')).join('、')
    return ans.join('、')
  }
  if (typeof ans === 'object') {
    if (ans.keys) return fmtAnswer(ans.keys, type)
    if (ans.selected) return fmtAnswer(ans.selected, type)
    if (ans.blanks) return ans.blanks.filter(Boolean).join('；') || '未作答'
    if (ans.text != null) return ans.text || '未作答'
    if (ans.points) return ans.points.filter(Boolean).join('；') || '未作答'
  }
  return String(ans) || '未作答'
}

async function load() {
  loading.value = true
  try {
    result.value = await getExamResult(examId)
  } catch (e) {
    result.value = { scorePublished: false }
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
.card-title {
  font-weight: 600;
}
.score {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
.detail-item {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 0;
}
.detail-item:last-child {
  border-bottom: none;
}
.d-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.seq {
  font-weight: 600;
}
.stem {
  margin-bottom: 10px;
}
.options {
  margin-bottom: 10px;
}
.opt {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
}
.opt.correct {
  background: #f0f9eb;
}
.opt.mine {
  background: #fef0f0;
}
.opt-key {
  font-weight: 600;
}
.answer-line {
  display: flex;
  gap: 32px;
  font-size: 14px;
  margin: 8px 0;
}
.answer-line .wrong {
  color: #f56c6c;
}
.answer-line .right {
  color: #67c23a;
}
.analysis {
  margin-top: 8px;
}
</style>
