import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 60000
})

function getToken() {
  return localStorage.getItem('accessToken') || ''
}

let refreshing = null

// 其他标签页可能已完成刷新（refreshToken 轮换后本页旧凭证失效），
// 刷新失败时稍等并重读 localStorage，若凭证已被更新则直接重放，避免误报"登录已过期"
async function retryWithLatestToken(config) {
  const usedToken = (config.headers && config.headers.Authorization || '').replace('Bearer ', '')
  await new Promise((r) => setTimeout(r, 300))
  const latest = getToken()
  if (latest && latest !== usedToken) {
    return request(config)
  }
  return null
}

async function refreshToken() {
  const rt = localStorage.getItem('refreshToken')
  if (!rt) throw new Error('no refresh token')
  const res = await axios.post('/api/v1/auth/refresh', { refreshToken: rt })
  if (res.data && res.data.code === 0) {
    localStorage.setItem('accessToken', res.data.data.accessToken)
    localStorage.setItem('refreshToken', res.data.data.refreshToken)
    return res.data.data.accessToken
  }
  throw new Error((res.data && res.data.message) || '刷新失败')
}

function toLogin() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  async (response) => {
    // 文件下载（blob）直接放行
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      // 登录接口的业务错误（如凭证错误也返回 10002）单独处理，不走登录失效逻辑
      if (response.config.url && response.config.url.includes('/auth/login')) {
        ElMessage.error(body.code === 10002 ? '账号或密码错误' : (body.message || '登录失败'))
        return Promise.reject(new Error(body.message || '登录失败'))
      }
      if (body.code === 10002 || body.code === 10010) {
        // 先尝试静默刷新一次并重放
        if (!response.config.__retried) {
          try {
            refreshing = refreshing || refreshToken()
            await refreshing
            refreshing = null
            response.config.__retried = true
            return request(response.config)
          } catch (e) {
            refreshing = null
            const retried = await retryWithLatestToken(response.config)
            if (retried) return retried
            ElMessage.error(body.code === 10010 ? '账号已在其他终端登录，请重新登录' : '登录已过期，请重新登录')
            toLogin()
            return Promise.reject(new Error(body.message))
          }
        }
        ElMessage.error('登录已过期，请重新登录')
        toLogin()
        return Promise.reject(new Error(body.message))
      }
      if (!response.config.__silent) ElMessage.error(body.message || '请求失败')
      const bizErr = new Error(body.message || '请求失败')
      bizErr.data = body.data
      return Promise.reject(bizErr)
    }
    return body
  },
  async (error) => {
    const { response, config } = error
    if (response && response.status === 401 && config && !config.__retried) {
      try {
        refreshing = refreshing || refreshToken()
        await refreshing
        refreshing = null
        config.__retried = true
        return request(config)
      } catch (e) {
        refreshing = null
        const retried = await retryWithLatestToken(config)
        if (retried) return retried
        ElMessage.error('登录已过期，请重新登录')
        toLogin()
        return Promise.reject(error)
      }
    }
    if (response && response.status === 403) {
      if (!config || !config.__silent) ElMessage.error('无权限访问')
    } else if (response && response.data && response.data.message) {
      if (!config || !config.__silent) ElMessage.error(response.data.message)
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
