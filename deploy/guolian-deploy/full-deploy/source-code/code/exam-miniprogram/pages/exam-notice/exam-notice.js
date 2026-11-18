// TAKE-02 进入考试（须知确认）：enterExam 成功后把考试会话写入本地 storage 供答题页使用
const { post } = require('../../utils/request')
const auth = require('../../utils/auth')
const { getDeviceId } = require('../../utils/device')

Page({
  data: {
    examName: '在线考试',
    confirmed: false,
    entering: false
  },

  examId: null,

  onLoad(options) {
    this.examId = options.examId
    if (options.name) {
      this.setData({ examName: decodeURIComponent(options.name) })
    }
  },

  onShow() {
    auth.ensureAuth()
  },

  onConfirmChange() {
    this.setData({ confirmed: !this.data.confirmed })
  },

  async onEnter() {
    if (!this.data.confirmed || this.data.entering) return
    this.setData({ entering: true })
    try {
      const data = await post('/app/exams/' + this.examId + '/enter', {
        noticeConfirmed: true,
        deviceId: getDeviceId(),
        terminal: 'MP'
      })
      // 考试会话（sheetId / remainingSec / deadlineTime / examRules 等）供答题页与断线续考使用
      wx.setStorageSync('mp_exam_session_' + this.examId, Object.assign({}, data, { examName: this.data.examName }))
      if (data.resumeFrom === 'RESUME') {
        wx.showToast({ title: '已恢复之前的考试进度', icon: 'none' })
      }
      wx.redirectTo({ url: '/pages/exam-answer/exam-answer?examId=' + this.examId })
    } catch (e) {
      // 错误提示由拦截器处理（50001/50002/50003/50005/50008）
    } finally {
      this.setData({ entering: false })
    }
  }
})
