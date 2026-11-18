<template>
  <div class="page page-has-navbar">
    <van-nav-bar title="测试结果" left-arrow @click-left="onBack" fixed placeholder />

    <!-- 交卷回执 -->
    <div v-if="submitResult" class="result-banner success">
      <van-icon name="checked" size="40" color="#07c160" />
      <div class="result-banner-title">交卷成功</div>
      <div class="text-muted">交卷时间：{{ submitResult.submitTime || '-' }}</div>
      <div v-if="submitResult.unansweredCount > 0" class="text-muted">未作答 {{ submitResult.unansweredCount }} 题</div>
      <div v-if="submitResult.message" class="text-muted">{{ submitResult.message }}</div>
    </div>

    <!-- 成绩 -->
    <van-loading v-if="loading" class="page-loading" size="24px">成绩查询中...</van-loading>

    <template v-else-if="result">
      <div v-if="!result.scorePublished" class="result-banner waiting">
        <van-icon name="clock-o" size="40" color="#ff976a" />
        <div class="result-banner-title">成绩待发布</div>
        <div class="text-muted">成绩发布后可在此查看，页面每 30 秒自动刷新</div>
      </div>

      <template v-else>
        <div class="score-panel">
          <div class="score-value">{{ result.myScore }}<span class="score-total"> / {{ result.totalScore }}</span></div>
          <div class="score-tags">
            <van-tag v-if="result.grade" type="primary" size="large">等级：{{ result.grade }}</van-tag>
            <van-tag :type="result.passed ? 'success' : 'danger'" size="large">
              {{ result.passed ? '合格' : '不合格' }}
            </van-tag>
          </div>
          <div class="text-muted score-sub">
            <span v-if="result.objectiveScore !== undefined">客观题 {{ result.objectiveScore }} 分</span>
            <span v-if="result.subjectiveScore !== undefined"> · 主观题 {{ result.subjectiveScore }} 分</span>
            <span v-if="result.retakeSeq"> · 第 {{ result.retakeSeq }} 次补考</span>
          </div>
        </div>

        <!-- 题目解析 -->
        <template v-if="result.showAnalysis && result.details && result.details.length">
          <div v-for="d in result.details" :key="d.seq" class="detail-card">
            <div class="detail-head">
              <van-tag size="medium">{{ quesTypeName[d.quesType] || d.quesType }}</van-tag>
              <span class="text-muted">第 {{ d.seq }} 题 · 得 {{ d.gainedScore }} 分</span>
              <van-tag :type="d.isCorrect ? 'success' : 'danger'" plain>{{ d.isCorrect ? '正确' : '错误' }}</van-tag>
            </div>
            <div class="detail-stem" v-html="d.stem"></div>
            <div class="detail-answers">
              <div>我的答案：<span :class="d.isCorrect ? 'ans-right' : 'ans-wrong'">{{ fmtAns(d.myAnswer, d.quesType) }}</span></div>
              <div>正确答案：<span class="ans-right">{{ fmtAns(d.correctAnswer, d.quesType) }}</span></div>
            </div>
            <div v-if="d.analysis" class="detail-analysis">解析：{{ d.analysis }}</div>
          </div>
        </template>
        <van-notice-bar v-else-if="result.scorePublished" wrapable text="本场测试未开放答题解析。" />
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamResult } from '../../api/exam'
import { quesTypeName } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const examId = route.params.examId

const loading = ref(true)
const result = ref(null)
const submitResult = ref(null)
let pollTimer = null

// 返回：交卷回执场景回测试列表；其余按来源回退（/mine/scores 或 /exam）；无历史直接打开时回我的成绩
function onBack() {
  const back = window.history.state && window.history.state.back
  if (submitResult.value || (back && (back.includes('/exam/answer') || back.includes('/exam/notice')))) {
    router.replace('/exam')
  } else if (back) {
    router.back()
  } else {
    router.replace('/mine/scores')
  }
}

// 作答/正确答案取值：兼容数组与 {keys}/{selected}/{blanks}/{text}/{points} 对象形态（对齐 PC 端 ScoreDetail）
function fmtAns(ans, type) {
  if (ans == null) return '未作答'
  if (Array.isArray(ans)) {
    if (!ans.length) return '未作答'
    if (type === 'JUDGE') return ans.map((x) => (x === 'T' ? '正确' : '错误')).join('、')
    return ans.join('、')
  }
  if (typeof ans === 'object') {
    if (ans.keys) return fmtAns(ans.keys, type)
    if (ans.selected) return fmtAns(ans.selected, type)
    if (ans.blanks) return ans.blanks.filter(Boolean).join('；') || '未作答'
    if (ans.text != null) return ans.text || '未作答'
    if (ans.points) return ans.points.filter(Boolean).join('；') || '未作答'
  }
  return String(ans) || '未作答'
}

async function load() {
  try {
    const data = await getExamResult(examId)
    result.value = data
    if (data && data.scorePublished && pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  } catch (e) {
    // 50009 未发布等由拦截器提示；保持等待态
    if (!result.value) result.value = { scorePublished: false }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  try {
    submitResult.value = JSON.parse(sessionStorage.getItem(`h5_submit_result_${examId}`))
  } catch (e) {
    // ignore
  }
  load()
  // 成绩等待页每 30 秒轮询（API 设计 6.4）
  pollTimer = setInterval(() => {
    if (!result.value || !result.value.scorePublished) load()
  }, 30000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.page-loading {
  display: block;
  text-align: center;
  margin-top: 80px;
}

.result-banner {
  text-align: center;
  padding: 32px 16px;
  background: #fff;
  margin-bottom: 12px;
}

.result-banner-title {
  font-size: 17px;
  font-weight: 600;
  margin: 8px 0;
  color: #323233;
}

.score-panel {
  text-align: center;
  padding: 28px 16px;
  background: #fff;
  margin-bottom: 12px;
}

.score-value {
  font-size: 44px;
  font-weight: 700;
  color: #b3271e;
}

.score-total {
  font-size: 16px;
  color: #969799;
  font-weight: 400;
}

.score-tags {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.score-sub {
  margin-top: 10px;
}

.detail-card {
  background: #fff;
  margin: 0 12px 12px;
  border-radius: 8px;
  padding: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-stem {
  font-size: 14px;
  line-height: 1.7;
  color: #323233;
  margin-bottom: 8px;
  word-break: break-word;
}

.detail-stem :deep(img) {
  max-width: 100%;
}

.detail-answers {
  font-size: 13px;
  color: #646566;
  line-height: 1.8;
}

.ans-right {
  color: #07c160;
}

.ans-wrong {
  color: #ee0a24;
}

.detail-analysis {
  margin-top: 8px;
  padding: 8px;
  background: #f7f8fa;
  border-radius: 6px;
  font-size: 13px;
  color: #646566;
  line-height: 1.7;
}
</style>
