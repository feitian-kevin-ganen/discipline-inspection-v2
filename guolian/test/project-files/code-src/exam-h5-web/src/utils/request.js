import axios from 'axios'
import { showToast, showDialog } from 'vant'
import { useAuthStore } from '../stores/auth'
import router from '../router'

// 统一响应 {code,message,data}；code=0 成功；业务错误 toast；10010 踢下线；10002 静默刷新重放
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 20000
})

let refreshingPromise = null
let kickedDialogShowing = false

service.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

function isNetworkError(error) {
  return !error.response && (error.code === 'ECONNABORTED' || error.message === 'Network Error' || !error.status)
}

// 被踢下线 / token 强制失效：提示一次并回登录页
function handleKicked(message) {
  const auth = useAuthStore()
  auth.clearLocal()
  if (!kickedDialogShowing) {
    kickedDialogShowing = true
    showDialog({
      title: '提示',
      message: message || '账号已在其他终端登录，您已被强制下线',
      confirmButtonText: '重新登录'
    }).finally(() => {
      kickedDialogShowing = false
      router.replace('/login')
    })
  } else {
    router.replace('/login')
  }
}

function toLogin() {
  const auth = useAuthStore()
  auth.clearLocal()
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
}

// 其他标签页可能已完成刷新（refreshToken 轮换后本页旧凭证失效），
// 刷新失败时稍等并重读 localStorage，若凭证已被更新则直接重放，避免误报"登录已过期"
async function retryWithLatestToken(config) {
  const auth = useAuthStore()
  const usedToken = (config.headers && config.headers.Authorization || '').replace('Bearer ', '')
  await new Promise((r) => setTimeout(r, 300))
  let latest = null
  try {
    latest = JSON.parse(localStorage.getItem('h5_auth') || '{}')
  } catch (e) {
    latest = null
  }
  if (latest && latest.accessToken && latest.accessToken !== usedToken) {
    auth.setTokens(latest.accessToken, latest.refreshToken || auth.refreshToken)
    return service(config)
  }
  return null
}

async function tryRefresh() {
  if (!refreshingPromise) {
    const auth = useAuthStore()
    refreshingPromise = axios
      .post('/api/v1/auth/refresh', { refreshToken: auth.refreshToken })
      .then((res) => {
        if (res.data && res.data.code === 0) {
          auth.setTokens(res.data.data.accessToken, res.data.data.refreshToken)
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

service.interceptors.response.use(
  async (response) => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    const code = body ? body.code : -1
    const message = (body && body.message) || '请求失败'
    const config = response.config

    if (code === 10010) {
      handleKicked(message)
      return Promise.reject({ code, message, kicked: true })
    }
    if (code === 10002) {
      // 白名单接口（登录/刷新）失败直接抛错，不做重放
      if (config.__isAuthFree || config.__retried) {
        if (!config.__isAuthFree) toLogin()
        return Promise.reject({ code, message })
      }
      config.__retried = true
      const ok = await tryRefresh()
      if (ok) return service(config)
      const retried = await retryWithLatestToken(config)
      if (retried) return retried
      toLogin()
      return Promise.reject({ code, message })
    }
    if (!config.__silent) {
      showToast({ message, type: 'fail' })
    }
    return Promise.reject({ code, message, data: body && body.data })
  },
  async (error) => {
    const config = error.config || {}
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        if (config.__isAuthFree || config.__retried) {
          if (!config.__isAuthFree) toLogin()
          return Promise.reject({ code: 10002, message: '登录已过期' })
        }
        config.__retried = true
        const ok = await tryRefresh()
        if (ok) return service(config)
        const retried = await retryWithLatestToken(config)
        if (retried) return retried
        toLogin()
        return Promise.reject({ code: 10002, message: '登录已过期' })
      }
      if (status === 403) {
        if (!config.__silent) showToast({ message: '无权限访问', type: 'fail' })
        return Promise.reject({ code: 10003, message: '无权限访问' })
      }
      if (status === 429) {
        if (!config.__silent) showToast({ message: '请求过于频繁，请稍后再试', type: 'fail' })
        return Promise.reject({ code: 10006, message: '请求过于频繁' })
      }
      if (!config.__silent) showToast({ message: `服务异常(${status})`, type: 'fail' })
      return Promise.reject({ code: status, message: `服务异常(${status})` })
    }
    // 网络错误：不 toast，交由调用方（如答题本地队列）处理
    return Promise.reject({ code: -1, message: '网络连接异常', networkError: isNetworkError(error) })
  }
)

export default service
