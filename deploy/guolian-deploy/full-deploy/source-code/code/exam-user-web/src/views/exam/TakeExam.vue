<template>
  <div class="take-page" v-loading="loading" element-loading-text="正在加载试卷...">
    <!-- 顶栏：考试名称 + 倒计时 + 交卷 -->
    <header class="topbar">
      <div class="exam-title">{{ paper?.paperName || '在线考试' }}</div>
      <div class="countdown" :class="{ danger: remainingSec <= 300 }">
        <el-icon><Timer /></el-icon>
        <span>{{ fmtCountdown(remainingSec) }}</span>
      </div>
      <div class="top-actions">
        <span class="save-status" :class="{ warn: saveWarn }">{{ saveStatusText }}</span>
        <el-button type="danger" @click="onSubmitClick">交卷</el-button>
      </div>
    </header>

    <div class="body" v-if="questions.length">
      <!-- 左侧题目区 -->
      <main class="ques-area">
        <div class="ques-card" v-if="currentQ">
          <div class="ques-head">
            <span class="seq">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</span>
            <el-tag size="small">{{ currentQ.typeName }}</el-tag>
            <span class="score">（{{ currentQ.score }}分）</span>
            <el-button text :type="isMarked(currentQ) ? 'warning' : 'info'" @click="toggleMark(currentQ)">
              <el-icon><StarFilled v-if="isMarked(currentQ)" /><Star v-else /></el-icon>
              {{ isMarked(currentQ) ? '取消标记' : '标记' }}
            </el-button>
          </div>
          <div class="stem rich-content" v-html="currentQ.stem"></div>

          <!-- 单选 -->
          <el-radio-group
            v-if="currentQ.quesType === 'SINGLE'"
            :model-value="getSelected(currentQ)[0] ?? ''"
            class="opt-group"
            @change="(v) => setSelected(currentQ, v ? [v] : [])"
          >
            <el-radio v-for="opt in currentQ.options" :key="opt.optKey" :value="opt.optKey" class="opt-item">
              <span class="opt-key">{{ opt.optKey }}.</span>
              <span class="rich-content opt-content" v-html="opt.content"></span>
            </el-radio>
          </el-radio-group>

          <!-- 多选 -->
          <el-checkbox-group
            v-else-if="currentQ.quesType === 'MULTI'"
            :model-value="getSelected(currentQ)"
            class="opt-group"
            @change="(v) => setSelected(currentQ, v)"
          >
            <el-checkbox v-for="opt in currentQ.options" :key="opt.optKey" :value="opt.optKey" class="opt-item">
              <span class="opt-key">{{ opt.optKey }}.</span>
              <span class="rich-content opt-content" v-html="opt.content"></span>
            </el-checkbox>
          </el-checkbox-group>

          <!-- 判断 -->
          <el-radio-group
            v-else-if="currentQ.quesType === 'JUDGE'"
            :model-value="getSelected(currentQ)[0] ?? ''"
            class="opt-group"
            @change="(v) => setSelected(currentQ, v ? [v] : [])"
          >
            <el-radio value="T" class="opt-item">正确</el-radio>
            <el-radio value="F" class="opt-item">错误</el-radio>
          </el-radio-group>

          <!-- 填空 -->
          <div v-else-if="currentQ.quesType === 'FILL'" class="fill-group">
            <div v-for="i in currentQ.blankCount" :key="i" class="fill-row">
              <span class="blank-no">第 {{ i }} 空</span>
              <el-input
                :model-value="getBlanks(currentQ)[i - 1] || ''"
                placeholder="请输入答案"
                @input="(v) => setBlank(currentQ, i - 1, v)"
              />
            </div>
          </div>

          <!-- 简答 -->
          <el-input
            v-else-if="currentQ.quesType === 'ESSAY'"
            type="textarea"
            :rows="10"
            :model-value="getText(currentQ)"
            placeholder="请输入作答内容"
            @input="(v) => setText(currentQ, v)"
          />

          <div class="nav-btns">
            <el-button :disabled="currentIndex === 0" @click="go(currentIndex - 1)">上一题</el-button>
            <el-button v-if="currentIndex < questions.length - 1" type="primary" @click="go(currentIndex + 1)">
              下一题
            </el-button>
            <el-button v-else type="danger" @click="onSubmitClick">完成并交卷</el-button>
          </div>
        </div>
      </main>

      <!-- 右侧答题卡 -->
      <aside class="side">
        <el-card shadow="never" class="answer-card">
          <template #header>
            <div class="ac-header">
              <span>答题卡</span>
              <span class="text-secondary">已答 {{ answeredCount }}/{{ questions.length }}</span>
            </div>
          </template>
          <div v-for="sec in sectionView" :key="sec.quesType" class="ac-section">
            <div class="ac-type">{{ sec.typeName }}（{{ sec.items.length }}题）</div>
            <div class="ac-grid">
              <button
                v-for="item in sec.items"
                :key="item.sheetQuesId"
                class="ac-cell"
                :class="{
                  answered: isAnswered(item),
                  current: item._index === currentIndex,
                  marked: isMarked(item)
                }"
                @click="go(item._index)"
              >
                {{ item._index + 1 }}
              </button>
            </div>
          </div>
          <div class="legend">
            <span><i class="dot answered"></i>已答</span>
            <span><i class="dot"></i>未答</span>
            <span><i class="dot marked"></i>标记</span>
            <span><i class="dot current"></i>当前</span>
          </div>
          <el-button type="danger" style="width: 100%" @click="onSubmitClick">交卷</el-button>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSheet, getExamPaper, saveAnswers, reportScreenEvent, submitSheet } from '@/api/take'
import { fmtCountdown, fmtNow, countBlanks } from '@/utils'
import { uuid } from '@/utils'

const route = useRoute()
const router = useRouter()
const examId = route.params.examId
const sheetId = route.query.sheetId || sessionStorage.getItem(`exam_sheet_${examId}`)

const loading = ref(true)
const paper = ref(null)
const questions = ref([])
const currentIndex = ref(0)
const answers = reactive({})
const markedSet = reactive(new Set())
const remainingSec = ref(0)

const saveStatusText = ref('')
const saveWarn = ref(false)

let submitted = false
let deadline = 0 // performance.now() 时间轴上的截止点
let countdownTimer = null
let syncTimer = null
let debounceTimer = null
let retryTimer = null
let flushing = false
let reminded5Min = false
const pending = new Map() // sheetQuesId -> answer（待保存）

const LOCAL_KEY = `exam_answers_${sheetId}`

const currentQ = computed(() => questions.value[currentIndex.value] || null)
const answeredCount = computed(() => questions.value.filter((q) => isAnswered(q)).length)
const sectionView = computed(() => {
  const groups = []
  for (const q of questions.value) {
    let g = groups.find((x) => x.quesType === q.quesType)
    if (!g) {
      g = { quesType: q.quesType, typeName: q.typeName, items: [] }
      groups.push(g)
    }
    g.items.push(q)
  }
  return groups
})

// ---------- 答案读写 ----------
function getSelected(q) {
  return answers[q.sheetQuesId]?.selected || []
}
function setSelected(q, arr) {
  setAnswer(q, { selected: [...arr].sort() })
}
function getBlanks(q) {
  const b = answers[q.sheetQuesId]?.blanks || []
  return Array.from({ length: q.blankCount }, (_, i) => b[i] || '')
}
function setBlank(q, idx, v) {
  const b = getBlanks(q)
  b[idx] = v
  setAnswer(q, { blanks: b })
}
function getText(q) {
  return answers[q.sheetQuesId]?.text || ''
}
function setText(q, v) {
  setAnswer(q, { text: v })
}
function setAnswer(q, ans) {
  answers[q.sheetQuesId] = ans
  pending.set(q.sheetQuesId, ans)
  persistLocal()
  saveStatusText.value = '有未保存的修改...'
  saveWarn.value = false
  scheduleFlush()
}
function isAnswered(q) {
  const a = answers[q.sheetQuesId]
  if (!a) return false
  if (a.selected) return a.selected.length > 0
  if (a.blanks) return a.blanks.some((x) => (x || '').trim() !== '')
  if (a.text != null) return a.text.trim() !== ''
  return false
}
function isMarked(q) {
  return markedSet.has(q.sheetQuesId)
}
function toggleMark(q) {
  if (markedSet.has(q.sheetQuesId)) markedSet.delete(q.sheetQuesId)
  else markedSet.add(q.sheetQuesId)
}
function go(i) {
  currentIndex.value = Math.min(Math.max(0, i), questions.value.length - 1)
}

// ---------- 本地快照 ----------
function persistLocal() {
  try {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(Object.entries(answers)))
  } catch (e) {
    /* 存储满等异常忽略 */
  }
}
function clearLocal() {
  localStorage.removeItem(LOCAL_KEY)
  sessionStorage.removeItem(`exam_enter_${examId}`)
  sessionStorage.removeItem(`exam_sheet_${examId}`)
}

// ---------- 倒计时（performance.now 时间轴 + 服务端校时） ----------
function syncClock(serverRemainingSec) {
  if (typeof serverRemainingSec === 'number') {
    deadline = performance.now() + serverRemainingSec * 1000
    remainingSec.value = serverRemainingSec
  }
}
function tickCountdown() {
  const left = Math.max(0, Math.round((deadline - performance.now()) / 1000))
  remainingSec.value = left
  if (left <= 300 && !reminded5Min && left > 0) {
    reminded5Min = true
    ElMessageBox.alert('距离考试结束仅剩 5 分钟，请抓紧时间作答。', '时间提醒', { type: 'warning' })
  }
  if (left <= 0 && !submitted) {
    doSubmit(true)
  }
}

// ---------- 自动保存 ----------
function scheduleFlush() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(flushAnswers, 3000) // 作答后 3s 防抖保存
}

async function flushAnswers() {
  if (flushing || submitted || pending.size === 0) return
  flushing = true
  const batch = [...pending.entries()]
  try {
    const res = await saveWithRetry({
      clientBatchId: uuid(),
      answers: batch.map(([sqid, ans]) => ({ sheetQuesId: sqid, answer: ans }))
    })
    // 仅移除批次内未被再次修改的条目
    for (const [k, v] of batch) {
      if (pending.get(k) === v) pending.delete(k)
    }
    if (res) {
      syncClock(res.remainingSec)
      if (res.submitStatus && res.submitStatus !== 'IN_EXAM') {
        handleRemoteSubmit(res.submitStatus)
        return
      }
    }
    saveStatusText.value = `已自动保存 ${fmtNow().slice(11)}`
    saveWarn.value = false
  } catch (err) {
    if (err && err.code === 50003) {
      handleRemoteSubmit('SUBMITTED')
      return
    }
    saveStatusText.value = '网络异常，答案已保存在本机，恢复后自动同步'
    saveWarn.value = true
    clearTimeout(retryTimer)
    retryTimer = setTimeout(flushAnswers, 10000)
  } finally {
    flushing = false
  }
}

// 弱网指数退避重试 1s/3s/8s
async function saveWithRetry(payload) {
  const delays = [0, 1000, 3000, 8000]
  let lastErr
  for (let i = 0; i < delays.length; i++) {
    if (delays[i]) await new Promise((r) => setTimeout(r, delays[i]))
    try {
      return await saveAnswers(sheetId, payload)
    } catch (e) {
      lastErr = e
      if (e && (e.code === 50003 || e.code === 50006)) throw e // 业务错误不重试
    }
  }
  throw lastErr
}

// ---------- 周期同步：30s 校时 + 强制收卷检测 ----------
async function periodicSync() {
  if (submitted) return
  if (pending.size > 0) {
    await flushAnswers()
    return
  }
  try {
    const info = await getSheet(sheetId)
    syncClock(info.remainingSec)
    if (info.submitStatus && info.submitStatus !== 'IN_EXAM') {
      handleRemoteSubmit(info.submitStatus)
    }
  } catch (e) {
    /* 静默，下个周期重试 */
  }
}

// ---------- 切屏上报 ----------
let pageAway = false
function reportAway(type) {
  if (submitted) return
  reportScreenEvent(sheetId, { eventType: type, occurredAt: fmtNow() })
    .then((res) => {
      if (!res) return
      if (res.action === 'FORCE_SUBMIT') {
        handleRemoteSubmit('FORCED', '切屏次数超过上限，系统已强制交卷')
      } else if (type === 'RETURN' && res.action === 'WARN') {
        ElMessageBox.alert(`您已离开考试页面 ${res.leaveCount} 次（上限 ${res.limit} 次），继续离开将被强制交卷！`, '警告', {
          type: 'warning'
        })
      }
    })
    .catch(() => {})
}
function onVisibility() {
  if (document.visibilityState === 'hidden') {
    if (!pageAway) {
      pageAway = true
      reportAway('LEAVE')
    }
  } else if (pageAway) {
    pageAway = false
    reportAway('RETURN')
  }
}
function onBlur() {
  if (!pageAway) {
    pageAway = true
    reportAway('LEAVE')
  }
}
function onFocus() {
  if (pageAway && document.visibilityState === 'visible') {
    pageAway = false
    reportAway('RETURN')
  }
}

// ---------- 交卷 ----------
function unansweredList() {
  return questions.value.filter((q) => !isAnswered(q))
}

async function onSubmitClick() {
  const n = unansweredList().length
  try {
    if (n > 0) {
      await ElMessageBox.confirm(`还有 ${n} 道题未作答，确定要交卷吗？`, '确认交卷', {
        type: 'warning',
        confirmButtonText: '确认交卷',
        cancelButtonText: '继续答题'
      })
    } else {
      await ElMessageBox.confirm('确认提交试卷吗？提交后不可修改。', '确认交卷', {
        type: 'info',
        confirmButtonText: '确认交卷',
        cancelButtonText: '再检查一下'
      })
    }
  } catch (e) {
    return // 取消
  }
  await doSubmit(false)
}

async function doSubmit(auto) {
  if (submitted) return
  submitted = true
  // 先把未保存的答案同步上去
  clearTimeout(debounceTimer)
  try {
    if (pending.size > 0) {
      await saveWithRetry({
        clientBatchId: uuid(),
        answers: [...pending.entries()].map(([sqid, ans]) => ({ sheetQuesId: sqid, answer: ans }))
      })
      pending.clear()
    }
  } catch (e) {
    /* 保存失败也继续交卷，服务端以已保存内容为准 */
  }
  let result = null
  let serverAcked = false
  const delays = [0, 1000, 3000]
  for (let i = 0; i < delays.length; i++) {
    if (delays[i]) await new Promise((r) => setTimeout(r, delays[i]))
    try {
      result = await submitSheet(sheetId, { confirmUnanswered: true, clientSubmitId: uuid() })
      break
    } catch (e) {
      if (e && e.code === 50003) {
        serverAcked = true // 服务端已收卷
        break
      }
    }
  }
  cleanupTimers()
  clearLocal()
  if (result) {
    sessionStorage.setItem(`exam_submit_${examId}`, JSON.stringify(result))
  }
  if (auto) {
    ElMessageBox.alert('考试时间已到，系统已自动交卷。', '提示', { type: 'info' }).catch(() => {})
  }
  if (result || serverAcked) {
    router.replace(`/exam/result/${examId}`)
  } else {
    // 交卷请求未送达：答案已自动保存，到点由服务端自动收卷
    ElMessageBox.alert('交卷请求未能送达，已作答内容不会丢失；到达截止时间后系统将自动收卷，您可稍后从考试中心查看状态。', '提示', {
      type: 'warning'
    }).catch(() => {})
    router.replace('/exam')
  }
}

function handleRemoteSubmit(status, msg) {
  if (submitted) return
  submitted = true
  cleanupTimers()
  clearLocal()
  ElMessageBox.alert(msg || (status === 'FORCED' ? '您已被监考强制收卷。' : '试卷已提交。'), '提示', {
    type: 'warning'
  }).catch(() => {})
  router.replace(`/exam/result/${examId}`)
}

function cleanupTimers() {
  clearInterval(countdownTimer)
  clearInterval(syncTimer)
  clearTimeout(debounceTimer)
  clearTimeout(retryTimer)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('focus', onFocus)
}

// ---------- 加载：断线续考恢复 ----------
async function load() {
  if (!sheetId) {
    ElMessage.warning('缺少答卷信息，请重新进入考试')
    router.replace('/exam')
    return
  }
  loading.value = true
  try {
    // TAKE-08：取回已保存答案与权威剩余时间
    const sheet = await getSheet(sheetId)
    if (sheet.submitStatus && sheet.submitStatus !== 'IN_EXAM') {
      router.replace(`/exam/result/${examId}`)
      return
    }
    syncClock(sheet.remainingSec)
    ;(sheet.answers || []).forEach((a) => {
      answers[a.sheetQuesId] = a.answer
    })

    // TAKE-04：拉取试卷（含本人乱序后的顺序）
    const p = await getExamPaper(examId, sheetId)
    paper.value = p
    if (typeof p.remainingSec === 'number') syncClock(p.remainingSec)
    const flat = []
    ;(p.sections || []).forEach((sec) => {
      ;(sec.questions || []).forEach((q) => {
        flat.push({
          ...q,
          quesType: sec.quesType,
          typeName: sec.typeName,
          score: q.score ?? sec.scorePerQues,
          blankCount: q.blankCount || countBlanks(q.stem),
          _index: flat.length
        })
      })
    })
    questions.value = flat

    // 本地未同步快照合并（服务端为准，本地多出的修改重新入队保存）
    try {
      const localEntries = JSON.parse(localStorage.getItem(LOCAL_KEY) || '[]')
      for (const [k, v] of localEntries) {
        const key = Number(k)
        if (JSON.stringify(answers[key]) !== JSON.stringify(v)) {
          answers[key] = v
          pending.set(key, v)
        }
      }
    } catch (e) {
      /* 忽略损坏的本地快照 */
    }
    if (pending.size > 0) scheduleFlush()

    persistLocal()
    saveStatusText.value = '答案将自动保存'

    countdownTimer = setInterval(tickCountdown, 500)
    syncTimer = setInterval(periodicSync, 30000) // 30s 校时/同步
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
    window.addEventListener('focus', onFocus)
  } catch (err) {
    if (err && err.code === 50003) {
      router.replace(`/exam/result/${examId}`)
      return
    }
    ElMessage.error('试卷加载失败，请稍后重试')
    router.replace('/exam')
  } finally {
    loading.value = false
  }
}

onMounted(load)

onBeforeUnmount(() => {
  cleanupTimers()
  if (!submitted) flushAnswers()
})

onBeforeRouteLeave(async () => {
  if (submitted) return true
  try {
    await ElMessageBox.confirm('考试正在进行中，答案已自动保存，确定要离开吗？可在考试中心继续答题。', '提示', {
      type: 'warning',
      confirmButtonText: '离开',
      cancelButtonText: '继续答题'
    })
    return true
  } catch (e) {
    return false
  }
})
</script>

<style scoped>
.take-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.topbar {
  height: 56px;
  background: #1f2d3d;
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
}
.exam-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.countdown {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.countdown.danger {
  color: #f56c6c;
  animation: blink 1s step-start infinite;
}
@keyframes blink {
  50% {
    opacity: 0.5;
  }
}
.top-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
.save-status {
  font-size: 12px;
  color: #a3d0a9;
}
.save-status.warn {
  color: #e6a23c;
}
.body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}
.ques-area {
  flex: 1;
  overflow-y: auto;
}
.ques-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px 32px;
  min-height: 100%;
}
.ques-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
}
.seq {
  font-weight: 600;
}
.score {
  color: #909399;
  font-size: 13px;
}
.stem {
  font-size: 15px;
  margin-bottom: 20px;
}
.opt-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.opt-item {
  display: flex;
  align-items: flex-start;
  height: auto;
  padding: 8px 0;
  margin-right: 0;
  white-space: normal;
}
.opt-key {
  font-weight: 600;
  margin-right: 6px;
}
.opt-content {
  display: inline-block;
}
.fill-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 480px;
}
.fill-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.blank-no {
  white-space: nowrap;
  color: #606266;
}
.nav-btns {
  margin-top: 32px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.side {
  width: 300px;
  flex-shrink: 0;
  overflow-y: auto;
}
.ac-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.ac-section {
  margin-bottom: 12px;
}
.ac-type {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.ac-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ac-cell {
  width: 34px;
  height: 34px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
.ac-cell.answered {
  background: #67c23a;
  border-color: #67c23a;
  color: #fff;
}
.ac-cell.marked {
  border-color: #e6a23c;
  box-shadow: inset 0 0 0 2px #e6a23c;
}
.ac-cell.current {
  outline: 2px solid #409eff;
}
.legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #909399;
  margin: 12px 0;
}
.dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: -2px;
  background: #fff;
}
.dot.answered {
  background: #67c23a;
  border-color: #67c23a;
}
.dot.marked {
  border-color: #e6a23c;
  box-shadow: inset 0 0 0 2px #e6a23c;
}
.dot.current {
  outline: 2px solid #409eff;
}
</style>
