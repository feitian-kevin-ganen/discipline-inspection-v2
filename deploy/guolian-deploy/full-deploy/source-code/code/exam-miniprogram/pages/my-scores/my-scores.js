// STAT-08 个人历次成绩单
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')

const PAGE_SIZE = 10

Page({
  data: {
    list: [],
    loading: false,
    finished: false
  },

  pageNum: 1,

  onShow() {
    if (!auth.ensureAuth()) return
    if (!this.data.list.length && !this.data.loading) this.fetchList(true)
  },

  onReachBottom() {
    if (!this.data.loading && !this.data.finished) this.fetchList(false)
  },

  onPullDownRefresh() {
    this.fetchList(true).finally(() => wx.stopPullDownRefresh())
  },

  async fetchList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.pageNum = 1
      this.setData({ list: [], finished: false })
    }
    this.setData({ loading: true })
    try {
      const data = await get('/app/stat/my-transcripts', { pageNum: this.pageNum, pageSize: PAGE_SIZE })
      const items = data.list || []
      const list = reset ? items : this.data.list.concat(items)
      const finished = items.length < PAGE_SIZE || list.length >= (data.total || 0)
      this.setData({ list, finished })
      this.pageNum += 1
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ loading: false })
    }
  },

  goResult(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/exam-result/exam-result?examId=' + id })
  }
})
