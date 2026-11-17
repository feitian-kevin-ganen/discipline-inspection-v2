import request from './request'

/** MARK-01 阅卷任务列表 */
export const getMarkTasks = (params) => request.get('/admin/mark/tasks', { params })

/** MARK-02 领取阅卷任务 */
export const claimMarkTask = (taskId) => request.post(`/admin/mark/tasks/${taskId}/claim`)

/** MARK-03 待阅答卷题目列表 */
export const getMarkItems = (taskId, params) => request.get(`/admin/mark/tasks/${taskId}/items`, { params })

/** MARK-04 提交评分（批量，含评语） */
export const submitMarkScores = (data) => request.post('/admin/mark/scores', data)
