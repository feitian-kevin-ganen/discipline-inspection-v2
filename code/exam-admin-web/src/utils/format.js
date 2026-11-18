// 时间与展示格式化工具

function pad2(n) {
  return String(n).padStart(2, '0')
}

/**
 * 服务器时间（ISO 字符串如 2026-09-20T23:46:49.589835 / 时间戳 / Date）→ 'YYYY-MM-DD HH:mm:ss'（24小时制）
 * 空值返回 '-'；无法解析的字符串截断到 19 位兜底
 */
export function fmtTime(t) {
  if (t === null || t === undefined || t === '') return '-'
  if (typeof t === 'string') {
    const s = t.replace('T', ' ')
    return s.length > 19 ? s.slice(0, 19) : s
  }
  const d = t instanceof Date ? t : new Date(t)
  if (isNaN(d.getTime())) return String(t)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}
