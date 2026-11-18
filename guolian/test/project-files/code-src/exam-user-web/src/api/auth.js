import request from './request'
import { getDeviceId } from '@/utils'

export const login = (username, password) =>
  request.post('/auth/login', { username, password, terminal: 'PC_APP', deviceId: getDeviceId() })

// 国联 APP/门户单点免密登录（白名单接口，ticket 一次性）
export const ticketLogin = (ticket) =>
  request.post('/integration/ticket/login', { ticket, terminal: 'PC_APP', deviceId: getDeviceId() })

export const logout = () => request.post('/auth/logout', {}, { silent: true })

export const changePassword = (oldPassword, newPassword) =>
  request.put('/auth/password', { oldPassword, newPassword })

export const getMe = () => request.get('/auth/me')
