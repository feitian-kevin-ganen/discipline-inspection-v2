import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

let refreshing = null

function doRefreshToken() {
  const store = useUserStore()
  if (!store.refreshToken) return Promise.reject(new Error('no refresh token'))
  if (!refreshing) {
    refreshing = axios
      .post('/api/v1/auth/refresh', { refreshToken: store.refreshToken }, { timeout: 15000 })
      .then((res) => {
        if (res.data && res.data.code === 0) {
          store.setTokens(res.data.data)
          return res.data.data.accessToken
        }
        return Promise.reject(res.data)
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

function forceLogin(msg) {
  const store = useUserStore()
  store.logout()
  if (msg) ElMessage.error(msg)
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

// 其他标签页可能已完成刷新（refreshToken 轮换后本页旧凭证失效），
// 刷新失败时稍等并重读 localStorage，若凭证已被更新则直接重放，避免误报"登录已过期"
async function retryWithLatestToken(config) {
  const usedToken = (config.headers && config.headers.Authorization || '').replace('Bearer ', '')
  await new Promise((r) => setTimeout(r, 300))
  const latest = localStorage.getItem('web_accessToken') || ''
  if (latest && latest !== usedToken) {
    const store = useUserStore()
    store.accessToken = latest
    store.refreshToken = localStorage.getItem('web_refreshToken') || store.refreshToken
    return request(config)
  }
  return null
}

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.accessToken) {
    config.headers.Authorization = `Bearer ${store.accessToken}`
  }
  return config
})

request.interceptors.response.use(
  async (response) => {
    const body = response.data
    const config = response.config
    if (!body || typeof body.code === 'undefined') return body
    if (body.code === 0) return body.data
    // token 过期：静默刷新后重放一次（登录类白名单接口除外，避免无效刷新和跳转死循环）
    if (body.code === 10002 && !config._retried
        && !config.url.includes('/auth/') && !config.url.includes('/integration/')) {
      config._retried = true
      try {
        await doRefreshToken()
        return request(config)
      } catch (e) {
        const retried = await retryWithLatestToken(config)
        if (retried) return retried
        forceLogin('登录已过期，请重新登录')
        return Promise.reject(body)
      }
    }
    // 异地登录被踢下线
    if (body.code === 10010) {
      forceLogin('账号已在其他终端登录，您已被强制下线')
      return Promise.reject(body)
    }
    if (!config.silent) ElMessage.error(body.message || '请求失败')
    return Promise.reject(body)
  },
  async (error) => {
    const { response, config } = error
    if (response) {
      if (response.status === 401 && config && !config._retried
          && !config.url.includes('/auth/') && !config.url.includes('/integration/')) {
        config._retried = true
        try {
          await doRefreshToken()
          return request(config)
        } catch (e) {
          const retried = await retryWithLatestToken(config)
          if (retried) return retried
          forceLogin('登录已过期，请重新登录')
          return Promise.reject(error)
        }
      }
      if (response.status === 401) {
        forceLogin('登录已过期，请重新登录')
      } else if (!config || !config.silent) {
        if (response.status === 403) ElMessage.error('无权限访问')
        else ElMessage.error(response.data?.message || `请求失败(${response.status})`)
      }
    } else if (!config || !config.silent) {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
