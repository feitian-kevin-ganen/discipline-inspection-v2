// TAKE-01 待考/历史考试列表（type: TODO/HISTORY，与 H5 端已联调参数一致）
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')
const { sheetStatusName } = require('../../utils/format')

const PAGE_SIZE = 10

Page({
  data: {
    active: 'TODO',
    list: [],
    loading: false,
    finished: false
  },

  pageNum: 1,

  onShow() {
    if (!auth.ensureAuth()) return
    this.fetchList(true)
  },

  onReachBottom() {
    if (!this.data.loading && !this.data.finished) this.fetchList(false)
  },

  onPullDownRefresh() {
    this.fetchList(true).finally(() => wx.stopPullDownRefresh())
  },

  onTabTap(e) {
    const name = e.currentTarget.dataset.name
    if (name === this.data.active) return
    this.setData({ active: name })
    this.fetchList(true)
  },

  decorate(items) {
    return items.map((exam) => {
      const isTodo = this.data.active === 'TODO'
      let statusText
      let statusClass
      if (isTodo) {
        statusText = exam.canEnter ? '可进入' : sheetStatusName[exam.mySheetStatus] || '未开始'
        statusClass = exam.canEnter ? 'tag-success' : 'tag-default'
      } else {
        // 与 H5/用户端口径一致：已发布且本人已交卷才可查看成绩；缺考/未参考不提供入口
        const submitted = exam.mySheetStatus === 'SUBMITTED' || exam.mySheetStatus === 'FORCED'
        const scoreVisible = !!exam.scorePublished && submitted
        if (scoreVisible) {
          statusText = '成绩已发布'
          statusClass = 'tag-primary'
        } else if (exam.mySheetStatus === 'ABSENT') {
          statusText = '缺考无成绩'
          statusClass = 'tag-default'
        } else if (!submitted) {
          statusText = '未参考'
          statusClass = 'tag-default'
        } else {
          statusText = '成绩待发布'
          statusClass = 'tag-warning'
        }
        return Object.assign({}, exam, { statusText, statusClass, scoreVisible })
      }
      return Object.assign({}, exam, { statusText, statusClass })
    })
  },

  async fetchList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.pageNum = 1
      this.setData({ list: [], finished: false })
    }
    this.setData({ loading: true })
    try {
      const data = await get('/app/exams', { type: this.data.active, pageNum: this.pageNum, pageSize: PAGE_SIZE })
      const items = this.decorate(data.list || [])
      const list = reset ? items : this.data.list.concat(items)
      const finished = (data.list || []).length < PAGE_SIZE || list.length >= (data.total || 0)
      this.setData({ list, finished })
      this.pageNum += 1
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ loading: false })
    }
  },

  goNotice(e) {
    const { id, name } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/exam-notice/exam-notice?examId=' + id + '&name=' + encodeURIComponent(name || '') })
  },

  resumeExam(e) {
    const { id, name } = e.currentTarget.dataset
    let s = null
    try {
      s = wx.getStorageSync('mp_exam_session_' + id)
    } catch (err) {
      // ignore
    }
    if (s && s.sheetId) {
      wx.navigateTo({ url: '/pages/exam-answer/exam-answer?examId=' + id })
    } else {
      // 本地无会话（换终端）：重新走进入流程，服务端按 RESUME 恢复
      wx.showToast({ title: '正在恢复考试会话...', icon: 'none' })
      wx.navigateTo({ url: '/pages/exam-notice/exam-notice?examId=' + id + '&name=' + encodeURIComponent(name || '') })
    }
  },

  goResult(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/exam-result/exam-result?examId=' + id })
  }
})
