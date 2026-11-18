// PRAC-04 练习统计：总题量/正确率/练习天数 + 按法规正确率 + 薄弱知识点
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')

function pct(v) {
  return Math.round((v || 0) * 100) + '%'
}

Page({
  data: {
    stat: null,
    byLaw: [],
    weakPoints: []
  },

  onShow() {
    if (!auth.ensureAuth()) return
    this.loadStat()
  },

  onPullDownRefresh() {
    this.loadStat().finally(() => wx.stopPullDownRefresh())
  },

  async loadStat() {
    try {
      const data = await get('/app/practice/stat')
      const byLaw = (data.byLaw || []).map((l) =>
        Object.assign({}, l, {
          accuracyText: pct(l.accuracy),
          accuracyPct: Math.round((l.accuracy || 0) * 100),
          barClass: (l.accuracy || 0) >= 0.6 ? 'progress-inner done' : 'progress-inner bar-danger'
        })
      )
      const weakPoints = (data.weakPoints || []).map((w) =>
        Object.assign({}, w, { wrongRateText: pct(w.wrongRate) })
      )
      this.setData({ stat: Object.assign({}, data, { accuracyText: pct(data.accuracy) }), byLaw, weakPoints })
    } catch (e) {
      // 拦截器已提示
    }
  }
})
