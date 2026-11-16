// LEARN-02/03/04/05 资料详情：图文 rich-text 渲染、视频播放、PDF wx.openDocument
// 学习心跳每 15s 上报（materialId/eventId/deltaSec/positionSec），断点续学恢复上次位置
const { get, post } = require('../../utils/request')
const auth = require('../../utils/auth')
const { uuid } = require('../../utils/device')
const { materialTypeName, formatDuration } = require('../../utils/format')

const HEARTBEAT_SEC = 15

Page({
  data: {
    loading: true,
    detail: {},
    typeName: '',
    durationText: '',
    displayProgress: 0,
    completed: false,
    fileUrl: '',
    initialTime: 0, // 视频断点（秒）
    opening: false // 文件打开中
  },

  materialId: null,
  // 心跳状态（非渲染数据放实例上）
  hbTimer: null,
  lastTickTime: 0,
  stopped: false,
  pageVisible: true,
  videoPlaying: false,
  videoCurrentSec: 0,
  progressPct: 0,
  effectiveSec: 0,
  hbStatus: '',

  onLoad(options) {
    this.materialId = options.id
    this.init()
  },

  onShow() {
    this.pageVisible = true
    this.lastTickTime = Date.now()
  },

  onHide() {
    // 失焦时重置计时刻，避免失焦时长被计入
    this.pageVisible = false
  },

  onUnload() {
    // 离开页面前补发一次心跳，尽量保留进度
    this.flushHeartbeat()
  },

  // ---------- 初始化 ----------
  async init() {
    if (!auth.ensureAuth()) return
    try {
      const d = await get('/app/learn/materials/' + this.materialId)
      const rec = d.myRecord || {}
      this.progressPct = rec.progressPct || 0
      this.effectiveSec = rec.effectiveSec || 0
      this.hbStatus = rec.status || ''

      this.setData({
        detail: d,
        typeName: materialTypeName[d.materialType] || d.materialType,
        durationText: formatDuration(d.durationSec),
        displayProgress: Math.min(100, Math.round((this.progressPct || 0) * 100)),
        completed: this.hbStatus === 'COMPLETED'
      })
      wx.setNavigationBarTitle({ title: d.title || '资料详情' })

      if (d.materialType === 'DOC' || d.materialType === 'VIDEO') {
        try {
          const f = await get('/app/learn/materials/' + this.materialId + '/file')
          this.setData({ fileUrl: f.fileUrl })
        } catch (e) {
          // 文件地址获取失败，页面仍可展示元信息
        }
      }

      if (d.materialType === 'VIDEO') {
        const resume = rec.lastPosition || 0
        this.setData({ initialTime: resume })
        this.videoCurrentSec = resume
      }

      // 切换终端场景下再对账一次断点（LEARN-05）
      try {
        const r = await get('/app/learn/records/' + this.materialId)
        if (d.materialType === 'VIDEO' && (r.lastPosition || 0) > this.videoCurrentSec) {
          this.videoCurrentSec = r.lastPosition
          this.setData({ initialTime: r.lastPosition })
        }
      } catch (e) {
        // 忽略
      }

      this.startHeartbeat()
    } catch (e) {
      // 拦截器已提示
    } finally {
      this.setData({ loading: false })
    }
  },

  // ---------- 学习心跳（LEARN-04） ----------
  isActive() {
    // 视频仅播放中计有效时长；图文/文档页面可见即计
    if (this.data.detail.materialType === 'VIDEO') return this.videoPlaying
    return true
  },

  currentDelta() {
    const now = Date.now()
    if (!this.lastTickTime) {
      this.lastTickTime = now
      return 0
    }
    let delta = 0
    if (this.pageVisible && this.isActive()) {
      delta = Math.round((now - this.lastTickTime) / 1000)
    }
    this.lastTickTime = now
    return delta
  },

  async sendHeartbeat() {
    if (this.stopped) return
    const deltaSec = Math.min(this.currentDelta(), HEARTBEAT_SEC)
    if (deltaSec <= 0) return
    const body = {
      materialId: Number(this.materialId),
      eventId: uuid(),
      deltaSec
    }
    if (this.data.detail.materialType === 'VIDEO') {
      body.positionSec = Math.floor(this.videoCurrentSec)
    }
    try {
      const data = await post('/app/learn/heartbeat', body, { __silent: true })
      this.progressPct = data.progressPct
      this.effectiveSec = data.effectiveSec
      this.hbStatus = data.status
      this.setData({
        displayProgress: Math.min(100, Math.round((data.progressPct || 0) * 100)),
        completed: data.status === 'COMPLETED'
      })
    } catch (e) {
      // 心跳失败静默丢弃（服务端幂等，下次心跳补齐进度）
    }
  },

  startHeartbeat() {
    this.stopped = false
    this.lastTickTime = Date.now()
    this.hbTimer = setInterval(() => this.sendHeartbeat(), HEARTBEAT_SEC * 1000)
  },

  async flushHeartbeat() {
    if (this.hbTimer) {
      clearInterval(this.hbTimer)
      this.hbTimer = null
    }
    await this.sendHeartbeat()
    this.stopped = true
  },

  // ---------- 视频 ----------
  onVideoPlay() {
    this.videoPlaying = true
  },
  onVideoPause() {
    this.videoPlaying = false
  },
  onVideoEnded() {
    this.videoPlaying = false
  },
  onVideoTimeUpdate(e) {
    this.videoCurrentSec = e.detail.currentTime || 0
  },

  // ---------- 文档（PDF）：下载后用 wx.openDocument 打开 ----------
  // 注意：正式发布需配置 downloadFile 合法域名
  async openFile() {
    if (this.data.opening) return
    let url = this.data.fileUrl
    this.setData({ opening: true })
    try {
      if (!url) {
        const f = await get('/app/learn/materials/' + this.materialId + '/file')
        url = f.fileUrl
        this.setData({ fileUrl: url })
      }
      wx.showLoading({ title: '文件加载中...' })
      const dl = await new Promise((resolve, reject) => {
        wx.downloadFile({ url, success: resolve, fail: reject })
      })
      if (dl.statusCode !== 200) throw new Error('download failed')
      await new Promise((resolve, reject) => {
        wx.openDocument({
          filePath: dl.tempFilePath,
          showMenu: true,
          success: resolve,
          fail: reject
        })
      })
    } catch (e) {
      wx.showToast({ title: '文件打开失败，请稍后重试', icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ opening: false })
    }
  }
})
