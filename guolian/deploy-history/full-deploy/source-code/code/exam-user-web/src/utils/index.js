export function uuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function getDeviceId() {
  let id = localStorage.getItem('deviceId')
  if (!id) {
    id = 'pc-' + uuid()
    localStorage.setItem('deviceId', id)
  }
  return id
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

/** 倒计时显示 HH:MM:SS */
export function fmtCountdown(totalSec) {
  const s = Math.max(0, Math.floor(totalSec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return `${pad2(h)}:${pad2(m)}:${pad2(sec)}`
}

/** 时长友好显示 */
export function fmtDuration(sec) {
  const s = Math.floor(sec || 0)
  if (s < 60) return `${s}秒`
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h}小时${m}分` : `${m}分钟`
}

/** 当前时间 yyyy-MM-dd HH:mm:ss */
export function fmtNow(d = new Date()) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

export const QUES_TYPE_NAME = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  ESSAY: '简答题'
}

export const MATERIAL_TYPE_NAME = {
  DOC: '文档',
  VIDEO: '视频',
  RICHTEXT: '图文'
}

export const ROLE_NAME = {
  SUPER_ADMIN: '超级管理员',
  HQ_ADMIN: '总部管理员',
  L2_ADMIN: '二级管理员',
  SUB_ADMIN: '子企业管理员',
  MARKER: '阅卷教师',
  EXAMINEE: '考生'
}

/** 从题干推断填空空数 */
export function countBlanks(stem) {
  if (!stem) return 1
  const text = String(stem).replace(/<[^>]+>/g, '')
  const m = text.match(/_{2,}|＿+|（\s*）|\(\s*\)|【\s*】/g)
  return m ? m.length : 1
}
