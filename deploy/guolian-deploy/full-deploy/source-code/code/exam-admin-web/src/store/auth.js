import { defineStore } from 'pinia'
import request from '../utils/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('userInfo') || 'null'),
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    mustChangePassword: false
  }),
  getters: {
    isLogin: (s) => !!localStorage.getItem('accessToken'),
    roles: (s) => (s.user && s.user.roles) || [],
    isAdmin: (s) => {
      const rs = (s.user && s.user.roles) || []
      return rs.some((r) => ['SUPER_ADMIN', 'HQ_ADMIN', 'L2_ADMIN', 'SUB_ADMIN', 'MARKER'].includes(r))
    }
  },
  actions: {
    async login(username, password) {
      const data = await request.post('/auth/login', { username, password, terminal: 'PC_ADMIN' })
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('userInfo', JSON.stringify(data.user))
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
        localStorage.setItem('userInfo', JSON.stringify(this.user))
        localStorage.setItem('permissions', JSON.stringify(this.permissions))
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
