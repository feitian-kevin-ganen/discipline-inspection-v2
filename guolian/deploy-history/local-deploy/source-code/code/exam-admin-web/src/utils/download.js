// 下载工具：请求返回带签名 URL 的，直接打开；返回文件流的，转 blob 下载
import request from './request'

export function openSignedUrl(url) {
  if (!url) return
  window.open(url, '_blank')
}

// 模板/导出等文件流下载（responseType: blob），params 拼为 query string
export async function downloadFile(path, filename, params) {
  let url = path
  if (params) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    if (qs) url += (path.includes('?') ? '&' : '?') + qs
  }
  const res = await request.get(url, { responseType: 'blob' })
  const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

export function formatSec(sec) {
  if (sec == null) return '-'
  const s = Number(sec)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (h > 0) return `${h}小时${m}分`
  if (m > 0) return `${m}分${s % 60}秒`
  return `${s}秒`
}

export function formatSize(bytes) {
  if (bytes == null) return '-'
  const b = Number(bytes)
  if (b >= 1024 * 1024 * 1024) return (b / 1024 / 1024 / 1024).toFixed(1) + ' GB'
  if (b >= 1024 * 1024) return (b / 1024 / 1024).toFixed(1) + ' MB'
  if (b >= 1024) return (b / 1024).toFixed(1) + ' KB'
  return b + ' B'
}

export function pct(v) {
  if (v == null) return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}
