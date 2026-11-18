// PRAC-02 练习答题页：一题一页，提交后即时反馈（对/错 + 正确答案 + 解析 + 来源法规）
const { post } = require('../../utils/request')

const SESSION_KEY = 'mp_practice_session'

function genClientEventId() {
  return 'mp-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

Page({
  data: {
    session: null,
    current: null,
    options: [],
    selected: '',
    feedback: null,
    correctText: '',
    submitting: false,
    progressPct: 0,
    isLast: false
  },

  enterTime: 0,
  clientEventId: '',

  onLoad() {
    let s = null
    try {
      s = wx.getStorageSync(SESSION_KEY)
    } catch (e) {
      // ignore
    }
    if (!s || !s.questions || !s.questions.length) {
      wx.showToast({ title: '练习会话不存在，请重新发起', icon: 'none' })
      setTimeout(() => wx.navigateBack({ delta: 1 }), 800)
      return
    }
    this.setData({ session: s })
    if (!s.finished) this.renderQuestion()
  },

  // 渲染当前题并重置作答状态
  renderQuestion() {
    const s = this.data.session
    const current = s.questions[s.index]
    this.enterTime = Date.now()
    this.clientEventId = genClientEventId()
    this.setData({
      current,
      selected: '',
      feedback: null,
      correctText: '',
      options: this.decorateOptions(current.options || [], '', []),
      progressPct: Math.round((s.index / s.questions.length) * 100),
      isLast: s.index >= s.questions.length - 1
    })
  },

  // 选项样式：未提交高亮已选；提交后正确答案绿、错选红
  decorateOptions(options, selected, correctKeys, feedback) {
    return options.map((o) => {
      let cls = ''
      if (!feedback) {
        cls = selected === o.optKey ? 'opt-selected' : ''
      } else if (correctKeys.indexOf(o.optKey) >= 0) {
        cls = 'opt-right'
      } else if (!feedback.isRight && selected === o.optKey) {
        cls = 'opt-wrong'
      }
      return Object.assign({}, o, { cls })
    })
  },

  onSelect(e) {
    if (this.data.feedback) return
    const selected = e.currentTarget.dataset.key
    this.setData({
      selected,
      options: this.decorateOptions(this.data.current.options || [], selected, [])
    })
  },

  async onSubmit() {
    const { session, current, selected } = this.data
    if (!selected) {
      wx.showToast({ title: '请先选择答案', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const body = {
        quesItemId: current.quesItemId,
        answer: { selected: [selected] },
        mode: session.mode,
        durationSec: Math.max(1, Math.round((Date.now() - this.enterTime) / 1000)),
        clientEventId: this.clientEventId
      }
      if (session.mode === 'CATEGORY' && session.bankId) body.bankId = session.bankId
      const res = await post('/app/practice/answer', body)
      const correctKeys = res.correctAnswer || []
      this.setData({
        feedback: res,
        correctText: correctKeys.join('、'),
        options: this.decorateOptions(current.options || [], selected, correctKeys, res),
        progressPct: Math.round(((session.index + 1) / session.questions.length) * 100)
      })
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ submitting: false })
    }
  },

  onNext() {
    const s = this.data.session
    const isRight = this.data.feedback && this.data.feedback.isRight
    if (isRight === true) s.rightCount += 1
    if (isRight === false) s.wrongCount += 1
    if (s.index >= s.questions.length - 1) {
      s.finished = true
    } else {
      s.index += 1
    }
    wx.setStorageSync(SESSION_KEY, s)
    this.setData({ session: s })
    if (!s.finished) this.renderQuestion()
  },

  onAgain() {
    wx.removeStorageSync(SESSION_KEY)
    wx.switchTab({ url: '/pages/practice/practice' })
  },

  goRecords() {
    wx.removeStorageSync(SESSION_KEY)
    wx.navigateTo({ url: '/pages/practice-records/practice-records' })
  }
})
