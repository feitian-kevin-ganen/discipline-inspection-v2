// SYS-08 本人站内消息列表 / SYS-09 标记已读（noticeId=0 全部已读）
const { get, put } = require('../../utils/request')
const auth = require('../../utils/auth')
const { noticeTypeName } = require('../../utils/format')

const PAGE_SIZE = 10

Page({
  data: {
    active: 'ALL', // ALL / UNREAD
    list: [],
    unreadCount: 0,
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
    return items.map((n) =>
      Object.assign({}, n, {
        typeName: noticeTypeName[n.noticeType] || n.noticeType || '通知',
        expanded: false
      })
    )
  },

  async fetchList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.pageNum = 1
      this.setData({ list: [], finished: false })
    }
    const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
    if (this.data.active === 'UNREAD') params.read = false
    this.setData({ loading: true })
    try {
      const data = await get('/app/notices', params)
      const items = this.decorate(data.list || [])
      const list = reset ? items : this.data.list.concat(items)
      const finished = items.length < PAGE_SIZE || list.length >= (data.total || 0)
      this.setData({ list, finished, unreadCount: data.unreadCount || 0 })
      this.pageNum += 1
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ loading: false })
    }
  },

  // 点击展开内容，并标记已读（SYS-09，幂等）
  async onItemTap(e) {
    const { id, index } = e.currentTarget.dataset
    const item = this.data.list[index]
    this.setData({ ['list[' + index + '].expanded']: !item.expanded })
    if (!item.read) {
      try {
        await put('/app/notices/' + id + '/read', {}, { __silent: true })
        this.setData({
          ['list[' + index + '].read']: true,
          unreadCount: Math.max(0, this.data.unreadCount - 1)
        })
      } catch (e) {
        // 忽略
      }
    }
  },

  async onReadAll() {
    try {
      await put('/app/notices/0/read', {})
      this.fetchList(true)
    } catch (e) {
      // 拦截器已提示
    }
  }
})
