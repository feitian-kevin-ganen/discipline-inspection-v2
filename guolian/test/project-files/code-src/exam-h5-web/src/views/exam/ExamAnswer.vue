<template>
  <div class="answer-page">
    <!-- 顶部：标题 + 倒计时 -->
    <div class="answer-top">
      <div class="answer-title van-ellipsis">{{ examName }}</div>
      <div class="answer-countdown" :class="{ 'countdown-danger': remainingSec <= 300 }">
        <van-icon name="clock-o" />
        <span>{{ formatCountdown(remainingSec) }}</span>
      </div>
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">试卷加载中...</van-loading>

    <template v-else-if="current">
      <!-- 题型分区信息 -->
      <div class="ques-section-bar">
        <van-tag type="primary">{{ current.typeName }}</van-tag>
        <span class="text-muted">第 {{ currentIdx + 1 }} / {{ questions.length }} 题 · {{ current.scorePerQues }} 分</span>
        <van-icon
          :name="marked.has(current.sheetQuesId) ? 'flag' : 'flag-o'"
          :color="marked.has(current.sheetQuesId) ? '#ff976a' : '#969799'"
          size="20"
          class="ques-flag"
          @click="toggleMark"
        />
      </div>

      <!-- 题干 -->
      <div class="ques-stem" v-html="current.stem"></div>

      <!-- 作答控件 -->
      <div class="ques-control">
        <!-- 单选 / 判断 -->
        <van-radio-group
          v-if="current.quesType === 'SINGLE' || current.quesType === 'JUDGE'"
          :model-value="singleValue"
          @update:model-value="onSingle"
        >
          <van-cell-group inset>
            <van-cell
              v-for="opt in optionsOf(current)"
              :key="opt.optKey"
              clickable
              @click="onSingle(opt.optKey)"
            >
              <template #title>
                <span class="opt-key">{{ opt.optKey }}.</span>
                <span v-html="opt.content"></span>
              </template>
              <template #right-icon>
                <van-radio :name="opt.optKey" />
              </template>
            </van-cell>
          </van-cell-group>
        </van-radio-group>

        <!-- 多选 -->
        <van-checkbox-group
          v-else-if="current.quesType === 'MULTI'"
          :model-value="multiValue"
          @update:model-value="onMulti"
        >
          <van-cell-group inset>
            <van-cell
              v-for="opt in optionsOf(current)"
              :key="opt.optKey"
              clickable
              @click="toggleMulti(opt.optKey)"
            >
              <template #title>
                <span class="opt-key">{{ opt.optKey }}.</span>
                <span v-html="opt.content"></span>
              </template>
              <template #right-icon>
                <van-checkbox :name="opt.optKey" />
              </template>
            </van-cell>
          </van-cell-group>
        </van-checkbox-group>

        <!-- 填空 -->
        <van-cell-group v-else-if="current.quesType === 'FILL'" inset>
          <van-field
            v-for="i in blankCountOf(current)"
            :key="i"
            :model-value="blankValue(i - 1)"
            :label="`第${i}空`"
            placeholder="请输入答案"
            @update:model-value="(v) => onBlank(i - 1, v)"
          />
        </van-cell-group>

        <!-- 简答 -->
        <van-cell-group v-else-if="current.quesType === 'ESSAY'" inset>
          <van-field
            :model-value="essayValue"
            type="textarea"
            rows="6"
            autosize
            maxlength="2000"
            show-word-limit
            placeholder="请输入作答内容"
            @update:model-value="onEssay"
          />
        </van-cell-group>
      </div>

      <div v-if="offlineTip" class="offline-tip">答案已本地保存，恢复网络后自动同步</div>

      <!-- 底部操作区（拇指热区） -->
      <div class="answer-actions">
        <van-button size="small" :disabled="currentIdx === 0" @click="gotoIdx(currentIdx - 1)">上一题</van-button>
        <van-button size="small" icon="apps-o" @click="showCard = true">答题卡</van-button>
        <van-button size="small" :disabled="currentIdx >= questions.length - 1" @click="gotoIdx(currentIdx + 1)">下一题</van-button>
        <van-button size="small" type="danger" :loading="submitting" @click="onSubmitClick">交卷</van-button>
      </div>
    </template>

    <!-- 答题卡 -->
    <van-popup v-model:show="showCard" position="bottom" round class="card-popup">
      <div class="card-head">
        <span>答题卡</span>
        <span class="text-muted">已答 {{ answeredCount }} / {{ questions.length }}</span>
      </div>
      <div class="card-legend">
        <span><i class="dot dot-answered"></i>已答</span>
        <span><i class="dot dot-unanswered"></i>未答</span>
        <span><i class="dot dot-marked"></i>标记</span>
      </div>
      <div class="card-body">
        <div v-for="sec in sections" :key="sec.quesType" class="card-section">
          <div class="card-section-title">{{ sec.typeName }}（每题 {{ sec.scorePerQues }} 分）</div>
          <div class="card-grid">
            <div
              v-for="q in sec.questions"
              :key="q.sheetQuesId"
              class="card-cell"
              :class="cellClass(q)"
              @click="jumpTo(q)"
            >
              {{ q.seq }}
              <i v-if="marked.has(q.sheetQuesId)" class="cell-mark"></i>
            </div>
          </div>
        </div>
      </div>
      <div class="card-footer">
        <van-button block type="danger" round :loading="submitting" @click="onSubmitClick">
          交卷（未答 {{ unansweredCount }} 题）
        </van-button>
      </div>
    </van-popup>

    <van-overlay :show="submitting" class="submit-overlay">
      <van-loading size="28px" vertical color="#fff">{{ submitTip }}</van-loading>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog, showNotify, closeNotify } from 'vant'
import { getPaper, getSheet, saveAnswers, submitSheet, reportScreenEvent, getServerTime } from '../../api/exam'
import { useExamStore } from '../../stores/exam'
import { uuid } from '../../utils/device'
import { formatCountdown, parseServerTime } from '../../utils/format'
import * as answerQueue from '../../utils/answerQueue'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const examId = route.params.examId

const session = examStore.loadSession(examId)
if (!session) {
  router.replace('/exam')
}
const sheetId = session ? session.sheetId : null
const examName = ref(session ? session.examName : '在线测试')
const examRules = (session && session.examRules) || {}

const loading = ref(true)
const sections = ref([])
const questions = ref([]) // 扁平化题目（含分区信息）
const answers = reactive({}) // sheetQuesId -> {selected?/blanks?/text?}
const marked = reactive(new Set())
const currentIdx = ref(0)
const showCard = ref(false)

const remainingSec = ref((session && session.remainingSec) || 0)
const submitting = ref(false)
const submitTip = ref('正在交卷...')
const offlineTip = ref(false)

let destroyed = false
let submitted = false

// ---------- 倒计时（performance.now 差值渲染，防改系统时间） ----------
let remainBase = remainingSec.value
let remainBasePerf = performance.now()
let tickTimer = null
let calibrateTimer = null
let warned5min = false

function updateBaseline(sec) {
  remainBase = Math.max(0, Math.round(sec))
  remainBasePerf = performance.now()
  remainingSec.value = remainBase
}

function localRemaining() {
  return Math.max(0, remainBase - (performance.now() - remainBasePerf) / 1000)
}

function startTimers() {
  tickTimer = setInterval(() => {
    if (destroyed || submitted) return
    remainingSec.value = Math.round(localRemaining())
    if (!warned5min && remainingSec.value > 0 && remainingSec.value <= 300) {
      warned5min = true
      showDialog({ title: '时间提醒', message: '距测试结束仅剩 5 分钟，请抓紧时间作答。' })
    }
    if (remainingSec.value <= 0) {
      autoSubmit()
    }
  }, 1000)
  // 每 30 秒服务端校时
  calibrateTimer = setInterval(calibrateTime, 30000)
}

async function calibrateTime() {
  if (destroyed || submitted) return
  try {
    const reqPerf = performance.now()
    const t = await getServerTime()
    const serverNow = t.epochMillis + (performance.now() - reqPerf)
    const deadline = parseServerTime(session.deadlineTime)
    if (deadline > 0) {
      const serverRemaining = (deadline - serverNow) / 1000
      if (Math.abs(serverRemaining - localRemaining()) > 2) {
        updateBaseline(serverRemaining)
      }
    }
  } catch (e) {
    // 校时失败忽略，等下次
  }
}

// ---------- 答案判定 ----------
function isAnswered(q) {
  const a = answers[q.sheetQuesId]
  if (!a) return false
  if (a.selected) return a.selected.length > 0
  if (a.blanks) return a.blanks.some((b) => (b || '').trim() !== '')
  if (a.text !== undefined) return (a.text || '').trim() !== ''
  return false
}

const answeredCount = computed(() => questions.value.filter(isAnswered).length)
const unansweredCount = computed(() => questions.value.length - answeredCount.value)
const current = computed(() => questions.value[currentIdx.value])

function optionsOf(q) {
  if (q.quesType === 'JUDGE' && (!q.options || q.options.length === 0)) {
    return [
      { optKey: 'T', content: '正确' },
      { optKey: 'F', content: '错误' }
    ]
  }
  return q.options || []
}

function blankCountOf(q) {
  if (q.blankCount) return q.blankCount
  const m = String(q.stem || '').replace(/<[^>]+>/g, '').match(/_{2,}|＿{1,}|（\s*）|\(\s*\)/g)
  return m ? m.length : 1
}

// ---------- 作答控件绑定 ----------
const singleValue = computed(() => {
  const a = current.value && answers[current.value.sheetQuesId]
  return a && a.selected && a.selected.length ? a.selected[0] : ''
})
const multiValue = computed(() => {
  const a = current.value && answers[current.value.sheetQuesId]
  return (a && a.selected) || []
})
const essayValue = computed(() => {
  const a = current.value && answers[current.value.sheetQuesId]
  return (a && a.text) || ''
})

function blankValue(i) {
  const a = current.value && answers[current.value.sheetQuesId]
  return (a && a.blanks && a.blanks[i]) || ''
}

function onSingle(key) {
  const q = current.value
  answers[q.sheetQuesId] = { selected: [key] }
  markDirty(q.sheetQuesId)
}

const multiGuard = ref(false)
function onMulti(val) {
  if (multiGuard.value) return
  const q = current.value
  answers[q.sheetQuesId] = { selected: val.slice().sort() }
  markDirty(q.sheetQuesId)
}

function toggleMulti(key) {
  // van-cell click 与 checkbox 自身切换会双触发，这里手动接管
  multiGuard.value = true
  const cur = multiValue.value.slice()
  const i = cur.indexOf(key)
  if (i >= 0) cur.splice(i, 1)
  else cur.push(key)
  const q = current.value
  answers[q.sheetQuesId] = { selected: cur.sort() }
  markDirty(q.sheetQuesId)
  setTimeout(() => (multiGuard.value = false), 50)
}

function onBlank(i, v) {
  const q = current.value
  const a = answers[q.sheetQuesId] || {}
  const blanks = (a.blanks || []).slice()
  blanks[i] = v
  answers[q.sheetQuesId] = { blanks }
  markDirty(q.sheetQuesId)
}

function onEssay(v) {
  const q = current.value
  answers[q.sheetQuesId] = { text: v }
  markDirty(q.sheetQuesId)
}

// ---------- 答题卡 ----------
function cellClass(q) {
  const idx = questions.value.indexOf(q)
  return {
    'cell-answered': isAnswered(q),
    'cell-current': idx === currentIdx.value
  }
}

function jumpTo(q) {
  currentIdx.value = questions.value.indexOf(q)
  showCard.value = false
}

function gotoIdx(i) {
  if (i < 0 || i >= questions.value.length) return
  currentIdx.value = i
}

function toggleMark() {
  const id = current.value.sheetQuesId
  if (marked.has(id)) marked.delete(id)
  else marked.add(id)
  persistSnapshot()
}

// ---------- 答案保存：3s 防抖 + 30s 心跳 + 断网本地队列 ----------
const pending = new Map() // sheetQuesId -> answer
let debounceTimer = null
let heartbeatTimer = null
let saving = false

function markDirty(id) {
  pending.set(id, answers[id])
  persistSnapshot()
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(flushPending, 3000)
}

function persistSnapshot() {
  answerQueue.saveSnapshot(sheetId, { answers, marked: Array.from(marked) })
}

async function flushPending() {
  if (saving || destroyed || submitted) return
  // 先重放本地队列（按序），再发送本次
  await replayQueue()
  if (pending.size === 0) return
  const batch = {
    clientBatchId: uuid(),
    answers: Array.from(pending.entries()).map(([id, a]) => ({ sheetQuesId: id, answer: a }))
  }
  pending.clear()
  await sendBatch(batch)
}

async function sendBatch(batch) {
  saving = true
  try {
    const data = await saveAnswers(sheetId, batch)
    answerQueue.dequeueBatch(sheetId, batch.clientBatchId)
    offlineTip.value = answerQueue.loadQueue(sheetId).length > 0
    if (typeof data.remainingSec === 'number') updateBaseline(data.remainingSec)
    if (data.submitStatus && data.submitStatus !== 'IN_EXAM') {
      onServerTerminated(data.submitStatus)
    }
  } catch (e) {
    if (e.kicked) return
    if (e.code === 50003) {
      onServerTerminated('SUBMITTED')
      return
    }
    // 网络类失败：入本地队列，恢复网络后重放
    answerQueue.enqueueBatch(sheetId, batch)
    offlineTip.value = true
  } finally {
    saving = false
  }
}

async function replayQueue() {
  const queue = answerQueue.loadQueue(sheetId)
  for (const batch of queue) {
    if (destroyed || submitted) return
    try {
      const data = await saveAnswers(sheetId, batch)
      answerQueue.dequeueBatch(sheetId, batch.clientBatchId)
      if (typeof data.remainingSec === 'number') updateBaseline(data.remainingSec)
      if (data.submitStatus && data.submitStatus !== 'IN_EXAM') {
        onServerTerminated(data.submitStatus)
        return
      }
    } catch (e) {
      if (e.kicked || e.code === 50003) {
        if (e.code === 50003) onServerTerminated('SUBMITTED')
        return
      }
      return // 仍在断网，保留队列等下次
    }
  }
  if (answerQueue.loadQueue(sheetId).length === 0) offlineTip.value = false
}

function onOnline() {
  if (!destroyed && !submitted) flushPending()
}

// ---------- 切屏上报 ----------
function nowStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

let hidden = false
async function onVisibility() {
  if (destroyed || submitted) return
  const eventType = document.visibilityState === 'hidden' ? 'LEAVE' : 'RETURN'
  if (eventType === 'LEAVE') hidden = true
  try {
    const data = await reportScreenEvent(sheetId, { eventType, occurredAt: nowStr() })
    if (eventType === 'LEAVE' && data.action === 'WARN') {
      // 回前台后提示（hidden 期间 toast 不可见，记录待 RETURN 时提示由服务端计数为准）
    }
    if (eventType === 'RETURN' && data.action === 'WARN' && hidden) {
      showDialog({ title: '切屏警告', message: `已切屏 ${data.leaveCount} 次，上限 ${data.limit} 次，超限将被强制交卷。` })
    }
    if (data.action === 'FORCE_SUBMIT') {
      onServerTerminated('FORCED')
    }
    hidden = false
  } catch (e) {
    hidden = false
  }
}

// ---------- 服务端终止（强制收卷/到点收卷/异地） ----------
function onServerTerminated(status) {
  if (submitted) return
  submitted = true
  cleanup()
  answerQueue.clearAll(sheetId)
  examStore.clearSession(examId)
  showDialog({
    title: '测试已结束',
    message: status === 'FORCED' ? '测试已被强制收卷。' : '测试已交卷。'
  }).finally(() => {
    router.replace(`/exam/result/${examId}`)
  })
}

// ---------- 交卷 ----------
function checkEarlySubmit() {
  if (examRules.allowEarlySubmit === false) {
    const elapsed = (examRules.durationMin || 0) * 60 - remainingSec.value
    const need = (examRules.earlySubmitAfterMin || 0) * 60
    if (elapsed < need) {
      showToast(`开考 ${examRules.earlySubmitAfterMin} 分钟后方可交卷`)
      return false
    }
  }
  return true
}

async function onSubmitClick() {
  if (submitting.value || submitted) return
  if (!checkEarlySubmit()) return
  showCard.value = false
  try {
    await showDialog({
      title: '确认交卷',
      message:
        unansweredCount.value > 0
          ? `还有 ${unansweredCount.value} 题未作答，确定交卷吗？`
          : '全部题目已作答，确定交卷吗？',
      showCancelButton: true,
      confirmButtonText: '确定交卷'
    })
  } catch (e) {
    return // 取消
  }
  doSubmit()
}

function autoSubmit() {
  if (submitted || submitting.value) return
  showNotify({ type: 'warning', message: '测试时间到，正在自动交卷...', duration: 0 })
  doSubmit()
}

async function doSubmit() {
  submitting.value = true
  submitTip.value = '正在交卷...'
  // 先尽力把未同步答案发出去
  await flushPending()
  const clientSubmitId = uuid()
  const backoffs = [1000, 3000, 8000]
  let attempt = 0
  while (!destroyed && !submitted) {
    try {
      const data = await submitSheet(sheetId, { confirmUnanswered: true, clientSubmitId })
      finishSubmit(data)
      return
    } catch (e) {
      if (e.kicked) return
      if (e.code === 50003) {
        // 幂等：首提已成功，data 携带原交卷结果
        finishSubmit(e.data || null)
        return
      }
      attempt += 1
      submitTip.value = `交卷失败，正在重试（第${attempt}次）...`
      await sleep(backoffs[Math.min(attempt - 1, backoffs.length - 1)])
    }
  }
}

function finishSubmit(result) {
  submitted = true
  submitting.value = false
  closeNotify()
  cleanup()
  answerQueue.clearAll(sheetId)
  examStore.clearSession(examId)
  if (result) {
    sessionStorage.setItem(`h5_submit_result_${examId}`, JSON.stringify(result))
  }
  router.replace(`/exam/result/${examId}`)
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

// ---------- 初始化 ----------
async function init() {
  try {
    const paper = await getPaper(examId, sheetId)
    sections.value = paper.sections || []
    examName.value = paper.paperName || examName.value
    const flat = []
    sections.value.forEach((sec) => {
      ;(sec.questions || []).forEach((q) => {
        flat.push({
          ...q,
          quesType: sec.quesType,
          typeName: sec.typeName,
          scorePerQues: sec.scorePerQues
        })
      })
    })
    questions.value = flat
    if (typeof paper.remainingSec === 'number') updateBaseline(paper.remainingSec)

    // 断线续考：以服务端答卷为准对账（TAKE-08）
    try {
      const sheet = await getSheet(sheetId)
      ;(sheet.answers || []).forEach((item) => {
        if (item.answer) answers[item.sheetQuesId] = item.answer
      })
      if (typeof sheet.remainingSec === 'number') updateBaseline(sheet.remainingSec)
      if (sheet.deadlineTime) session.deadlineTime = sheet.deadlineTime
      if (sheet.submitStatus && sheet.submitStatus !== 'IN_EXAM') {
        onServerTerminated(sheet.submitStatus)
        return
      }
    } catch (e) {
      if (e.kicked) return
      // 拉取答卷状态失败：退回本地快照
      const snap = answerQueue.loadSnapshot(sheetId)
      if (snap) {
        Object.assign(answers, snap.answers || {})
        ;(snap.marked || []).forEach((id) => marked.add(id))
      }
    }

    startTimers()
    heartbeatTimer = setInterval(flushPending, 30000)
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', onOnline)
    window.addEventListener('beforeunload', onBeforeUnload)
  } catch (e) {
    if (!e.kicked) {
      showDialog({ title: '进入测试失败', message: e.message || '请稍后重试' }).finally(() => router.replace('/exam'))
    }
  } finally {
    loading.value = false
  }
}

function onBeforeUnload(e) {
  if (!submitted) {
    e.preventDefault()
    e.returnValue = ''
  }
}

function cleanup() {
  destroyed = true
  if (tickTimer) clearInterval(tickTimer)
  if (calibrateTimer) clearInterval(calibrateTimer)
  if (debounceTimer) clearTimeout(debounceTimer)
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('beforeunload', onBeforeUnload)
}

onMounted(init)
onBeforeUnmount(() => {
  cleanup()
  if (!submitted) {
    // 离开答题页（如误触返回）：把未同步答案尽力入队/发送
    flushPending()
  }
})
</script>

<style scoped>
.answer-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 72px;
}

.answer-top {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.answer-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-right: 12px;
}

.answer-countdown {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 17px;
  font-weight: 600;
  color: #1989fa;
  font-variant-numeric: tabular-nums;
}

.page-loading {
  display: block;
  text-align: center;
  margin-top: 100px;
}

.ques-section-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
}

.ques-flag {
  margin-left: auto;
}

.ques-stem {
  padding: 0 16px 12px;
  font-size: 16px;
  line-height: 1.8;
  color: #323233;
  word-break: break-word;
}

.ques-stem :deep(img) {
  max-width: 100%;
}

.ques-control {
  margin-top: 4px;
}

.opt-key {
  font-weight: 600;
  margin-right: 6px;
}

.offline-tip {
  margin: 12px 16px;
  padding: 8px 12px;
  background: #fffbe8;
  color: #ed6a0c;
  font-size: 12px;
  border-radius: 6px;
}

.answer-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  justify-content: space-around;
  gap: 8px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -1px 6px rgba(0, 0, 0, 0.08);
}

.card-popup {
  max-width: 750px;
  margin: 0 auto;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 8px;
  font-size: 16px;
  font-weight: 600;
}

.card-legend {
  display: flex;
  gap: 16px;
  padding: 0 16px 8px;
  font-size: 12px;
  color: #646566;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
}

.dot-answered {
  background: #07c160;
}

.dot-unanswered {
  background: #fff;
  border: 1px solid #dcdee0;
}

.dot-marked {
  background: #ff976a;
}

.card-body {
  overflow-y: auto;
  padding: 0 16px;
  flex: 1;
}

.card-section-title {
  font-size: 13px;
  color: #646566;
  margin: 10px 0 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.card-cell {
  position: relative;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdee0;
  border-radius: 6px;
  font-size: 14px;
  color: #323233;
  background: #fff;
}

.cell-answered {
  background: #07c160;
  border-color: #07c160;
  color: #fff;
}

.cell-current {
  box-shadow: 0 0 0 2px #1989fa;
}

.cell-mark {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ff976a;
  border: 2px solid #fff;
}

.card-footer {
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.submit-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
