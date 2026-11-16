// 我的：用户信息 + 功能入口 + 退出登录（AUTH-02）
const { get, post } = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    user: null,
    loggingOut: false
  },

  onShow() {
    if (!auth.ensureAuth()) return
    this.setData({ user: auth.getAuth().user || null })
    // 进页刷新一次用户信息（AUTH-05）
    this.fetchMe()
  },

  async fetchMe() {
    try {
      const data = await get('/auth/me', null, { __silent: true })
      const user = {
        userId: data.userId,
        name: data.name,
        jobNo: data.jobNo,
        orgId: data.orgId,
        orgName: data.orgName,
        roles: data.roles
      }
      auth.saveUser(user)
      this.setData({ user })
    } catch (e) {
      // 忽略，用本地缓存
    }
  },

  goScores() {
    wx.navigateTo({ url: '/pages/my-scores/my-scores' })
  },
  goLearnStat() {
    wx.navigateTo({ url: '/pages/learn-stat/learn-stat' })
  },
  goPracticeRecords() {
    wx.navigateTo({ url: '/pages/practice-records/practice-records' })
  },
  goPracticeStat() {
    wx.navigateTo({ url: '/pages/practice-stat/practice-stat' })
  },
  goNotices() {
    wx.navigateTo({ url: '/pages/notices/notices' })
  },
  goChangePassword() {
    wx.navigateTo({ url: '/pages/change-password/change-password' })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: async (res) => {
        if (!res.confirm) return
        this.setData({ loggingOut: true })
        try {
          await post('/auth/logout', {}, { __silent: true })
        } catch (e) {
          // 登出接口失败不阻塞本地退出
        }
        auth.clearAuth()
        this.setData({ loggingOut: false })
        wx.reLaunch({ url: '/pages/login/login' })
      }
    })
  }
})
