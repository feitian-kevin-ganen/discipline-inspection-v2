// PRAC-01 日常练习发起页：模式（按法规分类/全库随机）+ 题数（5/10/20）
const { get, post } = require('../../utils/request')
const auth = require('../../utils/auth')

const SESSION_KEY = 'mp_practice_session'

Page({
  data: {
    mode: 'CATEGORY',
    banks: [],
    bankId: null,
    counts: [5, 10, 20],
    count: 10,
    banksLoading: false,
    starting: false
  },

  onShow() {
    if (!auth.ensureAuth()) return
    if (this.data.banks.length === 0) this.loadBanks()
  },

  onPullDownRefresh() {
    this.loadBanks().finally(() => wx.stopPullDownRefresh())
  },

  async loadBanks() {
    this.setData({ banksLoading: true })
    try {
      const banks = await get('/app/practice/banks')
      const bankId = this.data.bankId || (banks.length ? banks[0].bankId : null)
      this.setData({ banks, bankId })
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ banksLoading: false })
    }
  },

  onModeTap(e) {
    this.setData({ mode: e.currentTarget.dataset.key })
  },

  onBankTap(e) {
    this.setData({ bankId: e.currentTarget.dataset.id })
  },

  onCountTap(e) {
    this.setData({ count: e.currentTarget.dataset.value })
  },

  async onStart() {
    if (this.data.mode === 'CATEGORY' && !this.data.bankId) {
      wx.showToast({ title: '请选择法规分类', icon: 'none' })
      return
    }
    this.setData({ starting: true })
    try {
      const body = { mode: this.data.mode, count: this.data.count }
      if (this.data.mode === 'CATEGORY') body.bankId = this.data.bankId
      const data = await post('/app/practice/start', body)
      wx.setStorageSync(SESSION_KEY, {
        mode: data.mode,
        bankId: body.bankId || null,
        bankName: data.bankName || '',
        count: data.count,
        questions: data.questions || [],
        index: 0,
        rightCount: 0,
        wrongCount: 0,
        finished: false
      })
      wx.navigateTo({ url: '/pages/practice-answer/practice-answer' })
    } catch (e) {
      // 拦截器已提示（如 70002 题量不足）
    } finally {
      this.setData({ starting: false })
    }
  },

  goRecords() {
    wx.navigateTo({ url: '/pages/practice-records/practice-records' })
  },

  goStat() {
    wx.navigateTo({ url: '/pages/practice-stat/practice-stat' })
  }
})
