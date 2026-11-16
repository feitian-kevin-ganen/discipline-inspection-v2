import request from './request'
import { getDeviceId } from '@/utils'

export const login = (username, password) =>
  request.post('/auth/login', { username, password, terminal: 'PC_APP', deviceId: getDeviceId() })

export const logout = () => request.post('/auth/logout', {}, { silent: true })

export const changePassword = (oldPassword, newPassword) =>
  request.put('/auth/password', { oldPassword, newPassword })

export const getMe = () => request.get('/auth/me')
