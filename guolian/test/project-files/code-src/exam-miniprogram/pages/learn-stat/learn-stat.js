// LSTAT-01 个人学习统计
const { get } = require('../../utils/request')
const auth = require('../../utils/auth')
const { formatDuration } = require('../../utils/format')

Page({
  data: {
    loading: true,
    stat: null,
    totalDurationText: '0秒',
    ms: {},
    ts: {},
    doneRatePct: 0,
    catList: []
  },

  onShow() {
    if (!auth.ensureAuth()) return
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const stat = await get('/app/lstat/personal')
      const ts = stat.taskStats || {}
      const catDist = stat.categoryDist || []
      const maxCatSec = Math.max(1, ...catDist.map((c) => c.effectiveSec || 0))
      this.setData({
        stat,
        totalDurationText: formatDuration(stat.totalEffectiveSec || 0),
        ms: stat.materialStats || {},
        ts,
        doneRatePct: Math.round((ts.doneRate || 0) * 100),
        catList: catDist.map((c) =>
          Object.assign({}, c, {
            durationText: formatDuration(c.effectiveSec || 0),
            pct: Math.round(((c.effectiveSec || 0) / maxCatSec) * 100)
          })
        )
      })
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ loading: false })
    }
  }
})
