<template>
  <div class="page" v-if="session">
    <!-- 答题进行中 -->
    <template v-if="!session.finished">
      <div class="topbar">
        <el-button size="small" @click="onBack">返回</el-button>
        <span class="topbar-title">{{ session.bankName || '日常练习' }}</span>
        <span class="text-secondary">已答对 {{ session.rightCount }} 题</span>
      </div>

      <el-card shadow="never" class="qa-card">
        <div class="progress-row">
          <span>第 {{ session.index + 1 }} / {{ session.questions.length }} 题</span>
          <el-progress
            :percentage="progressPct"
            :stroke-width="8"
            :show-text="false"
            class="progress-bar"
          />
        </div>

        <div class="ques-stem">{{ current.stem }}</div>

        <div
          v-for="opt in current.options || []"
          :key="opt.optKey"
          class="opt-item"
          :class="optClass(opt.optKey)"
          @click="onSelect(opt.optKey)"
        >
          <span class="opt-key">{{ opt.optKey }}.</span>
          <span class="opt-content">{{ opt.content }}</span>
          <el-icon v-if="isCorrectOpt(opt.optKey)" class="opt-mark" color="#67c23a"><CircleCheckFilled /></el-icon>
          <el-icon v-else-if="isWrongPick(opt.optKey)" class="opt-mark" color="#f56c6c"><CircleCloseFilled /></el-icon>
        </div>

        <div v-if="feedback" class="feedback">
          <el-tag :type="feedback.isRight ? 'success' : 'danger'" size="large">
            {{ feedback.isRight ? '回答正确' : '回答错误' }}
          </el-tag>
          <div v-if="!feedback.isRight" class="feedback-line">
            正确答案：{{ (feedback.correctAnswer || []).join('、') }}
          </div>
          <div v-if="feedback.analysis" class="feedback-line">解析：{{ feedback.analysis }}</div>
          <div v-if="feedback.sourceLaw" class="feedback-line text-secondary">来源：{{ feedback.sourceLaw }}</div>
        </div>

        <div class="action-row">
          <el-button
            v-if="!feedback"
            type="primary"
            size="large"
            :disabled="!selected"
            :loading="submitting"
            @click="onSubmit"
          >
            提交
          </el-button>
          <el-button v-else type="primary" size="large" @click="onNext">
            {{ session.index >= session.questions.length - 1 ? '查看小结' : '下一题' }}
          </el-button>
        </div>
      </el-card>
    </template>

    <!-- 完成小结 -->
    <template v-else>
      <el-card shadow="never" class="summary-card">
        <el-icon :size="64" color="#b3271e"><Medal /></el-icon>
        <div class="summary-title">本次练习完成</div>
        <div class="summary-score">
          答对 <span class="num">{{ session.rightCount }}</span> / 共 {{ session.questions.length }} 题
        </div>
        <div class="summary-actions">
          <el-button type="primary" size="large" @click="onAgain">再练一次</el-button>
          <el-button size="large" @click="$router.replace('/practice/records')">查看练习记录</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { answerPractice } from '@/api/practice'
import { usePracticeStore } from '@/stores/practice'

const router = useRouter()
const practiceStore = usePracticeStore()

const session = ref(null)
const selected = ref('')
const feedback = ref(null)
const submitting = ref(false)
let enterTime = Date.now()
let clientEventId = ''

const current = computed(() => session.value.questions[session.value.index] || {})
const progressPct = computed(() =>
  Math.round(((session.value.index + (feedback.value ? 1 : 0)) / session.value.questions.length) * 100)
)

function newQuestionState() {
  selected.value = ''
  feedback.value = null
  enterTime = Date.now()
  clientEventId = (crypto.randomUUID && crypto.randomUUID()) ||
    `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function onSelect(key) {
  if (feedback.value) return
  selected.value = key
}

// 提交后：正确选项打勾；选错的选项打叉
function isCorrectOpt(key) {
  return feedback.value && (feedback.value.correctAnswer || []).includes(key)
}

function isWrongPick(key) {
  return feedback.value && !feedback.value.isRight && selected.value === key && !isCorrectOpt(key)
}

function optClass(key) {
  if (!feedback.value) return selected.value === key ? 'opt-selected' : ''
  if (isCorrectOpt(key)) return 'opt-right'
  if (isWrongPick(key)) return 'opt-wrong'
  return ''
}

async function onSubmit() {
  submitting.value = true
  try {
    const body = {
      quesItemId: current.value.quesItemId,
      answer: { selected: [selected.value] },
      mode: session.value.mode,
      durationSec: Math.max(1, Math.round((Date.now() - enterTime) / 1000)),
      clientEventId
    }
    if (session.value.mode === 'CATEGORY' && session.value.bankId) body.bankId = session.value.bankId
    feedback.value = await answerPractice(body)
  } catch (e) {
    // 错误已由 request 拦截器统一提示
  } finally {
    submitting.value = false
  }
}

function onNext() {
  practiceStore.advance(feedback.value && feedback.value.isRight)
  if (!session.value.finished) newQuestionState()
}

async function onBack() {
  // 练习已完成并保存记录后，直接返回，不再提示进度丢失
  if (!session.value.finished) {
    try {
      await ElMessageBox.confirm('退出后本次练习进度将丢失，确定退出吗？', '退出练习', { type: 'warning' })
    } catch (e) {
      return
    }
  }
  practiceStore.clearSession()
  router.replace('/practice')
}

function onAgain() {
  practiceStore.clearSession()
  router.replace('/practice')
}

onMounted(() => {
  const s = practiceStore.loadSession()
  if (!s || s.finished === undefined) {
    router.replace('/practice')
    return
  }
  session.value = s
  if (!s.finished) newQuestionState()
})
</script>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.topbar-title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}
.qa-card {
  margin-bottom: 16px;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #606266;
}
.progress-bar {
  flex: 1;
}
.ques-stem {
  margin: 18px 0;
  font-size: 17px;
  line-height: 1.8;
  color: #303133;
}
.opt-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 14px;
  margin-bottom: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  line-height: 1.7;
  transition: background 0.15s;
}
.opt-item:hover {
  background: #f5f7fa;
}
.opt-selected {
  border-color: #1565c0;
  background: #eaf2fe;
}
.opt-right {
  border-color: #67c23a;
  background: #f0f9eb;
}
.opt-wrong {
  border-color: #f56c6c;
  background: #fef0f0;
}
.opt-key {
  font-weight: 600;
}
.opt-content {
  flex: 1;
}
.opt-mark {
  font-size: 20px;
}
.feedback {
  margin-top: 14px;
  padding: 14px;
  background: #f8f9fb;
  border-radius: 6px;
  line-height: 1.9;
  font-size: 14px;
}
.feedback-line {
  margin-top: 6px;
}
.action-row {
  margin-top: 18px;
  text-align: center;
}
.summary-card {
  margin: 60px auto 0;
  max-width: 480px;
  text-align: center;
  padding: 20px 0;
}
.summary-title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 600;
}
.summary-score {
  margin-top: 12px;
  font-size: 15px;
  color: #606266;
}
.summary-score .num {
  font-size: 30px;
  font-weight: 600;
  color: #b3271e;
}
.summary-actions {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
