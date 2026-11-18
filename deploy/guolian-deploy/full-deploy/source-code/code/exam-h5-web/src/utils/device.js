// 设备指纹：H5 端生成并本地持久化，用于单终端答题控制（TAKE-02 / AUTH-01）
const KEY = 'h5_device_id'

export function getDeviceId() {
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = 'h5-' + randomHex(8)
    localStorage.setItem(KEY, id)
  }
  return id
}

function randomHex(len) {
  const chars = '0123456789abcdef'
  let s = ''
  const arr = new Uint8Array(len)
  ;(window.crypto || {}).getRandomValues
    ? window.crypto.getRandomValues(arr)
    : arr.forEach((_, i) => (arr[i] = Math.floor(Math.random() * 256)))
  for (let i = 0; i < len; i++) s += chars[arr[i] % 16]
  return s
}

export function uuid() {
  if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
