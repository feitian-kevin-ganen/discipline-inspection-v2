// 鉴权状态：token 持久化、强制改密标记、当前用户
import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

const TOKEN_KEY = 'h5_auth'

function loadLocal() {
  try {
    return JSON.parse(localStorage.getItem(TOKEN_KEY)) || {}
  } catch (e) {
    return {}
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const local = loadLocal()
    return {
      accessToken: local.accessToken || '',
      refreshToken: local.refreshToken || '',
      user: local.user || null,
      mustChangePassword: !!local.mustChangePassword
    }
  },
  getters: {
    isLogin: (s) => !!s.accessToken
  },
  actions: {
    persist() {
      localStorage.setItem(
        TOKEN_KEY,
        JSON.stringify({
          accessToken: this.accessToken,
          refreshToken: this.refreshToken,
          user: this.user,
          mustChangePassword: this.mustChangePassword
        })
      )
    },
    async login(username, password) {
      const data = await authApi.login({ username, password })
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
      this.mustChangePassword = !!data.mustChangePassword
      this.persist()
      return data
    },
    setTokens(accessToken, refreshToken) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      this.persist()
    },
    async fetchMe() {
      const data = await authApi.me()
      this.user = {
        userId: data.userId,
        name: data.name,
        jobNo: data.jobNo,
        orgId: data.orgId,
        orgName: data.orgName,
        roles: data.roles
      }
      this.persist()
      return data
    },
    clearPasswordFlag() {
      this.mustChangePassword = false
      this.persist()
    },
    // 本地清理（不请求后端，用于被踢下线等场景）
    clearLocal() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      this.mustChangePassword = false
      localStorage.removeItem(TOKEN_KEY)
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 登出接口失败不阻塞本地退出
      }
      this.clearLocal()
    }
  }
})
