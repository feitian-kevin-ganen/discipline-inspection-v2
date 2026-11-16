// AUTH-01 登录（白名单接口）
const { post } = require('../../utils/request')
const auth = require('../../utils/auth')
const { getDeviceId } = require('../../utils/device')

Page({
  data: {
    username: '',
    password: '',
    loading: false
  },

  onShow() {
    // 已登录且无需改密：直接进首页
    const local = auth.getAuth()
    if (local.accessToken) {
      if (local.mustChangePassword) {
        wx.redirectTo({ url: '/pages/change-password/change-password' })
      } else {
        wx.switchTab({ url: '/pages/learn/learn' })
      }
    }
  },

  onUserInput(e) {
    this.setData({ username: e.detail.value })
  },
  onPwdInput(e) {
    this.setData({ password: e.detail.value })
  },

  async onSubmit() {
    const username = (this.data.username || '').trim()
    const password = this.data.password || ''
    if (!username) {
      wx.showToast({ title: '请输入工号', icon: 'none' })
      return
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const data = await post(
        '/auth/login',
        { username, password, terminal: 'MP', deviceId: getDeviceId() },
        { __isAuthFree: true }
      )
      auth.saveLogin(data)
      if (data.mustChangePassword) {
        wx.showToast({ title: '首次登录请先修改密码', icon: 'none' })
        wx.redirectTo({ url: '/pages/change-password/change-password' })
      } else {
        wx.switchTab({ url: '/pages/learn/learn' })
      }
    } catch (e) {
      // 错误提示已由 request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  }
})
