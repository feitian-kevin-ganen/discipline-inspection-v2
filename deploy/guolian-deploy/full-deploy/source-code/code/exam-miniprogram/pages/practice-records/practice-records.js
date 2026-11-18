// PRAC-03 练习记录：分页 + 只看错题（错题展开正确答案与解析）
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')

const PAGE_SIZE = 10

function answerText(ans) {
  if (!ans) return '-'
  const keys = ans.keys || ans.selected
  return Array.isArray(keys) ? keys.join('、') : String(keys || '-')
}

function formatTime(str) {
  if (!str) return ''
  const d = new Date(String(str).replace(/-/g, '/').replace('T', ' '))
  if (isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

Page({
  data: {
    onlyWrong: false,
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

  onOnlyWrongChange(e) {
    this.setData({ onlyWrong: e.detail.value })
    this.fetchList(true)
  },

  decorate(items) {
    return items.map((r) =>
      Object.assign({}, r, {
        myAnswerText: answerText(r.myAnswer),
        correctAnswerText: answerText(r.correctAnswer),
        answerTimeText: formatTime(r.answerTime),
        // onlyWrong=true 时接口才展开解析字段
        expanded: this.data.onlyWrong && r.analysis !== undefined
      })
    )
  },

  async fetchList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.pageNum = 1
      this.setData({ list: [], finished: false })
    }
    this.setData({ loading: true })
    try {
      const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
      if (this.data.onlyWrong) params.onlyWrong = true
      const data = await get('/app/practice/records', params)
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
  }
})
