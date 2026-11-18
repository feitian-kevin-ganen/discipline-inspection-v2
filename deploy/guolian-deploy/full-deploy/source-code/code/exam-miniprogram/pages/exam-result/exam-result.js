// TAKE-09 成绩结果页：交卷回执 + 成绩查询（未发布时每 30s 轮询）
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')
const { quesTypeName } = require('../../utils/format')

Page({
  data: {
    loading: true,
    result: null,
    submitResult: null,
    details: [] // 预处理后的解析列表
  },

  examId: null,
  pollTimer: null,

  onLoad(options) {
    this.examId = options.examId
    if (!auth.ensureAuth()) return
    let submitResult = null
    try {
      submitResult = wx.getStorageSync('mp_submit_result_' + this.examId) || null
    } catch (e) {
      // ignore
    }
    this.setData({ submitResult })
    this.load()
    // 成绩等待页每 30 秒轮询（API 设计 6.4）
    this.pollTimer = setInterval(() => {
      if (!this.data.result || !this.data.result.scorePublished) this.load()
    }, 30000)
  },

  onUnload() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },

  // 作答/正确答案取值：兼容数组与 {keys}/{selected}/{blanks}/{text}/{points} 对象形态（对齐 PC 端 ScoreDetail）
  fmtAns(ans, type) {
    if (ans == null) return '未作答'
    if (Array.isArray(ans)) {
      if (!ans.length) return '未作答'
      if (type === 'JUDGE') return ans.map((x) => (x === 'T' ? '正确' : '错误')).join('、')
      return ans.join('、')
    }
    if (typeof ans === 'object') {
      if (ans.keys) return this.fmtAns(ans.keys, type)
      if (ans.selected) return this.fmtAns(ans.selected, type)
      if (ans.blanks) return ans.blanks.filter(Boolean).join('；') || '未作答'
      if (ans.text != null) return ans.text || '未作答'
      if (ans.points) return ans.points.filter(Boolean).join('；') || '未作答'
    }
    return String(ans) || '未作答'
  },

  decorateDetails(details) {
    return (details || []).map((d) =>
      Object.assign({}, d, {
        quesTypeText: quesTypeName[d.quesType] || d.quesType,
        myAnswerText: this.fmtAns(d.myAnswer, d.quesType),
        correctAnswerText: this.fmtAns(d.correctAnswer, d.quesType)
      })
    )
  },

  async load() {
    try {
      const data = await get('/app/exams/' + this.examId + '/result')
      this.setData({ result: data, details: this.decorateDetails(data.details) })
      if (data && data.scorePublished && this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    } catch (e) {
      // 50009 未发布等由拦截器提示；保持等待态
      if (!this.data.result) this.setData({ result: { scorePublished: false } })
    } finally {
      this.setData({ loading: false })
    }
  },

  backToExamList() {
    wx.switchTab({ url: '/pages/exam/exam' })
  }
})
