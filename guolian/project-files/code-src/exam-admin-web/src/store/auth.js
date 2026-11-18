import { defineStore } from 'pinia'
import request from '../utils/request'

export const useAuthStore = defineStore('auth', {
  state: () => {
    // 旧键迁移：mgmt_ 前缀键为空而旧键（与用户端共用的键名）存在时复制一次，
    // 保证改造前已登录的管理端会话不丢失（改造目的：与用户端 localStorage 键隔离，同浏览器双端共存）
    if (!localStorage.getItem('mgmt_accessToken') && localStorage.getItem('accessToken')) {
      for (const k of ['accessToken', 'refreshToken', 'userInfo', 'permissions']) {
        const v = localStorage.getItem(k)
        if (v != null) localStorage.setItem('mgmt_' + k, v)
      }
    }
    return {
      user: JSON.parse(localStorage.getItem('mgmt_userInfo') || 'null'),
      permissions: JSON.parse(localStorage.getItem('mgmt_permissions') || '[]'),
      mustChangePassword: false
    }
  },
  getters: {
    isLogin: (s) => !!localStorage.getItem('mgmt_accessToken'),
    roles: (s) => (s.user && s.user.roles) || [],
    isAdmin: (s) => {
      const rs = (s.user && s.user.roles) || []
      return rs.some((r) => ['SUPER_ADMIN', 'HQ_ADMIN', 'L2_ADMIN', 'SUB_ADMIN', 'MARKER'].includes(r))
    }
  },
  actions: {
    async login(username, password) {
      const data = await request.post('/auth/login', { username, password, terminal: 'PC_ADMIN' })
      localStorage.setItem('mgmt_accessToken', data.accessToken)
      localStorage.setItem('mgmt_refreshToken', data.refreshToken)
      localStorage.setItem('mgmt_userInfo', JSON.stringify(data.user))
      this.user = data.user
      this.mustChangePassword = !!data.mustChangePassword
      return data
    },
    // 国联 APP/门户单点免密登录：ticket 换 token，存储方式与普通登录一致
    async loginByTicket(ticket) {
      const data = await request.post('/integration/ticket/login', { ticket, terminal: 'PC_ADMIN' })
      localStorage.setItem('mgmt_accessToken', data.accessToken)
      localStorage.setItem('mgmt_refreshToken', data.refreshToken)
      localStorage.setItem('mgmt_userInfo', JSON.stringify(data.user))
      this.user = data.user
      this.mustChangePassword = !!data.mustChangePassword
      return data
    },
    async fetchMe() {
      try {
        const data = await request.get('/auth/me')
        this.user = {
          userId: data.userId,
          name: data.name,
          jobNo: data.jobNo,
          orgId: data.orgId,
          orgName: data.orgName,
          roles: data.roles || []
        }
        this.permissions = data.permissions || []
        localStorage.setItem('mgmt_userInfo', JSON.stringify(this.user))
        localStorage.setItem('mgmt_permissions', JSON.stringify(this.permissions))
      } catch (e) {
        // token 失效时拦截器已处理跳转
      }
    },
    async logout() {
      try {
        await request.post('/auth/logout')
      } catch (e) {
        // 忽略
      }
      localStorage.removeItem('mgmt_accessToken')
      localStorage.removeItem('mgmt_refreshToken')
      localStorage.removeItem('mgmt_userInfo')
      localStorage.removeItem('mgmt_permissions')
      // 清理改造前的旧键（与用户端共用键名时期的残留）
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('permissions')
      this.user = null
      this.permissions = []
    },
    hasRole(...codes) {
      return this.roles.some((r) => codes.includes(r))
    }
  }
})
