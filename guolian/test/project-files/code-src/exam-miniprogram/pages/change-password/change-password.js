// AUTH-04 修改密码（首登强制改密也在本页完成）
const { put } = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    force: false, // 首登强制改密模式
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    loading: false
  },

  onShow() {
    // 强制改密模式下允许停留在本页
    if (!auth.ensureAuth({ allowChangePassword: true })) return
    this.setData({ force: !!auth.getAuth().mustChangePassword })
  },

  onOldInput(e) {
    this.setData({ oldPassword: e.detail.value })
  },
  onNewInput(e) {
    this.setData({ newPassword: e.detail.value })
  },
  onConfirmInput(e) {
    this.setData({ confirmPassword: e.detail.value })
  },

  validPwd(val) {
    return /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(val)
  },

  async onSubmit() {
    const { oldPassword, newPassword, confirmPassword } = this.data
    if (!oldPassword) {
      wx.showToast({ title: '请输入原密码', icon: 'none' })
      return
    }
    if (!this.validPwd(newPassword)) {
      wx.showToast({ title: '8-32位，须含字母和数字', icon: 'none' })
      return
    }
    if (confirmPassword !== newPassword) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return
    }
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      await put('/auth/password', { oldPassword, newPassword })
      // 修改成功后服务端吊销全部 token，需重新登录
      auth.clearAuth()
      wx.showModal({
        title: '修改成功',
        content: '密码已修改，请使用新密码重新登录',
        showCancel: false,
        success: () => {
          wx.reLaunch({ url: '/pages/login/login' })
        }
      })
    } catch (e) {
      // 错误提示由 request.js 处理
    } finally {
      this.setData({ loading: false })
    }
  }
})
