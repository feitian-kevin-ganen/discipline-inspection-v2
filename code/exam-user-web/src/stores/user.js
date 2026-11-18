import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => {
    // 旧键迁移：web_ 前缀键为空而旧键（与管理端共用的键名）存在时复制一次，
    // 保证改造前已登录的用户端会话不丢失（改造目的：与管理端 localStorage 键隔离，同浏览器双端共存）
    if (!localStorage.getItem('web_accessToken') && localStorage.getItem('accessToken')) {
      for (const k of ['accessToken', 'refreshToken', 'userInfo', 'mustChangePassword']) {
        const v = localStorage.getItem(k)
        if (v != null) localStorage.setItem('web_' + k, v)
      }
    }
    return {
      accessToken: localStorage.getItem('web_accessToken') || '',
      refreshToken: localStorage.getItem('web_refreshToken') || '',
      mustChangePassword: localStorage.getItem('web_mustChangePassword') === '1',
      user: JSON.parse(localStorage.getItem('web_userInfo') || 'null')
    }
  },
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
      localStorage.setItem('web_accessToken', this.accessToken)
      localStorage.setItem('web_refreshToken', this.refreshToken)
      localStorage.setItem('web_userInfo', JSON.stringify(this.user))
      localStorage.setItem('web_mustChangePassword', this.mustChangePassword ? '1' : '0')
    },
    setTokens(data) {
      this.accessToken = data.accessToken
      if (data.refreshToken) this.refreshToken = data.refreshToken
      localStorage.setItem('web_accessToken', this.accessToken)
      localStorage.setItem('web_refreshToken', this.refreshToken)
    },
    setUser(user) {
      this.user = { ...(this.user || {}), ...user }
      localStorage.setItem('web_userInfo', JSON.stringify(this.user))
    },
    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      this.mustChangePassword = false
      localStorage.removeItem('web_accessToken')
      localStorage.removeItem('web_refreshToken')
      localStorage.removeItem('web_userInfo')
      localStorage.removeItem('web_mustChangePassword')
      // 清理改造前的旧键（与管理端共用键名时期的残留）
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('mustChangePassword')
    }
  }
})
