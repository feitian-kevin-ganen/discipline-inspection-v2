import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    mustChangePassword: localStorage.getItem('mustChangePassword') === '1',
    user: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    roles: (s) => s.user?.roles || [],
    isMarker: (s) => (s.user?.roles || []).includes('MARKER'),
    isLoggedIn: (s) => !!s.accessToken
  },
  actions: {
    setLogin(data) {
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
      this.mustChangePassword = !!data.mustChangePassword
      localStorage.setItem('accessToken', this.accessToken)
      localStorage.setItem('refreshToken', this.refreshToken)
      localStorage.setItem('userInfo', JSON.stringify(this.user))
      localStorage.setItem('mustChangePassword', this.mustChangePassword ? '1' : '0')
    },
    setTokens(data) {
      this.accessToken = data.accessToken
      if (data.refreshToken) this.refreshToken = data.refreshToken
      localStorage.setItem('accessToken', this.accessToken)
      localStorage.setItem('refreshToken', this.refreshToken)
    },
    setUser(user) {
      this.user = { ...(this.user || {}), ...user }
      localStorage.setItem('userInfo', JSON.stringify(this.user))
    },
    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      this.mustChangePassword = false
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('mustChangePassword')
    }
  }
})
