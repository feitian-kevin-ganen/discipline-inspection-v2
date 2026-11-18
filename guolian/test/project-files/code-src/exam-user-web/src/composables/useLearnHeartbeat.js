import { onBeforeUnmount } from 'vue'
import { sendHeartbeat } from '@/api/learn'
import { uuid } from '@/utils'

/**
 * 学习心跳（FR-LEARN-03 / LEARN-04）：
 * - 每 15s 上报一次有效时长 deltaSec
 * - 页面失焦/隐藏、视频暂停时不计时
 * - 页面隐藏与组件卸载时立即补发一次
 *
 * options:
 *  - isActive(): 额外活跃条件（视频播放中才计时），可省略
 *  - getPayload(): 返回 { positionSec?, durationSec?, pageNo?, readPages? }，readPages 为本次新增已读页码
 *  - onUpdate(data): 心跳响应（progressPct/effectiveSec/status/serverTime）
 *
 * 返回 pendingSec()：已计时但尚未上报的观看秒数，供页面在两次心跳之间本地刷新进度百分比
 */
export function useLearnHeartbeat(materialId, options = {}) {
  const INTERVAL_SEC = 15
  const MAX_DELTA_SEC = Math.floor(INTERVAL_SEC * 1.5)

  let ticker = null
  let accMs = 0
  let lastTs = 0
  let secCounter = 0
  let flushing = false
  let stopped = false

  function isCounting() {
    if (stopped) return false
    if (document.visibilityState !== 'visible') return false
    if (!document.hasFocus()) return false
    if (options.isActive && !options.isActive()) return false
    return true
  }

  async function flush() {
    if (flushing || stopped) return
    const payload = options.getPayload ? options.getPayload() || {} : {}
    const deltaSec = Math.min(Math.floor(accMs / 1000), MAX_DELTA_SEC)
    if (deltaSec <= 0 && !payload.pageNo && payload.positionSec == null && !(payload.readPages && payload.readPages.length)) {
      return
    }
    flushing = true
    accMs = Math.max(0, accMs - deltaSec * 1000)
    try {
      const data = await sendHeartbeat({
        materialId: Number(materialId),
        eventId: uuid(),
        deltaSec,
        ...payload
      })
      options.onUpdate && options.onUpdate(data)
    } catch (e) {
      // 静默失败，下个周期重试（eventId 每次新建，delta 由服务端单调推进）
    } finally {
      flushing = false
    }
  }

  function tick() {
    const now = Date.now()
    if (isCounting()) {
      accMs += now - lastTs
    }
    lastTs = now
    secCounter += 1
    if (secCounter >= INTERVAL_SEC) {
      secCounter = 0
      flush()
    }
  }

  function onVisibility() {
    if (document.visibilityState === 'hidden') {
      flush()
    }
    lastTs = Date.now()
  }

  lastTs = Date.now()
  ticker = setInterval(tick, 1000)
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('beforeunload', flush)

  function stop() {
    stopped = true
    clearInterval(ticker)
    document.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('beforeunload', flush)
  }

  // 已计时但尚未上报的观看秒数（accMs 每秒 tick 累计一次，误差 <1s）
  function pendingSec() {
    return Math.floor(accMs / 1000)
  }

  onBeforeUnmount(() => {
    flush()
    stop()
  })

  return { flush, stop, pendingSec }
}
