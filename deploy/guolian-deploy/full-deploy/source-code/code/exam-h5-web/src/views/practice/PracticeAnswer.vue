<template>
  <div v-if="session">
    <van-nav-bar :title="session.bankName || '日常练习'" left-arrow @click-left="onBack" />

    <template v-if="!session.finished">
      <div class="progress-row">
        <span>第 {{ session.index + 1 }} / {{ session.questions.length }} 题</span>
        <span class="text-muted">已答对 {{ session.rightCount }} 题</span>
      </div>
      <van-progress :percentage="progressPct" stroke-width="4" :show-pivot="false" />

      <div class="ques-stem">{{ current.stem }}</div>

      <van-cell-group inset class="opt-group">
        <van-cell
          v-for="opt in current.options || []"
          :key="opt.optKey"
          clickable
          :class="optClass(opt.optKey)"
          @click="onSelect(opt.optKey)"
        >
          <template #title>
            <span class="opt-key">{{ opt.optKey }}.</span>
            {{ opt.content }}
          </template>
          <template #right-icon>
            <van-icon v-if="isCorrectOpt(opt.optKey)" name="success" color="#07c160" />
            <van-icon v-else-if="isWrongPick(opt.optKey)" name="cross" color="#ee0a24" />
          </template>
        </van-cell>
      </van-cell-group>

      <div v-if="feedback" class="feedback">
        <van-tag :type="feedback.isRight ? 'success' : 'danger'" size="large">
          {{ feedback.isRight ? '回答正确' : '回答错误' }}
        </van-tag>
        <div v-if="!feedback.isRight" class="feedback-line">
          正确答案：{{ (feedback.correctAnswer || []).join('、') }}
        </div>
        <div v-if="feedback.analysis" class="feedback-line">解析：{{ feedback.analysis }}</div>
        <div v-if="feedback.sourceLaw" class="feedback-line text-muted">来源：{{ feedback.sourceLaw }}</div>
      </div>

      <div class="action-btn">
        <van-button
          v-if="!feedback"
          round
          block
          type="primary"
          :disabled="!selected"
          :loading="submitting"
          @click="onSubmit"
        >
          提交
        </van-button>
        <van-button v-else round block type="primary" @click="onNext">
          {{ session.index >= session.questions.length - 1 ? '查看小结' : '下一题' }}
        </van-button>
      </div>
    </template>

    <template v-else>
      <div class="summary">
        <van-icon name="medal-o" size="64" color="#b3271e" />
        <div class="summary-title">本次练习完成</div>
        <div class="summary-score">
          答对 <span class="num">{{ session.rightCount }}</span> / 共 {{ session.questions.length }} 题
        </div>
        <div class="summary-actions">
          <van-button round block type="primary" @click="onAgain">再练一次</van-button>
          <van-button round block plain type="primary" @click="$router.push('/practice/records')">
            查看练习记录
          </van-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { answerPractice } from '../../api/practice'
import { usePracticeStore } from '../../stores/practice'

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
    // 错误已由 request 统一 toast
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
      await showConfirmDialog({ title: '退出练习', message: '退出后本次练习进度将丢失，确定退出吗？' })
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
.progress-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px 8px;
  font-size: 14px;
  color: #323233;
}

.ques-stem {
  padding: 16px;
  font-size: 16px;
  line-height: 1.7;
  color: #323233;
}

.opt-group {
  margin-top: 4px;
}

.opt-key {
  font-weight: 600;
  margin-right: 4px;
}

.opt-selected {
  background: #e8f0fe;
}

.opt-right {
  background: #e8f7ee;
}

.opt-wrong {
  background: #fdecec;
}

.feedback {
  margin: 16px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  line-height: 1.8;
  font-size: 14px;
}

.feedback-line {
  margin-top: 8px;
}

.action-btn {
  margin: 20px 16px;
}

.summary {
  padding: 64px 24px;
  text-align: center;
}

.summary-title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 600;
}

.summary-score {
  margin-top: 12px;
  font-size: 15px;
  color: #646566;
}

.summary-score .num {
  font-size: 28px;
  font-weight: 600;
  color: #b3271e;
}

.summary-actions {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
