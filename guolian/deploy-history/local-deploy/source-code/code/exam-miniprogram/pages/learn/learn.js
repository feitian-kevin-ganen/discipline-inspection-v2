// LEARN-01 学习首页：分类筛选 + 资料列表（分类由资料列表聚合得出，对齐 H5 端实现）
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')
const { materialTypeName, learnStatusName, learnStatusClass, formatDuration } = require('../../utils/format')

const PAGE_SIZE = 10

Page({
  data: {
    keyword: '',
    activeCat: 'ALL',
    categories: [{ key: 'ALL', name: '全部' }],
    list: [],
    loading: false,
    finished: false,
    todoCount: 0
  },

  pageNum: 1,

  onLoad() {
    this.loadCategories()
  },

  onShow() {
    if (!auth.ensureAuth()) return
    this.loadTodoCount()
  },

  onPullDownRefresh() {
    this.onSearch().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.loading && !this.data.finished) {
      this.fetchList(false)
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    return this.fetchList(true)
  },

  onCatTap(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.activeCat) return
    this.setData({ activeCat: key })
    this.fetchList(true)
  },

  // 考生端无分类树接口（LIB-01 为管理端），分类由资料列表聚合得出
  async loadCategories() {
    try {
      const data = await get('/app/learn/materials', { pageNum: 1, pageSize: 200 })
      const seen = new Map()
      ;(data.list || []).forEach((m) => {
        if (m.catName && !seen.has(m.catName)) seen.set(m.catName, m.catId)
      })
      const cats = [{ key: 'ALL', name: '全部' }]
      seen.forEach((id, name) => cats.push({ key: name, name, catId: id }))
      this.setData({ categories: cats })
    } catch (e) {
      // 分类加载失败不阻塞列表
    }
  },

  decorate(items) {
    return items.map((m) => {
      const status = (m.myProgress && m.myProgress.status) || 'NOT_STARTED'
      const pct = (m.myProgress && m.myProgress.progressPct) || 0
      return Object.assign({}, m, {
        typeName: materialTypeName[m.materialType] || m.materialType,
        statusName: learnStatusName[status] || status,
        statusClass: learnStatusClass[status] || 'tag-default',
        progressPct: Math.min(100, Math.round(pct * 100)),
        durationText: formatDuration(m.durationSec)
      })
    })
  },

  async fetchList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.pageNum = 1
      this.setData({ list: [], finished: false })
    }
    const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
    if (this.data.keyword) params.keyword = this.data.keyword
    const cat = this.data.categories.find((c) => c.key === this.data.activeCat)
    if (cat && cat.catId) params.catId = cat.catId
    this.setData({ loading: true })
    try {
      const data = await get('/app/learn/materials', params)
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

  async loadTodoCount() {
    try {
      const data = await get('/app/learn/tasks', { status: 'TODO', pageNum: 1, pageSize: 1 }, { __silent: true })
      this.setData({ todoCount: data.total || 0 })
    } catch (e) {
      // 忽略
    }
  },

  goTasks() {
    wx.navigateTo({ url: '/pages/learn-tasks/learn-tasks' })
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/material-detail/material-detail?id=' + id })
  }
})
