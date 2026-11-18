import service from '../utils/request'
import { getDeviceId } from '../utils/device'

// AUTH-01 登录（白名单）
export function login({ username, password }) {
  return service.post(
    '/auth/login',
    { username, password, terminal: 'H5', deviceId: getDeviceId() },
    { __isAuthFree: true }
  )
}

// AUTH-02 登出
export function logout() {
  return service.post('/auth/logout')
}

// AUTH-04 修改密码
export function changePassword({ oldPassword, newPassword }) {
  return service.put('/auth/password', { oldPassword, newPassword })
}

// AUTH-05 当前用户
export function me() {
  return service.get('/auth/me')
}
