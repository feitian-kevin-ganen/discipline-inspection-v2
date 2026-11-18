// 时间与展示格式化工具
export function formatDuration(sec) {
  sec = Math.max(0, Math.floor(sec || 0))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h > 0) return `${h}小时${m}分`
  if (m > 0) return `${m}分${s > 0 ? s + '秒' : ''}`
  return `${s}秒`
}

export function formatCountdown(totalSec) {
  totalSec = Math.max(0, Math.floor(totalSec))
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${String(h).padStart(2, '0')}:${mm}:${ss}` : `${mm}:${ss}`
}

// 'yyyy-MM-dd HH:mm:ss'（东八区字符串，按本地时间解析，服务端已按东八区输出）
export function parseServerTime(str) {
  if (!str) return 0
  return new Date(String(str).replace(/-/g, '/').replace('T', ' ')).getTime()
}

export const materialTypeName = {
  ARTICLE: '图文',
  RICHTEXT: '图文',
  DOC: '文档',
  VIDEO: '视频'
}

export const learnStatusName = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '学习中',
  COMPLETED: '已完成'
}

export const learnStatusColor = {
  NOT_STARTED: '#969799',
  IN_PROGRESS: '#1989fa',
  COMPLETED: '#07c160'
}

export const sheetStatusName = {
  NOT_ENTERED: '未进入',
  IN_EXAM: '考试中',
  SUBMITTED: '已交卷',
  FORCED: '强制收卷',
  ABSENT: '缺考'
}

export const taskStatusName = {
  TODO: '待完成',
  DONE: '已完成',
  EXPIRED: '已逾期'
}

export const quesTypeName = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  ESSAY: '简答题'
}
