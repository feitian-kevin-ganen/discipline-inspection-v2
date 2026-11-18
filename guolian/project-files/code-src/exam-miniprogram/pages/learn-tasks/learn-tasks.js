// LEARN-06 学习任务（TODO/DONE 筛选）
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')
const { formatDuration, taskStatusName, materialTypeName } = require('../../utils/format')

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
    if (!this.data.list.length && !this.data.loading) this.fetchList(true)
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

  tagClass(status) {
    return status === 'DONE' ? 'tag-success' : status === 'EXPIRED' ? 'tag-danger' : 'tag-primary'
  },

  decorate(items) {
    return items.map((t) =>
      Object.assign({}, t, {
        statusName: taskStatusName[t.myStatus] || t.myStatus,
        statusClass: this.tagClass(t.myStatus),
        requiredText: formatDuration(t.requiredSec),
        materials: (t.materials || []).map((m) =>
          Object.assign({}, m, {
            icon: m.materialType === 'VIDEO' ? '🎬' : m.materialType === 'DOC' ? '📄' : '📰',
            typeName: materialTypeName[m.materialType] || m.materialType,
            progressPct: Math.min(100, Math.round((m.myProgressPct || 0) * 100))
          })
        )
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
      const data = await get('/app/learn/tasks', { status: this.data.active, pageNum: this.pageNum, pageSize: PAGE_SIZE })
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

  goMaterial(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/material-detail/material-detail?id=' + id })
  }
})
