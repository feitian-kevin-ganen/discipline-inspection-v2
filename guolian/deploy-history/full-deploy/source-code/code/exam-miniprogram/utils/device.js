// 设备标识：本地持久化，用于单终端答题控制（TAKE-02 / AUTH-01），对齐 H5 端 utils/device.js
const KEY = 'mp_device_id'

function randomHex(len) {
  const chars = '0123456789abcdef'
  let s = ''
  for (let i = 0; i < len; i++) {
    s += chars[Math.floor(Math.random() * 16)]
  }
  return s
}

function getDeviceId() {
  let id = ''
  try {
    id = wx.getStorageSync(KEY)
  } catch (e) {
    // ignore
  }
  if (!id) {
    id = 'mp-' + randomHex(8)
    try {
      wx.setStorageSync(KEY, id)
    } catch (e) {
      // ignore
    }
  }
  return id
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

// 'yyyy-MM-dd HH:mm:ss'
function nowStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}

module.exports = { getDeviceId, uuid, nowStr }
