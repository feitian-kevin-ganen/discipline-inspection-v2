// 鉴权状态：token / 用户 / 强制改密标记 的本地持久化（对齐 H5 端 stores/auth.js）
const TOKEN_KEY = 'mp_auth'

function loadLocal() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || {}
  } catch (e) {
    return {}
  }
}

function getAuth() {
  return loadLocal()
}

function getToken() {
  return loadLocal().accessToken || ''
}

function isLogin() {
  return !!getToken()
}

// 登录成功：data = { accessToken, refreshToken, mustChangePassword, user }
function saveLogin(data) {
  wx.setStorageSync(TOKEN_KEY, {
    accessToken: data.accessToken || '',
    refreshToken: data.refreshToken || '',
    user: data.user || null,
    mustChangePassword: !!data.mustChangePassword
  })
}

// 刷新 token 后回写
function setTokens(accessToken, refreshToken) {
  const local = loadLocal()
  local.accessToken = accessToken
  local.refreshToken = refreshToken
  wx.setStorageSync(TOKEN_KEY, local)
}

function saveUser(user) {
  const local = loadLocal()
  local.user = user
  wx.setStorageSync(TOKEN_KEY, local)
}

function clearPasswordFlag() {
  const local = loadLocal()
  local.mustChangePassword = false
  wx.setStorageSync(TOKEN_KEY, local)
}

function clearAuth() {
  wx.removeStorageSync(TOKEN_KEY)
}

// 路由守卫：页面 onShow 调用；未登录跳登录页，首登强制改密跳改密页
// opts.allowChangePassword: 改密页自身调用时置 true，避免自我跳转死循环
function ensureAuth(opts) {
  const local = loadLocal()
  if (!local.accessToken) {
    wx.reLaunch({ url: '/pages/login/login' })
    return false
  }
  if (local.mustChangePassword && !(opts && opts.allowChangePassword)) {
    wx.reLaunch({ url: '/pages/change-password/change-password' })
    return false
  }
  return true
}

module.exports = {
  getAuth,
  getToken,
  isLogin,
  saveLogin,
  setTokens,
  saveUser,
  clearPasswordFlag,
  clearAuth,
  ensureAuth
}
