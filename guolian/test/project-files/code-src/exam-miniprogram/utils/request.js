// 统一请求封装（对齐 H5 端 utils/request.js 的行为）：
// - baseURL 拼接、query 参数序列化、Authorization: Bearer <accessToken> 注入
// - 统一响应 {code,message,data}，code=0 返回 data
// - code=10002 / HTTP 401：用 refreshToken 静默刷新一次并重放原请求，失败则回登录页
// - code=10010：被踢下线，弹窗提示一次后回登录页
// - 其余 code!=0：默认 wx.showToast 报错（options.__silent 静默，由调用方自行处理）
// - 网络错误：不 toast，reject { code:-1, networkError:true }，交由调用方（如答题本地队列）处理
//
// 注意：正式发布前需在微信公众平台配置 request 合法域名，开发期靠 project.config.json 的 urlCheck:false 直连。
const auth = require('./auth')

const BASE_URL = 'http://10.110.10.160:8088/api/v1'

let refreshingPromise = null
let kickedShowing = false

function buildQuery(params) {
  if (!params) return ''
  const parts = []
  Object.keys(params).forEach((k) => {
    const v = params[k]
    if (v === undefined || v === null || v === '') return
    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v))
  })
  return parts.length ? '?' + parts.join('&') : ''
}

function rawRequest(options) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    const token = auth.getToken()
    if (token && !options.__isAuthFree) {
      header.Authorization = 'Bearer ' + token
    }
    wx.request({
      url: BASE_URL + options.url + buildQuery(options.params),
      method: options.method || 'GET',
      data: options.data || {},
      header,
      timeout: options.timeout || 20000,
      success: resolve,
      fail: reject
    })
  })
}

function toastError(message) {
  wx.showToast({ title: String(message || '请求失败'), icon: 'none', duration: 2500 })
}

function isOnLoginPage() {
  const pages = getCurrentPages()
  const cur = pages.length ? pages[pages.length - 1].route : ''
  return cur === 'pages/login/login'
}

// 回登录页（清理本地登录态）
function toLogin() {
  auth.clearAuth()
  if (!isOnLoginPage()) {
    wx.reLaunch({ url: '/pages/login/login' })
  }
}

// 被踢下线 / token 强制失效：提示一次并回登录页
function handleKicked(message) {
  auth.clearAuth()
  if (kickedShowing) {
    wx.reLaunch({ url: '/pages/login/login' })
    return
  }
  kickedShowing = true
  wx.showModal({
    title: '提示',
    content: message || '账号已在其他终端登录，您已被强制下线',
    confirmText: '重新登录',
    showCancel: false,
    complete: () => {
      kickedShowing = false
      wx.reLaunch({ url: '/pages/login/login' })
    }
  })
}

// AUTH-03 静默刷新（全局只保留一个在飞的刷新请求）
function tryRefresh() {
  if (!refreshingPromise) {
    const refreshToken = auth.getAuth().refreshToken
    refreshingPromise = rawRequest({
      url: '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
      __isAuthFree: true
    })
      .then((res) => {
        const body = res.data
        if (res.statusCode === 200 && body && body.code === 0) {
          auth.setTokens(body.data.accessToken, body.data.refreshToken)
          return true
        }
        return false
      })
      .catch(() => false)
      .finally(() => {
        refreshingPromise = null
      })
  }
  return refreshingPromise
}

async function service(options) {
  let res
  try {
    res = await rawRequest(options)
  } catch (err) {
    // 网络层失败（断网/超时）：静默 reject，交由调用方处理
    return Promise.reject({ code: -1, message: '网络连接异常', networkError: true })
  }

  const status = res.statusCode
  const body = res.data

  if (status === 200 && body && body.code === 0) {
    return body.data
  }

  const code = body && typeof body.code === 'number' ? body.code : status
  const message = (body && body.message) || '请求失败'

  // HTTP 401 / 10002：token 失效，尝试静默刷新并重放一次
  if (status === 401 || code === 10002) {
    if (options.__isAuthFree || options.__retried) {
      if (!options.__isAuthFree) toLogin()
      return Promise.reject({ code: 10002, message: '登录已过期' })
    }
    const ok = await tryRefresh()
    if (ok) {
      return service(Object.assign({}, options, { __retried: true }))
    }
    toLogin()
    return Promise.reject({ code: 10002, message: '登录已过期' })
  }

  // 被踢下线
  if (code === 10010) {
    handleKicked(message)
    return Promise.reject({ code, message, kicked: true })
  }

  if (status === 403) {
    if (!options.__silent) toastError('无权限访问')
    return Promise.reject({ code: 10003, message: '无权限访问' })
  }
  if (status === 429) {
    if (!options.__silent) toastError('请求过于频繁，请稍后再试')
    return Promise.reject({ code: 10006, message: '请求过于频繁' })
  }

  if (status !== 200 && !(body && typeof body.code === 'number')) {
    if (!options.__silent) toastError('服务异常(' + status + ')')
    return Promise.reject({ code: status, message: '服务异常(' + status + ')' })
  }

  // 业务错误码（如 50001/50003/50009 等），data 一并透出（交卷幂等需要）
  if (!options.__silent) toastError(message)
  return Promise.reject({ code, message, data: body && body.data })
}

function get(url, params, extra) {
  return service(Object.assign({ url, method: 'GET', params }, extra))
}
function post(url, data, extra) {
  return service(Object.assign({ url, method: 'POST', data }, extra))
}
function put(url, data, extra) {
  return service(Object.assign({ url, method: 'PUT', data }, extra))
}

module.exports = { service, get, post, put, BASE_URL }
