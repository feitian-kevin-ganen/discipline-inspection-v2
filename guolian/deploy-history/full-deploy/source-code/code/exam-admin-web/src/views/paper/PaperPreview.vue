<template>
  <div class="page-container">
    <div class="page-card" v-loading="loading">
      <template v-if="paper">
        <div class="toolbar">
          <el-button @click="router.push('/papers')"><el-icon style="margin-right: 4px"><Back /></el-icon>返回列表</el-button>
          <div class="spacer" />
          <el-tag v-if="paper.sample" type="warning">随机卷样卷</el-tag>
        </div>

        <div class="preview-header">
          <h2 class="preview-title">{{ paper.paperName }}</h2>
          <div class="preview-meta">
            <span>总分：{{ paper.totalScore }} 分</span>
            <el-divider direction="vertical" />
            <span>及格分：{{ paper.passScore }} 分</span>
          </div>
        </div>

        <div v-for="(section, si) in paper.sections || []" :key="si" class="preview-section">
          <div class="section-title">
            {{ sectionIndexLabel(si) }}、{{ QUES_TYPES[section.quesType]?.label || section.quesType }}
            （共 {{ section.questions?.length || 0 }} 题，每题 {{ section.questions?.[0]?.score ?? '-' }} 分）
          </div>

          <div v-for="q in section.questions || []" :key="q.itemId" class="question-card">
            <div class="question-head">
              <span class="question-seq">{{ q.seq }}.</span>
              <span class="stem-html" v-html="q.stem" />
              <span class="question-score">（{{ q.score }} 分）</span>
            </div>

            <!-- 选项（单选/多选） -->
            <ul v-if="q.options && q.options.length" class="option-list">
              <li
                v-for="(opt, oi) in q.options"
                :key="oi"
                :class="{ 'option-answer': opt.isAnswer || opt.answer }"
              >
                <span class="option-key">{{ optionKey(oi) }}.</span>
                <span class="stem-html" v-html="opt.content" />
              </li>
            </ul>

            <!-- 非选择题答案 -->
            <div v-if="answerText(q)" class="answer-block">
              <span class="answer-label">答案：</span>
              <template v-if="q.quesType === 'FILL'">
                <div v-for="(blank, bi) in fillBlanks(q)" :key="bi" class="fill-blank">空{{ bi + 1 }}：{{ blank }}</div>
              </template>
              <template v-else-if="q.quesType === 'ESSAY'">
                <div class="essay-reference">{{ essayReference(q) }}</div>
                <ul v-if="essayKeyPoints(q).length" class="key-point-list">
                  <li v-for="(kp, ki) in essayKeyPoints(q)" :key="ki">{{ kp }}</li>
                </ul>
              </template>
              <template v-else>{{ answerText(q) }}</template>
            </div>

            <!-- 解析 -->
            <div v-if="q.analysis" class="analysis-block">
              <span class="analysis-label">解析：</span>
              <span class="stem-html" v-html="q.analysis" />
            </div>
          </div>
        </div>

        <el-empty v-if="!(paper.sections || []).length" description="试卷暂无题目" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { paperApi } from '../../api'
import { QUES_TYPES } from '../../utils/dict'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const paper = ref(null)

const CN_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const sectionIndexLabel = (i) => CN_NUMS[i] || String(i + 1)
const optionKey = (i) => String.fromCharCode(65 + i)

const fillBlanks = (q) => {
  const blanks = q.answer?.blanks || []
  return blanks.map((b) => (Array.isArray(b) ? b.join(' / ') : b))
}

const essayReference = (q) => q.answer?.reference ?? ''
const essayKeyPoints = (q) => q.answer?.keyPoints || []

const answerText = (q) => {
  const ans = q.answer
  if (ans == null) return ''
  if (q.quesType === 'SINGLE' || q.quesType === 'MULTI') return ''
  if (q.quesType === 'JUDGE') {
    const v = typeof ans === 'string' ? ans : ans.judge ?? ans.answer ?? ''
    if (v === 'T' || v === true || v === 'TRUE') return '正确'
    if (v === 'F' || v === false || v === 'FALSE') return '错误'
    return String(v)
  }
  if (q.quesType === 'FILL') return fillBlanks(q).length ? ' ' : ''
  if (q.quesType === 'ESSAY') return essayReference(q) || essayKeyPoints(q).length ? ' ' : ''
  return typeof ans === 'string' ? ans : ''
}

onMounted(async () => {
  loading.value = true
  try {
    paper.value = await paperApi.preview(route.params.id)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.preview-header {
  text-align: center;
  margin-bottom: 24px;
}
.preview-title {
  margin: 8px 0;
  font-size: 20px;
}
.preview-meta {
  color: #606266;
  font-size: 14px;
}
.preview-section {
  margin-bottom: 24px;
}
.section-title {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.question-card {
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.question-head {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.question-seq {
  font-weight: 600;
  flex-shrink: 0;
}
.question-score {
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
  margin-left: auto;
}
.option-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0 0 0 20px;
}
.option-list li {
  padding: 4px 8px;
  border-radius: 4px;
  display: flex;
  gap: 4px;
}
.option-list li.option-answer {
  color: #67c23a;
  background: #f0f9eb;
  font-weight: 600;
}
.option-key {
  flex-shrink: 0;
}
.answer-block {
  margin: 10px 0 0 20px;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 4px;
  color: #67c23a;
}
.answer-label {
  font-weight: 600;
}
.fill-blank {
  margin-top: 4px;
}
.essay-reference {
  margin-top: 4px;
  white-space: pre-wrap;
}
.key-point-list {
  margin: 6px 0 0;
  padding-left: 18px;
}
.analysis-block {
  margin: 10px 0 0 20px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 4px;
  color: #909399;
  font-size: 13px;
}
.analysis-label {
  font-weight: 600;
}
</style>
