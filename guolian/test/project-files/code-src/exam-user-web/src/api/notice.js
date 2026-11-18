import request from './request'

/** SYS-08 本人站内消息列表 */
export const getNotices = (params) => request.get('/app/notices', { params })

/** SYS-09 标记消息已读（noticeId=0 全部已读） */
export const markNoticeRead = (noticeId) => request.put(`/app/notices/${noticeId}/read`)
