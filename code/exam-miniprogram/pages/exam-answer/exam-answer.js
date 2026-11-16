// TAKE-03~08 在线答题页：拉卷 / 倒计时（服务端校时）/ 答案暂存（防抖+本地队列）/ 切屏上报 / 幂等交卷
// 逻辑对齐 H5 端 views/exam/ExamAnswer.vue（已联调通过），字段名保持一致。
const { get, post, put } = require('../../utils/request')
const auth = require('../../utils/auth')
const { uuid, nowStr } = require('../../utils/device')
const { formatCountdown, parseServerTime } = require('../../utils/format')

const SAVE_DEBOUNCE_MS = 3000 // 作答后 3s 防抖暂存
const SAVE_HEARTBEAT_MS = 30000 // 30s 定时兜底暂存
const CALIBRATE_MS = 30000 // 30s 服务端校时

Page({
  data: {
    loading: true,
    examName: '在线考试',
    countdownText: '--:--',
    countdownDanger: false,
    current: null, // 当前题视图模型
    currentIdx: 0,
    total: 0,
    offlineTip: false,
    showCard: false,
    card: null, // 答题卡视图模型
    submitting: false,
    submitTip: '正在交卷...'
  },

  examId: null,
  sheetId: null,
  session: null,
  examRules: {},
  questions: [], // 扁平化题目（含分区信息）
  answers: {}, // sheetQuesId -> {selected?/blanks?/text?}
  marked: {}, // sheetQuesId -> true
  destroyed: false,
  submitted: false,

  // 倒计时基线（Date.now 差值渲染，防改系统时间）
  remainBase: 0,
  remainBaseAt: 0,
  tickTimer: null,
  calibrateTimer: null,
  warned5min: false,

  // 答案保存
  pending: null, // Map
  debounceTimer: null,
  saveTimer: null,
  saving: false,

  // 切屏
  hidden: false,
  inited: false,

  onLoad(options) {
    this.examId = options.examId
    this.pending = new Map()
    if (!auth.ensureAuth()) return
    let session = null
    try {
      session = wx.getStorageSync('mp_exam_session_' + this.examId)
    } catch (e) {
      // ignore
    }
    if (!session || !session.sheetId) {
      wx.reLaunch({ url: '/pages/exam/exam' })
      return
    }
    this.session = session
    this.sheetId = session.sheetId
    this.examRules = session.examRules || {}
    this.updateBaseline(session.remainingSec || 0)
    this.setData({ examName: session.examName || '在线考试' })
    this.init()
  },

  onUnload() {
    this.cleanup()
    if (!this.submitted) {
      // 离开答题页（如误触返回）：把未同步答案尽力入队/发送
      this.flushPending()
    }
  },

  // ---------- 切屏上报（TAKE-06） ----------
  // 页面 onHide/onShow 在「切入后台（等价 wx.onAppHide/onAppShow）」与「页面被压栈/返回」时均会触发，
  // 与 H5 端 visibilitychange 语义对齐。
  onHide() {
    if (!this.inited || this.destroyed || this.submitted) return
    this.reportScreen('LEAVE')
  },

  onShow() {
    if (!this.inited || this.destroyed || this.submitted) return
    this.reportScreen('RETURN')
  },

  async reportScreen(eventType) {
    if (eventType === 'LEAVE') this.hidden = true
    try {
      const data = await post(
        '/app/sheets/' + this.sheetId + '/screen-events',
        { eventType, occurredAt: nowStr() },
        { __silent: true }
      )
      if (eventType === 'RETURN' && data.action === 'WARN' && this.hidden) {
        wx.showModal({
          title: '切屏警告',
          content: '已切屏 ' + data.leaveCount + ' 次，上限 ' + data.limit + ' 次，超限将被强制交卷。',
          showCancel: false
        })
      }
      if (data.action === 'FORCE_SUBMIT') {
        this.onServerTerminated('FORCED')
      }
      this.hidden = false
    } catch (e) {
      this.hidden = false
    }
  },

  // ---------- 倒计时 ----------
  updateBaseline(sec) {
    this.remainBase = Math.max(0, Math.round(sec))
    this.remainBaseAt = Date.now()
    this.renderCountdown(this.remainBase)
  },

  localRemaining() {
    return Math.max(0, this.remainBase - (Date.now() - this.remainBaseAt) / 1000)
  },

  renderCountdown(sec) {
    this.setData({
      countdownText: formatCountdown(sec),
      countdownDanger: sec > 0 && sec <= 300
    })
  },

  startTimers() {
    this.tickTimer = setInterval(() => {
      if (this.destroyed || this.submitted) return
      const sec = Math.round(this.localRemaining())
      this.renderCountdown(sec)
      if (!this.warned5min && sec > 0 && sec <= 300) {
        this.warned5min = true
        wx.showModal({ title: '时间提醒', content: '距考试结束仅剩 5 分钟，请抓紧时间作答。', showCancel: false })
      }
      if (sec <= 0) {
        this.autoSubmit()
      }
    }, 1000)
    this.calibrateTimer = setInterval(() => this.calibrateTime(), CALIBRATE_MS)
  },

  // TAKE-03 服务端校时（静默）
  async calibrateTime() {
    if (this.destroyed || this.submitted) return
    try {
      const reqAt = Date.now()
      const t = await get('/app/time', null, { __silent: true })
      const serverNow = t.epochMillis + (Date.now() - reqAt)
      const deadline = parseServerTime(this.session.deadlineTime)
      if (deadline > 0) {
        const serverRemaining = (deadline - serverNow) / 1000
        if (Math.abs(serverRemaining - this.localRemaining()) > 2) {
          this.updateBaseline(serverRemaining)
        }
      }
    } catch (e) {
      // 校时失败忽略，等下次
    }
  },

  // ---------- 初始化 ----------
  async init() {
    try {
      // TAKE-04 拉取试卷（脱敏）
      const paper = await get('/app/exams/' + this.examId + '/paper', { sheetId: this.sheetId })
      const sections = paper.sections || []
      const flat = []
      let seq = 0
      sections.forEach((sec) => {
        ;(sec.questions || []).forEach((q) => {
          seq += 1
          flat.push(
            Object.assign({}, q, {
              seq,
              quesType: sec.quesType,
              typeName: sec.typeName,
              scorePerQues: sec.scorePerQues
            })
          )
        })
      })
      this.questions = flat
      if (paper.paperName) this.setData({ examName: paper.paperName })
      if (typeof paper.remainingSec === 'number') this.updateBaseline(paper.remainingSec)

      // TAKE-08 断线续考：以服务端答卷为准对账
      try {
        const sheet = await get('/app/sheets/' + this.sheetId)
        ;(sheet.answers || []).forEach((item) => {
          if (item.answer) this.answers[item.sheetQuesId] = item.answer
        })
        if (typeof sheet.remainingSec === 'number') this.updateBaseline(sheet.remainingSec)
        if (sheet.deadlineTime) this.session.deadlineTime = sheet.deadlineTime
        if (sheet.submitStatus && sheet.submitStatus !== 'IN_EXAM') {
          this.onServerTerminated(sheet.submitStatus)
          return
        }
      } catch (e) {
        if (e.kicked) return
        // 拉取答卷状态失败：退回本地快照
        const snap = this.loadSnapshot()
        if (snap) {
          Object.assign(this.answers, snap.answers || {})
          ;(snap.marked || []).forEach((id) => {
            this.marked[id] = true
          })
        }
      }

      this.inited = true
      this.setData({ total: flat.length })
      this.buildCurrent()
      this.rebuildCard()
      this.startTimers()
      this.saveTimer = setInterval(() => this.flushPending(), SAVE_HEARTBEAT_MS)
    } catch (e) {
      if (!e.kicked) {
        wx.showModal({
          title: '进入考试失败',
          content: e.message || '请稍后重试',
          showCancel: false,
          success: () => wx.reLaunch({ url: '/pages/exam/exam' })
        })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  cleanup() {
    this.destroyed = true
    if (this.tickTimer) clearInterval(this.tickTimer)
    if (this.calibrateTimer) clearInterval(this.calibrateTimer)
    if (this.debounceTimer) clearTimeout(this.debounceTimer)
    if (this.saveTimer) clearInterval(this.saveTimer)
  },

  // ---------- 视图模型 ----------
  optionsOf(q) {
    if (q.quesType === 'JUDGE' && (!q.options || q.options.length === 0)) {
      return [
        { optKey: 'T', content: '正确' },
        { optKey: 'F', content: '错误' }
      ]
    }
    return q.options || []
  },

  blankCountOf(q) {
    if (q.blankCount) return q.blankCount
    const m = String(q.stem || '')
      .replace(/<[^>]+>/g, '')
      .match(/_{2,}|＿{1,}|（\s*）|\(\s*\)/g)
    return m ? m.length : 1
  },

  isAnswered(q) {
    const a = this.answers[q.sheetQuesId]
    if (!a) return false
    if (a.selected) return a.selected.length > 0
    if (a.blanks) return a.blanks.some((b) => (b || '').trim() !== '')
    if (a.text !== undefined) return (a.text || '').trim() !== ''
    return false
  },

  buildCurrent() {
    const q = this.questions[this.data.currentIdx]
    if (!q) {
      this.setData({ current: null })
      return
    }
    const a = this.answers[q.sheetQuesId] || {}
    const selected = a.selected || []
    const view = {
      sheetQuesId: q.sheetQuesId,
      seq: q.seq,
      quesType: q.quesType,
      typeName: q.typeName,
      scorePerQues: q.scorePerQues,
      stem: q.stem || '',
      marked: !!this.marked[q.sheetQuesId],
      options: this.optionsOf(q).map((opt) => ({
        optKey: opt.optKey,
        content: opt.content,
        checked: selected.indexOf(opt.optKey) >= 0
      })),
      blanks: [],
      essayText: a.text || ''
    }
    if (q.quesType === 'FILL') {
      const n = this.blankCountOf(q)
      for (let i = 0; i < n; i++) {
        view.blanks.push({ index: i, label: '第' + (i + 1) + '空', value: (a.blanks && a.blanks[i]) || '' })
      }
    }
    this.setData({ current: view })
  },

  rebuildCard() {
    const sections = []
    let answeredCount = 0
    this.questions.forEach((q, idx) => {
      const answered = this.isAnswered(q)
      if (answered) answeredCount += 1
      let sec = sections.find((s) => s.quesType === q.quesType)
      if (!sec) {
        sec = { quesType: q.quesType, typeName: q.typeName, scorePerQues: q.scorePerQues, cells: [] }
        sections.push(sec)
      }
      sec.cells.push({
        idx,
        seq: q.seq,
        sheetQuesId: q.sheetQuesId,
        answered,
        marked: !!this.marked[q.sheetQuesId],
        isCurrent: idx === this.data.currentIdx
      })
    })
    this.setData({
      card: {
        answeredCount,
        total: this.questions.length,
        unansweredCount: this.questions.length - answeredCount,
        sections
      }
    })
  },

  // ---------- 作答 ----------
  onOptionTap(e) {
    const key = e.currentTarget.dataset.key
    const q = this.questions[this.data.currentIdx]
    if (!q) return
    if (q.quesType === 'SINGLE' || q.quesType === 'JUDGE') {
      this.answers[q.sheetQuesId] = { selected: [key] }
    } else if (q.quesType === 'MULTI') {
      const a = this.answers[q.sheetQuesId] || {}
      const cur = (a.selected || []).slice()
      const i = cur.indexOf(key)
      if (i >= 0) cur.splice(i, 1)
      else cur.push(key)
      this.answers[q.sheetQuesId] = { selected: cur.sort() }
    } else {
      return
    }
    this.afterAnswer(q.sheetQuesId)
  },

  onBlankInput(e) {
    const q = this.questions[this.data.currentIdx]
    if (!q) return
    const i = e.currentTarget.dataset.index
    const a = this.answers[q.sheetQuesId] || {}
    const blanks = (a.blanks || []).slice()
    blanks[i] = e.detail.value
    this.answers[q.sheetQuesId] = { blanks }
    this.afterAnswer(q.sheetQuesId, true)
  },

  onEssayInput(e) {
    const q = this.questions[this.data.currentIdx]
    if (!q) return
    this.answers[q.sheetQuesId] = { text: e.detail.value }
    this.afterAnswer(q.sheetQuesId, true)
  },

  // textInput: 输入中不重建视图（避免光标跳动），仅入暂存队列
  afterAnswer(sheetQuesId, textInput) {
    this.markDirty(sheetQuesId)
    if (!textInput) {
      this.buildCurrent()
      this.rebuildCard()
    }
  },

  // ---------- 答案保存：3s 防抖 + 30s 心跳 + 断网本地队列（TAKE-05） ----------
  markDirty(id) {
    this.pending.set(id, this.answers[id])
    this.persistSnapshot()
    if (this.debounceTimer) clearTimeout(this.debounceTimer)
    this.debounceTimer = setTimeout(() => this.flushPending(), SAVE_DEBOUNCE_MS)
  },

  snapshotKey() {
    return 'mp_answer_snap_' + this.sheetId
  },
  queueKey() {
    return 'mp_answer_queue_' + this.sheetId
  },

  persistSnapshot() {
    try {
      wx.setStorageSync(this.snapshotKey(), {
        answers: this.answers,
        marked: Object.keys(this.marked)
      })
    } catch (e) {
      // ignore
    }
  },

  loadSnapshot() {
    try {
      return wx.getStorageSync(this.snapshotKey()) || null
    } catch (e) {
      return null
    }
  },

  loadQueue() {
    try {
      return wx.getStorageSync(this.queueKey()) || []
    } catch (e) {
      return []
    }
  },

  saveQueue(queue) {
    try {
      wx.setStorageSync(this.queueKey(), queue)
    } catch (e) {
      // ignore
    }
  },

  enqueueBatch(batch) {
    const queue = this.loadQueue()
    queue.push(batch)
    this.saveQueue(queue)
  },

  dequeueBatch(clientBatchId) {
    this.saveQueue(this.loadQueue().filter((b) => b.clientBatchId !== clientBatchId))
  },

  clearAllLocal() {
    try {
      wx.removeStorageSync(this.snapshotKey())
      wx.removeStorageSync(this.queueKey())
    } catch (e) {
      // ignore
    }
  },

  async flushPending() {
    if (this.saving || this.destroyed || this.submitted) return
    // 先重放本地队列（按序），再发送本次
    await this.replayQueue()
    if (this.pending.size === 0) return
    const batch = {
      clientBatchId: uuid(),
      answers: Array.from(this.pending.entries()).map(([id, a]) => ({ sheetQuesId: id, answer: a }))
    }
    this.pending.clear()
    await this.sendBatch(batch)
  },

  async sendBatch(batch) {
    this.saving = true
    try {
      const data = await put('/app/sheets/' + this.sheetId + '/answers', batch, { __silent: true, timeout: 10000 })
      this.dequeueBatch(batch.clientBatchId)
      this.setData({ offlineTip: this.loadQueue().length > 0 })
      if (typeof data.remainingSec === 'number') this.updateBaseline(data.remainingSec)
      if (data.submitStatus && data.submitStatus !== 'IN_EXAM') {
        this.onServerTerminated(data.submitStatus)
      }
    } catch (e) {
      if (e.kicked) return
      if (e.code === 50003) {
        this.onServerTerminated('SUBMITTED')
        return
      }
      // 网络类失败：入本地队列，恢复网络后重放
      this.enqueueBatch(batch)
      this.setData({ offlineTip: true })
    } finally {
      this.saving = false
    }
  },

  async replayQueue() {
    const queue = this.loadQueue()
    for (const batch of queue) {
      if (this.destroyed || this.submitted) return
      try {
        const data = await put('/app/sheets/' + this.sheetId + '/answers', batch, { __silent: true, timeout: 10000 })
        this.dequeueBatch(batch.clientBatchId)
        if (typeof data.remainingSec === 'number') this.updateBaseline(data.remainingSec)
        if (data.submitStatus && data.submitStatus !== 'IN_EXAM') {
          this.onServerTerminated(data.submitStatus)
          return
        }
      } catch (e) {
        if (e.kicked || e.code === 50003) {
          if (e.code === 50003) this.onServerTerminated('SUBMITTED')
          return
        }
        return // 仍在断网，保留队列等下次
      }
    }
    if (this.loadQueue().length === 0) this.setData({ offlineTip: false })
  },

  // ---------- 答题卡 ----------
  openCard() {
    this.rebuildCard()
    this.setData({ showCard: true })
  },
  closeCard() {
    this.setData({ showCard: false })
  },
  onCellTap(e) {
    const idx = e.currentTarget.dataset.idx
    this.gotoIdx(idx)
    this.closeCard()
  },

  gotoPrev() {
    this.gotoIdx(this.data.currentIdx - 1)
  },
  gotoNext() {
    this.gotoIdx(this.data.currentIdx + 1)
  },
  gotoIdx(i) {
    if (i < 0 || i >= this.questions.length) return
    this.setData({ currentIdx: i })
    this.buildCurrent()
    this.rebuildCard()
  },

  toggleMark() {
    const q = this.questions[this.data.currentIdx]
    if (!q) return
    if (this.marked[q.sheetQuesId]) delete this.marked[q.sheetQuesId]
    else this.marked[q.sheetQuesId] = true
    this.persistSnapshot()
    this.buildCurrent()
    this.rebuildCard()
  },

  // ---------- 服务端终止（强制收卷/到点收卷） ----------
  onServerTerminated(status) {
    if (this.submitted) return
    this.submitted = true
    this.cleanup()
    this.clearAllLocal()
    try {
      wx.removeStorageSync('mp_exam_session_' + this.examId)
    } catch (e) {
      // ignore
    }
    wx.showModal({
      title: '考试已结束',
      content: status === 'FORCED' ? '考试已被强制收卷。' : '考试已交卷。',
      showCancel: false,
      complete: () => {
        wx.redirectTo({ url: '/pages/exam-result/exam-result?examId=' + this.examId })
      }
    })
  },

  // ---------- 交卷（TAKE-07，clientSubmitId 幂等 + 退避重试） ----------
  checkEarlySubmit() {
    if (this.examRules.allowEarlySubmit === false) {
      const elapsed = (this.examRules.durationMin || 0) * 60 - this.localRemaining()
      const need = (this.examRules.earlySubmitAfterMin || 0) * 60
      if (elapsed < need) {
        wx.showToast({ title: '开考 ' + this.examRules.earlySubmitAfterMin + ' 分钟后方可交卷', icon: 'none' })
        return false
      }
    }
    return true
  },

  onSubmitClick() {
    if (this.data.submitting || this.submitted) return
    if (!this.checkEarlySubmit()) return
    this.setData({ showCard: false })
    const unanswered = (this.data.card && this.data.card.unansweredCount) || 0
    wx.showModal({
      title: '确认交卷',
      content: unanswered > 0 ? '还有 ' + unanswered + ' 题未作答，确定交卷吗？' : '全部题目已作答，确定交卷吗？',
      confirmText: '确定交卷',
      success: (res) => {
        if (res.confirm) this.doSubmit()
      }
    })
  },

  autoSubmit() {
    if (this.submitted || this.data.submitting) return
    wx.showToast({ title: '考试时间到，正在自动交卷...', icon: 'none', duration: 3000 })
    this.doSubmit()
  },

  async doSubmit() {
    this.setData({ submitting: true, submitTip: '正在交卷...' })
    // 先尽力把未同步答案发出去
    await this.flushPending()
    const clientSubmitId = uuid()
    const backoffs = [1000, 3000, 8000]
    let attempt = 0
    while (!this.destroyed && !this.submitted) {
      try {
        const data = await post(
          '/app/sheets/' + this.sheetId + '/submit',
          { confirmUnanswered: true, clientSubmitId },
          { __silent: true, timeout: 15000 }
        )
        this.finishSubmit(data)
        return
      } catch (e) {
        if (e.kicked) return
        if (e.code === 50003) {
          // 幂等：首提已成功，data 携带原交卷结果
          this.finishSubmit(e.data || null)
          return
        }
        attempt += 1
        this.setData({ submitTip: '交卷失败，正在重试（第' + attempt + '次）...' })
        await this.sleep(backoffs[Math.min(attempt - 1, backoffs.length - 1)])
      }
    }
  },

  finishSubmit(result) {
    this.submitted = true
    this.setData({ submitting: false })
    this.cleanup()
    this.clearAllLocal()
    try {
      wx.removeStorageSync('mp_exam_session_' + this.examId)
      if (result) {
        wx.setStorageSync('mp_submit_result_' + this.examId, result)
      }
    } catch (e) {
      // ignore
    }
    wx.redirectTo({ url: '/pages/exam-result/exam-result?examId=' + this.examId })
  },

  noop() {},

  sleep(ms) {
    return new Promise((r) => setTimeout(r, ms))
  }
})
