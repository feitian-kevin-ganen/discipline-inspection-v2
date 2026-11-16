// 学习心跳：每 15 秒上报进度/位置/有效时长，页面失焦停发
// 用法：const hb = useLearnHeartbeat(materialId)；hb.start() / hb.stop() / hb.flush()
import { ref, onBeforeUnmount } from 'vue'
import { heartbeat } from '../api/learn'
import { uuid } from '../utils/device'

const HEARTBEAT_SEC = 15

export function useLearnHeartbeat(materialId) {
  const status = ref('')
  const progressPct = ref(0)
  const effectiveSec = ref(0)

  let timer = null
  let lastTickTime = 0 // performance.now() 上次计时刻
  let pendingNewPages = [] // 自上次心跳以来新增已读页码
  let getPosition = null // () => ({ positionSec?, pageNo? }) 由调用方注入
  let isActive = null // () => boolean 是否计有效时长（如视频播放中）
  let stopped = false

  function setPositionGetter(fn) {
    getPosition = fn
  }

  function setActiveChecker(fn) {
    isActive = fn
  }

  function markPageRead(pageNo) {
    if (!pendingNewPages.includes(pageNo)) pendingNewPages.push(pageNo)
  }

  // 有效时长累计：仅页面可见且处于活跃状态时计时
  function currentDelta() {
    const now = performance.now()
    if (!lastTickTime) {
      lastTickTime = now
      return 0
    }
    let delta = 0
    const visible = document.visibilityState === 'visible'
    const active = isActive ? isActive() : true
    if (visible && active) {
      delta = Math.round((now - lastTickTime) / 1000)
    }
    lastTickTime = now
    return delta
  }

  async function send() {
    if (stopped) return
    const deltaSec = Math.min(currentDelta(), HEARTBEAT_SEC)
    if (deltaSec <= 0 && pendingNewPages.length === 0) return
    const body = {
      materialId: Number(materialId),
      eventId: uuid(),
      deltaSec
    }
    if (getPosition) Object.assign(body, getPosition() || {})
    if (pendingNewPages.length > 0) {
      body.readPages = pendingNewPages.slice()
      pendingNewPages = []
    }
    try {
      const data = await heartbeat(body)
      progressPct.value = data.progressPct
      effectiveSec.value = data.effectiveSec
      status.value = data.status
    } catch (e) {
      // 心跳失败静默丢弃（服务端幂等，下次心跳补齐进度；delta 不计入则少计，可接受）
    }
  }

  function onVisibility() {
    // 失焦/回焦时重置计时刻，避免失焦时长被计入
    lastTickTime = performance.now()
  }

  function start() {
    stopped = false
    lastTickTime = performance.now()
    document.addEventListener('visibilitychange', onVisibility)
    timer = setInterval(send, HEARTBEAT_SEC * 1000)
  }

  async function flush() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    await send()
  }

  function stop() {
    stopped = true
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    document.removeEventListener('visibilitychange', onVisibility)
  }

  onBeforeUnmount(() => {
    stop()
  })

  return { status, progressPct, effectiveSec, start, stop, flush, setPositionGetter, setActiveChecker, markPageRead }
}
